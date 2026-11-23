"""用独立 PostgreSQL 对照 pgvector 精确排序与本地 BGE 余弦。"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from time import perf_counter

from .evaluate_dense import _percentile, _sha256
from .local_embeddings import DIMENSIONS, LocalBge

LAB_DATABASE = "citeweave_vector_lab"
LAB_WORKSPACE = "fixture-workspace"

# 临时表退出即释放，实验不写现有应用表。复合主键可先收窄资料与版本范围。
CREATE = """
CREATE TEMP TABLE vector_chunks (
    workspace text NOT NULL,
    source_id text NOT NULL,
    source_version text NOT NULL,
    model_version text NOT NULL,
    ordinal integer NOT NULL,
    evidence_id text NOT NULL,
    embedding vector(512) NOT NULL CHECK (vector_norm(embedding) > 0),
    PRIMARY KEY (workspace, source_id, source_version, model_version, ordinal)
) ON COMMIT DROP
"""
INSERT = """
INSERT INTO vector_chunks VALUES (%s, %s, %s, %s, %s, %s, %s::vector)
"""
SEARCH = """
SELECT evidence_id, 1 - (embedding <=> %s::vector) AS similarity
FROM vector_chunks
WHERE workspace = %s AND source_id = %s AND source_version = %s AND model_version = %s
ORDER BY similarity DESC, ordinal
LIMIT %s
"""


def _literal(vector) -> str:
    return json.dumps(vector.tolist(), allow_nan=False, separators=(",", ":"))


def _search(connection, literal: str, scope: tuple[str, str, str, str], limit: int):
    return connection.execute(SEARCH, (literal, *scope, limit)).fetchall()


def evaluate(connection, model: LocalBge, model_version: str, dataset: dict, limit: int) -> dict:
    """写入真实向量，再逐题校验范围、排序、分数和证据 ID。"""
    connection.execute(CREATE)
    prepared = {}
    with connection.cursor() as cursor:
        for track_key, track in dataset["tracks"].items():
            vectors = model.encode([item["text"] for item in track])
            first = track[0]
            scope = (LAB_WORKSPACE, first["sourceId"], first["sourceVersionId"], model_version)
            cursor.executemany(INSERT, [(*scope, ordinal, item["evidenceId"], _literal(vector))
                                        for ordinal, (item, vector) in enumerate(zip(track, vectors, strict=True))])
            prepared[track_key] = (track, vectors, scope)

    # 先写入 4 条与首题查询完全相同的诱饵。每条仅偏离一个范围字段，未限制时会排到前面。
    first_case = dataset["cases"][0]
    first_scope = prepared[first_case["source"]][2]
    decoy = _literal(model.encode([first_case["question"]], query=True)[0])
    names = ("workspace", "source", "sourceVersion", "modelVersion")
    for index, name in enumerate(names):
        scope = tuple("decoy-" + name if i == index else value for i, value in enumerate(first_scope))
        connection.execute(INSERT, (*scope, 0, "decoy-" + name, decoy))
        result = _search(connection, decoy, scope, 1)
        if len(result) != 1 or result[0][0] != "decoy-" + name:
            raise ValueError("范围对照未正确建立")

    timings = []
    max_error = 0.0
    cases = []
    for case in dataset["cases"]:
        track, vectors, scope = prepared[case["source"]]
        query = model.encode([case["question"]], query=True)[0]
        scores = vectors @ query
        expected = sorted(range(len(track)), key=lambda i: float(scores[i]), reverse=True)[:limit]

        literal = _literal(query)
        started = perf_counter()
        rows = _search(connection, literal, scope, limit)
        timings.append((perf_counter() - started) * 1000)
        ids = [row[0] for row in rows]
        if ids != [track[i]["evidenceId"] for i in expected]:
            raise ValueError(f"证据或排序不一致：{case['id']}")
        for row, i in zip(rows, expected, strict=True):
            max_error = max(max_error, abs(float(scores[i]) - row[1]))
        cases.append({"id": case["id"], "evidenceIds": ids})

    if max_error > 1e-6:
        raise ValueError(f"数据库距离误差超过容差：{max_error}")
    return {
        "dataset": dataset["dataset"],
        "topK": limit,
        "matched": len(cases),
        "scopeDecoys": list(names),
        "maxCosineError": max_error,
        "databaseQueryMs": {"p50": _percentile(timings, .50), "p95": _percentile(timings, .95)},
        "cases": cases,
    }


def main() -> None:
    """只连接本机专用实验库，检查 512 维类型约束并保存报告。"""
    import psycopg

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, action="append", required=True)
    parser.add_argument("--model-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--port", type=int, default=int(os.getenv("CITEWEAVE_VECTOR_LAB_PORT", "15436")))
    parser.add_argument("--top-k", type=int, default=5, choices=range(1, 6))
    args = parser.parse_args()
    if args.output.exists():
        parser.error("报告需使用新文件。")

    weights_sha = _sha256(args.model_dir / "model_optimized.onnx")
    model = LocalBge(args.model_dir)
    with psycopg.connect(host="127.0.0.1", port=args.port, dbname=LAB_DATABASE, user="vector_lab",
                         password=os.getenv("CITEWEAVE_VECTOR_LAB_PASSWORD", "citeweave_vector_lab_local"),
                         connect_timeout=5) as connection:
        database, postgres, extension = connection.execute("""
            SELECT current_database(), current_setting('server_version'),
                (SELECT extversion FROM pg_extension WHERE extname = 'vector')
        """).fetchone()
        if database != LAB_DATABASE or not extension:
            parser.error("请先启动独立 pgvector 实验库。")
        results = []
        for path in args.input:
            # 每个冻结集独立提交一笔实验事务。提交时释放临时表。
            with connection.transaction():
                dataset = json.loads(path.read_text(encoding="utf-8"))
                result = evaluate(connection, model, weights_sha, dataset, args.top_k)
                try:
                    with connection.transaction():
                        connection.execute(INSERT, ("dimension-test", "fixture", "v", weights_sha, 0, "bad", "[1,0]"))
                except psycopg.errors.DataException as error:
                    result["wrongDimensionSqlState"] = error.sqlstate
                else:
                    raise ValueError("维度错误的向量意外写入成功")
                results.append({**result, "inputSha256": _sha256(path)})
            connection.commit()

    with args.output.open("x", encoding="utf-8") as handle:
        json.dump({"postgres": postgres, "pgvector": extension, "dimensions": DIMENSIONS,
                   "weightsSha256": weights_sha, "results": results}, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print(f"{postgres} / pgvector {extension}，{sum(item['matched'] for item in results)} 题排序一致")


if __name__ == "__main__":
    main()
