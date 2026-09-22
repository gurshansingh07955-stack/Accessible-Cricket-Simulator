#!/usr/bin/env bash
# Downloads every audio file the game uses and packs them into ONE
# encrypted resource, app/src/main/res/raw/audio_pack.bin, instead of
# committing individually-named, directly-playable files.
#
# See app/src/main/java/com/cricketsim/audio/AudioPack.kt for how the pack
# is read back on-device, and that file's doc comment for an honest
# account of what this protects against and what it does not — in short:
# it stops a casual unzip/adb-pull from handing someone playable audio,
# but the key below is committed to this PUBLIC repository, so it is not
# real secrecy against anyone who reads this file or decompiles the APK.
#
# Run by .github/workflows/fetch-audio.yml (unchanged by this rewrite — it
# just does `git add app/src/main/res/raw app/src/main/assets/commentary
# ...`, which already covers both the new pack file below and the removal
# of the old loose files this script used to leave in those two paths).
# Safe to re-run by hand: bash tools/fetch_audio.sh
#
# The list of commentary clips is read from CommentaryLibrary.kt's
# audioUrl values, so it can't drift from the code.

set -u

BASE_URL="${BASE_URL:-https://5764.floot.app}"
SCRATCH_DIR="tools/.audio_scratch"
OUT_FILE="app/src/main/res/raw/audio_pack.bin"
OLD_CLIP_DIR="app/src/main/assets/commentary"
LIBRARY="app/src/main/java/com/cricketsim/logic/CommentaryLibrary.kt"
REPORT="tools/audio_fetch_report.txt"

# Must exactly match app/build.gradle.kts's default AUDIO_PACK_KEY_HEX /
# AUDIO_PACK_IV_HEX (read by AudioPack.kt). Raw AES-256-CBC, no salt, no
# key-derivation function — deliberately the simplest possible scheme, with
# no ambiguity between what openssl and Kotlin's javax.crypto each derive.
KEY_HEX="df49894bbeb5bc80ee17563f080390e2eae82814a6d58bcf8315519abc24ec66"
IV_HEX="48b415240d5285efe644906d4b4a3b2a"

rm -rf "$SCRATCH_DIR"
mkdir -p "$SCRATCH_DIR" "$(dirname "$OUT_FILE")"

got=0
missing=()

# fetch <url> <destination>
fetch() {
  local url="$1" dest="$2"
  # -f: an HTTP error (the host answers 403 for a file that doesn't exist)
  # is a failure rather than a saved error page.
  if curl -fsSL --retry 3 --retry-delay 2 --max-time 60 -o "$dest.part" "$url" && [ -s "$dest.part" ]; then
    mv "$dest.part" "$dest"
    got=$((got + 1))
    return 0
  fi
  rm -f "$dest.part"
  missing+=("$(basename "$dest")")
  return 1
}

echo "== Downloading sound recordings =="
while IFS='|' read -r name path; do
  [ -z "$name" ] && continue
  fetch "$BASE_URL$path" "$SCRATCH_DIR/$name.mp3"
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
echo "== Downloading commentary clips =="
clip_paths=$(grep -o '/_cdn/commentary/[A-Za-z0-9_.-]*\.mp3' "$LIBRARY" | sort -u)
clip_count=0
for path in $clip_paths; do
  clip_count=$((clip_count + 1))
  fetch "$BASE_URL$path" "$SCRATCH_DIR/$(basename "$path")"
done

fetched_files=$(find "$SCRATCH_DIR" -type f -name '*.mp3' | wc -l)
if [ "$fetched_files" -eq 0 ]; then
  echo "Nothing was fetched: is $BASE_URL reachable? Leaving existing files untouched." >&2
  rm -rf "$SCRATCH_DIR"
  exit 1
fi

echo
echo "== Packing $fetched_files files into $OUT_FILE =="
python3 - "$SCRATCH_DIR" "$OUT_FILE.plain" << 'PY'
import json, os, struct, sys
scratch, out_path = sys.argv[1], sys.argv[2]
names = sorted(os.listdir(scratch))
index = {}
payload = bytearray()
for name in names:
    with open(os.path.join(scratch, name), "rb") as f:
        data = f.read()
    index[name] = {"offset": len(payload), "length": len(data)}
    payload += data
index_bytes = json.dumps(index).encode("utf-8")
with open(out_path, "wb") as f:
    f.write(struct.pack(">I", len(index_bytes)))
    f.write(index_bytes)
    f.write(payload)
print(f"packed {len(names)} files, {len(payload)} bytes payload, {len(index_bytes)} bytes index")
PY

if [ ! -s "$OUT_FILE.plain" ]; then
  echo "Packing produced nothing; leaving existing files untouched." >&2
  rm -rf "$SCRATCH_DIR"
  exit 1
fi

openssl enc -aes-256-cbc -K "$KEY_HEX" -iv "$IV_HEX" -nosalt -in "$OUT_FILE.plain" -out "$OUT_FILE"
rm -f "$OUT_FILE.plain"
rm -rf "$SCRATCH_DIR"

# Superseded by the single pack above: removing these here is what makes
# the workflow's next commit actually delete them from the repository.
find app/src/main/res/raw -maxdepth 1 -name '*.mp3' -delete 2>/dev/null || true
rm -rf "$OLD_CLIP_DIR"

{
  echo "Audio fetch report"
  echo "Source: $BASE_URL"
  echo "Sound recordings expected: 7"
  echo "Commentary clips referenced by CommentaryLibrary.kt: $clip_count"
  echo "Files packed into $OUT_FILE this run: $fetched_files"
  echo "Packed file size: $(wc -c < "$OUT_FILE") bytes"
  echo "Not available on the web app (skipped): ${#missing[@]}"
  for name in "${missing[@]}"; do echo "  $name"; done
} > "$REPORT"

cat "$REPORT"
exit 0
