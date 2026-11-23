"""回归冻结评测的融合排序与命中口径，测试不加载模型。"""

from citeweave_ai.evaluate_dense import groups_found, reciprocal_rank_fusion


def test_hybrid_promotes_evidence_returned_by_both_rankings():
    assert reciprocal_rank_fusion(["a", "b"], ["c", "b"], limit=2) == ["b", "a"]


def test_empty_lexical_scope_returns_dense_order():
    assert reciprocal_rank_fusion([], ["c", "b", "a"], limit=2) == ["c", "b"]


def test_alternative_evidence_hits_one_group_once():
    assert groups_found(["a", "b"], [["a", "b"], ["c"]]) == 1
