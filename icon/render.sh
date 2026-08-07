#!/usr/bin/env bash
#
# Renders every launcher PNG from the SVG masters beside this script. The icon is
# edited in one place and re-rendered rather than touched up per density, and
# there are two sets of it now — the real one and the test build's, which is the
# same art with a stamp on it — so doing that by hand is nine files times five
# densities of chances to leave one behind.
#
#     icon/render.sh
#
# Needs rsvg-convert (brew install librsvg).
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v rsvg-convert > /dev/null; then
  echo "rsvg-convert not found — brew install librsvg" >&2
  exit 1
fi

# density:launcher size:adaptive size. The launcher sizes are dp at each density
# for a 48 dp tile, the adaptive ones for the 108 dp canvas an adaptive icon's
# layers are drawn on.
densities="mdpi:48:108 hdpi:72:162 xhdpi:96:216 xxhdpi:144:324 xxxhdpi:192:432"

# Both sets: the real books' icon into main, the test build's into the dev
# flavour, where it overrides main by name and nothing else has to know.
render_set() {
  local prefix="$1" res="$2"

  for d in $densities; do
    local density="${d%%:*}" rest="${d#*:}"
    local tile="${rest%%:*}" canvas="${rest#*:}"
    local dir="../androidApp/src/$res/res/mipmap-$density"

    mkdir -p "$dir"
    rsvg-convert -w "$tile" -h "$tile" "$prefix.svg" -o "$dir/ic_launcher.png"
    rsvg-convert -w "$tile" -h "$tile" "$prefix-round.svg" -o "$dir/ic_launcher_round.png"
    rsvg-convert -w "$canvas" -h "$canvas" "$prefix-foreground.svg" -o "$dir/ic_launcher_foreground.png"
    rsvg-convert -w "$canvas" -h "$canvas" "$prefix-monochrome.svg" -o "$dir/ic_launcher_monochrome.png"
  done

  echo "rendered $prefix.svg → androidApp/src/$res/res/mipmap-*"
}

render_set starocie-icon main
render_set starocie-icon-test dev

# The store listing and iOS take one large square each. iOS rejects an alpha
# channel at upload, which is why its master carries a background of its own
# rather than the rounded tile.
rsvg-convert -w 512 -h 512 starocie-icon.svg -o play-store-512.png
rsvg-convert -w 1024 -h 1024 starocie-icon-square.svg \
  | magick png:- -background '#FFF8F4' -alpha remove -alpha off \
    ../iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
echo "rendered play-store-512.png and AppIcon-1024.png"
