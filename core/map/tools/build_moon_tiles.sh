#!/usr/bin/env bash
#
# Builds the Moon map's Web Mercator tiles (F25) from the LRO WAC global mosaic.
#
# Source: "Lunar LRO LROC-WAC Global Morphologic Mosaic 100m v3", June 2013, released by USGS
# Astrogeology from the PDS (public domain; credit "NASA/GSFC/Arizona State University").
#   https://planetarymaps.usgs.gov/mosaic/Lunar_LRO_LROC-WAC_Mosaic_global_100m_June2013.tif
# The URL redirects to an S3 copy; the file is 5,959,263,751 bytes, 109164 x 54582 px, simple
# cylindrical on a 1737.4 km sphere, stripped and without overviews (so it must be downloaded whole).
#
# Output (checked in, never built at Gradle time):
#   core/map/src/main/assets/moon_tiles/{z}/{x}/{y}.jpg   zoom 0-4, inside the APK (offline)
#   moontiles/src/main/assets/moon_tiles/{z}/{x}/{y}.jpg  zoom 5-6, the on-demand asset pack
#
# How the Moon becomes Web Mercator: PROJ refuses to transform between celestial bodies, and the
# spherical Mercator tile grid depends only on longitude and latitude, not on the sphere's radius.
# So the mosaic's corners are relabelled as EPSG:4326 degrees (-180..180, -90..90) and gdal2tiles
# cuts EPSG:3857 tiles from that. Mercator cannot show the poles: latitudes beyond +-85.05 deg are
# clipped, which the app notes on the map.
#
# Requirements: GDAL >= 3.9 (gdal_translate, gdal2tiles with --tiledriver=JPEG), curl, ~15 GB free.
# Usage: core/map/tools/build_moon_tiles.sh [work dir]   (default ~/.cache/nasaexplorer-moon-tiles)
# Takes about an hour to download at ~2 MB/s, then a few minutes to tile. Re-runs reuse the
# download and the intermediate file.

set -euo pipefail

SOURCE_URL="https://planetarymaps.usgs.gov/mosaic/Lunar_LRO_LROC-WAC_Mosaic_global_100m_June2013.tif"
SOURCE_BYTES=5959263751
# 32768 px across (~333 m/px) is finer than zoom 6 needs up to +-60 deg (16384 px across at the
# equator, stretched by 1/cos(lat) towards the poles).
WORK_WIDTH=32768
WORK_HEIGHT=16384
JPEG_QUALITY=70
# The mosaic is dark (mean ~63 of 255); a gamma of 0.7 lifts the maria without clipping highlights.
GAMMA=0.7

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
WORK_DIR="${1:-$HOME/.cache/nasaexplorer-moon-tiles}"
SOURCE_TIF="$WORK_DIR/$(basename "$SOURCE_URL")"
WORK_TIF="$WORK_DIR/moon_wac_${WORK_WIDTH}_gamma${GAMMA}_epsg4326.tif"
TILES_DIR="$WORK_DIR/tiles"
APK_ASSETS="$REPO_ROOT/core/map/src/main/assets/moon_tiles"
PACK_ASSETS="$REPO_ROOT/moontiles/src/main/assets/moon_tiles"

mkdir -p "$WORK_DIR"

# 1. Download (resumable) and check the size.
size_of() { stat -f %z "$1" 2>/dev/null || stat -c %s "$1"; }
if [[ ! -f "$SOURCE_TIF" || "$(size_of "$SOURCE_TIF")" != "$SOURCE_BYTES" ]]; then
    curl -fL -C - --retry 10 --retry-delay 5 -o "$SOURCE_TIF" "$SOURCE_URL"
fi
if [[ "$(size_of "$SOURCE_TIF")" != "$SOURCE_BYTES" ]]; then
    echo "Unexpected size for $SOURCE_TIF" >&2
    exit 1
fi

# 2. Downsample into a tiled GeoTIFF labelled as plain degrees. The mosaic's nodata value is 0,
#    which also occurs in shadowed polar craters; drop it so those stay black instead of holes,
#    drop the near-identity scale/offset, and brighten with GAMMA.
if [[ ! -f "$WORK_TIF" ]]; then
    gdal_translate -of GTiff -outsize "$WORK_WIDTH" "$WORK_HEIGHT" -r average \
        -a_srs EPSG:4326 -a_ullr -180 90 180 -90 -a_nodata none -a_scale 1 -a_offset 0 \
        -scale 0 255 0 255 -exponent "$GAMMA" \
        -co TILED=YES -co COMPRESS=DEFLATE -co BIGTIFF=IF_SAFER \
        "$SOURCE_TIF" "$WORK_TIF.part"
    mv "$WORK_TIF.part" "$WORK_TIF"
fi

# 3. Cut XYZ (slippy map) tiles, 256 px, greyscale JPEG.
rm -rf "$TILES_DIR"
gdal2tiles.py --profile=mercator --xyz --zoom=0-6 --resampling=average --webviewer=none \
    --tiledriver=JPEG --jpeg-quality="$JPEG_QUALITY" --processes=6 \
    "$WORK_TIF" "$TILES_DIR"

# 4. Split into the APK assets (0-4) and the asset pack (5-6).
rm -rf "$APK_ASSETS" "$PACK_ASSETS"
mkdir -p "$APK_ASSETS" "$PACK_ASSETS"
for z in 0 1 2 3 4; do cp -R "$TILES_DIR/$z" "$APK_ASSETS/"; done
for z in 5 6; do cp -R "$TILES_DIR/$z" "$PACK_ASSETS/"; done
find "$APK_ASSETS" "$PACK_ASSETS" -type f ! -name '*.jpg' -delete

echo "APK tiles:  $(find "$APK_ASSETS" -name '*.jpg' | wc -l | tr -d ' ') ($(du -sh "$APK_ASSETS" | cut -f1))"
echo "Pack tiles: $(find "$PACK_ASSETS" -name '*.jpg' | wc -l | tr -d ' ') ($(du -sh "$PACK_ASSETS" | cut -f1))"
