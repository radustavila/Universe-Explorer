#!/usr/bin/env python3
"""Builds the sky map's bundled catalogue from HYG and d3-celestial.

Writes two compact binary files (big-endian, read by ResourceSkyCatalog) into
core/skymap/src/main/resources/com/radustavila/nasaexplorer/core/skymap/:

  stars.bin           every HYG v4.4 star down to magnitude 6.5 (the naked-eye limit under a dark
                      sky), brightest first, with its proper name when it has one.
                      Derived from the HYG database: CC BY-SA 4.0, see stars-LICENSE.txt.
  constellations.bin  the 88 IAU constellations' stick figures and label points from d3-celestial
                      (Serpens comes in two parts). BSD-3-Clause, see constellations-LICENSE.txt.

Run from the repository root with Python 3 (standard library only):

  python3 core/skymap/tools/build_sky_catalog.py

It downloads the pinned sources below, or reads local copies given with --hyg, --lines and
--names. Commit the regenerated .bin files together with any change to this script and to
ResourceSkyCatalog's reader.

Do not add Stellarium sky-culture data here: it is GPL.
"""

import argparse
import csv
import gzip
import io
import json
import os
import struct
import sys
import urllib.request

HYG_URL = "https://codeberg.org/astronexus/hyg/media/branch/main/data/hyg/CURRENT/hyg_v44.csv.gz"
D3_BASE = "https://raw.githubusercontent.com/ofrohn/d3-celestial/master/data/"
LINES_URL = D3_BASE + "constellations.lines.json"
NAMES_URL = D3_BASE + "constellations.json"

MAGNITUDE_LIMIT = 6.5
STARS_MAGIC = 0x534B5953  # "SKYS"
CONSTELLATIONS_MAGIC = 0x534B5943  # "SKYC"
FORMAT_VERSION = 1
NO_COLOUR_INDEX = -32768
NO_NAME = -1

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


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--hyg", help="local hyg_v44.csv or hyg_v44.csv.gz")
    parser.add_argument("--lines", help="local d3-celestial constellations.lines.json")
    parser.add_argument("--names", help="local d3-celestial constellations.json")
    parser.add_argument("--out", default=OUTPUT_DIR, help="output directory")
    args = parser.parse_args()

    stars, star_count, name_count = build_stars(read_source(args.hyg, HYG_URL))
    constellations, figure_count = build_constellations(
        read_source(args.lines, LINES_URL),
        read_source(args.names, NAMES_URL),
    )
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "stars.bin"), "wb") as file:
        file.write(stars)
    with open(os.path.join(args.out, "constellations.bin"), "wb") as file:
        file.write(constellations)
    print(f"stars.bin: {star_count} stars, {name_count} names, {len(stars)} bytes")
    print(f"constellations.bin: {figure_count} figures, {len(constellations)} bytes")


if __name__ == "__main__":
    main()
