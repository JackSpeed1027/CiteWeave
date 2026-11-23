# 开发日志

## 2026-11-19 · 首次资料导入链路

从空目录建立 React + TypeScript 和 Java 21 + Spring Boot 工程。通过 Compose 提供 PostgreSQL，使用 Flyway 创建资料表。新增 Markdown、SRT 和 VTT 示例，自动化测试覆盖真实 PDF 文本提取、字幕时间戳保留、错误编码及粘贴文本校验。

运行和手动验收指令见 README。

### 验证结果

- Java 21.0.12.1、Maven 3.9.11、Node.js 24.22.0、npm 11.19.1 和 Docker Compose 5.6.1 实机验证。PostgreSQL 17.12 容器通过健康检查，Flyway V1 成功应用。
- Maven 打包通过，4 项测试全部通过。直连 PostgreSQL 的 HTTP 验证覆盖文件和文本入库、列表与详情、404/413/415/422 返回及 Java 重启后的同记录回读。
- 浏览器验证 Markdown 文件上传、原文显示、刷新回读、标题搜索与类型筛选。损坏 PDF 显示可操作的错误消息，首页仍显示 5 份验收样本。
- 由于本机 8080 已占用，Java API 和 Vite 代理默认配对到 8087。

## 2026-11-19 · 接管审计与字幕时间轴

- 核实 GitHub 仓库无引用，关联 origin，保存本地原型基线 71e351e，从该提交创建 feature/video-transcript。
- 增加时间轴读接口和上传前校验。真实 PostgreSQL HTTP 检查通过，SRT/VTT 新旧记录均可回读，非字幕 404、倒序字幕 422，失败时资料数不变。两轮验证创建了四条标为 API transcript verification 的样本。
- Maven 打包通过，16 项测试通过。Vite 生产构建通过。用户人工字幕实验和答题待记录。
- 完成视频候选对照，验证 Windows 的 yt-dlp 对一个公开样例可读元数据。没有拿到可用字幕，后续用获授权样例验证多 P 与有字幕分支。

## 2026-11-20 · 正式开发底座

- 从已验收原型的提交 cd53c89 建立正式集成基线，保留现有分支、原型入口和开发数据卷。
- 实跑 PostgreSQL 17.12、Java 8087、Vite 5173 与 Python 8091。Vite 转发的资料列表与 Java 直连一致，FastAPI 健康和 OpenAPI 入口可访问，原型端口 5175 可用。
- Maven 打包通过，16 项测试通过。uv 锁一致性检查通过，Python 健康端点测试 1 项通过。Vite 生产构建通过。
- 增加仓库规范、Java 和 Python 并行计划、设计模式适用边界与环境隔离小实验。AI Engine 的首个能力为 HTTP 健康检查，模型业务接口在后续功能交付。

## 2026-11-20 · HTTP 契约与文档目录

- 资料模块采用稳定错误码，业务异常由 HTTP 层映射为 Problem Details，原成功响应及 `201 Location` 契约保留。
- Maven 20 项测试及前端生产构建通过。在临时回环端口实测 400、404、413、415、422 响应，核对媒体类型、状态、请求路径、问题类型和错误码。
- 既有文档整理为产品、开发、架构、计划、测试和学习六类。根 README 与索引已更新，50 条本地 Markdown 链接全部可定位。

## 2026-11-21 · Bilibili 输入与字幕证据契约

- Java 增加无平台请求和无入库副作用的 Bilibili 输入规范化接口。支持 BV、AV、短链与分 P 提示，返回稳定的 Problem Details 错误码。
- Python 增加严格的字幕证据和摘要草稿模型，拒绝非法时间区间、重复证据 ID 和本批次以外的引用。证据测试使用仓库字幕样例。
- Maven 42 项、Python 8 项测试通过。临时 Java 实例实测 BV、AV、短链、非法输入及损坏 JSON，前后资料列表一致。
- 更新接口文档和小实验 04。下一步为受控元数据预览 Adapter 和模型供应商契约，分别验收。

## 2026-11-21 · Bilibili 预览与模型供应商适配

- Java 增加 `POST /api/video-imports/bilibili/preview`。BV/AV 编号查询固定的匿名公开端点，分 P 在本地校验，CID 按字符串保留精度。短链返回 422 并引导使用完整链接。
- 真实样例最初因平台返回空分 P 标题被拒绝。补充标题回退及回归用例后，公开 `BV1xx411c7mD` 返回 200、一个分 P，越界分 P 返回 422。临时实例还验证损坏 JSON 返回 400、短链返回 422、预览前后 PostgreSQL 资料列表一致。验证结束后关闭临时实例。
- Python 增加 OpenAI-compatible 文本供应商 Adapter。将 HTTPX 放入运行依赖，锁文件已更新。离线契约测试覆盖请求体、超时、鉴权、限流、重定向、拒答和截断。真实模型调用与摘要接口待联调。
- `mvn.cmd -o -B -f server/pom.xml package` 通过，50 项测试。由 `ai` 运行 `uv run --offline --locked pytest` 通过，23 项测试。
- 更新接口、测试计划、架构决策和实验 05。下一步为 Java 来源与字幕轨道契约、Python 结构化摘要与证据引用校验接线。用户实验的观察结果待记录。
- 交付验收环境：确认 8087 的旧 Java 属于本工作区后，后台启动新版 Java。资料列表返回 200 且比对一致，公开预览返回 200，短链返回 422。使用现有数据库结构启动，前端、Python 和 PostgreSQL 未重启。

## 2026-11-21 · 正式首页与仓库里程碑看板

- 在 `develop` 核对源码、接口与已有实测记录。原 README 已有五项里程碑，正式首页缺少看板。正式首页保留导入、资料库和最近导入。字幕原文及已有时间轴 API、公开 BV 预览、Python 离线契约的验证层级分别保留。
- 新增 [共享里程碑数据](../../web/src/progress/milestones.json)和正式首页看板。README 通过脚本生成对应区域。每项记录证据、负责人、下一道验收和个人体验状态。生产构建会拒绝过期的 README。
- 在 `web` 执行 `npm.cmd run build` 和 `npm.cmd run progress:test`。构建通过，3 项同步测试通过。合成的摘要状态变更可让旧 README 被判定为过期，生成后两处状态相同。
- 使用 Playwright CLI 的 `open`、`run-code --filename` 从 5173 正式入口验证 1440×960 和 390×844，各显示 5 项且无横向溢出。导入、资料库和返回工作台路径通过。浏览器内拦截资料响应，记录加载、503、重试后空库，期间看板保持可见。只替换浏览器的摘要状态夹具，验证标记变化并恢复。截图和执行记录位于本机 Git 忽略路径 `.tool-cache/output/playwright/`。
- 现有 Vite 已加载改动并通过浏览器验证，本轮没有服务需要手动重启。数据与接口无变更。用户的 [实验 06](../learning/LABS.md#实验-06--看板如何发现两处进度不一致) 待亲自执行。下一项为正式字幕摘要、证据回跳与刷新读回闭环。


## 2026-11-21 · 字幕摘要、引用定位及持久化

- 复用 SRT/VTT 解析、证据模型与供应商 Adapter。Web 的字幕资料工作区增加「字幕摘要」和可聚焦的时间轴。Java 新增摘要 GET/POST，Python 新增内部 V1 端点。Java 保存首个通过复核的结果和实际输入 ID。
- `mvn.cmd -o -B -f server/pom.xml package` 通过 52 项测试；在 `ai` 执行 `uv run --offline --locked pytest` 通过 26 项；在 `web` 执行 `npm.cmd run build` 通过。README 与工作台同步。
- 启动 Docker Desktop，以 `docker compose -p citeweave-summary-qa -f infra/compose.yaml up -d --wait` 启用独立 PostgreSQL 17.12（15434），仅此库应用 V1/V2。背景联调使用 Java 18087、Python 18091、Vite 预览 15173 和回环合成供应商。用 `node .tool-cache/summary-qa.mjs` 验证 200、重复提交只调用供应商一次、Java 重启后结果一致。分别观察坏引用 502、预算不足 422、超时 504；重启独立 AI Engine 移除测试配置后观察 503。各失败的摘要 GET 仍为 204。
- Playwright CLI 从正式 Web 构建验证生成、引用按钮回跳、1000–2400 ms 原句和键盘焦点，以及刷新后再次跳转。脚本为本机 Git 忽略路径 `.tool-cache/summary-browser-check.js`。合成供应商的结果只用于工程验证，没有测量真实模型的摘要质量。
- 正式数据库及其迁移状态保留。应用验收进程验证后退出，独立数据库与夹具数据保留。用户集中验收包括 [实验 07](../learning/LABS.md)、真实模型生成和字幕分 P 隔离。真实模型未配置；分 P 轨道绑定待开发。


## 2026-11-21 · Bilibili 分 P 与手动字幕绑定

- 正式导入页增加「Bilibili 字幕」。用户预览并选择分 P，再上传 SRT/VTT。Java 在固定平台端点重查 BV、分 P、CID；确认一致后，在同一事务内保存正文与来源。V3 新增绑定表，资料详情可读回分 P、CID 和用户上传标记。
- 在隔离 PostgreSQL 17.12（15434）设置 `CITEWEAVE_ISOLATED_PG_TEST=1`，执行 `mvn.cmd -o -B -f server/pom.xml package`，53 项通过。V3 从已有 V2 升级成功。两条合成字幕使用相同区间 1000–2400 ms，第二集引用第一集返回 502，摘要读取仍为 204，修正引用可发布第二集原句。过期 CID 返回 409、非字幕 415，两种错误均未新增资料。
- 后台启动独立 Java 18087，实际 HTTP 验证两集的来源与摘要在重启后仍可读，V2 独立字幕及旧摘要继续可读。验完关闭独立 Java。Vite 生产构建通过，Playwright 390 px 窄屏的合成响应验收覆盖换 P 清除旧文件、409 后重新预览和第二集原站链接。
- 正式库保留现有迁移状态。真实多 P 素材、真实模型与用户体验验收留待集中执行。手动绑定不验证字幕与音视频内容的语义匹配。操作与结果表见[实验 08](../learning/LABS.md)。


## 2026-11-21 · 字幕摘要笔记

- 已发布摘要可保存为笔记。Java/V4 复制摘要、来源版本和引用快照，个人空间内同一资料版本只保留一条。侧边栏「笔记」可浏览及重开快照，点击引用按完整证据 ID 回到字幕。内容编辑和多工作区鉴权待后续功能。
- 在独立 PostgreSQL 17.12 应用 V4，设置 `CITEWEAVE_ISOLATED_PG_TEST=1` 运行 `mvn.cmd -o -B -f server/pom.xml package`，54 项通过。8 个同时保存请求得到 1 个 201、7 个 200、同一 UUID 和相同快照；摘要缺失返回 409，未知笔记返回 404。原分 P 用例同时回归通过。
- 在 Web 运行 `npm.cmd run progress:test`（3 项）及 `npm.cmd run build` 均通过。用 Playwright CLI 和本机脚本 `.tool-cache/note-browser.js` 从 390 px 的正式 Web 构建走保存、列表重开、刷新、引用回跳；使用真实 Java 和独立 PostgreSQL，以及上轮的合成摘要。伪造完整证据 ID 时有明确提示且无字幕被选中。
- 关闭后后台重启独立 Java 18087，通过真实 HTTP 读回浏览器创建的笔记与相同摘要快照，时间区间为 1000–2400 ms。验收进程已关闭，正式数据库未迁移。真实模型及个人体验仍待集中验收。实验记录见[实验 09](../learning/LABS.md)。


## 2026-11-21 · 单字幕资料问答

- 字幕详情增加「问这份字幕」。Java 在当前轨道内按中文双字词与英文词匹配排序，最多取 5 段，无命中时跳过模型。Python 处理预算和结构化回答；Java 再确认实际输入与引用范围，并从原字幕回填定位。回答暂不持久化。
- `mvn.cmd -o -B -f server/pom.xml package` 通过 58 项（含隔离 PostgreSQL 回归）；`uv run --offline --locked pytest` 通过 30 项；Web 构建和同步检查通过。摘要、分 P、笔记的旧数据与接口回归通过。
- 后台启动独立 Java 18087、Python 18091、Web 15175 与回环模型夹具，健康检查通过。Playwright 从 390 px 字幕详情验证 4 条路径：无命中模型 0 次、命中后引用聚焦原句、模型主动拒答、坏引用显示错误且没有引用按钮。问答前后资料列表与笔记一致，原摘要仍为 204；刷新清空当次问答。验收脚本在 `.tool-cache/question-browser.js`。临时进程已关闭，本轮无新增迁移。
- 真实模型质量、中文同义问题召回及个人体验待集中验收。这批结果是工程回归，真实模型未调用。[实验 10](../learning/LABS.md)记录操作和个人实测空表。


## 2026-11-21 · 单字幕检索对照题集

- 新增 `transcript-v1` 合成基线，2 份 SRT 和 42 题。其中 30 题可回答，12 题无答案，覆盖 12 对原词与同义改写、6 道同资料多证据题。逐题标注预期结论及必需证据组。
- `mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptRetrievalBenchmarkTest,TranscriptQuestionTest" test` 通过 5 项。单独重跑检索入口，逐题 JSON 一致。可回答题宏平均 Recall@5 为 76.7%，完整召回 23/30；原词 12/12，改写 5/12；无答案题 6/12 无候选。输出与失败案例归档在[评测记录](../quality/BENCHMARK.md)。
- 本轮评测直接跑生产解析和检索。生产排序沿用现有实现，本轮无服务需要启动或重启。完整发布题集、真实模型回答质量和跨资料评测待做。用户集中验收见[实验 11](../learning/LABS.md)。


## 2026-11-23 · 同义术语补召回

- 加入默认关闭的 `citeweave.question.alias-fallback`。只在词面召回为空时匹配完整术语，复用当前字幕与证据校验。保留原 42 题和原始归档，新增 12 道同领域独立合成题。
- 原题 Recall@5 从 76.7% 到 83.3%，新题从 37.5% 到 87.5%。新题 4 道无答案题中，3 道开启后出现相关但不能回答的候选。因此暂作对照开关，真实模型拒答待验。[评测记录](../quality/BENCHMARK.md)
- `mvn.cmd -o -B -f server/pom.xml package` 成功，60 项通过，2 项独立数据库用例按默认配置跳过。启动独立 PostgreSQL，在后台启动 Java 与无模型配置的 Python。默认请求返回 200 和证据不足；重启 Java 开启开关后，同一问题返回 503 `QUESTION_MODEL_NOT_CONFIGURED`，证实新候选进入了模型路径。关闭两端验收进程，正式库未迁移。
- 实测留在逐题对照 JSON。集中验收见[实验 12](../learning/LABS.md)。


## 2026-11-23 · 无答案候选的拒答评测

- Java 输出 12 道合成题的两套真实候选，Python CLI 使用同一问答工作流，分开记录无候选、模型拒绝、无依据答案与待复核结论。默认使用离线供应商夹具，外部模式需显式 `--live`。
- `mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptRetrievalBenchmarkTest" test` 通过 2 项，`uv run --offline --locked pytest` 通过 31 项。实际跑完 24 条配对路径。H10 有合法引用但回答超出原句，被标为 `UNSUPPORTED_ANSWER`。无模型配置时，`--live` 以参数错误结束，未生成报告。
- [评测记录](../quality/BENCHMARK.md)保留分母与运行方法，[实验 13](../learning/LABS.md)留待集中验收。没有生产接口、数据库或运行配置变更，本轮无需重启。真实模型调用待合法配置与预算，补召回仍默认关闭。


## 2026-11-23 · 资料收藏与筛选

- 资料详情增加收藏和取消操作，资料库可以叠加「仅收藏」、标题搜索和类型筛选。Java 用工作区与资料 ID 定位收藏关系，通过显式目标状态的 PUT 写入。取消后保留原文、摘要与笔记。
- 独立 PostgreSQL 17.12（15434）应用 V5。设置 `CITEWEAVE_ISOLATED_PG_TEST=1` 后执行 `mvn.cmd -o -B -f server/pom.xml package`，63 项全通过。并发 8 次收藏只写入一条状态记录；两次取消后正文仍可读。布尔值格式错误返回 400，资料不存在返回 404。
- 后台启动独立 Java 18087 和 Web 15175。Playwright 从 390 px 的正式构建验证了模拟 503 后重试、收藏后刷新、叠加标题搜索与「仅收藏」。随后重启独立 Java，浏览器读回收藏，再取消并核对收藏列表及原文。测试使用独立合成资料，验收进程已关闭。
- 正式数据库未迁移。个人收藏的集中验收见[实验 14](../learning/LABS.md)。分组收藏夹与多工作区鉴权待后续交付。


## 2026-11-23 · 笔记 Markdown 导出

- 新增只读的笔记 Markdown 下载，复用笔记快照与本机工作区读取边界。输出包含摘要、资料版本、证据 ID、毫秒区间与原句。用户与模型文本用字面块保存，文件名取稳定 UUID。原文件和数据库迁移无变更。
- 启用现有独立 V5 验收库，执行 `mvn.cmd -o -B -f server/pom.xml package`，64 项通过。覆盖 UTF-8 响应头、附件名、不存在的笔记以及 HTML、图片和标题语法的字面导出。
- 后台启动 Java 18087 和 Web 15175。Playwright 在 390 px 实测模拟 503 后重试、浏览器下载及实际文件读回。下载内容含中文原句和 1000–2400 ms，笔记列表一致。导出不需要 Python 或模型。验证后关闭独立进程。
- 接口见[笔记导出](../development/API_REFERENCE.md)，集中验收入[实验 15](../learning/LABS.md)。


## 2026-11-23 · 跨字幕问答与证据定位

- 「资料库 → 跨字幕问答」支持选 2–3 份字幕。Java 仅在选定资料召回，轮流取候选，每份最多两段。复用 Python 的预算、供应商与双层引用校验。新增证据读取接口，用资料 ID、版本和完整证据 ID 定位原句。没有新增数据库迁移。
- 独立 PostgreSQL 回归下运行 `mvn.cmd -o -B -f server/pom.xml package`，66 项通过；`uv run --offline --locked pytest` 31 项通过。Web 构建通过。测试覆盖重复及非数字资料 ID、两份字幕同时间但不同身份，以及未选资料引用拒绝。
- 后台启动独立 Java、Python、Web 和回环合成模型。Playwright 在 390 px 验证三份合成字幕中只选两份，模型只收到两份证据；实际存在但未选中的第三份引用返回 502。两条 1000–2400 ms 链接各自高亮正确原句，刷新后可定位，错资料和错版本返回 404。问答无持久化写入，正式库未变更。验收后关闭临时进程。
- 真实比较答案的质量与个人体验仍待集中验收。[实验 16](../learning/LABS.md)已留实测栏。


## 2026-11-23 · 文本问答与段落引用

- 粘贴文本、TXT、Markdown 资料详情支持「问这份资料」。Java 以空行分段并保留源文本，通过资料 ID、正文版本和段号建立引用；Python 校验段落与字幕时间区间互斥。字幕版本前缀与旧引用保持兼容，PDF 页码引用待处理。
- 在独立库启用数据库回归，运行 `mvn.cmd -o -B -f server/pom.xml package`，67 项通过；`uv run --offline --locked pytest` 32 项通过；Web 构建通过。没有新增数据库迁移。
- 后台启动独立 Web、Java、Python 和回环合成供应商。Playwright 在 390 px 下验证有据回答、未召回、越界引用拒绝。CRLF 与连续空白行的 Markdown 正确分为三段，点击引用聚焦第 2 段的原句，刷新后证据 ID 一致。关闭临时进程，正式库无变更。
- 真实答案质量仍待集中验收，[实验 17](../learning/LABS.md)已留实测栏。


## 2026-11-23 · PDF 问答与原始页码

- 新上传文本层 PDF 在一次 PDFBox 解析中保留合并正文和原始页码。V6 在同一事务内保存资料与逐页文本。空白页不生成证据，后续页不重排；长页按片段召回但共用物理页码。
- 用独立 PostgreSQL 17.12 应用 V6。启用数据库集成回归后，`mvn.cmd -o -B -f server/pom.xml package` 通过 68 项，`uv run --offline --locked pytest` 通过 33 项，Web 构建通过。历史 PDF 合并正文兼容，旧记录不补造页码。
- 后台启动独立 Web、Java、Python 和合成供应商。Playwright 从 390 px 的导入页上传真实三页测试 PDF，读取页码 1、3；回答的第 3 页引用聚焦原句，刷新后证据 ID 一致。历史 PDF 依旧显示原文和重新导入提示。打包后再次后台启动独立 Java，浏览器导入的测试 PDF 仍读回第 1、3 页与准确原句。验收进程已关闭，正式库未迁移。
- 扫描页 OCR 与 PDF 原件显示待后续工作，真实比较答案仍待集中验收。[接口文档](../development/API_REFERENCE.md)与[实验 18](../learning/LABS.md)已更新。

## 2026-11-23 · 跨资料问答与三种引用坐标

- 「资料库 → 跨资料问答」可混选 2–3 份字幕、文本、Markdown 和有页码证据的 PDF。沿用现有版本化证据、Python 问答契约和模型校验；原字幕专用端点保持兼容。历史 PDF 仍可读正文，候选列表排除无页码的记录。没有新增数据库迁移。
- 在独立 PostgreSQL 17.12 上运行 `mvn.cmd -o -B -f server/pom.xml package`，69 项通过。集成回归覆盖历史 PDF 排除、提交不可引用资料时跳过模型、三种坐标回填和越界引用拒绝。`uv run --offline --locked pytest -q` 通过 33 项。`npm.cmd run progress:test` 通过 3 项，`npm.cmd run build` 通过。
- 后台启动独立 Web 15175、Java 18087、Python 18091 和本地模型夹具 18092。Playwright 在 390 px 和 1280 px 保存截图，验证三种证据点击定位及刷新可定位，错误资料链接有提示，证据为空时拒答，范围外引用被拒绝。浏览器与 Java 写入局限于合成资料所在独立库，正式库未迁移。
- 最终构建后后台重启 Web 和本地模型夹具。浏览器重新加载首页可读到本轮进度，Web 200、Python 健康检查正常，既有合成样例再次返回三类引用。独立验收服务保留运行，日志在 Git 忽略的 `.tool-cache/service-logs/`。
- [接口契约](../development/API_REFERENCE.md#跨资料问答)和[实验 19](../learning/LABS.md#实验-19--三种坐标混选时如何防止引用串到另一份资料)已更新。真实模型比较的准确性和个人体验仍待集中验收。

## 2026-11-23 · 先查证据，再决定是否生成

- 「资料库 → 跨资料问答」新增「查证据」，可选 1–3 份资料。检索与生成共用现有词面候选，始终先限定资料，再排序。另提供按资料 ID 和完整证据 ID 读取当前版本原句的接口。两条读路径无模型调用、无业务数据写入、无新数据库迁移。
- 独立 PostgreSQL 回归下，`mvn.cmd -o -B -f server/pom.xml package` 的 70 项 Java 测试全部通过，旧 Java 持有 Windows 上的构建 JAR 导致首次重打包失败。核对进程后停止旧实例，执行 `mvn.cmd -o -B -f server/pom.xml "-Dmaven.jar.forceCreation=true" "-DskipTests" package` 成功，从独立 JAR 副本启动，避免后续构建锁。最终 Web 构建通过，看板同步 3 项通过。
- 后台重启隔离 Java、Web 及与 Web 同进程的合成模型夹具，保留 Python 进程。Web 返回 200，Python 健康检查正常。HTTP 验收确认命中一条 Markdown 第 2 段、精确回读原句、换资料返回 404，无关词返回空数组。
- Playwright 从 390 px 的资料库入口验证单份查证据、HTTP 503 后重试、无匹配反馈、跳转并刷新定位；浏览器未请求生成接口，Java 集成测试验证模型无调用。截图在 `.tool-cache/output/evidence-search-mobile.png`。独立服务留给集中验收，正式数据库未迁移。
- 已更新[接口契约](../development/API_REFERENCE.md#查找和读取证据)、[Agent 计划](AGENT_MEMORY.md)和[实验 20](../learning/LABS.md)。工具调用、身份授权、研究轨迹与跨会话恢复仍待开发。

## 2026-11-23 · 只读研究工具回合

- Python 模型适配器接入原生函数调用，现有非流式文本请求兼容。小型循环只执行检索与精确回读。调用方固定资料范围，Python 验证工具名、参数、预算与证据版本，通过 `tool_call_id` 配对工具结果。只有二次读回的证据可被最终引用。内部接口默认关闭，轨迹随响应返回。
- `uv run --offline --locked pytest -q` 通过 44 项。新增覆盖调用配对、原句保留、非法工具名、参数额外字段、越界资料、重复或并行调用、证据变更、未读就引用和步数超限。Web 构建和看板同步 3 项均通过。
- 后台重启隔离 Python 18091，新增本地模型夹具 18093，沿用 Java 18087 与独立 PostgreSQL。HTTP 实测健康检查正常；正常例返回 `ANSWERED`、三步与两条引用，越界例返回 502 `RESEARCH_EVIDENCE_OUT_OF_SCOPE` 且只有搜索一步；原跨资料问答仍返回三条引用。正式库未迁移。
- [实验 21](../learning/LABS.md)及[内部接口文档](../development/API_REFERENCE.md#只读研究内部接口)已更新。真实模型兼容性、应用内的 Agent 入口、身份授权和会话恢复仍待验收或接入。

## 2026-11-23 · 正式 Web 接入单次只读研究

- 跨资料问答页加入「开始研究」。Java `POST /api/research` 先预检所选资料，再调用现有 Python 研究。Java 核验轨迹的来源和步骤关系，从当前版本补齐最终引用。失败响应保留经核验的已完成步骤，页面可改问后重试。
- `mvn.cmd -o -B -f server/pom.xml package` 在独立 PostgreSQL 环境通过 75 项。新增范围预检、原句回填、未回读引用和非法轨迹等五项回归。`npm.cmd run progress:test` 通过 3 项，`npm.cmd run build` 通过。
- 后台重启独立 Java 18087 与 Web 15175，Python 18091 健康检查正常，沿用本地模型夹具。Playwright 从资料库走通 HTTP 502 越界、保留一步轨迹、改问重试、三步成功和两条引用。两条原文链接刷新后都定位到原句并恢复焦点。保存宽 390 px、1280 px 的截图。正式数据库未迁移。
- [实验 22](../learning/LABS.md)和[接口文档](../development/API_REFERENCE.md#web-单次只读研究)已更新。当前单次运行适用本机访问边界，真实模型、会话级权限、历史与恢复待接续。

## 2026-11-23 · 已完成研究可从历史重开

- V7 新增研究历史。Java 仅对正常终态一次写入问题、资料范围、答案、轨迹与证据 ID；详情重查资料和原文，失败不入历史。Web 可从结果页或「最近研究」重开，后台只读接口无需模型。
- 独立库启用集成验证，`mvn.cmd -o -B -f server/pom.xml package` 通过 76 项，Web 构建及 3 项看板测试通过。测试验证跨工作区 UUID 返回 404、快照未复制原文、详情读取无模型调用。V7 仅应用于独立 PostgreSQL。
- 后台重启独立 Java、Web。Playwright 在 390 px 的正式入口保存一条合成记录，确认失败请求未入历史。再次重启 Java 后刷新同一 UUID，返回三步轨迹和两条引用；第 2 条仍定位到原文，浏览器读回无 POST。截图位于 `.tool-cache/output/research-history-mobile.png`。
- [接口文档](../development/API_REFERENCE.md#最近研究与详情)与[实验 23](../learning/LABS.md)已更新。可恢复检查点、会话权限、同一请求幂等键和真实模型仍待接续。

## 2026-11-23 · 同一研究请求只发布一条结果

- V8 用工作区和 `Idempotency-Key` 唯一认领。耗时模型调用在事务外；快照写入与请求完成在同一个短事务。完成后同键回读原记录，执行期间重复提交或改变绑定内容返回 409。已知失败关闭原键。
- `mvn.cmd -o -B -f server/pom.xml package` 在独立 PostgreSQL 通过 78 项。并发对照验证首个请求仍阻塞时第二次返回 409，模型仅被调用一次；终态发布失败时，快照插入回滚。Web 构建和看板同步 3 项通过。
- 后台重启独立 Java、Web，Python 健康检查正常。Playwright 实际让 Java 保存成功后丢弃浏览器响应，再重试。浏览器复用原键并读回同一 UUID，列表中只有一条。同键改写问题返回 409。V8 仅应用于独立库。
- 已更新[实验 24](../learning/LABS.md)和[请求契约](../development/API_REFERENCE.md#web-单次只读研究)。进程异常退出导致的处理中状态、检查点恢复和真实模型仍待接续。

## 2026-11-23 · 过期发布拦截与研究结果未知

- V9 为有键请求添加固定 60 秒发布期限。使用数据库时间限制发布，过期返回会回滚快照。重试发现过期，或收到可能已被上游受理的超时或通信错误时，保留原键并返回 `409 RESEARCH_REQUEST_OUTCOME_UNKNOWN`。Web 提供查看历史和明确用新键重跑。
- 隔离 PostgreSQL 通过 `mvn.cmd -o -B -f server/pom.xml package` 80 项。新测试仅对合成请求推进截止时间，验证晚到的回答不能落库，并覆盖超时后原键不再调用模型、新键可执行。Web 构建和看板同步 3 项通过。
- 后台重启隔离 Java、Web 和本地故障夹具，Python 健康检查及历史接口正常。Playwright 在 390 px 从正常入口验证未知状态、查看历史无 POST、明确重跑换键并成功、旧键持续返回 409。截图保存在 `.tool-cache/output/research-outcome-mobile.png`。V9 只应用在隔离库。
- [实验 25](../learning/LABS.md)和[接口文档](../development/API_REFERENCE.md#web-单次只读研究)已更新。使用固定期限处理阻断发布，步骤检查点、续租及自动接管仍待接续。

## 2026-11-23 · 研究步骤检查点

- V10 为有键研究保存已完成步骤的累计轨迹。Java 为本次运行绑定回调授权、资料范围和期限。Python 在下次模型调用前确认写入成功。Java 通过旧轨迹条件更新拒绝乱序或改写，在最终发布事务中核对快照轨迹与当前检查点。回调令牌未进入模型输入。
- 独立 PostgreSQL 中 `mvn.cmd -o -B -f server/pom.xml package` 通过 81 项，`uv run --offline --locked pytest -q` 通过 46 项，Web 构建和看板同步 3 项通过。新增覆盖重复步骤、非法授权、范围和顺序、超期写入、回调冲突后停止推进，以及只回传已提交的步骤。
- 后台重启独立 Java、Web、Python 和合成夹具。Playwright 在 390 px 确认上游在搜索后中断时页面仍有一步，同键重查调用 ID 未改变，新键显式重跑得到三步和两条引用。再次重启 Java 后用原键返回同一个 `call_search`。原越界路径复测仍返回 502 并带已确认的搜索步骤。
- [实验 26](../learning/LABS.md)及[接口文档](../development/API_REFERENCE.md#web-单次只读研究)已更新。V10 仅应用于隔离库。检查点可读回，模型消息和预算的持久化、续租与自动恢复仍待接续。

## 2026-11-23 · 研究检查点保存工具参数

- V11 将通过校验的工具参数、格式版本和剩余步数与轨迹一起原子写入。已确认参数不可改写，读取动作必须匹配版本化证据 ID。原文、模型消息和回调授权不会写入上下文。
- 初次 Java 集成测试遇到 Docker Desktop 未运行。恢复原隔离容器后，`mvn.cmd -o -B -f server/pom.xml package` 通过 81 项；`uv run --offline --locked pytest -q` 通过 46 项；Web 构建和看板 3 项通过。V11 只应用于独立库。
- 后台恢复隔离服务并走正常 Web 代理联调。搜索后的合成故障返回 409，PostgreSQL 只读核对保存的查询为 `Cache rebuild`、剩余 2 步；新键成功完成三步，V10 已保存记录仍可读。开发对照确认同轨迹改写参数返回 409、错误步数返回 400。
- [实验 27](../learning/LABS.md)和[接口契约](../development/API_REFERENCE.md#web-单次只读研究)已更新。模型上下文重建、令牌预算和接管待下一阶段实现。

## 2026-11-23 · 按原证据重建研究上下文

- Python 增加本机只读重建预检。沿 V11 检查点顺序回读精确版本，重建调用 ID、候选预览、已读原文和剩余工具步数。同一证据仅需一次 GET。预检只返回数量和序列化字节数。
- 首次离线运行发现源文件误写入字面 NUL，修正转义后 `uv run --offline --locked pytest -q` 通过 49 项。测试覆盖调用配对、证据已不可读、范围外和错误预算。
- 后台重启隔离 Python。通过现有 Web 和 Java 生成合成检查点，再读取对应的 PostgreSQL 记录联调。三步共重建 8 条消息、确认 2 条证据、剩余 0 步；本次编码 3617 字节。只更换首条证据的版本后返回 `404 RESEARCH_EVIDENCE_UNAVAILABLE`。
- [实验 28](../learning/LABS.md)与[接口文档](../development/API_REFERENCE.md#web-单次只读研究)已更新。重建已可验证，旧运行接管、模型续跑和令牌计量待接续。

## 2026-11-23 · 手动从检查点续跑

- V12 允许已标记结果未知且存在检查点的请求，在本机工作区从原问题和资料范围续跑。数据库只允许一个直接续跑。新键与父键绑定，重复提交同一新键回读同一结果。Python 精确回读原证据，恢复工具消息和剩余步数，然后调用模型。
- 首次运行发现 Python 前向注解未延迟求值，修正后 `uv run --offline --locked pytest -q` 通过 51 项。隔离 PostgreSQL 下 `mvn.cmd -o -B -f server/pom.xml package` 通过 81 项，覆盖变更输入、重复续跑键及拒绝分叉。Web 构建与看板同步 3 项通过。
- 后台重启隔离 Java、Python 和 Web。Playwright 用 390 px 从跨资料问答触发搜索后故障，点击「从检查点继续」得到 200、保留原搜索调用 ID、总计三步两引用。再用同一续跑键返回同一记录。Python 离线对照确认失效证据会在模型调用前停止；未使用真实模型。
- V12 仅应用于隔离库。[实验 29](../learning/LABS.md)和[接口文档](../development/API_REFERENCE.md#web-单次只读研究)已更新。当前从页面收到未知结果后显式续跑，独立用户身份、自动接管及令牌计量待接续。

## 2026-11-23 · 刷新后恢复有键研究

- V13 保存新建有键研究的原问题。Web 在提交时把请求 UUID 写入地址。刷新 GET 读回问题、资料、状态和轨迹。父链接可找到已发起的续跑，完成的链接能打开历史。过期状态在查询时按数据库时钟计算，读取未改变请求行。
- 隔离 PostgreSQL 下 `mvn.cmd -o -B -f server/pom.xml package` 通过 81 项。验证状态查询不调用模型、过期读取不写库、无效键返回 404，以及父子链接。`npm.cmd run build` 和看板 3 项通过。
- 后台重启隔离 Java 和 Web，Python 健康检查通过。390 px 浏览器实测在夹具故障后刷新恢复原问题、两个选项和一次搜索，仅发出 GET。手动续跑成功后，原链接能导航到同一完成记录。窄屏截图保存至 `.tool-cache/output/research-request-restored-mobile.png`。
- V13 仅应用于隔离库。旧请求无原问题时，已有终态仍可从「最近研究」打开。[实验 30](../learning/LABS.md)及[接口文档](../development/API_REFERENCE.md#web-单次只读研究)已更新。

## 2026-11-23 · 研究结果保存为可编辑笔记

- 从有据研究直接创建一份持久化笔记，重复创建回读原稿。标题与 Markdown 按修订号条件更新，引用保留服务端核验过的原句、定位和证据版本。笔记入口显示研究笔记和原字幕摘要笔记。
- 隔离 PostgreSQL 下 `mvn.cmd -o -B -f server/pom.xml package` 通过 82 项。新增对照验证重复创建保留编辑、旧修订号返回 409、导出包含原文版本，以及保存和导出无需再调用模型。Web 构建和看板 3 项通过。
- 后台重启隔离 Java 和 Web，Python 健康检查正常。Playwright 首次精确标签选择器未匹配编辑框，改用无障碍 textbox 选择器后完成 390 px 验收。编辑后刷新并从列表重开，通过浏览器实际下载 714 字节 Markdown，核对正文、毫秒和段号两类引用及证据 ID，点击第 2 条返回原文。
- V14 仅应用于隔离库。手动保存已实现，Markdown 预览、自动保存和版本恢复仍待后续。[实验 31](../learning/LABS.md)及[接口文档](../development/API_REFERENCE.md#可编辑研究笔记)已更新。

## 2026-11-23 · 任务中心接入后台字幕摘要

- V15 保存队列与尝试状态。Java 用 `FOR UPDATE SKIP LOCKED` 领取，摘要发布校验当前尝试和 60 秒租约，失败需用户显式重试。任务中心显示真实状态并可打开已保存的摘要。同步摘要入口仍可用。
- `mvn.cmd -o -B -f server/pom.xml package` 在独立 PostgreSQL 通过 84 项，覆盖同资料去重、原任务重试、迟到结果拒绝发布。Web 构建和看板 3 项通过。
- 后台重启隔离 Java、Web 和本地模型夹具，Python 健康检查正常。Playwright 在 390 px 从合成 SRT 入队，限流后刷新并手动重试，同一任务第 2 次成功。点击摘要引用定位到 1000–2400 ms。截图在 `.tool-cache/output/summary-task-mobile.png`。
- V15 仅应用于隔离库。当前环境未配置真实模型，联调仅使用合成夹具。视频导入、索引任务、取消和自动恢复仍待接续。[接口文档](../development/API_REFERENCE.md#后台字幕摘要任务)和[实验 32](../learning/LABS.md)已更新。

## 2026-11-23 · 摘要与任务接入 MyBatis-Plus

- 接入 Spring Boot 3 starter。任务详情使用 `BaseMapper`，领取、租约和摘要发布 SQL 集中到 Mapper XML。控制器通过 Service 访问任务，Flyway 版本和 API 保持兼容。其他持久化模块保留 JdbcClient。
- 回归发现 PostgreSQL 的 UUID 需要显式类型处理，修复后任务 ID 可正确读回。隔离 PostgreSQL 下设置 `CITEWEAVE_ISOLATED_PG_TEST=1`，执行 `mvn.cmd -o -B -f server/pom.xml '-Dciteweave.tasks.worker-enabled=false' package`，84 项通过。测试关闭后台调度，任务用例手动推进 worker，避免其他测试上下文领取同一个队列的任务。
- 后台重启隔离 Java 18087，启动时通过 Flyway V15 校验。Web 15175 的任务列表返回 200，已有任务重启后仍为 `SUCCEEDED`、尝试次数 2，保存的摘要保留 1000–2400 ms 引用，未知任务返回 404 `TASK_NOT_FOUND`。本轮使用已有合成任务读回，未调用真实模型。[实验 33](../learning/LABS.md#实验-33--哪些查询由-mybatis-plus-生成)待用户执行。

## 2026-11-23 · 持久化迁移与摘要原子提交

- 资料、B 站字幕来源、收藏、两种笔记和研究历史迁移到 MyBatis。业务 Java 的 JdbcClient 和内联 SQL 清零。常规访问用 BaseMapper，收藏原子更新、JSONB 和检查点条件更新等由 Mapper XML 保证。Flyway 仍为 V15。
- 模型调用结束后才开启摘要发布事务。先插入摘要，再检查资料版本、任务尝试与租约并标记成功。第二步失效会回滚新摘要。租约改为从 `CITEWEAVE_TASK_LEASE_SECONDS` 注入，默认 60 秒。
- Maven 在隔离 PostgreSQL 下关闭后台调度并通过 85 项测试。首次测试注入方式与 MyBatis 代理冲突，改用 Repository 的真实对象故障注入。一条本轮中断残留的合成排队任务已在隔离库标记失败，避免下次执行器先领到它。
- 后台重启 Java 18087，通过 Web 15175 的任务列表确认代理与数据库可用。合成字幕 429 对照中，首次任务失败且摘要返回 204，重试沿用同一个 UUID，第二次成功并读回 1000–2400 ms 引用。独立集成测试在摘要插入后注入失败，确认事务回滚。真实模型和用户个人体验均待验收。[实验 34](../learning/LABS.md#实验-34--任务失败时能否读到半成品摘要)待用户执行。

## 2026-11-23 · 本地 MP4/MOV 预检

- 正式「导入资料 → 本地视频」通过 Java 调用 FFprobe，显示视频时长、编码尺寸和首条音轨。子进程使用固定参数、本地协议、受支持容器及服务端生成的临时路径，限制并发、运行时间和输出。上传限额沿用 multipart。此路由不新增资料、任务或迁移。
- 隔离 PostgreSQL 下设置 `CITEWEAVE_ISOLATED_PG_TEST=1`、`CITEWEAVE_LOCAL_VIDEO_TEST=1` 后运行 `mvn.cmd -o -B -f server/pom.xml '-Dciteweave.tasks.worker-enabled=false' package`，88 项全部通过。真实 FFmpeg/FFprobe 9.0.2 额外覆盖合成 MP4、纯音频、伪装文件和 MOV 时长上限。Web 构建及看板同步检查通过。
- 后台重启隔离 Java 18087，Web 15175 代理返回 200。真文件返回 1000 ms、160×90、MPEG-4/AAC，改名文本返回 422 `VIDEO_INVALID`。Playwright 在 1366 px 检查错误，切到 390 px 更换文件并成功，检查无横向溢出、资料和任务列表条数前后相同。截图 `.tool-cache/output/local-video-preview-mobile.png`。浏览器另记录既有 favicon 404。
- 本轮开发验收使用合成样本与真实解析器。持久化、ASR 和共享部署下的进程隔离留待后续，用户个人验收请执行[实验 35](../learning/LABS.md#实验-35--改后缀能否通过视频检查)。

## 2026-11-23 · 桌面 Agent 纳入计划，本地视频保存

- [正式计划](IMPLEMENTATION_PLAN.md#桌面-agent-与外部交付)单列目录授权、通用工具、设备恢复和邮箱连接器，成品用批量文档处理、视频报告保存和真实邮件发送联合验收。
- Web 增加确认保存和最近视频记录。Java 在同卷发布 SHA-256 寻址对象后登记 V16 元数据。并发及换名上传同字节文件均复用原记录。现阶段页面只展示媒体记录，字幕绑定和 ASR 待接。
- 在新建测试 PostgreSQL 库启用 `CITEWEAVE_LOCAL_VIDEO_TEST=1` 和 `CITEWEAVE_VIDEO_STORAGE_TEST=1` 后执行 `mvn.cmd -o -B -f server/pom.xml '-Dciteweave.tasks.worker-enabled=false' package`。通过 73 项，16 项依赖此前的隔离库而未启用。并发获一条 201 和一条 200，只有一个记录和对象；损坏文件返回 422。Web 构建及 3 项看板测试通过。
- 独立 Web 15185 和 Java 18097 启动，V16 只在新建验收库应用。Playwright 在 390 px 验证预检零记录、首次保存 201、换名重传 200、刷新仅一条。后台重启 Java 后，经 Web 代理读回原 UUID，且磁盘对象 SHA-256 和 12,509 字节与记录一致。截图位于 `.tool-cache/output/local-video-storage-mobile.png`。用户验收见[实验 36](../learning/LABS.md#实验-36--同一个视频换名字能否再创建一条记录)。

## 2026-11-23 · 本地视频挂载用户字幕

- Web 在「最近保存的视频」增加管理字幕。V17 把字幕正文和对应视频关系在同一事务提交，行锁串行化同一视频的查重和写入。换名上传同版本读回原轨道，内容变化保留为独立轨道。越过视频时长返回 422，原轨道仍可读回。
- 隔离新库执行 `mvn.cmd -o -B -f server/pom.xml '-Dciteweave.tasks.worker-enabled=false' package`，74 项通过，16 项旧隔离库用例未启用。补充 VTT 在视频结束时间边界通过后，复跑 `LocalVideoStorageIntegrationTest` 两项均通过。Web 构建及 3 项看板测试通过。
- Web 15186、Java 18098 在新建环境运行 V17。Playwright 在 390 px 验证过长字幕 422、有效字幕 201，从来源面板读回关联，使用合成模型生成摘要，刷新后点击引用定位 100–900 ms。背景重启 Java 后再次核对同一视频 ID 和已保存引用。截图 `.tool-cache/output/local-video-track-citation-mobile.png`。真实模型与自动转写尚待验收，用户可做[实验 37](../learning/LABS.md#实验-37--字幕时间超出本地视频会怎样)。

## 2026-11-23 · 本地中文 CPU 转写实测

- 新增可选 `asr` 依赖组和本地转写命令。FFprobe 检查容器，FFmpeg 抽取第一条音轨，faster-whisper CPU INT8 输出 VTT 和测量报告。首次实验暴露了 PyAV 文件读取参数差异，改为让模型直接接收 FFmpeg 已解码的 PCM 数组。
- PaddleSpeech 官方演示语音配合合成画面，4.996 s 音轨的两轮推理分别为 3.987 s 和 3.716 s。去掉音轨后退出码 1，未生成产物。使用 `uv sync --directory ai --locked --no-default-groups --group asr` 建立独立 Python 3.12 环境后，离线复跑读回相同字幕与时间区间。[测量口径及局限](../quality/BENCHMARK.md#2026-11-23--中文-cpu-转写单样本)。
- 经隔离 Web 15186 实测导入视频 201、上传真实机器生成的 VTT 201，资料 2 时间轴保留 0–4520 ms。当前通过用户字幕入口手动绑定，自动转写任务和机器轨道标记尚未接入。本轮无服务端代码变更，现有服务无需重启。
- `python -m pytest -q -p no:cacheprovider` 在现有 AI 环境执行 54 项全部通过，关闭第三方插件自动加载。Web 构建及 3 项看板测试通过。用户亲手操作见[实验 38](../learning/LABS.md#实验-38--生成-vtt-需要哪些输入)。

## 2026-11-23 · 本地视频后台 CPU 转写

- V18 增加按视频唯一的转写任务和机器轨道来源。Java 独立调度器领取任务，在短事务外启动经过限制的离线 Python 命令。机器字幕和任务成功一起提交。失效租约无法发布，失败可显式重试。
- Java 在新建隔离库执行 `mvn.cmd -o -B -f server/pom.xml '-Dciteweave.tasks.worker-enabled=false' package`，75 项通过，16 项旧库用例未启用。故障对照把租约置为过期，字幕发布回滚，重试第 2 次成功并只保存一条机器轨道。Python 54 项及 Web 构建与 3 项看板测试通过。
- Web 15187、Java 18099、V18 运行在新建验收环境。Playwright 在 390 px 用官方中文样本从页面提交，实测本机 CPU 转写，重复请求沿用同一任务且只执行一次。刷新后从视频打开机器字幕，显示 0–4520 ms。API 另验无音轨返回 422 且没有任务，后台重启 Java 后身份、来源和时间轴一致。截图 `.tool-cache/output/local-video-asr-mobile.png`。
- 转写部署默认关闭，本轮隔离环境已配置。当前每个视频保留一个中文转写任务，长视频质量和性能、任务自动重试、不同模型版本的重新转写仍待后续。用户验收见[实验 39](../learning/LABS.md#实验-39--后台转写重复提交会不会多写字幕)。

## 2026-11-23 · PostgreSQL 版本化词面索引

- V19 把所选资料的证据和词项按资料版本、索引版本保存。版本与片段在同一事务发布。查证据、混合问答与研究工具共用该入口，保持原分词、每份两条、候选排序和原文引用格式。环境开关默认关闭，本轮独立验证环境已启用。
- 独立 PostgreSQL 17.12 上执行 `mvn.cmd -o -B -f server/pom.xml '-Dciteweave.tasks.worker-enabled=false' package`，77 项通过，16 项旧库用例未启用。42 道冻结题候选内容与顺序全部一致。并发首次搜索只生成一代，故障事务回滚后旧代可读，源内容变化只读新版本。Web 构建及 3 项看板测试通过。
- Playwright 在 390 px 的 Web 15188 验证无命中、段落和字幕混合命中、刷新后定位到 1000–2400 ms。后台重启 Java 18100 后，两份资料仍返回相同两条证据，数据库保持两代三片段。截图 `.tool-cache/output/lexical-index-mobile.png`。V19 仅应用新建隔离库；本轮未接向量索引或测吞吐。用户可执行[实验 40](../learning/LABS.md#实验-40--同一份资料为什么只建一个词面索引)。

## 2026-11-23 · 中文 CPU 向量检索对照

- Java 复用生产切片和词面结果导出 42 道原题及 12 道保留题。Python 用固定 Qdrant ONNX 构建的 BGE-small-zh-v1.5 生成 512 维向量，逐题比较原词面、精确余弦和 RRF。锁定的可选依赖组在独立环境离线运行。
- Top 5 向量将原题完整命中由 23/30 提到 29/30，保留题由 3/8 提到 7/8。Top 2 向量分别为 28/30、6/8。向量与 RRF 都为 16/16 无答案题提供候选，得分与可答题重叠，尚需证据充分性判断。逐题 JSON、模型指纹及耗时见[评测记录](../quality/BENCHMARK.md#2026-11-23--中文向量与词面对照)。
- `mvn.cmd -o -B -f server/pom.xml '-Dtest=TranscriptRetrievalBenchmarkTest' test` 两项通过。Python 57 项通过。标准测试库未提供 pgvector，应用没有切换检索方式，本轮无服务端进程需要重启。用户验收见[实验 41](../learning/LABS.md#实验-41--同义改写换成向量以后查到了什么)。

## 2026-11-23 · 独立 pgvector 精确检索验收

- 用单独的 Compose 文件启动回环 15436、PostgreSQL 17.12 与 pgvector 0.8.7。沿用固定 BGE 的 512 维向量。临时表只用于隔离实验，正式应用库与既有卷保留。
- `uv run --directory ai --locked --no-default-groups --group vector-lab python -m citeweave_ai.evaluate_pgvector` 验证两套题 Top 5 排序 54/54 一致。工作区、资料、原文版本、模型版本四项范围对照通过，错维由 SQLSTATE `22000` 拒绝。 [逐题报告](../quality/results/pgvector-parity-v1.json) · [实验 42](../learning/LABS.md)
- 正式检索未接入 pgvector，无需重启 Java 和 Python。本阶段保留可复现的独立数据库配置、命令和评测记录。
