"""在相同证据下区分检索空集、模型拒答和带合法引用的错误答案。"""

import asyncio

from citeweave_ai.evaluate_refusal import Case, Dataset, evaluate, fixture_transport
from citeweave_ai.evidence import EvidenceSegment
from citeweave_ai.model_provider import ProviderConfig


def test_valid_citation_does_not_mark_an_unsupported_answer_as_correct():
    cue = EvidenceSegment(evidenceId="cue", sourceId="1", sourceVersionId="v1",
                          locationType="transcript", startMs=0, endMs=1000, text="用整数毫秒保存时间码。")
    dataset = Dataset(dataset="transcript-alias-holdout-v1", cases=[
        Case(id="H10", question="支持 60 fps 吗？", expectedAnswer=None, requiredGroups=[],
             lexical=[], aliasFallback=[cue]),
        Case(id="H11", question="使用哪种视觉模型？", expectedAnswer=None, requiredGroups=[],
             lexical=[], aliasFallback=[cue]),
        Case(id="H02", question="时间精度如何？", expectedAnswer="毫秒", requiredGroups=[["cue"]],
             lexical=[cue], aliasFallback=[cue]),
    ])
    config = ProviderConfig("http://fixture.invalid/v1", "test", "fake")
    report = asyncio.run(evaluate(dataset, config, fixture_transport(dataset)))
    counts = report["counts"]
    assert counts["lexical"]["modelCalls"] == 1
    assert counts["aliasFallback"]["unanswerableOutcomes"] == {"UNSUPPORTED_ANSWER": 1, "MODEL_REFUSED": 1}
    assert counts["aliasFallback"]["outcomes"]["NEEDS_REVIEW"] == 1
    unsupported = report["observations"][1]
    assert unsupported["citationIds"] == ["cue"]
    assert unsupported["actualInputIds"] == ["cue"]
