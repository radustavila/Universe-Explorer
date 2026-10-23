#!/usr/bin/env python3
"""Builds the sky map's bundled catalogue from HYG, d3-celestial and OpenNGC.

Writes three compact binary files (big-endian, read by ResourceSkyCatalog) into
core/skymap/src/main/resources/com/radustavila/nasaexplorer/core/skymap/:

  stars.bin           every HYG v4.4 star down to magnitude 6.5 (the naked-eye limit under a dark
                      sky), brightest first, with its proper name when it has one.
                      Derived from the HYG database: CC BY-SA 4.0, see stars-LICENSE.txt.
  constellations.bin  the 88 IAU constellations' stick figures and label points from d3-celestial
                      (Serpens comes in two parts). BSD-3-Clause, see constellations-LICENSE.txt.
  deepsky.bin         galaxies, clusters and nebulae from OpenNGC: every object of visual magnitude
                      below 12, plus all 110 Messier and 109 Caldwell objects whatever their
                      brightness, brightest first. Caldwell numbers come from the hand-made table
                      CALDWELL below (OpenNGC has no Caldwell column). Derived from OpenNGC:
                      CC BY-SA 4.0, see deepsky-LICENSE.txt.

Run from the repository root with Python 3 (standard library only):

  python3 core/skymap/tools/build_sky_catalog.py

It downloads the pinned sources below, or reads local copies given with --hyg, --lines, --names,
--ngc and --addendum. --parts rebuilds only some files (HYG's URL follows its latest release, so
rebuilding stars.bin may change it). Commit the regenerated .bin files together with any change to this script and to
ResourceSkyCatalog's reader.

Do not add Stellarium sky-culture data here: it is GPL.
"""

import argparse
import csv
import gzip
import io
import json
import os
import re
import struct
import sys
import urllib.request

HYG_URL = "https://codeberg.org/astronexus/hyg/media/branch/main/data/hyg/CURRENT/hyg_v44.csv.gz"
D3_BASE = "https://raw.githubusercontent.com/ofrohn/d3-celestial/master/data/"
LINES_URL = D3_BASE + "constellations.lines.json"
NAMES_URL = D3_BASE + "constellations.json"

OPENNGC_BASE = "https://raw.githubusercontent.com/mattiaverga/OpenNGC/v20260501/database_files/"
NGC_URL = OPENNGC_BASE + "NGC.csv"
ADDENDUM_URL = OPENNGC_BASE + "addendum.csv"

MAGNITUDE_LIMIT = 6.5
STARS_MAGIC = 0x534B5953  # "SKYS"
CONSTELLATIONS_MAGIC = 0x534B5943  # "SKYC"
FORMAT_VERSION = 1
NO_COLOUR_INDEX = -32768
NO_NAME = -1

DEEP_SKY_MAGIC = 0x534B5944  # "SKYD"
DEEP_SKY_MAGNITUDE_LIMIT = 12.0
NO_MAGNITUDE = -32768
NO_ANGLE = -1

# OpenNGC type codes to the app's DeepSkyType ordinals (core/model DeepSky.kt; keep in step).
GALAXY, OPEN_CLUSTER, GLOBULAR_CLUSTER, PLANETARY_NEBULA, NEBULA, SUPERNOVA_REMNANT, DARK_NEBULA, STAR_GROUP = range(8)
DEEP_SKY_TYPES = {
    "G": GALAXY, "GPair": GALAXY, "GTrpl": GALAXY, "GGroup": GALAXY,
    "OCl": OPEN_CLUSTER,
    "GCl": GLOBULAR_CLUSTER,
    "PN": PLANETARY_NEBULA,
    "Neb": NEBULA, "EmN": NEBULA, "RfN": NEBULA, "HII": NEBULA, "Cl+N": NEBULA,
    "SNR": SUPERNOVA_REMNANT,
    "DrkN": DARK_NEBULA,
    "*Ass": STAR_GROUP, "**": STAR_GROUP, "*": STAR_GROUP, "Other": STAR_GROUP,
}
# Single and double stars, and the odd "Other", only come in when Messier or Caldwell lists them.
LISTED_ONLY_TYPES = {"*", "**", "Other"}

# OpenNGC marks M102 as a duplicate of M101; like NASA's Hubble Messier Catalog, the app takes the
# Spindle Galaxy, NGC 5866.
MESSIER_OVERRIDES = {"NGC5866": 102}

# The Double Cluster is two open clusters; OpenNGC lists the pair as an association.
TYPE_OVERRIDES = {"C014": OPEN_CLUSTER}

# Bright Caldwell clusters OpenNGC gives no magnitude, from the Caldwell catalogue's own table, so
# the app does not call them telescope objects.
MAGNITUDE_OVERRIDES = {"C014": 4.3, "C041": 0.5, "IC2602": 1.9, "NGC4755": 4.2, "NGC6193": 5.2}

# The Coalsack (C99) has no size in OpenNGC; about 7 by 5 degrees.
AXIS_OVERRIDES = {"C099": (420.0, 300.0)}

# Caldwell number -> OpenNGC name, from NASA's Hubble Caldwell Catalog (a factual list). C9, C14,
# C41 and C99 are in OpenNGC's addendum under their Caldwell numbers.
CALDWELL = {
    1: "NGC0188", 2: "NGC0040", 3: "NGC4236", 4: "NGC7023", 5: "IC0342", 6: "NGC6543",
    7: "NGC2403", 8: "NGC0559", 9: "C009", 10: "NGC0663", 11: "NGC7635", 12: "NGC6946",
    13: "NGC0457", 14: "C014", 15: "NGC6826", 16: "NGC7243", 17: "NGC0147", 18: "NGC0185",
    19: "IC5146", 20: "NGC7000", 21: "NGC4449", 22: "NGC7662", 23: "NGC0891", 24: "NGC1275",
    25: "NGC2419", 26: "NGC4244", 27: "NGC6888", 28: "NGC0752", 29: "NGC5005", 30: "NGC7331",
    31: "IC0405", 32: "NGC4631", 33: "NGC6992", 34: "NGC6960", 35: "NGC4889", 36: "NGC4559",
    37: "NGC6885", 38: "NGC4565", 39: "NGC2392", 40: "NGC3626", 41: "C041", 42: "NGC7006",
    43: "NGC7814", 44: "NGC7479", 45: "NGC5248", 46: "NGC2261", 47: "NGC6934", 48: "NGC2775",
    49: "NGC2237", 50: "NGC2244", 51: "IC1613", 52: "NGC4697", 53: "NGC3115", 54: "NGC2506",
    55: "NGC7009", 56: "NGC0246", 57: "NGC6822", 58: "NGC2360", 59: "NGC3242", 60: "NGC4038",
    61: "NGC4039", 62: "NGC0247", 63: "NGC7293", 64: "NGC2362", 65: "NGC0253", 66: "NGC5694",
    67: "NGC1097", 68: "NGC6729", 69: "NGC6302", 70: "NGC0300", 71: "NGC2477", 72: "NGC0055",
    73: "NGC1851", 74: "NGC3132", 75: "NGC6124", 76: "NGC6231", 77: "NGC5128", 78: "NGC6541",
    79: "NGC3201", 80: "NGC5139", 81: "NGC6352", 82: "NGC6193", 83: "NGC4945", 84: "NGC5286",
    85: "IC2391", 86: "NGC6397", 87: "NGC1261", 88: "NGC5823", 89: "NGC6087", 90: "NGC2867",
    91: "NGC3532", 92: "NGC3372", 93: "NGC6752", 94: "NGC4755", 95: "NGC6025", 96: "NGC2516",
    97: "NGC3766", 98: "NGC4609", 99: "C099", 100: "IC2944", 101: "NGC6744", 102: "IC2602",
    103: "NGC2070", 104: "NGC0362", 105: "NGC4833", 106: "NGC0104", 107: "NGC6101", 108: "NGC4372",
    109: "NGC3195",
}

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    os.pardir, "src", "main", "resources", "com", "radustavila", "nasaexplorer", "core", "skymap",
)


def read_source(path, url):
    if path:
        with open(path, "rb") as file:
            return file.read()
    print("Downloading " + url, file=sys.stderr)
    with urllib.request.urlopen(url) as response:
        return response.read()


def write_utf(out, text):
    # DataInput.readUTF: a two-byte length, then the bytes. Names are BMP-only and NUL-free, where
    # Java's modified UTF-8 and UTF-8 agree.
    data = text.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def build_stars(raw):
    text = gzip.decompress(raw).decode("utf-8") if raw[:2] == b"\x1f\x8b" else raw.decode("utf-8")
    stars = []
    for row in csv.DictReader(io.StringIO(text)):
        if row["id"] == "0":  # The Sun.
            continue
        try:
            magnitude = float(row["mag"])
        except ValueError:
            continue
        if magnitude > MAGNITUDE_LIMIT:
            continue
        try:
            colour_index = max(-32767, min(32767, round(float(row["ci"]) * 1000)))
        except ValueError:
            colour_index = NO_COLOUR_INDEX
        stars.append((
            magnitude,
            float(row["ra"]) * 15.0,  # Hours to degrees.
            float(row["dec"]),
            colour_index,
            row["proper"].strip(),
        ))
    stars.sort(key=lambda star: star[0])

    names = []
    out = io.BytesIO()
    out.write(struct.pack(">iii", STARS_MAGIC, FORMAT_VERSION, len(stars)))
    for magnitude, ra, dec, colour_index, name in stars:
        name_index = NO_NAME
        if name:
            name_index = len(names)
            names.append(name)
        out.write(struct.pack(">ffhhh", ra, dec, round(magnitude * 100), colour_index, name_index))
    out.write(struct.pack(">i", len(names)))
    for name in names:
        write_utf(out, name)
    return out.getvalue(), len(stars), len(names)


def build_constellations(lines_raw, names_raw):
    lines = json.loads(lines_raw)["features"]
    labels = json.loads(names_raw)["features"]
    if len(lines) != len(labels):
        raise SystemExit("constellations.lines.json and constellations.json disagree")
    out = io.BytesIO()
    out.write(struct.pack(">iii", CONSTELLATIONS_MAGIC, FORMAT_VERSION, len(lines)))
    for figure, label in zip(lines, labels):
        if figure["id"] != label["id"]:
            raise SystemExit("Constellation order differs: " + figure["id"] + " / " + label["id"])
        label_ra, label_dec = label["geometry"]["coordinates"]
        write_utf(out, figure["id"])
        write_utf(out, label["properties"]["name"])
        out.write(struct.pack(">ffb", label_ra % 360.0, label_dec, int(label["properties"]["rank"])))
        polylines = figure["geometry"]["coordinates"]
        out.write(struct.pack(">h", len(polylines)))
        for polyline in polylines:
            out.write(struct.pack(">h", len(polyline)))
            for ra, dec in polyline:
                # d3-celestial keeps right ascension in -180..180 degrees.
                out.write(struct.pack(">ff", ra % 360.0, dec))
    return out.getvalue(), len(lines)


def sexagesimal(text, scale):
    sign = -1.0 if text.startswith("-") else 1.0
    parts = [float(part) for part in text.lstrip("+-").split(":")]
    return sign * (parts[0] + parts[1] / 60.0 + parts[2] / 3600.0) * scale


def designation(name):
    """ "NGC0224" -> "NGC 224", "Mel022" -> "Mel 22"; empty for the addendum's bare Messier and Caldwell rows. """
    match = re.match(r"^([A-Za-z]+)0*(\d.*)$", name)
    if not match or match.group(1) in ("M", "C"):
        return ""
    return match.group(1) + " " + match.group(2)


def optional_float(text):
    try:
        return float(text)
    except ValueError:
        return None


def master(by_name, name):
    row = by_name[name]
    if row["Type"] != "Dup":
        return name
    if row["NGC"]:
        return "NGC" + row["NGC"].zfill(4)
    return "IC" + row["IC"].zfill(4)


def build_deep_sky(ngc_raw, addendum_raw):
    rows = []
    for raw in (ngc_raw, addendum_raw):
        rows += csv.DictReader(io.StringIO(raw.decode("utf-8")), delimiter=";")
    by_name = {row["Name"]: row for row in rows}
    # NGC 2244 (C50) and NGC 6885 (C37) are duplicates in OpenNGC: their NGC column names the entry
    # that holds the data.
    caldwell_of = {master(by_name, name): number for number, name in CALDWELL.items()}
    objects = []
    for row in rows:
        name = row["Name"]
        kind = row["Type"]
        messier = MESSIER_OVERRIDES.get(name) or (int(row["M"]) if row["M"] and kind != "Dup" else 0)
        caldwell = caldwell_of.get(name, 0)
        if kind not in DEEP_SKY_TYPES:  # Dup, NonEx, Nova.
            if messier or caldwell:
                raise SystemExit(f"{name} is listed but has type {kind}")
            continue
        magnitude = MAGNITUDE_OVERRIDES.get(name, optional_float(row["V-Mag"]))
        if magnitude is None:
            magnitude = optional_float(row["B-Mag"])
        major, minor = AXIS_OVERRIDES.get(name, (optional_float(row["MajAx"]), optional_float(row["MinAx"])))
        is_bright = magnitude is not None and magnitude < DEEP_SKY_MAGNITUDE_LIMIT and kind not in LISTED_ONLY_TYPES
        if not (messier or caldwell or is_bright):
            continue
        constellation = row["Const"]
        if constellation in ("Se1", "Se2"):
            constellation = "Ser"
        common = row["Common names"].split(",")[0].strip()
        objects.append({
            "name": name,
            "designation": designation(name),
            "type": TYPE_OVERRIDES.get(name, DEEP_SKY_TYPES[kind]),
            "ra": sexagesimal(row["RA"], 15.0),
            "dec": sexagesimal(row["Dec"], 1.0),
            "constellation": constellation,
            "magnitude": magnitude,
            "major": major,
            "minor": minor,
            "angle": optional_float(row["PosAng"]),
            "messier": messier,
            "caldwell": caldwell,
            "common": common,
        })
    messier = sorted(o["messier"] for o in objects if o["messier"])
    caldwell = sorted(o["caldwell"] for o in objects if o["caldwell"])
    if messier != list(range(1, 111)):
        raise SystemExit("Messier objects missing or doubled: " + str(messier))
    if caldwell != list(range(1, 110)):
        raise SystemExit("Caldwell objects missing or doubled: " + str(caldwell))
    # Brightest first, those without a magnitude last; the name keeps the order stable.
    objects.sort(key=lambda o: (o["magnitude"] is None, o["magnitude"] or 0.0, o["name"]))

    out = io.BytesIO()
    out.write(struct.pack(">iii", DEEP_SKY_MAGIC, FORMAT_VERSION, len(objects)))
    for o in objects:
        write_utf(out, o["name"])
        write_utf(out, o["designation"])
        write_utf(out, o["common"])
        write_utf(out, o["constellation"])
        out.write(struct.pack(
            ">bffhhhhbb",
            o["type"],
            o["ra"],
            o["dec"],
            NO_MAGNITUDE if o["magnitude"] is None else round(o["magnitude"] * 100),
            round((o["major"] or 0.0) * 10),
            round((o["minor"] or 0.0) * 10),
            NO_ANGLE if o["angle"] is None else round(o["angle"]),
            o["messier"],
            o["caldwell"],
        ))
    return out.getvalue(), len(objects)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--hyg", help="local hyg_v44.csv or hyg_v44.csv.gz")
    parser.add_argument("--lines", help="local d3-celestial constellations.lines.json")
    parser.add_argument("--names", help="local d3-celestial constellations.json")
    parser.add_argument("--ngc", help="local OpenNGC NGC.csv")
    parser.add_argument("--addendum", help="local OpenNGC addendum.csv")
    parser.add_argument("--out", default=OUTPUT_DIR, help="output directory")
    parser.add_argument(
        "--parts",
        default="stars,constellations,deepsky",
        help="comma-separated files to rebuild: stars, constellations, deepsky",
    )
    args = parser.parse_args()
    parts = set(args.parts.split(","))

    os.makedirs(args.out, exist_ok=True)
    if "stars" in parts:
        stars, star_count, name_count = build_stars(read_source(args.hyg, HYG_URL))
        with open(os.path.join(args.out, "stars.bin"), "wb") as file:
            file.write(stars)
        print(f"stars.bin: {star_count} stars, {name_count} names, {len(stars)} bytes")
    if "constellations" in parts:
        constellations, figure_count = build_constellations(
            read_source(args.lines, LINES_URL),
            read_source(args.names, NAMES_URL),
        )
        with open(os.path.join(args.out, "constellations.bin"), "wb") as file:
            file.write(constellations)
        print(f"constellations.bin: {figure_count} figures, {len(constellations)} bytes")
    if "deepsky" in parts:
        deep_sky, object_count = build_deep_sky(
            read_source(args.ngc, NGC_URL),
            read_source(args.addendum, ADDENDUM_URL),
        )
        with open(os.path.join(args.out, "deepsky.bin"), "wb") as file:
            file.write(deep_sky)
        print(f"deepsky.bin: {object_count} objects, {len(deep_sky)} bytes")


if __name__ == "__main__":
    main()
