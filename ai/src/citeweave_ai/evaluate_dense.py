"""按冻结题集对比词面、CPU 向量和秩融合候选。"""

from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
from statistics import mean
from time import perf_counter

from .local_embeddings import DIMENSIONS, MAX_TOKENS, QUERY_PREFIX, LocalBge

RRF_RANK_CONSTANT = 60
METHODS = ("lexical", "dense", "hybrid")


def reciprocal_rank_fusion(lexical: list[str], dense: list[str], limit: int) -> list[str]:
    """用名次求和，不把词面分数和余弦分数直接相加。"""
    scores: dict[str, float] = defaultdict(float)
    for ranking in (lexical, dense):
        for rank, evidence_id in enumerate(ranking, start=1):
            scores[evidence_id] += 1.0 / (RRF_RANK_CONSTANT + rank)
    # 同分保持进入融合时的顺序，结果可重复。
    return sorted(scores, key=scores.__getitem__, reverse=True)[:limit]


def groups_found(candidates: list[str], groups: list[list[str]]) -> int:
    """每个预先标注的证据组只命中一次。"""
    present = set(candidates)
    return sum(bool(present.intersection(group)) for group in groups)


def _count(rows: list[dict], method: str) -> dict:
    answerable = [row for row in rows if row["required"]]
    unanswerable = [row for row in rows if not row["required"]]
    return {
        "answerable": len(answerable),
        "fullyCovered": sum(row["found"][method] == row["required"] for row in answerable),
        "macroRecall": mean(row["found"][method] / row["required"] for row in answerable) if answerable else None,
        "unanswerable": len(unanswerable),
        "unanswerableWithCandidates": sum(bool(row["candidates"][method]) for row in unanswerable),
    }


def _percentile(values: list[float], fraction: float) -> float:
    return sorted(values)[math.ceil(len(values) * fraction) - 1]


def _sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def evaluate(dataset: dict, model: LocalBge, limit: int) -> dict:
    """在指定来源内精确计算余弦，并保留每题的证据 ID、命中与时间。"""
    started = perf_counter()
    tracks = dataset["tracks"]
    embeddings = {source: model.encode([item["text"] for item in track]) for source, track in tracks.items()}
    encode_passage_ms = (perf_counter() - started) * 1000
    # 单独预热一次会话，正式查询逐条编码，便于记录交互耗时。
    model.encode([dataset["cases"][0]["question"]], query=True)

    timings = []
    rows = []
    for case in dataset["cases"]:
        scope = case["source"]
        track = tracks[scope]
        started = perf_counter()
        query_vector = model.encode([case["question"]], query=True)[0]
        similarities = embeddings[scope] @ query_vector
        best = sorted(range(len(track)), key=lambda i: float(similarities[i]), reverse=True)[:limit]
        dense = [track[i]["evidenceId"] for i in best]
        timings.append((perf_counter() - started) * 1000)

        lexical = case["lexical"][:limit]
        allowed = {item["evidenceId"] for item in track}
        if not set(lexical).issubset(allowed):
            raise ValueError(f"词面基线跨出资料范围：{case['id']}")
        candidates = {"lexical": lexical, "dense": dense, "hybrid": reciprocal_rank_fusion(lexical, dense, limit)}
        required = case["requiredGroups"]
        rows.append({
            "id": case["id"],
            "category": case["category"],
            "source": scope,
            "required": len(required),
            "found": {name: groups_found(items, required) for name, items in candidates.items()},
            "denseTopScore": float(similarities[best[0]]) if best else None,
            "candidates": candidates,
        })

    return {
        "dataset": dataset["dataset"],
        "topK": limit,
        "passageEncodeMs": encode_passage_ms,
        "queries": len(rows),
        "chunks": sum(len(track) for track in tracks.values()),
        "denseQueryMs": {"p50": _percentile(timings, 0.50), "p95": _percentile(timings, 0.95)},
        "metrics": {name: _count(rows, name) for name in METHODS},
        "byCategory": {
            category: {name: _count([row for row in rows if row["category"] == category], name) for name in METHODS}
            for category in dict.fromkeys(row["category"] for row in rows)
        },
        "cases": rows,
    }


def main() -> None:
    """读取 Java 导出的生产片段和基线，保存完整对照。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, action="append", required=True)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--top-k", type=int, default=5, choices=range(1, 6))
    args = parser.parse_args()
    if args.threads < 1 or args.output.exists():
        parser.error("线程数须大于零，输出需使用新文件。")

    started = perf_counter()
    model = LocalBge(args.model_dir, args.threads)
    model_load_ms = (perf_counter() - started) * 1000
    report = {
        "model": "BAAI/bge-small-zh-v1.5",
        "artifactDirectoryName": args.model_dir.name,
        "dimensions": DIMENSIONS,
        "maxTokens": MAX_TOKENS,
        "threads": args.threads,
        "queryPrefix": QUERY_PREFIX,
        "modelLoadMs": model_load_ms,
        "artifactSha256": {name: _sha256(args.model_dir / name)
                           for name in ("model_optimized.onnx", "tokenizer.json")},
        "results": [
            {**evaluate(json.loads(path.read_text(encoding="utf-8")), model, args.top_k), "inputSha256": _sha256(path)}
            for path in args.input
        ],
    }
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2, ensure_ascii=False, allow_nan=False)
        handle.write("\n")
    print(json.dumps([{"dataset": row["dataset"], **row["metrics"]} for row in report["results"]], ensure_ascii=False))


if __name__ == "__main__":
    main()
