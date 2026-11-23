"""以冻结的合成字幕对照词面与同义候选的拒答表现。"""

import argparse
import asyncio
import json
from collections import Counter
from hashlib import sha256
from pathlib import Path
from typing import Literal

import httpx
from pydantic import BaseModel, ConfigDict

from .evidence import EvidenceSegment
from .model_provider import OpenAICompatibleProvider, ProviderConfig, ProviderError
from .transcript_question import PROMPT, QuestionBatch, ask_question
from .transcript_summary import EVIDENCE_BUDGET, provider_config

INPUT = Path(__file__).resolve().parents[3] / "server/target/transcript-refusal-eval-input.json"
STRATEGIES = ("lexical", "aliasFallback")


class Case(BaseModel):
    """单一测试问题的人工标注与两条候选列表。"""

    model_config = ConfigDict(strict=True, extra="forbid")
    id: str
    question: str
    expectedAnswer: str | None
    requiredGroups: list[list[str]]
    lexical: list[EvidenceSegment]
    aliasFallback: list[EvidenceSegment]


class Dataset(BaseModel):
    """只接受专用合成集的导出格式。"""

    model_config = ConfigDict(strict=True, extra="forbid")
    dataset: Literal["transcript-alias-holdout-v1"]
    cases: list[Case]


def fixture_transport(dataset: Dataset) -> httpx.MockTransport:
    """用已知标注模拟正确拒答、有效引用和有引用却答错的输出。"""
    cases = {case.question: case for case in dataset.cases}

    def respond(request: httpx.Request) -> httpx.Response:
        messages = json.loads(request.content)["messages"]
        prompt = json.loads(messages[-1]["content"])
        case = cases[prompt["question"]]
        ids = {item["id"] for item in prompt["evidence"]}
        cited = [next((candidate for candidate in group if candidate in ids), None)
                 for group in case.requiredGroups]
        output = {"status": "INSUFFICIENT_EVIDENCE", "answer": None, "evidenceIds": []}
        if case.id == "H10":
            # 这是故意构造的坏答案：引用 ID 存在，内容却没有证据。
            output = {"status": "ANSWERED", "answer": "夹具演示的不受支持结论：支持 60 fps。",
                      "evidenceIds": [prompt["evidence"][0]["id"]]}
        elif ids and cited and all(cited):
            output = {"status": "ANSWERED", "answer": case.expectedAnswer, "evidenceIds": cited}
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(output)}}]})

    return httpx.MockTransport(respond)


async def observe(case: Case, strategy: str, provider: OpenAICompatibleProvider) -> dict:
    """将未召回、模型拒答、错误及待人工复核的回答分别记录。"""
    candidates = getattr(case, strategy)
    row = {"id": case.id, "strategy": strategy, "question": case.question,
           "expectedAnswer": case.expectedAnswer, "requiredGroups": case.requiredGroups,
           "candidates": [item.model_dump() for item in candidates], "modelCalled": False}
    if not candidates:
        return {**row, "outcome": "NO_CANDIDATES"}
    batch = QuestionBatch(question=case.question, segments=candidates)
    row["modelCalled"] = True
    try:
        result = await ask_question(batch, provider)
    except TimeoutError:
        return {**row, "outcome": "ERROR", "errorCode": "MODEL_TIMEOUT"}
    except ProviderError as failure:
        return {**row, "outcome": "ERROR", "errorCode": failure.code}
    outcome = "MODEL_REFUSED"
    if result.status == "ANSWERED":
        if case.expectedAnswer is None:
            outcome = "UNSUPPORTED_ANSWER"
        elif not all(set(group).intersection(result.evidenceIds) for group in case.requiredGroups):
            outcome = "EXPECTED_EVIDENCE_MISSING"
        else:
            outcome = "NEEDS_REVIEW"
    return {**row, "outcome": outcome, "actualInputIds": result.inputEvidenceIds,
            "answer": result.answer, "citationIds": result.evidenceIds, "truncated": result.truncated}


async def evaluate(dataset: Dataset, config: ProviderConfig, transport: httpx.AsyncBaseTransport | None) -> dict:
    """串行运行每条配对用例，输出原始分母与各结果数量。"""
    async with httpx.AsyncClient(transport=transport) as client:
        provider = OpenAICompatibleProvider(client, config)
        rows = [await observe(case, strategy, provider) for case in dataset.cases for strategy in STRATEGIES]
    counts = {}
    for strategy in STRATEGIES:
        chosen = [row for row in rows if row["strategy"] == strategy]
        no_answer = [row for row in chosen if row["expectedAnswer"] is None]
        counts[strategy] = {
            "cases": len(chosen),
            "modelCalls": sum(row["modelCalled"] for row in chosen),
            "outcomes": dict(Counter(row["outcome"] for row in chosen)),
            "unanswerableCases": len(no_answer),
            "unanswerableModelCalls": sum(row["modelCalled"] for row in no_answer),
            "unanswerableOutcomes": dict(Counter(row["outcome"] for row in no_answer)),
        }
    return {"counts": counts, "observations": rows}


def main() -> None:
    """默认离线运行；外部供应商调用须明确选择 --live。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="通过已配置供应商运行合成题")
    parser.add_argument("--output", required=True, type=Path, help="新建 JSON 报告，不覆盖旧报告")
    args = parser.parse_args()
    config = provider_config() if args.live else ProviderConfig("http://fixture.invalid/v1", "fixture", "fixture-key")
    if config is None:
        parser.error("未配置 CITEWEAVE_MODEL_BASE_URL、CITEWEAVE_MODEL_NAME 或 CITEWEAVE_MODEL_API_KEY")
    if not INPUT.is_file():
        parser.error("找不到合成候选文件，请先运行 TranscriptRetrievalBenchmarkTest")
    payload = INPUT.read_bytes()
    dataset = Dataset.model_validate_json(payload)
    if args.output.exists():
        parser.error("目标报告已存在，请选择新路径")
    if not args.output.parent.is_dir():
        parser.error("报告所在目录不存在")
    transport = None if args.live else fixture_transport(dataset)
    # 在发起请求前独占新报告路径，避免并发任务向供应商重复提交。
    with args.output.open("x", encoding="utf-8") as output:
        report = asyncio.run(evaluate(dataset, config, transport))
        report.update(dataset=dataset.dataset, mode="live" if args.live else "fixture", model=config.model,
                      inputSha256=sha256(payload).hexdigest(), promptSha256=sha256(PROMPT.encode()).hexdigest(),
                      evidenceBudget=EVIDENCE_BUDGET, attemptsPerStrategyPerCase=1)
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(json.dumps(report["counts"], ensure_ascii=False))


if __name__ == "__main__":
    main()
