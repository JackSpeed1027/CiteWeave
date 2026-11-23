"""字幕边界与 CER 口径的回归，不需要下载模型。"""

from types import SimpleNamespace

import pytest

from citeweave_ai.local_asr import AsrError, Cue, character_error, cues_from, to_vtt


def test_vtt_uses_integer_milliseconds_and_video_boundary():
    segments = [
        SimpleNamespace(start=0.12, end=1.21, text="  第一句\n字幕 "),
        SimpleNamespace(start=1.2, end=1.5, text="视频外"),
        SimpleNamespace(start=float("nan"), end=0.3, text="未知时间"),
    ]
    cues = cues_from(segments, duration_ms=1000)
    assert cues == [Cue(120, 1000, "第一句 字幕")]
    assert to_vtt(cues) == "WEBVTT\n\n00:00:00.120 --> 00:00:01.000\n第一句 字幕\n"


def test_empty_recognition_cannot_be_exported_as_success():
    with pytest.raises(AsrError, match="未识别到"):
        cues_from([SimpleNamespace(start=0, end=1, text=" ")], duration_ms=1000)


def test_cer_retains_edits_and_normalizes_punctuation_and_whitespace():
    assert character_error("你好， 世界！", " 你好世间。") == {"edits": 1, "referenceChars": 4}
