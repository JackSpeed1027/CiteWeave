import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from citeweave_ai.evidence import EvidenceBatch, SummaryDraft


@pytest.fixture
def evidence_data() -> dict:
    fixture = Path(__file__).resolve().parents[2] / "sample-data" / "transcript-evidence.json"
    return json.loads(fixture.read_text(encoding="utf-8"))


def test_accepts_known_references(evidence_data: dict) -> None:
    batch = EvidenceBatch.model_validate(evidence_data)
    draft = SummaryDraft(summary="测试草稿", evidenceIds=["fixture-cue-2"], truncated=False)

    assert batch.verify_draft(draft) is draft
    assert batch.segments[1].startMs == 4500


@pytest.mark.parametrize("value", [1000, 999, "4000", False])
def test_rejects_invalid_interval_or_time_type(evidence_data: dict, value: object) -> None:
    evidence_data["segments"][0]["endMs"] = value

    with pytest.raises(ValidationError):
        EvidenceBatch.model_validate(evidence_data)


def test_rejects_duplicate_evidence_ids(evidence_data: dict) -> None:
    evidence_data["segments"][1]["evidenceId"] = "fixture-cue-1"

    with pytest.raises(ValidationError, match="必须唯一"):
        EvidenceBatch.model_validate(evidence_data)


def test_rejects_unknown_reference(evidence_data: dict) -> None:
    batch = EvidenceBatch.model_validate(evidence_data)
    draft = SummaryDraft(summary="测试草稿", evidenceIds=["missing-cue"], truncated=False)

    with pytest.raises(ValueError, match="不在本次证据集合"):
        batch.verify_draft(draft)
