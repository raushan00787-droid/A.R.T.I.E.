import unittest
from pathlib import Path
from unittest.mock import patch

from voice.engine import VoiceEngine


class VoiceEnginePreRollTests(unittest.TestCase):
    def test_silent_pre_roll_is_added_after_voice_effects(self):
        engine = VoiceEngine.__new__(VoiceEngine)
        engine.model_dir = Path("/tmp/artie-voices")
        engine.voice_name = "en_GB-cori-medium"
        engine.piper = "/usr/bin/piper"
        engine.play = "/usr/bin/play"
        engine.closed = False

        piper_result = type("Result", (), {
            "stdout": b"\x00" * 64,
            "returncode": 0,
        })()
        play_result = type("Result", (), {"returncode": 0})()

        with patch(
            "voice.engine.subprocess.run",
            side_effect=[piper_result, play_result],
        ) as run:
            result = engine.speak("Hello, Sir.", profile="excited")

        self.assertEqual(result["status"], "spoken")
        playback_command = run.call_args_list[1].args[0]
        self.assertEqual(playback_command[-2:], ["pad", "0.18@0"])
        self.assertLess(
            playback_command.index("tempo"),
            playback_command.index("pad"),
        )


if __name__ == "__main__":
    unittest.main()
