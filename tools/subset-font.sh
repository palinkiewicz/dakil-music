#!/usr/bin/env bash
# Rebuilds app/src/main/res/font/google_sans_flex.ttf from upstream Google Sans Flex (OFL-1.1).
#
# Keeps the two axes the app uses (wght 300–900, ROND 0–100), pins the rest
# (opsz=18, wdth=100, slnt=0) and subsets to Latin + Latin Extended-A + common punctuation.
# Result is ~170 KB instead of ~3.9 MB.
#
# Usage: tools/subset-font.sh [work-dir]   (needs python3; installs fonttools into a throwaway venv)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${1:-$(mktemp -d)}"
OUT="$ROOT/app/src/main/res/font/google_sans_flex.ttf"
SRC_URL="https://raw.githubusercontent.com/google/fonts/main/ofl/googlesansflex/GoogleSansFlex%5BROND,opsz,slnt,wdth,wght%5D.ttf"
UNICODES="U+0020-007E,U+00A0-00FF,U+0100-017F,U+0218-021B,U+02C6,U+02DA,U+02DC,U+2013-2014,U+2018-201E,U+2020-2022,U+2026,U+2030,U+2039-203A,U+2044,U+20AC,U+2122,U+2190-2193,U+2212,U+2215,U+00B7,U+2116"

python3 -m venv "$WORK/venv"
"$WORK/venv/bin/pip" install -q fonttools
curl -sSL -o "$WORK/source.ttf" "$SRC_URL"
"$WORK/venv/bin/fonttools" varLib.instancer "$WORK/source.ttf" opsz=18 wdth=100 slnt=0 "wght=300:900" -o "$WORK/instanced.ttf" -q
mkdir -p "$(dirname "$OUT")"
"$WORK/venv/bin/pyftsubset" "$WORK/instanced.ttf" \
  --unicodes="$UNICODES" \
  --layout-features='kern,liga,calt,ccmp,locl,mark,mkmk,tnum,lnum,pnum,case' \
  --output-file="$OUT"
echo "Wrote $OUT ($(du -h "$OUT" | cut -f1))"
