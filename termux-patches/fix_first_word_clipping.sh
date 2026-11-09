#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="\${1:-$HOME/ARTIE-CORE-TEST/ARTIE-NEURAL-CORE}"
ENGINE="$ROOT/voice/engine.py"

if [[ ! -f "$ENGINE" ]]; then
    echo "ERROR: Expected ARTIE VoiceEngine file not found: $ENGINE" >&2
    echo "This patch targets the installed V4/V5 Piper voice engine; no files changed." >&2
    exit 1
fi

if grep -Fq 'playback += ["pad", "0.18@0"]' "$ENGINE"; then
    echo "First-word pre-roll is already installed."
    python -m py_compile "$ENGINE"
    exit 0
fi

STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP="$ROOT/.artie_backups/voice_preroll_$STAMP"
mkdir -p "$BACKUP/voice"
cp -p "$ENGINE" "$BACKUP/voice/engine.py"

python - "$ENGINE" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")

anchor = '''        if settings["tempo"] != 1.0:
            playback += ["tempo", str(settings["tempo"])]

        subprocess.run(
'''
replacement = '''        if settings["tempo"] != 1.0:
            playback += ["tempo", str(settings["tempo"])]

        # Start the audio device with a short silent pre-roll before Cori speaks.
        playback += ["pad", "0.18@0"]

        subprocess.run(
'''

if source.count(anchor) != 1:
    raise SystemExit(
        "ERROR: The expected Piper playback block was not found exactly once. "
        "The original source was left unchanged."
    )

path.write_text(source.replace(anchor, replacement), encoding="utf-8")
PY

python -m py_compile "$ENGINE"
echo "ARTIE first-word audio pre-roll installed."
echo "Backup: $BACKUP/voice/engine.py"
echo "Restart the Neural Core bridge before testing Cori speech."
