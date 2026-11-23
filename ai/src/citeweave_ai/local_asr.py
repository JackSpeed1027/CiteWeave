"""离线 CPU 转写实验，将受控 MP4/MOV 转成可导入的 VTT 与可复算报告。"""

from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import json
import math
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
from time import perf_counter
from typing import Iterable, Protocol
import unicodedata
import wave

SAMPLE_RATE = 16_000
MAX_SEGMENTS = 10_000
MAX_VTT_CHARS = 500_000
MOV_INPUT = ("-protocol_whitelist", "file", "-f", "mov", "-enable_drefs", "0", "-use_absolute_path", "0")


class AsrError(RuntimeError):
    """未能产出有效字幕时报告明确的失败原因。"""


class ModelSegment(Protocol):
    """抽象模型片段，保持时间轴校验可独立测试。"""

    start: float
    end: float
    text: str


@dataclass(frozen=True)
class Cue:
    """视频时间轴上的转写片段，单位为整数毫秒。"""

    start_ms: int
    end_ms: int
    text: str


def video_duration_ms(video: Path, ffprobe: str, max_seconds: int, timeout: int, *, managed_input: bool = False) -> int:
    """读容器时长，避免音轨解码填充产生的视频外字幕。"""
    allowed = {".media"} if managed_input else {".mp4", ".mov"}
    if video.suffix.lower() not in allowed or not video.is_file():
        raise AsrError("请选择本机 MP4 或 MOV 视频。")
    command = [ffprobe, "-v", "error", *MOV_INPUT, "-show_entries", "format=duration",
               "-of", "default=noprint_wrappers=1:nokey=1", "-i", str(video.resolve())]
    try:
        result = subprocess.run(command, check=True, timeout=timeout, capture_output=True, text=True)
        seconds = float(result.stdout.strip())
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        raise AsrError("无法取得视频时长，请检查 FFprobe 与视频文件。") from error
    if not math.isfinite(seconds) or not 0 < seconds <= max_seconds:
        raise AsrError("视频为空或超过实验允许的时长。")
    return math.ceil(seconds * 1000)


def extract_audio(video: Path, output: Path, ffmpeg: str, max_seconds: int, timeout: int) -> int:
    """抽第一条音轨，保留起始偏移并限制抽轨时间与输出长度。"""
    command = [
        ffmpeg, "-n", "-nostdin", "-hide_banner", "-loglevel", "error",
        *MOV_INPUT,
        "-copyts", "-start_at_zero", "-i", str(video.resolve()),
        "-map", "0:a:0", "-af", f"aresample={SAMPLE_RATE}:async=1:first_pts=0",
        "-ac", "1", "-c:a", "pcm_s16le", "-t", str(max_seconds + 1), str(output),
    ]
    try:
        subprocess.run(command, check=True, timeout=timeout, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        with wave.open(str(output), "rb") as audio:
            frames = audio.getnframes()
    except subprocess.TimeoutExpired as error:
        raise AsrError("FFmpeg 抽轨超时。") from error
    except (OSError, subprocess.CalledProcessError, wave.Error) as error:
        raise AsrError("无法提取第一条音轨，请检查 FFmpeg、容器及音轨。") from error

    if frames <= 0 or frames > max_seconds * SAMPLE_RATE:
        raise AsrError("音轨为空或超过实验允许的时长。")
    return frames * 1000 // SAMPLE_RATE


def cues_from(segments: Iterable[ModelSegment], duration_ms: int) -> list[Cue]:
    """忽略无效或纯空白片段，将结束点限制在音轨时长内。"""
    cues = []
    for segment in segments:
        text = " ".join(segment.text.split()).replace("\0", "").strip()
        if not text or not math.isfinite(segment.start) or not math.isfinite(segment.end):
            continue
        start = max(0, round(segment.start * 1000))
        end = min(duration_ms, round(segment.end * 1000))
        if start < end:
            cues.append(Cue(start, end, text))
    if not cues:
        raise AsrError("未识别到可导入的语音片段。")
    if len(cues) > MAX_SEGMENTS:
        raise AsrError("转写片段超过字幕导入上限。")
    return sorted(cues, key=lambda cue: cue.start_ms)


def _timestamp(millis: int) -> str:
    seconds, millis = divmod(millis, 1000)
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    return f"{hours:02}:{minutes:02}:{seconds:02}.{millis:03}"


def to_vtt(cues: list[Cue]) -> str:
    """生成现有字幕解析器支持的 VTT。"""
    blocks = ["WEBVTT"]
    for cue in cues:
        blocks.append(f"{_timestamp(cue.start_ms)} --> {_timestamp(cue.end_ms)}\n{cue.text}")
    text = "\n\n".join(blocks) + "\n"
    if len(text) > MAX_VTT_CHARS:
        raise AsrError("转写正文超过字幕导入上限。")
    return text


def _characters(text: str) -> str:
    return "".join(char for char in unicodedata.normalize("NFKC", text).lower()
                   if not char.isspace() and not unicodedata.category(char).startswith("P"))


def character_error(reference: str, hypothesis: str) -> dict[str, int]:
    """按字符编辑距离返回错误数和参考字数，去除标点和空白。"""
    reference, hypothesis = _characters(reference), _characters(hypothesis)
    if not reference:
        raise AsrError("参考转录没有可计分的字符。")
    previous = list(range(len(hypothesis) + 1))
    for i, expected in enumerate(reference, start=1):
        current = [i]
        for j, predicted in enumerate(hypothesis, start=1):
            current.append(min(previous[j] + 1, current[j - 1] + 1,
                               previous[j - 1] + (expected != predicted)))
        previous = current
    return {"edits": previous[-1], "referenceChars": len(reference)}


def main() -> None:
    """加载已下载的本地模型，执行 CPU INT8 推理并保留测量口径。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="必须为尚未存在的目录")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--ffprobe", default="ffprobe")
    parser.add_argument("--language", default="zh")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--runs", type=int, default=2)
    parser.add_argument("--max-seconds", type=int, default=120)
    parser.add_argument("--extract-timeout", type=int, default=30)
    parser.add_argument("--reference", type=Path)
    parser.add_argument("--managed-input", action="store_true", help="读取服务端管理的 .media 对象")
    args = parser.parse_args()
    if min(args.threads, args.runs, args.max_seconds, args.extract_timeout) < 1:
        parser.error("线程数、轮数、输入时长和超时必须为正数。")
    if args.output.exists() or not all((args.model_dir / name).is_file()
                                      for name in ("model.bin", "config.json", "tokenizer.json")):
        parser.error("输出目录必须尚未存在，模型目录应有 model.bin、config.json 和 tokenizer.json。")

    try:
        import ctranslate2
        import faster_whisper
        import numpy as np
    except ImportError:
        parser.exit(1, "请在已安装 faster-whisper 的实验环境中运行。\n")

    try:
        with TemporaryDirectory(prefix="citeweave-asr-") as temporary:
            audio = Path(temporary) / "audio.wav"
            video_ms = video_duration_ms(args.video, args.ffprobe, args.max_seconds, args.extract_timeout,
                                         managed_input=args.managed_input)
            phase = perf_counter()
            duration = extract_audio(args.video, audio, args.ffmpeg, args.max_seconds, args.extract_timeout)
            # FFmpeg 已产出受控 PCM。直接传采样数组，避免让模型二次解码容器。
            with wave.open(str(audio), "rb") as pcm:
                samples = np.frombuffer(pcm.readframes(pcm.getnframes()), dtype="<i2").astype(np.float32) / 32768
            extract_seconds = perf_counter() - phase
            phase = perf_counter()
            model = faster_whisper.WhisperModel(str(args.model_dir.resolve()), device="cpu", compute_type="int8",
                                                cpu_threads=args.threads, local_files_only=True)
            load_seconds = perf_counter() - phase
            timing = []
            output = None
            for _ in range(args.runs):
                phase = perf_counter()
                segments, _ = model.transcribe(samples, language=args.language, beam_size=5)
                # faster-whisper 延迟执行。计时必须包括遍历模型返回的全部片段。
                cues = cues_from(segments, min(duration, video_ms))
                timing.append(perf_counter() - phase)
                if output is None:
                    output = cues
        assert output is not None
        report = {
            "modelDirectoryName": args.model_dir.name,
            "fasterWhisperVersion": faster_whisper.__version__,
            "ctranslate2Version": ctranslate2.__version__,
            "device": "cpu",
            "computeType": "int8",
            "language": args.language,
            "cpuThreads": args.threads,
            "videoDurationMs": video_ms,
            "audioDurationMs": duration,
            "extractSeconds": extract_seconds,
            "modelLoadSeconds": load_seconds,
            "inferenceSeconds": timing,
            "realTimeFactor": [seconds / (duration / 1000) for seconds in timing],
            "cues": [asdict(cue) for cue in output],
        }
        if args.reference:
            report["characterError"] = character_error(args.reference.read_text(encoding="utf-8-sig"),
                                                       "".join(cue.text for cue in output))
        subtitles = to_vtt(output)
        args.output.mkdir()
        (args.output / "machine-captions.vtt").write_text(subtitles, encoding="utf-8")
        (args.output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                                encoding="utf-8")
        print(f"已生成 VTT 和报告，共 {len(output)} 条字幕。")
    except (AsrError, OSError, ValueError, RuntimeError) as error:
        parser.exit(1, f"转写失败：{error}\n")


if __name__ == "__main__":
    main()
