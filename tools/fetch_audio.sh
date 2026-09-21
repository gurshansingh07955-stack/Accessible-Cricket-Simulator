#!/usr/bin/env bash
# Downloads every audio file the game uses from the web app's hosting and
# puts it where the Android app looks for it:
#
#   app/src/main/res/raw/<name>.mp3            the 7 sound recordings
#   app/src/main/assets/commentary/<id>.mp3    the AI voice commentary clips
#
# It exists because those files are binary and live only on the web app's
# hosting, so they can't be copied into this repo with text-based tooling.
# It is run by .github/workflows/fetch-audio.yml (on GitHub's servers, which
# then commit the result), and can equally be run by hand:
#
#   bash tools/fetch_audio.sh
#
# Safe to re-run: files already present are kept, and only missing ones are
# fetched. That is how the clips that don't exist yet get picked up once they
# have been generated on the web app.
#
# The list of commentary clips is NOT hard-coded here: it is read from the
# `audioUrl` values in CommentaryLibrary.kt, so it always matches the code.
# The 7 recordings below must match the RecordedAsset enum in
# app/src/main/java/com/cricketsim/audio/AudioAssets.kt (same raw names, same
# paths). Android raw-resource names must be lowercase letters, digits and
# underscores, which is why the names differ from the hosted file names.

set -u

BASE_URL="${BASE_URL:-https://5764.floot.app}"
RAW_DIR="app/src/main/res/raw"
CLIP_DIR="app/src/main/assets/commentary"
LIBRARY="app/src/main/java/com/cricketsim/logic/CommentaryLibrary.kt"
REPORT="tools/audio_fetch_report.txt"

mkdir -p "$RAW_DIR" "$CLIP_DIR"

have=0
got=0
missing=()

# fetch <url> <destination>
fetch() {
  local url="$1" dest="$2"
  if [ -s "$dest" ]; then
    echo "have  $dest"
    have=$((have + 1))
    return 0
  fi
  # -f: an HTTP error (the host answers 403 for a file that doesn't exist)
  # is a failure rather than a saved error page.
  if curl -fsSL --retry 3 --retry-delay 2 --max-time 60 -o "$dest.part" "$url" && [ -s "$dest.part" ]; then
    mv "$dest.part" "$dest"
    echo "got   $dest"
    got=$((got + 1))
    return 0
  fi
  rm -f "$dest.part"
  echo "MISS  $dest  ($url)"
  missing+=("$(basename "$dest")")
  return 1
}

echo "== Sound recordings ($RAW_DIR) =="
while IFS='|' read -r name path; do
  [ -z "$name" ] && continue
  fetch "$BASE_URL$path" "$RAW_DIR/$name.mp3"
done <<'EOF'
crowd_ambience|/_cdn/static/1dd47f8a-7a8c-4ede-acef-ca28b4493556-arunangshubanerjee-live-football-match-stadium-crowd-cheering-5634.mp3
small_crowd_cheer|/_cdn/sfx/small_crowd_cheer.mp3
big_crowd_roar|/_cdn/sfx/big_crowd_roar.mp3
coin_flip|/_cdn/sfx/coin_flip.mp3
bat_hit|/_cdn/static/90a609ea-4f7c-42a2-aa68-e04a150e343c-NoiseFree_1788362501831.mp3
rain_ambience|/_cdn/sfx/rain_ambience.mp3
thunder_crack|/_cdn/sfx/thunder_crack.mp3
EOF

echo
echo "== Commentary clips ($CLIP_DIR) =="
clip_paths=$(grep -o '/_cdn/commentary/[A-Za-z0-9_.-]*\.mp3' "$LIBRARY" | sort -u)
clip_count=0
for path in $clip_paths; do
  clip_count=$((clip_count + 1))
  fetch "$BASE_URL$path" "$CLIP_DIR/$(basename "$path")"
done

# A short, timestamp-free report, so re-running with nothing new changes
# nothing and produces no commit.
{
  echo "Audio fetch report"
  echo "Source: $BASE_URL"
  echo "Sound recordings expected: 7"
  echo "Commentary clips referenced by CommentaryLibrary.kt: $clip_count"
  echo "Files present after this run: $(find "$RAW_DIR" "$CLIP_DIR" -type f -name '*.mp3' | wc -l)"
  echo "Not available on the web app (skipped): ${#missing[@]}"
  for name in "${missing[@]}"; do echo "  $name"; done
} > "$REPORT"

echo
cat "$REPORT"
echo "(this run: $got new, $have already present)"

# Failing outright (nothing at all fetched or present) means the host
# couldn't be reached, which is worth a red workflow run. A few missing
# clips is not.
if [ $((got + have)) -eq 0 ]; then
  echo "Nothing was fetched or present: is $BASE_URL reachable?" >&2
  exit 1
fi
exit 0
