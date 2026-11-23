# 字幕证据与摘要引用契约

当前交付包括 Python 可执行的数据契约、样例与模型供应商适配。供 Java–Python 摘要联调使用。

| 模型 | 字段与约束 |
| --- | --- |
| `EvidenceBatch` | `segments` 至少含一条，每批 `evidenceId` 唯一 |
| `EvidenceSegment` | `evidenceId`、`sourceId`、`sourceVersionId` 为非空字符串；`locationType` 当前为 `transcript`；`startMs`、`endMs` 为整数毫秒并满足 `0 ≤ startMs < endMs`；`text` 非空 |
| `SummaryDraft` | `summary` 非空；`evidenceIds` 为至少包含一个标识的数组；`truncated` 为布尔值，表示生成时是否裁剪上下文 |

Java 的当前资料 API 仍使用数字 ID。跨服务契约中的标识按字符串传递，版本和片段 ID 由证据生产方生成。相邻片段允许重叠。Pydantic 拒绝额外字段和错误类型，字符串 `"4000"` 不会转换为毫秒整数。

[证据样例](../../sample-data/transcript-evidence.json)取自仓库示例字幕，资料与版本 ID 为测试用途。以下是匹配该样例的测试草稿。

```json
{
  "summary": "整理要点时保留可回查的出处。",
  "evidenceIds": ["fixture-cue-2"],
  "truncated": false
}
```

生成边界调用 `EvidenceBatch.verify_draft` 将引用与本次提供的证据比对。证据 ID 不在本批次内时拒绝草稿，由调用链决定重新生成或报告失败。这验证了引用可被定位，内容与证据是否相符仍需另外评估。验收用例见 [Python 测试](../../ai/tests/test_evidence.py)。

## 首次联调的引用校验

Python 按顺序保留预算内的完整片段，按实际序列化内容计算 12,000 字符上限。它对实际输入批次调用 `verify_draft`，把输入 ID 和裁剪标志返回 Java。Java 校验输入是原证据的有序前缀、引用属于实际输入，并从原片段回填原句与时间。发布时通过 SQL 再次比对资料原文和类型，成功时保存结果及实际输入 ID。第一版使用现有本机资料访问边界，工作区权限待独立交付。

## 研究会话的版本化扩展（计划）

当前字幕摘要继续使用以上批次与草稿。扩展研究接口时另行版本化并提供 Java、Python、Web 共用的成功和失败样例。会话提交包含会话 ID、请求键、预期修订和当前问题。由 Java 从已认证请求确定用户与工作区。运行恢复携带运行 ID、尝试号及检查点版本，更新采用条件写入。

供应商适配器新增独立的工具调用方法，返回最终文本或带调用 ID、白名单工具名及结构化参数的请求列表。每个调用都应产生对应结果或可辨识的错误。限制单轮调用数量和结果大小，未知工具、重复调用 ID、无效参数及目标模型不支持工具均需单测。保留原有非流式文本方法供摘要使用。

压缩输入按有序轮次指定覆盖范围。输出包含原会话修订、目标、约束、结论与证据 ID、分歧、未决问题和记忆修订引用。发布前核验版本与引用。长期记忆确认、更正、撤销支持请求去重和修订冲突反馈，读取和最终生成均以最新授权为准。具体端点随实现更新[接口文档](API_REFERENCE.md)。业务验收见[专项计划](../planning/AGENT_MEMORY.md)。

## 模型供应商 Adapter（当前文本调用）

[OpenAI-compatible Adapter](../../ai/src/citeweave_ai/model_provider.py)由 Python 工作流注入 `httpx.AsyncClient` 和 `ProviderConfig`。配置包含 API 根地址（例如 `https://provider.example/v1`）、模型名、API Key 及超时值。生产配置应使用 HTTPS，密钥由运行环境注入。地址来自部署配置，资料与用户消息只进入请求正文。客户端关闭自动重定向，调用方负责连接生命周期。

Adapter 调用 `POST /chat/completions`，获取首个非流式文本结果。它区分 `MODEL_TIMEOUT`、`MODEL_UNAVAILABLE`、`MODEL_AUTH_FAILED`、`MODEL_RATE_LIMITED`、`MODEL_UPSTREAM_ERROR`、`MODEL_RESPONSE_INVALID`、`MODEL_REFUSED` 与 `MODEL_OUTPUT_TRUNCATED`。异常携带稳定错误码，调用方负责重试与展示。后续摘要工作流负责解析结构化输出，再交给 `EvidenceBatch.verify_draft` 校验引用。

[离线测试](../../ai/tests/test_model_provider.py)覆盖请求协议、响应形状、错误分支与重定向。内部摘要端点已接通，真实供应商推理单独验收。

## 单字幕问答

Java 与摘要复用原字幕解析及版本化证据格式。问答先以中文双字词和英文单词做候选匹配，按匹配数降序、同分保持字幕顺序，最多 5 段。跳过超过 1800 UTF-16 单元的单段。无命中时 Java 直接返回证据不足。Python 沿用 12,000 字符的序列化证据预算，问题最多 500 字符。Python 对实际输入做引用校验，Java 对返回的有序输入前缀和引用再校验。格式通过意味着引用能够定位，真实答案是否受原文支持需要独立评测。

同义术语实验只在词面无候选时启用完整术语匹配。开关默认关闭；候选仍来自当前字幕，并经过两层引用校验。召回收益与无答案候选的代价见[评测记录](../quality/BENCHMARK.md)。

跨字幕问答沿用内部问答请求。候选可来自 2–3 份经 Java 校验的字幕，并按所选资料轮流排列，总数至多 6 段。Python 在预算内为模型保留候选的 sourceId；Java 对实际输入前缀与引用集合的复核规则一致。


文档问答使用 locationType=paragraph，携带正整数 paragraph，省略 startMs 与 endMs。字幕仍使用 locationType=transcript 和整数毫秒区间。Python 要求两类定位互斥，模型输入的段落编号计入证据预算，Java 复核后回填原句与定位。

PDF 问答使用 locationType=page 与正整数 page，不能同时包含 paragraph 或字幕时间。长页片段共享物理页码，但是 evidenceId 不同。

跨资料问答允许同一批候选混用这三种定位。Java 先校验选中资料，分资料检索，再复核实际输入前缀和返回引用。Python 保留各候选的资料归属和定位字段；原句及定位以 Java 的证据为准。候选不足时，生成端需要指出证据缺口。

V19 提供可选的 `lexical-v1` 索引，开启后跨资料问答和研究工具复用现有切片、分词和排序。索引版本与资料版本分别记录，证据 ID 不包含索引版本。首查和后续查询返回相同形状，原文回读仍须核对当前资料版本。
