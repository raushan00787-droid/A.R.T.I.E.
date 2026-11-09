# Termux Voice Reliability Patch

Run this from Termux after confirming the ARTIE Neural Core folder is present:

```sh
bash /path/to/fix_first_word_clipping.sh
```

Or place the script in the project root and run:

```sh
bash termux-patches/fix_first_word_clipping.sh
```

The patch adds a 180 ms silent pre-roll to the Cori/Piper SoX playback command. It creates a timestamped backup first and is safe to run more than once. After applying it, restart `python run_bridge.py` before testing.

Focused regression test:

```sh
python -m unittest tests.test_voice_preroll -v
```

The patch intentionally targets the existing V4/V5 `voice/engine.py` implementation. It stops without changing files if that expected implementation is not present.
