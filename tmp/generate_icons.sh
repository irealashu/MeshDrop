#!/bin/bash
set -e
SRC="/tmp/meshdrop_base.png"

convert -size 288x288 xc:'#0B0E17' \
  -stroke '#1F2847' -strokewidth 2 -fill none -draw 'circle 144,144 144,70' \
  -stroke '#7C4DFF' -strokewidth 4 -draw 'line 80,100 144,140 line 208,100 144,140 line 90,190 144,150 line 198,190 144,150' \
  -stroke none -fill '#00E5FF' -draw 'circle 80,100 80,88 circle 208,100 208,88' \
  -stroke none -fill '#7C4DFF' -draw 'circle 90,190 90,178 circle 198,190 198,178' \
  -stroke '#00E5FF' -strokewidth 3 -fill '#00E5FF' -draw 'path "M 144,70 C 112,108 98,135 98,160 C 98,185 119,206 144,206 C 169,206 190,185 190,160 C 190,135 176,108 144,70 Z"' \
  -stroke none -fill '#7C4DFF' -draw 'path "M 144,85 C 118,115 106,138 106,160 C 106,180 123,197 144,197 C 165,197 182,180 182,160 C 182,138 170,115 144,85 Z"' \
  -stroke '#FFFFFF' -strokewidth 5 -fill none -draw 'line 134,150 134,138 arc 134,126 154,148 180,0 line 154,138 154,150' \
  -stroke none -fill '#FFFFFF' -draw 'roundrectangle 122,150 166,182 4,4' \
  -stroke none -fill '#0B0E17' -draw 'circle 144,163 144,159' \
  "PNG32:$SRC"

BASE="app/src/main/res"
for spec in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  density="${spec%:*}"
  size="${spec#*:}"
  dir="$BASE/mipmap-$density"
  mkdir -p "$dir"
  convert "$SRC" -resize "${size}x${size}!" "PNG32:$dir/ic_launcher.png"
  convert "$SRC" -resize "${size}x${size}!" "PNG32:$dir/ic_launcher_round.png"
done
echo "Icons generated successfully"
