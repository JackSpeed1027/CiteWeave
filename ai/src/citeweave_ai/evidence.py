"""字幕摘要的输入证据和输出引用契约。"""

from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

NonBlank = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class EvidenceSegment(BaseModel):
    """携带可唯一定位的资料版本与字幕时间区间。"""

    model_config = ConfigDict(strict=True, extra="forbid")

    evidenceId: NonBlank
    sourceId: NonBlank
    sourceVersionId: NonBlank
    locationType: Literal["transcript", "paragraph", "page"]
    startMs: int | None = Field(default=None, ge=0)
    endMs: int | None = Field(default=None, gt=0)
    paragraph: int | None = Field(default=None, ge=1)
    page: int | None = Field(default=None, ge=1, le=500)
    text: NonBlank

    @model_validator(mode="after")
    def validate_interval(self) -> Self:
        """时间段、段落和页码字段互斥。"""
        if self.locationType == "transcript":
            if (self.startMs is None or self.endMs is None or self.endMs <= self.startMs
                    or self.paragraph is not None or self.page is not None):
                raise ValueError("字幕须有合法时间区间")
        elif (self.startMs is not None or self.endMs is not None
              or (self.locationType == "paragraph") != (self.paragraph is not None)
              or (self.locationType == "page") != (self.page is not None)):
            raise ValueError("文档定位只能包含段落编号或页码")
        return self


class SummaryDraft(BaseModel):
    """保存摘要草稿与待校验的引用标识。"""

    model_config = ConfigDict(strict=True, extra="forbid")

    summary: NonBlank
    evidenceIds: list[NonBlank] = Field(min_length=1)
    truncated: bool


class EvidenceBatch(BaseModel):
    """一次摘要请求的证据集合。"""

    model_config = ConfigDict(strict=True, extra="forbid")

    segments: list[EvidenceSegment] = Field(min_length=1)

    @model_validator(mode="after")
    def validate_unique_evidence_ids(self) -> Self:
        """同一批次的证据 ID 必须唯一，以便回查原始位置。"""
        if len({segment.evidenceId for segment in self.segments}) != len(self.segments):
            raise ValueError("同一批证据中的 evidenceId 必须唯一")
        return self

    def verify_draft(self, draft: SummaryDraft) -> SummaryDraft:
        """确认草稿引用只指向本次提供的证据。"""
        available = {segment.evidenceId for segment in self.segments}
        if not available.issuperset(draft.evidenceIds):
            raise ValueError("摘要引用了不在本次证据集合中的 evidenceId")
        return draft
