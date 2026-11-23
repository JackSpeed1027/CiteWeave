# CiteWeave 架构小实验

**使用方法**：在仓库根目录打开 PowerShell 7，先运行下面两行。每次选一个实验，将该节表格复制到[个人学习记录](LEARNING_LOG.md)。先预测，再执行，把真实输出填进空格。逐项符合预期才记「通过」；有差异记下第一处不同及排查结果；环境或上游不可用记「阻塞」。可选对照单独记录。

```powershell
# 显示 PowerShell 主版本，下面的上传与错误响应示例需要 7 或以上。
$PSVersionTable.PSVersion.Major
# 检查仓库相对路径，预计为 True。若为 False，先切换到 CiteWeave 根目录。
Test-Path -LiteralPath 'sample-data/transcript-evidence.json'
```

先按[本机开发环境](../development/DEVELOPMENT.md)启动各实验所需的服务。每次只改变一个输入或进程，保留前后两次输出。下面的「预计效果」是根据当前代码得出的预期，实际结果由你亲自执行后记录。开发者的接口验证与你的实验记录分别保留。

| 实验 | 预计用时 | 要观察的关键行为 | 数据影响 |
| --- | --- | --- | --- |
| 01 字幕导入 | 15–20 分钟 | 合法与非法字幕的 HTTP 状态、毫秒片段与记录数量 | 主实验创建一条明确命名的样例 |
| 02 应用边界 | 15–25 分钟 | 正常请求与停止独立进程后的变化 | 查询请求为只读，仅停止自己启动的实验进程 |
| 03 异常契约 | 15–20 分钟 | 区分路径、请求格式、业务校验和资源不存在 | 使用只读请求及预期被拒绝的写请求 |
| 04 视频输入与字幕证据 | 15–20 分钟 | 链接识别、分 P 提示、引用校验 | 不访问平台，不写入资料 |
| 05 上游 Adapter | 20–25 分钟 | Bilibili 分 P 与模型限流、超时在契约中的差异 | Java 只读取资料并查询公开元数据，Python 全程离线 |
| 06 看板一致性 | 15–20 分钟 | 共用数据与过期检测 | 只读文件，状态变化在内存 |
| 07 字幕摘要引用 | 20 分钟 | 证据回跳、读回、拒绝裁剪后引用 | 仅向独立验收库新增标注测试字幕 |
| 08 双分 P 引用 | 20–25 分钟 | 验证时间相同也无法跨集引用 | 只向隔离库写两条合成资料 |
| 09 笔记幂等保存 | 15–20 分钟 | 并发提交只保存一条笔记 | 只在独立验收库写入合成样例 |
| 10 字幕问答召回 | 15–20 分钟 | 命中、跳过模型、拒绝未召回证据 | 合成数据，仅测试内存 |
| 11 检索评测对照 | 15–20 分钟 | 原词与同义表达的 Top 5 差异 | 只写构建目录 |
| 12 同义术语补召回 | 15–20 分钟 | 对照召回收益和无关候选 | 只写构建目录 |
| 13 引用与拒答评测 | 15–20 分钟 | 有引用但无依据的答案如何被记录 | 只写本地评测输出 |
| 14 资料收藏状态 | 15–20 分钟 | 并发保存、取消及原文读回 | 独立库新增合成资料 |
| 15 Markdown 笔记导出 | 15 分钟 | 读取 UTF-8 原句及定位字段 | 只写新的本机 Markdown |
| 16 跨字幕引用 | 20 分钟 | 同时间区间定位到各自原句、拒绝越界引用 | Java 测试仅使用合成内存样例 |
| 17 文档段落引用 | 15–20 分钟 | 核对段号、原文和版本化引用 | 仅测试内存样例 |
| 18 PDF 页码 | 15–20 分钟 | 空白页的原页码与历史兼容 | 仅独立库和构建目录写入 |

执行后可将「输入 / 操作 → HTTP 状态与错误码 → 关键响应字段 → 你对原因的判断」记录到[学习记录](LEARNING_LOG.md)。遇到不同的结果，先保存原始输出，再排查环境和代码。

开始实验 01 和 03 前，可先用同一个只读请求确认 Java 接口版本。`curl.exe -sS http://127.0.0.1:8087/api/sources/not-a-number` 预计返回状态字段 `400` 和错误码 `REQUEST_INVALID`。若只有状态字段，先从当前代码启动自己的 Java 实验实例，再继续；已有服务归属不明时使用独立回环端口，并同步替换示例中的 URL。

## 实验 01 · 字幕导入的失败边界

### 目的与准备

对同一上传接口使用合法与倒序的时间轴。通过响应、结果查询和记录数量三个观察点，验证字幕的毫秒定位、成功写入以及失败请求的边界。需要启动 Java 8087、PostgreSQL，并在仓库根目录使用 **PowerShell 7**。

主实验会写入一条标题以「实验 01」开头的样例，并在系统临时目录创建一个独立测试字幕。实验条目保留供走读，不影响已有记录。观察数量变化期间，暂停其他导入操作。

### 步骤 1 · 导入合法字幕

先记录列表数量。上传仓库内的 SRT 样例，保存响应状态、`Location` 和资料 ID。随后分别读取详情和时间轴，对照原始文件里的时间表达。

```powershell
$api = 'http://127.0.0.1:8087/api/sources'
# 读取资料列表并把当前总数记作 Before，用来判断后面的上传是否写入。
$before = (Invoke-RestMethod $api).Count
# 用当前时间给实验资料命名，便于找到这一次的样例。
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
# POST 表示提交。-Form 把下面的标题和本地文件一起上传；-SkipHttpErrorCheck 让我们能读取失败响应。
$valid = Invoke-WebRequest $api -Method Post -Form @{
  title = "实验 01 · 合法字幕 · $stamp"
  file = Get-Item -LiteralPath 'sample-data/research-captions.srt'
} -SkipHttpErrorCheck
[pscustomobject]@{ Status = $valid.StatusCode; Location = $valid.Headers.Location }
if ($valid.StatusCode -ne 201) { throw "合法字幕导入失败：$($valid.Content)" }

# 把响应正文转成对象，取出新资料的 id，用它查询刚才上传的同一份资料。
$id = ($valid.Content | ConvertFrom-Json).source.id
$detail = Invoke-RestMethod "$api/$id"
# 打印资料类型和原文；随后用表格显示字幕序号及起止毫秒，抄入本节记录表。
$detail.source | Format-List id,title,kind
$detail.content
(Invoke-RestMethod "$api/$id/transcript") | Format-Table sequence,startMs,endMs,text
$afterValid = (Invoke-RestMethod $api).Count
```

**预计效果**。返回 `201 Created`，`Location` 指向刚创建的资源。详情的 `kind` 是 `SRT`，正文仍包含原始时间码。时间轴期望返回两条。

| sequence | startMs | endMs |
| ---: | ---: | ---: |
| 1 | 1000 | 4000 |
| 2 | 4500 | 8000 |

在无其他写入的前提下，列表总量增加一条。用 `$id` 查询可以直接确认本次创建的资料。

### 步骤 2 · 导入倒序字幕

保持接口和文件格式相同，只把第一个片段的结束时间改为早于开始时间，并保留样例中的第二个合法片段。这次重点是看混合了合法与非法片段的一次上传会留下什么。

```powershell
# 在系统临时目录生成唯一文件名，用作本次错误样例。
$bad = Join-Path ([IO.Path]::GetTempPath()) ("citeweave-" + [guid]::NewGuid() + ".srt")
# 读取原样例，在内存中改一个结束时间，再把结果写到新的临时文件。
$sample = Get-Content -Raw -Encoding utf8 -LiteralPath 'sample-data/research-captions.srt'
$invalidSample = $sample.Replace('00:00:01,000 --> 00:00:04,000', '00:00:01,000 --> 00:00:00,500')
Set-Content -LiteralPath $bad -Encoding utf8 -Value $invalidSample
# 提交临时字幕，保留 422 的响应体，下面读取 code 并再次统计资料数。
$rejected = Invoke-WebRequest $api -Method Post -Form @{
  title = "实验 01 · 倒序字幕 · $stamp"
  file = Get-Item -LiteralPath $bad
} -SkipHttpErrorCheck
$problem = $rejected.Content | ConvertFrom-Json
[pscustomobject]@{ Status = $rejected.StatusCode; Code = $problem.code; Detail = $problem.detail }
$afterInvalid = (Invoke-RestMethod $api).Count
[pscustomobject]@{ Before = $before; AfterValid = $afterValid; AfterInvalid = $afterInvalid }
```

**预计效果**。返回 `422 SOURCE_TRANSCRIPT_INVALID`，说明第一个字幕片段的结束时间必须晚于开始时间。`AfterInvalid` 等于 `AfterValid`。例如开始有 5 条，三次计数预计为 `5 → 6 → 6`。详情仍能读回上一步创建的合法样例。

### 解释、排查与进阶

- 分别看原文与时间轴，可以观察「保留用户提供的输入」和「按 API 契约派生毫秒字段」两件事。对照 `SourceService.upload`、`SourceParser.file`、`TranscriptParser.parse` 及 `SourceRepository.insert`，标出校验与写入的顺序。
- 若合法上传失败，先记录响应体，核对 8087 和文件路径。若只看到 `422` 没有错误码，确认调用的是含 `SourceErrorHandler` 的新版 Java。若计数不符，用实验标题和 `$id` 区分其他导入操作。
- 可选对照。新建一份只含一个片段的 SRT，使用相同的开始和结束时间，观察它与倒序片段的失败是否一致。若将结束时间改为多 1 毫秒再上传，预计变为 `201` 且新增一条样例。
- 思考题。第二个片段明明合法，为什么失败请求仍没有留下半条资料？仅凭三次计数能否说明任何并发场景都安全？根据代码和你的输出，用自己的话作答。

| 实验 01 验收项 | 通过条件 | 我的实测值 / 结论 |
| --- | --- | --- |
| 合法上传和读回 | 201；Location 指向返回的 ID；类型 SRT，原时间码仍在 | |
| 片段定位 | 恰有两段，起止毫秒分别为 1000–4000、4500–8000 | |
| 倒序输入与入库边界 | 422 SOURCE_TRANSCRIPT_INVALID；三次计数为 n、n+1、n+1 | |

## 实验 02 · 通过四个端口理解应用边界

### 目的与准备

区分静态页面已打开、Vite 到 Java 的转发可用、数据库可读和 Python 的 HTTP 进程可响应。需要 Java、PostgreSQL、正式 Vite 前端及 Python。先在仓库根目录执行，正常阶段只做读取。停止服务时仅操作你为实验亲自启动并确认归属的终端。

### 步骤 1 · 建立正常情况下的基线

先查看数据库，然后发送四个请求。使用 `-o NUL` 的请求会舍弃内容，只输出状态码，避免把资料正文或列表内容打印到日志。

```powershell
# 只查看 Compose 服务状态，在 STATUS 中找 healthy。
docker compose -f infra/compose.yaml ps
# 下面依次检查页面、经代理的资料接口、直连 Java、Python 健康接口。--max-time 4 最多等四秒，-sS 仅显示错误，-w 打印状态码。
curl.exe --max-time 4 -sS -o NUL -w "Vite page %{http_code}\n" http://127.0.0.1:5173/
curl.exe --max-time 4 -sS -o NUL -w "Vite API %{http_code}\n" http://127.0.0.1:5173/api/sources
curl.exe --max-time 4 -sS -o NUL -w "Java API %{http_code}\n" http://127.0.0.1:8087/api/sources
curl.exe --max-time 4 -sS -w "\nPython health %{http_code}\n" http://127.0.0.1:8091/healthz
```

**预计效果**。PostgreSQL 容器显示 `healthy`。四个 HTTP 请求为 `200`，Python 返回 `{"service":"citeweave-ai","status":"ok"}`。Vite 首页成功只说明页面入口正常，Vite 资料接口成功才涵盖从代理到 Java 资料读取的链路。

### 步骤 2 · 停止自己启动的临时 AI Engine

| 实验 02 基线 | 通过条件 | 我的实测值 / 结论 |
| --- | --- | --- |
| 数据库和资料读取链路 | healthy；首页、代理接口、直连 Java 依次为 200、200、200 | |
| 主 Python 进程 | HTTP 200，service 为 citeweave-ai，status 为 ok | |

另外开启一个终端，从仓库根目录启动临时 Python 实例。若 8092 被占用，选择一个空闲的回环端口，并在下方探测命令中同步替换。

```powershell
# 在新开的终端从项目根目录进入 Python 模块。
Set-Location ai
# 按已有锁文件启动仅监听本机 8092 的临时进程；等出现启动成功提示，让此终端保持运行。
uv run --locked uvicorn citeweave_ai.app:app --app-dir src --host 127.0.0.1 --port 8092
```

另开终端运行这组检查，记录结果。再回临时 Python 终端按 Ctrl+C，重复同一组检查。

```powershell
# 在另一个终端分别探测 8092、5173、8091；只停止自己的 8092 后，再运行这三行。
curl.exe --max-time 4 -sS -o NUL -w "Temporary AI %{http_code}\n" http://127.0.0.1:8092/healthz
curl.exe --max-time 4 -sS -o NUL -w "Vite API %{http_code}\n" http://127.0.0.1:5173/api/sources
curl.exe --max-time 4 -sS -o NUL -w "Primary AI %{http_code}\n" http://127.0.0.1:8091/healthz
```

| 观察点 | 预计临时进程运行时 | 预计停止后 | 实际前后结果 |
| --- | --- | --- | --- |
| 8092 健康检查 | 200 | 无法连接，curl 通常显示 `000` | 待记录 |
| 5173 的资料 API | 200 | 200 | 待记录 |
| 8091 健康检查 | 200 | 200 | 待记录 |

`000` 是 curl 未收到 HTTP 响应时的显示值。它与应用返回 `500` 或 `503` 的含义不同。

### 步骤 3 · 可选对照，断开正在被调用的 Java

此步骤只在 **8087 是你亲自为本实验启动的 Java，且当前没有其他使用者** 时执行。让 Vite、数据库和 Python 保持运行。在那个 Java 终端按 Ctrl+C，然后重跑步骤 1。观察 Vite 首页和资料 API 的区别。最后用原启动命令恢复 Java，并确认两条资料 API 均返回 `200`。8087 由其他人或不明进程运行时跳过该对照。

**预计效果**。Vite 首页仍可返回 `200`，Vite 的 `/api/sources` 失去后端后返回非 2xx 或报告代理错误，直连 8087 无法连接。Python 健康和 PostgreSQL 状态可保持原样。代理错误的具体形式取决于本地 Vite 的运行状态。

### 解释与排查

- 从 `web/vite.config.ts` 找到代理目标，再从 `SourceController` 追到 `SourceRepository`。对照 Python 的 `app.py`，把 HTTP 健康检查与资料请求各自的调用路线写出来。
- 临时 Python 停止后主链路能读取，只证明 8092 无须作为当前资料链路的依赖。判断 8091 是否被业务调用仍须结合代码；健康接口也无法说明模型已经接通。
- 首页是 `200` 而 Java 直连失败时，检查 8087 是否启动。Java 已运行但资料读取失败时，核对 PostgreSQL health、数据源配置和 Java 启动日志。Java 直连可读但 Vite 代理失败时，检查 Vite 的端口与代理目标。
- 思考题。根据两次对照，举例解释「进程可响应」「依赖可用」和「完整业务可用」的区别。补答[环境走读题](KNOWLEDGE_CHECK.md#正式开发环境)。

## 实验 03 · 从业务异常追踪到 HTTP 响应

### 目的与准备

发送四个分别触发路径转换、查询缺失资料、JSON 读取和业务校验的请求。用 HTTP 状态初步分类，再用稳定的 `code` 区分可采取的动作。需要含 `SourceErrorHandler` 的 Java 服务。下列命令使用 **PowerShell 7**。四个样例均预期被拒绝，数据库中无需创建测试资料。

### 操作

```powershell
$api = 'http://127.0.0.1:8087/api/sources'
# 把四个输入组成列表：每项含名称、请求方法、地址，有正文的请求另带 Body。
$cases = @(
  @{ Name = '路径 ID 格式有误'; Method = 'Get'; Url = "$api/not-a-number" }
  @{ Name = '查询不存在的资料'; Method = 'Get'; Url = "$api/9223372036854775807" }
  @{ Name = 'JSON 语法损坏'; Method = 'Post'; Url = $api; Body = '{' }
  @{ Name = 'JSON 合法但正文为空'; Method = 'Post'; Url = $api; Body = '{"title":"lab-03","content":""}' }
)
# 逐项发送。@request 把参数表交给命令；SkipHttpErrorCheck 保留预期的 4xx 结果。
foreach ($case in $cases) {
  $request = @{ Uri = $case.Url; Method = $case.Method; SkipHttpErrorCheck = $true }
  if ($case.ContainsKey('Body')) {
    $request.ContentType = 'application/json'
    $request.Body = $case.Body
  }
  $response = Invoke-WebRequest @request
  # 将错误正文转成对象；下方每行输出一个用例的状态、媒体类型、错误码和提示。
  $problem = $response.Content | ConvertFrom-Json
  [pscustomobject]@{
    Case = $case.Name
    Status = $response.StatusCode
    ContentType = $response.Headers['Content-Type'] -join ', '
    Code = $problem.code
    Detail = $problem.detail
  }
}
```

### 预计效果

四个响应的媒体类型预计为 `application/problem+json`。若响应带字符编码后缀，媒体类型主体相同即可。

| 请求 | 预计状态 | 预计错误码 | 该请求要观察什么 | 实测状态 / code / 媒体类型 |
| --- | --- | --- | --- | --- |
| 路径 ID 格式有误 | 400 | `REQUEST_INVALID` | `detail` 提示参数格式 | |
| 查询不存在的资料 | 404 | `SOURCE_NOT_FOUND` | 合法数字也可能找不到业务资源 | |
| JSON 语法损坏 | 400 | `REQUEST_INVALID` | 与非法路径共用稳定错误码，提示文本不同 | |
| JSON 合法但正文为空 | 422 | `SOURCE_CONTENT_INVALID` | 能读取请求体，业务校验拒绝空正文 | |

### 解释、排查与进阶

- `status` 描述失败类别，`code` 可用于程序分支，`detail` 给用户解释。对照[接口规范](../development/API_CONTRACT.md)和实际响应，比较两个 `400` 在程序处理与用户提示上的共同点和区别。
- 走读 `SourceErrorHandler`、`SourceService.find`、`SourceService.paste` 和 `SourceParser.text`。先在纸上标出每个样例可能到达的最深一层，再用响应或测试复核。
- 如果返回 HTML 或正文无法解析为 JSON，先核对地址是否为 Java 8087；若 `code` 为空，确认本机正在运行新版 Java。若极大 ID 在你的环境恰好存在，改用已有列表中确定不存在的合法数字。
- 可选对照。如果列表里已有 TEXT 资料，对它的 ID 调用 `GET /api/sources/{id}/transcript`。预计仍为 `404`，错误码改为 `SOURCE_TRANSCRIPT_NOT_FOUND`。用这个对照解释为何单独依赖状态码有时不足。
- 思考题。哪一条由于 HTTP 绑定失败而没有调用业务方法？哪些失败才适合保留原始异常作为服务端诊断信息？前端应如何把可读提示与重试决策分开？

## 实验 04 · 视频输入与字幕证据

### 目的与准备

区分 Java 的链接识别、后续平台元数据查询，以及 Python 的证据校验。启动含 `BilibiliInputController` 的新版 Java，在 PowerShell 7 中操作。Python 只需安装锁定依赖，可通过测试验证，运行中的 AI Engine 无须参与。

### 步骤 1 · 仅改变链接输入

```powershell
$url = 'http://127.0.0.1:8087/api/video-imports/bilibili/normalize'
# 列出四个输入：合法分 P、非法分 P、短链、域名不匹配。
$inputs = @(
  'https://m.bilibili.com/video/BV1xx411c7mD/?p=2&spm_id_from=example'
  'https://m.bilibili.com/video/BV1xx411c7mD/?p=0'
  'https://b23.tv/Example1'
  'https://example.invalid/video/BV1xx411c7mD'
)
# 依次把输入转为紧凑 JSON，POST 到本机规范化接口，并打印每次响应以填写记录表。
foreach ($inputValue in $inputs) {
  $body = @{ input = $inputValue } | ConvertTo-Json -Compress
  $result = Invoke-WebRequest $url -Method Post -ContentType 'application/json' -Body $body -SkipHttpErrorCheck
  [pscustomobject]@{ Input = $inputValue; Status = $result.StatusCode; Body = $result.Content }
}
```

**预计效果**。第一条返回 `200 BV`，规范地址中追踪参数已移除，`requestedPart` 为 2。第二条与第四条为 `400 BILIBILI_INPUT_INVALID`，提示原因不同。第三条返回 `200 SHORT`，仍为原短码。即使示例短码与实际视频无关，也能验证语法分类。若返回 404，查看 Java 是否运行最新构建。若用独立端口启动 Java，将命令中的 8087 对应更改。

### 步骤 2 · 对照合法与缺失的证据引用

从仓库根目录执行下列命令。测试会从 [字幕证据样例](../../sample-data/transcript-evidence.json) 创建输入数据。各测试仅修改内存中的副本。

```powershell
Set-Location 'D:\Work\Project\CiteWeave\ai'
# 按锁文件运行证据测试；-v 列出每项名称和 PASSED/FAILED，最后一行是汇总。
uv run --locked pytest tests/test_evidence.py -v
```

**预计效果**。七项测试通过。合法引用可以通过 `verify_draft`，未知 ID 会被拒绝，重复 ID 或时间区间不合法会在模型校验阶段失败。这里的测试通过表示程序找到了预期错误，并证明了约束起作用。

### 解释与进阶

- 读 Java 的 `BilibiliInputParser` 和 Python 的 `EvidenceBatch.verify_draft`。根据输出写出两个阶段各自信任哪些字段，以及它们能保证到哪里。
- 可选对照。把规范化请求的 `p=2` 改为 `p=999`。预计仍可通过格式校验。说明平台预览阶段还须补什么校验。
- 思考题。如果草稿引用的 ID 确实存在，但摘要把片段原意反过来了，为什么当前检查可能仍通过？还需要什么证据或验证步骤？

| 实验 04 验收项 | 通过条件 | 我的实测值 / 结论 |
| --- | --- | --- |
| 分 P 链接和短链 | 分别返回 200 BV、200 SHORT；第一条保留第 2 P，去掉追踪参数 | |
| 非法分 P 和其他域名 | 两条均为 400 BILIBILI_INPUT_INVALID，分别记录 detail | |
| 字幕证据 | 当前版本 7 passed，0 failed；能指出哪个用例阻止不存在的引用 | |

## 实验 05 · 把不稳定的上游响应映射为稳定契约

目标是理解 Adapter 隔离的两端。Java 将平台编号、分 P 协议和错误转换成浏览器可消费的行为。Python 将供应商 HTTP 协议转换成摘要工作流可消费的文本或稳定错误。这两个实验均不创建资料。

### A. 手动预览并改变分 P

启动含预览接口的新版 Java。在 PowerShell 7 中执行以下命令。默认 Java 端口为 8087。样例为公开视频编号，也可换成自己能匿名访问的 BV 视频。

```powershell
$sourceUrl = 'http://127.0.0.1:8087/api/sources'
$previewUrl = 'http://127.0.0.1:8087/api/video-imports/bilibili/preview'
# 保存当前列表用于最后比对；下一条向 Java 请求一次公开视频预览。
$before = (Invoke-WebRequest $sourceUrl).Content
$first = Invoke-WebRequest $previewUrl -Method Post -ContentType 'application/json' -SkipHttpErrorCheck -Body '{"input":"BV1xx411c7mD"}'
$first.StatusCode
# 上游失败时显示真实响应并结束这段脚本，避免把空分 P 列表误当成功结果。
if ($first.StatusCode -ne 200) { throw $first.Content }
$preview = $first.Content | ConvertFrom-Json
# 打印平台返回的分 P 列表与建议页，记录第一行 page 和 suggestedPart。
$preview.parts | Format-Table page, cid, title, durationSeconds
$preview.suggestedPart
# 取列表中的最大页码再加一，构造一定越界的分 P 请求。
$missingPage = ($preview.parts.page | Measure-Object -Maximum).Maximum + 1
$inputUrl = "https://www.bilibili.com/video/$($preview.bvid)?p=$missingPage"
$result = Invoke-WebRequest $previewUrl -Method Post -ContentType 'application/json' `
  -SkipHttpErrorCheck -Body (@{ input = $inputUrl } | ConvertTo-Json -Compress)
# 依次打印第二次请求的状态和错误正文；最后一行比较预览前后的资料列表。
$result.StatusCode
$result.Content
$before -eq (Invoke-WebRequest $sourceUrl).Content
```

预计第一次请求返回 200，`suggestedPart` 与第一行的 `page` 一致。第二次请求预计返回 422 与 `BILIBILI_PART_NOT_FOUND`。最后应为 `True`。两次请求之间请避免另行导入。若平台不可达或限频，先记录实际状态和错误码，再在仓库根运行 `mvn -f server/pom.xml "-Dtest=BilibiliPreviewHttpTest" test` 重现固定分 P 场景。如果收到 404 且响应缺少 `BILIBILI_VIDEO_NOT_FOUND`，检查运行中的 Java 是否包含新接口及端口是否正确。

### B. 对比供应商限流与超时

在仓库的 `ai` 目录运行以下命令。测试使用假的供应商地址、密钥和内存 HTTP 传输。

```powershell
# 只运行名称匹配 -k 表达式的用例；deselected 是未选中的测试，重点看 PASSED、FAILED 和汇总。
uv run --locked pytest -v tests/test_model_provider.py -k 'provider_errors or transport_errors or redirect'
```

预计 429 对应 `MODEL_RATE_LIMITED`，传输主动抛出读超时对应 `MODEL_TIMEOUT`，重定向只触及原地址并返回 `MODEL_UPSTREAM_ERROR`。在 IDE 中打开 [测试代码](../../ai/tests/test_model_provider.py)，给 429 响应夹具和 [供应商适配](../../ai/src/citeweave_ai/model_provider.py)的状态判断设断点，单独调试该用例。观察返回的响应属于哪一层，再对读超时用例重复一次，比较执行是否到达状态判断。出错先核对当前目录和锁定依赖，再核对所选用例的输出。

### 提交观察

记录两个 Java 请求的状态、响应字段，以及 Python 两个断点的命中差异。先用自己的话解释：如果前端直接消费平台返回的 `pages`，上游字段变化时哪些组件可能受影响？为什么 HTTP 429 与读超时需要分别处理？实际观察和解释待用户执行后记录。

| 实验 05 验收项 | 通过条件 | 我的实测值 / 结论 |
| --- | --- | --- |
| 在线预览 | 200，suggestedPart 等于第一行 page | |
| 在线越界与只读性 | 422 BILIBILI_PART_NOT_FOUND；列表比对为 True | |
| 离线供应商契约 | 当前筛选应有 11 passed，0 failed；429 和读超时分别映射到 MODEL_RATE_LIMITED、MODEL_TIMEOUT | |
| 在线阻塞时的替代验证 | 单独记录 BilibiliPreviewHttpTest 的测试汇总，在线项保留「阻塞」 | |

## 实验 06 · 看板如何发现两处进度不一致

**问题**　同一份里程碑数据在 Web 已改变时，怎样发现仓库 README 仍展示旧状态？

**预计用时**　15–20 分钟。需要包含项目看板的版本、Node.js 22.12+ 或 24、PowerShell 7。工作目录为 `D:\Work\Project\CiteWeave\web`。浏览器打开正式工作台 `http://127.0.0.1:5173/`，由 Vite 提供页面。命令本身只读文件；浏览器看板可在资料 API 无法连接时显示。样例为 [milestones.json](../../web/src/progress/milestones.json)的「字幕证据与可引用摘要」。对照只改变内存副本，零入库、零文件写入。

```powershell
# 切换到脚本所在的 Web 模块。后续两条命令均从此处执行。
Set-Location 'D:\Work\Project\CiteWeave\web'

# 正常组，读取共享数据及根 README，核对生成区域。预期提示一致、退出码为 0。
npm.cmd run progress:check
$LASTEXITCODE

# 对照组，测试只把内存副本中的摘要阶段从 fixture 改为 integration。
# 看这条测试通过。它断言旧 README 已过期、生成后出现「真实接口已联调」，且看板以外全文保持不变。
node --test --test-name-pattern '在内存中改变一个状态' scripts/progress.test.mjs
$LASTEXITCODE
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 正式首页与 README 查看摘要阶段 | | 两处都显示「夹具联调通过」以及真实模型待验收 | | | |
| 校验当前 README | | 输出「README 与 Web 里程碑一致」，退出码 0 | | | |
| 内存副本仅改变摘要阶段 | | 1 项测试通过，证明旧文档存在差异，重新生成后状态同步 | | | |

浏览器无看板时，先确认访问 5173 的正式首页而非原型入口，再检查 Vite 控制台是否出现编译错误。看板已显示但「最近导入」报错时，先查 8087 的资料 API；两处是独立的。命令报告进度不一致时，在 IDE 比对数据文件和 README 的生成区域，先确认哪条证据确实有更新。同步命令及验证顺序见[开发指南](../development/DEVELOPMENT.md#维护项目进度看板)。

**代码走读**　先从 [展示组件](../../web/src/progress/ProjectProgress.tsx)沿着 JSON 导入找到数据，再读 [生成器](../../web/scripts/progress.mjs)如何只改两条标记之间的内容。最后在 [测试](../../web/scripts/progress.test.mjs)定位修改内存副本与比较结果的两处断言。

**迁移问题**　以后工作台也要展示任务错误码，而接口文档要给出同一批错误码时，哪些字段适合作为共享数据，哪些内容应维护在各自的展示层？请把上表填好后贴到[学习记录](LEARNING_LOG.md)。开发者验证单独记入开发日志。


## 实验 07 · 为什么坏引用不能成为已保存摘要

**预计用时**　20 分钟。需要具备字幕摘要 V2 的独立验收环境、PowerShell 7 和仓库样例 `sample-data/research-captions.srt`，工作目录为仓库根。启动同一验收环境的 Web、Java、PostgreSQL 及 Python，Java 示例为 18087、Web 示例为 15173。正常路径用授权模型，或在明确标记的合成供应商夹具中执行。浏览器导入只写一条新测试资料，Python 对照在内存运行。

```powershell
# 从 Java 读取摘要；先在验收环境 Web 导入样例，
# 从资料详情 URL 的 /sources/ 后取得新资料 ID，复制到下方提示。
$id = Read-Host '输入本次实验新导入的字幕资料 ID'
$url = "http://127.0.0.1:18087/api/sources/$id/summary"
# 尚未生成时，预期 HTTP 204。
(Invoke-WebRequest $url).StatusCode

# 在 Web 中点击「生成摘要」，再点击第一条引用并刷新页面。
# 只读核对已保存的第一条证据，观察原句与毫秒区间。
$saved = Invoke-RestMethod $url
$saved.citations[0] | Select-Object evidenceId, startMs, endMs, text

# 失败对照使用两个确定性夹具：提示词一样，只把回复里的引用换成被裁剪的 ID。
# 退出码 0 且两条测试通过，表示有效引用可返回，被裁剪的引用被拒绝。
Push-Location ai
uv run --locked pytest -v tests/test_transcript_summary.py -k 'actual_prompt or trimmed_segment'
Pop-Location
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 导入、生成、点击引用并刷新 | | 生成前 204，生成后能读到同一 ID 和毫秒区间，焦点落在对应原句 | | | |
| 只替换模型回复里的引用 ID | | 成功夹具正常返回，坏引用夹具抛出 `SUMMARY_INVALID_CITATION` | | | |

看板能开但摘要接口 404 时，先检查 Java 是不是 V2 版本及端口。503 且提示模型未配置时，检查 Python 进程的三个模型环境参数。422 表示 Java/Python 已联通，但预算内没有可发的完整片段。没有获准模型时先做失败对照并把真实生成栏留空。

先读 [Python 校验](../../ai/src/citeweave_ai/transcript_summary.py)，再读 [Java 发布](../../server/src/main/java/dev/citeweave/source/TranscriptSummaryService.java)。迁移问题是：如果把超长字幕拆成多次模型请求，应如何证明最后引用的片段确实进入过其中一次请求？填表后将结果附到[学习记录](LEARNING_LOG.md)。开发者验证另见开发日志。


## 实验 08 · 两个分 P 时间一样，引用为什么不会串集

**预计 20–25 分钟。** 需要含 Flyway V3 的独立验收环境、PowerShell 7、Maven 和健康的独立 PostgreSQL 15434。工作目录为仓库根。测试夹具模拟同一视频两集的元数据与摘要输出，两份合成字幕使用相同的 1000–2400 ms 区间。重复运行会向独立验收库新增两条合成资料。

```powershell
# 先确认独立项目健康。应看到 citeweave-summary-qa-postgres-1，宿主端口 15434。
docker compose -p citeweave-summary-qa -f infra/compose.yaml ps

# 只启用该独立集成测试。它会保存两条字幕，先生成第一集摘要，
# 再让第二集错误地引用第一集并重试。观察 1 个测试通过。
$env:CITEWEAVE_ISOLATED_PG_TEST = '1'
try {
  mvn.cmd -o -B -f server/pom.xml "-Dtest=BilibiliSubtitleIntegrationTest" test
} finally {
  Remove-Item Env:CITEWEAVE_ISOLATED_PG_TEST -ErrorAction SilentlyContinue
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 给两集绑定不同字幕，相同时间 | | 201 各一条，CID、资料 ID 不同，第二集引用能读回第二集原句 | | | |
| 只把第二集的引用换成第一集的 ID | | 502 `SUMMARY_INVALID_CITATION`，读摘要仍返回 204；修正后成功 | | | |
| 选第二集却提交第一集的 CID | | 409 `BILIBILI_PART_CHANGED`，新增资料数为零 | | | |

失败时先检查 PostgreSQL 端口和项目名，再看 Maven 输出中 V3 迁移和 JUnit 断言。使用真实视频做集中验收时，从「导入资料 → Bilibili 字幕」预览获准的多 P 视频，分别上传对应 SRT/VTT，在资料详情核对链接参数、CID 与来源。真实模型可用后再分别生成摘要。

走读入口是 [提交前复核](../../server/src/main/java/dev/citeweave/video/bilibili/BilibiliSubtitleController.java)和 [双分 P 集成测试](../../server/src/test/java/dev/citeweave/video/bilibili/BilibiliSubtitleIntegrationTest.java)。迁移问题：如果第 2 P 后来更正了字幕，为什么旧摘要的引用还需要保留指向原轨道？把你的结果填入[学习记录](LEARNING_LOG.md)。


## 实验 09 · 并发点保存，为什么只有一条笔记

**预计 15–20 分钟。** 需要 V4、Maven、PowerShell 7 和已健康的独立 PostgreSQL 15434。工作目录为仓库根。测试生成一份合成 SRT、一条已发布摘要和一条笔记，全部写入指定验收库。模型使用确定性夹具。

```powershell
# 核对专用容器和 15434 端口，确认没有使用正式数据库。
docker compose -p citeweave-summary-qa -f infra/compose.yaml ps

# 只运行笔记集成测试。夹具先尝试无摘要保存，再生成摘要并同时提交 8 次保存。
# 应看到 1 个测试通过；具体 HTTP 状态和 UUID 一致性由测试代码断言。
$env:CITEWEAVE_ISOLATED_PG_TEST = '1'
try {
  mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptNoteIntegrationTest" test
} finally {
  Remove-Item Env:CITEWEAVE_ISOLATED_PG_TEST -ErrorAction SilentlyContinue
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 尚未生成摘要时保存 | | 409 `NOTE_SUMMARY_REQUIRED` | | | |
| 已有摘要时并发保存 8 次 | | 1 个 201、7 个 200，返回同一个笔记 UUID 和快照 | | | |
| 从工作台笔记列表重开并点引用 | | 刷新后正文仍在，跳到正确原句和 1000–2400 ms | | | |

第三行在集中验收的 V4 Web 与 Java 环境完成，入口为「资料详情 → 保存为笔记」和侧边栏「笔记」。如果测试被跳过，先检查环境变量是否为 1。连接失败先看 15434 与 Docker 项目，收到 409 时检查是否已有发布摘要。

代码走读先看 [并发测试](../../server/src/test/java/dev/citeweave/source/TranscriptNoteIntegrationTest.java)，再看 [数据库冲突处理](../../server/src/main/java/dev/citeweave/source/TranscriptNoteRepository.java)。迁移问题：如果改成「先查有没有笔记，再普通插入」，两个并发请求可能分别看到什么？把结果附到[学习记录](LEARNING_LOG.md)。


## 实验 10 · 哪些问题会调用模型

**预计 15–20 分钟。** 从仓库根目录用 PowerShell 7 运行。需要包含问答接口的版本、Maven 和 Java 21。Java 契约测试内置「缓存」与「数据库」两个合成字幕，不启动数据库、模型或平台服务。

```powershell
# 跑问答契约测试。JUnit 断言无关键词命中时没有调用模型，
# 有命中时只提交相关字幕，引用其他片段时返回 502。
mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptQuestionTest" test
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 问「海洋珊瑚」 | | 200、证据不足，模型调用为零 | | | |
| 问「缓存怎样失效」 | | 只提交缓存片段，答案引用从 1000 ms 开始 | | | |
| 保持问题，只将引用改为未召回的事务片段 | | 502 `QUESTION_INVALID_CITATION` | | | |

看不到对应测试时检查 Maven 从仓库根目录执行。正常运行后，在 IDE 打开 [Java 检索服务](../../server/src/main/java/dev/citeweave/source/TranscriptQuestionService.java) 和 [测试](../../server/src/test/java/dev/citeweave/source/TranscriptQuestionTest.java)，给调用 AI 的位置设置断点，对照前两种输入。集中验收时可以从字幕详情的「问这份字幕」复查证据不足和引用高亮。迁移问题：用户改用与原字幕完全不同的同义表达时，漏召回会发生在哪一层？下一版该用哪些题验证改善？把结果填在[学习记录](LEARNING_LOG.md)。


## 实验 11 · 只改提问措辞，为什么召回会变

**预计 15–20 分钟。** 需要包含 `transcript-v1` 评测入口的版本、PowerShell 7、Java 21 与 Maven。工作目录为仓库根。使用 [合成题集](../../sample-data/retrieval/transcript-v1/questions.json)和 [后端字幕](../../sample-data/retrieval/transcript-v1/backend.srt)。命令只读题集，输出写到 `server/target`。无需数据库、浏览器服务或模型。

```powershell
# 走生产字幕解析和 Java 排序，生成逐题 Top 5 与分类指标。
mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptRetrievalBenchmarkTest" test
if ($LASTEXITCODE -ne 0) { throw '检索评测运行失败' }

# 在同一份结果中提取原词和同义改写。两题共享资料与目标片段，只改问法。
$report = Get-Content -Raw -Encoding utf8 server/target/transcript-retrieval-v1.json | ConvertFrom-Json
$report.questions | Where-Object id -in 'P05-L','P05-P' |
  Format-Table id, source, top5, groupsFound, groupsRequired, recallAt5
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 原词「游标分页」 | | 目标第 12 段在 Top 5，Recall@5 为 1 | | | |
| 改成「翻页从哪里续接」 | | 目标仍是第 12 段，观察 Top 5 是否包含它，并解释区别 | | | |

报告未生成时先确认 Maven 执行位置和入口。输出不同可检查当前分支的题集、字幕文本和 Java 召回逻辑。用 IDE 打开 [评测入口](../../server/src/test/java/dev/citeweave/source/TranscriptRetrievalBenchmarkTest.java)与 [生产召回](../../server/src/main/java/dev/citeweave/source/TranscriptQuestionService.java)，核对分词和排序中哪一步导致差异。迁移问题：如果补一层同义词映射，怎样用新题确认它能推广到其他词，同时避免拉回更多无关片段？把预测和实测写入[学习记录](LEARNING_LOG.md)。


## 实验 12 · 同义术语补召回的收益与代价

**预计 15–20 分钟。** 需要 Java 21、Maven、PowerShell 7 和包含同义术语实验的版本。从仓库根目录运行。使用 [新增对照题](../../sample-data/retrieval/transcript-alias-holdout-v1/questions.json)及同目录的合成字幕。不连接数据库和模型，结果只写入构建目录。

```powershell
# 同一份字幕和问题运行两种策略；二者唯一差异是是否开启同义术语补召回。
mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptRetrievalBenchmarkTest" test
if ($LASTEXITCODE -ne 0) { throw '评测失败' }

# 对比一个有答案问题和一个含相同术语但没答案的问题。
$data = Get-Content -Raw -Encoding utf8 server/target/transcript-retrieval-alias-comparison.json | ConvertFrom-Json
foreach ($strategy in 'lexical','aliasFallback') {
  $strategy
  $data.holdout.$strategy.questions | Where-Object id -in 'H01','H09' |
    Format-Table id, top5, groupsFound, groupsRequired
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 用 H01 比较两种策略 | | 观察目标第 1 段是否被补召回 | | | |
| 用 H09 比较两种策略 | | 标注无答案，观察开启后是否出现相关但不能回答的片段 | | | |

如果命令失败，先核对 Java 与 Maven，再检查两份 `questions.json` 是否与对应字幕一起存在。走读 [问答检索](../../server/src/main/java/dev/citeweave/source/TranscriptQuestionService.java)的 `retrieveWithAliases`，确认它在什么条件下绕过补召回。迁移问题：用什么方法判断“找到了相关资料”与“资料能回答这个问题”的差别？把实验结果填到[学习记录](LEARNING_LOG.md)。


## 实验 13 · 合法的引用为何可能支撑不了答案

**预计 15–20 分钟。** 需要 PowerShell 7、Java 21、Maven、Python 和 uv，从仓库根目录运行。使用 [12 道合成题](../../sample-data/retrieval/transcript-alias-holdout-v1/questions.json)。不启动服务、不使用模型密钥，输出到构建目录和 Git 忽略路径。

```powershell
# Java 导出开启与关闭补召回的真实候选。后续用同一份候选清单。
mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptRetrievalBenchmarkTest" test
if ($LASTEXITCODE -ne 0) { throw '候选导出失败' }

# 在 ai 中运行离线夹具，每次用新路径保存报告。
$null = New-Item -ItemType Directory -Force .tool-cache
$filename = "refusal-lab-$(Get-Date -Format yyyyMMdd-HHmmss-fff).json"
Push-Location ai
try {
  $env:PYTHONPATH='src'
  uv run --locked python -m citeweave_ai.evaluate_refusal --output "../.tool-cache/$filename"
  if ($LASTEXITCODE -ne 0) { throw '评测失败' }
} finally { Pop-Location }

# 查看 H10 的两条路径。可展开 candidates、actualInputIds 和 citationIds 核对证据。
$r = Get-Content -Raw -Encoding utf8 ".tool-cache/$filename" | ConvertFrom-Json
$r.observations | Where-Object id -eq 'H10' | Format-List
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 查看 H10 的词面路径 | | 确认未召回时有没有调用模型 | | | |
| 同一道题，查看开启补召回的路径 | | 观察合法引用与错误结论能否同时出现，并指出原句缺了什么 | | | |

候选文件缺失时先检查 Maven 测试是否运行；输出路径已存在时使用新文件名。走读 [Python 评测入口](../../ai/src/citeweave_ai/evaluate_refusal.py)的 `observe` 与 [生产问答](../../ai/src/citeweave_ai/transcript_question.py)的引用校验。迁移问题：怎样把同一套区分延伸到 PDF 页码引用？请把实测填到[学习记录](LEARNING_LOG.md)。


## 实验 14 · 收藏状态改变后，原资料还在吗

**预计 15–20 分钟。** 需要 V5 版、Java 21、Maven、PowerShell 7 和健康的独立 PostgreSQL 15434。工作目录为仓库根。测试会新增一条标记为「收藏测试夹具」的合成正文和一条收藏状态记录。

```powershell
# 核对独立数据库的 Compose 项目和 15434 端口。
docker compose -p citeweave-summary-qa -f infra/compose.yaml ps

# 跑收藏专项测试。它会并发提交 8 次收藏，再重复取消两次，核对资料正文。
$env:CITEWEAVE_ISOLATED_PG_TEST = '1'
try {
  mvn.cmd -o -B -f server/pom.xml "-Dtest=SourceFavoriteIntegrationTest" test
} finally {
  Remove-Item Env:CITEWEAVE_ISOLATED_PG_TEST -ErrorAction SilentlyContinue
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 并发 8 次保存收藏 | | 8 个 200，库内只有一条状态记录 | | | |
| 将同一资料的目标状态改为取消 | | 两次均返回 false，收藏筛选不再显示，全部资料和原文仍在 | | | |
| 从 Web 收藏、筛选并刷新 | | 新状态读回成功，可从仅收藏打开原文 | | | |

专项测试被跳过时核对环境变量是否为 1。Web 集中验收在 V5 资料详情点「收藏资料」，到资料库打开「仅收藏」，刷新后再次从详情检查状态。若筛选为空，先查看 `GET /api/sources?favorite=true`，再检查关键词和类型筛选。

走读 [收藏 SQL](../../server/src/main/java/dev/citeweave/source/SourceRepository.java) 和 [并发测试](../../server/src/test/java/dev/citeweave/source/SourceFavoriteIntegrationTest.java)。迁移问题：如果改用「把现在状态反转」的接口，请求成功但响应丢失后，客户端重试会发生什么？将实测附到[学习记录](LEARNING_LOG.md)。


## 实验 15 · 导出的笔记能否离线追溯

**预计 15 分钟。** 需要含笔记导出接口的版本、PowerShell 7 与独立验收环境。从仓库根目录操作。启动指向独立库的 Java（示例 18087）与 Web（示例 15175），使用已有合成摘要笔记，脚本只读取接口并写一个本机 Markdown 文件。

```powershell
# 读取可见的笔记，选取独立库已有的合成摘要笔记。
$base = 'http://127.0.0.1:18087/api/notes'
$notes = Invoke-RestMethod $base
$note = $notes | Where-Object title -eq '摘要验证 · 成功' | Select-Object -First 1
if (!$note.id) { throw '独立库缺少示例笔记' }

# 导出到新文件。核对 200、文件名，并用 UTF-8 看原句与毫秒区间。
$null = New-Item -ItemType Directory -Force .tool-cache
$path = ".tool-cache/note-lab-$(Get-Date -Format yyyyMMdd-HHmmss-fff).md"
$r = Invoke-WebRequest "$base/$($note.id)/export/markdown" -OutFile $path -PassThru
$r.StatusCode
$r.Headers['Content-Disposition']
Get-Content -Encoding utf8 $path

# 只改变笔记 UUID，核对失败状态。
$missing = Invoke-WebRequest "$base/$([guid]::NewGuid())/export/markdown" -SkipHttpErrorCheck
$missing.StatusCode
$missing.Content
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 导出已有合成笔记 | | 200，UTF-8 原句清晰可读；含 1000–2400 ms、资料版本和证据 ID | | | |
| 使用不存在的 UUID | | 404 与 `NOTE_NOT_FOUND` | | | |

出现 404 时先核对 Java 端口与笔记 ID；详情可读而导出 404 时检查是否启动了新版 Java。也可在 Web 进入「笔记 → 下载 Markdown」核对浏览器附件。走读 [Markdown 渲染器](../../server/src/main/java/dev/citeweave/source/TranscriptNoteMarkdown.java)。迁移问题：如果摘要里包含外链图片，为什么导出时要保留原字符并让它按字面显示？把实测附到[学习记录](LEARNING_LOG.md)。


## 实验 16 · 两份字幕时间相同，引用能回对地方吗

**预计 20 分钟。** 使用 PowerShell 7、Java 21、Maven 和跨字幕问答版本，从仓库根运行。Java 测试内置两份合成字幕，每份都有 1000–2000 ms 的「缓存失效」片段。不启动数据库或模型，也不写入资料。

```powershell
# 正常组核对引用归属，失败组提交未选资料的证据 ID。
mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptQuestionTest" test
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 选中 1、2 并给出各自引用 | | 返回 200，两条引用带不同资料 ID，证据端点读回第二份原句 | | | |
| 选中范围相同，换成第三份引用 | | 返回 502 `QUESTION_INVALID_CITATION` | | | |
| 从第 2 份资料查询第 1 份证据 ID | | 返回 404 `SOURCE_EVIDENCE_NOT_FOUND` | | | |

集中验收时，在新版 Web 从「资料库 → 跨字幕问答」选择两份合成字幕，点击同一时间区间的两个引用，核对资料标题和高亮原句。列表为空先确认已导入两份 SRT/VTT，命中后返回 503 则检查验收环境的模型配置。走读 [候选分配和证据校验](../../server/src/main/java/dev/citeweave/source/TranscriptQuestionService.java)。迁移问题：如果以后接入页码引用，哪些条件靠时间区间无法表达，必须用资料版本和片段标识表达？把实际结果附到[学习记录](LEARNING_LOG.md)。


## 实验 17 · 文档引用为什么用段号

**预计 15–20 分钟。** 在仓库根使用 PowerShell 7、Maven 与 Java 21。Java 测试内置一份有 CRLF 和连续空白行的 Markdown。不需要数据库和模型，也不会写用户资料。

```powershell
# 走文档段落接口和问答契约。观察 Java 从同一份内容返回段号和原句。
mvn.cmd -o -B -f server/pom.xml "-Dtest=TranscriptQuestionTest#documentAnswerUsesParagraphIdentityAndCanonicalText" test
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 读取 CRLF 样例 | | 三个非空段落，「缓存失效后重建。」为第 2 段 | | | |
| 对样例文档提问「缓存失效」 | | 引用命中第 2 段并返回原句，定位字段中无毫秒值 | | | |

集中验收可从 Web 打开粘贴文本、TXT 或 Markdown 资料，使用「问这份资料」并点回答引用。若看不到入口，核对资料类型和段落 API；返回 503 则检查模型。沿 [段落构造器](../../server/src/main/java/dev/citeweave/source/DocumentEvidenceReader.java) 和 [证据字段校验](../../ai/src/citeweave_ai/evidence.py) 走读。迁移问题：如果更改分段规则，为什么之前的“第 2 段”可能指向错误正文，应该更新哪个版本？把实测附到[学习记录](LEARNING_LOG.md)。


## 实验 18 · 为什么空白页不应改变引用页码

**预计 15–20 分钟。** 使用 PowerShell 7、Maven、Java 21 和健康的独立 PostgreSQL 15434。从仓库根操作。测试在独立库应用 V6，新增两笔合成资料并在 `server/target` 保存测试 PDF。无需真实模型。

```powershell
# 核对当前使用独立数据库和 15434 端口。
docker compose -p citeweave-summary-qa -f infra/compose.yaml ps

# 运行真实 PDF 上传回归。目标 PDF 有三页，中间一页空白。
$env:CITEWEAVE_ISOLATED_PG_TEST = '1'
try {
  mvn.cmd -o -B -f server/pom.xml "-Dtest=PdfPageIntegrationTest" test
} finally {
  Remove-Item Env:CITEWEAVE_ISOLATED_PG_TEST -ErrorAction SilentlyContinue
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 读取含空白页的合成 PDF | | 页码列表为 1、3，问答引用返回第 3 页 | | | |
| 读取未存页码的合成历史记录 | | 正文仍可读，页码接口 404 `SOURCE_PDF_PAGES_UNAVAILABLE` | | | |

集中验收可在新版 Web 导入 `server/target/pdf-page-fixture.pdf` 并问「Cache rebuild」，点击第 3 页引用核对原句。测试被跳过时先查环境变量；网页没有第 3 页时确认 Java 与 V6 独立库匹配。走读 [PDF 解析](../../server/src/main/java/dev/citeweave/source/SourceParser.java) 中 PDFBox 的页回调。迁移问题：如果对扫描页补 OCR，怎样在保留原始页码的同时标记哪些证据来自识别？把实际观察附到[学习记录](LEARNING_LOG.md)。


## 实验 19 · 三种坐标混选时，如何防止引用串到另一份资料

**预计 15–20 分钟。** 使用包含 `POST /api/source-questions` 的版本。验收浏览器已预置三份标题共用「混选浏览器验收-1795397658638」的合成资料。浏览器实验只发查询。若独立 Web 已停，仍可从仓库根用 PowerShell、Java 21 和 Maven 运行下面的接口对照。接口对照需要健康的独立 PostgreSQL 15434，每次会向验收库新增合成资料；不需要启动 Web、Java HTTP 进程、Python 或接入模型。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
# 查看独立 PostgreSQL 是否 healthy，端口应为 15434。
docker compose -p citeweave-summary-qa -f infra/compose.yaml ps
# 仅运行混选回归。成功时观察 Tests run: 1, Failures: 0, Errors: 0, Skipped: 0。
$env:CITEWEAVE_ISOLATED_PG_TEST = '1'
try {
  mvn.cmd -o -B -f server/pom.xml "-Dtest=PdfPageIntegrationTest#mixedQuestionKeepsThreeCoordinatesAndChecksAllSourcesBeforeCallingEngine" test
} finally {
  Remove-Item Env:CITEWEAVE_ISOLATED_PG_TEST -ErrorAction SilentlyContinue
}
```

1. 打开[独立问答入口](http://127.0.0.1:15175/#/transcript-questions)，搜索 `混选浏览器验收-1795397658638`，按字幕、文档、PDF 的顺序勾选，提问 `Cache rebuild`。这是本地预设回答。用浏览器另开标签页查看每条链接，在原文页刷新，记录高亮的时间、段号与页码。
2. 只改一个变量。把 PDF 引用链接中的 `?evidence=` 及后面的值原样复制到 Markdown 资料链接里，保持 Markdown 链接的资料 ID。打开后记录错误提示。接口侧的对应对照用同一组选中资料，让夹具返回一条范围外的引用。

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 同时勾选合成字幕、Markdown、PDF | | 分别定位 1000–2400 ms、第 2 段和第 3 页；刷新仍定位 | | | |
| 在 Markdown 链接中替入 PDF 的证据 ID | | 提示当前资料版本找不到这条引用，页面没有定位到段落 | | | |
| 接口夹具只换成范围外的引用 ID | | 返回 502 `QUESTION_INVALID_CITATION` | | | |

页面打不开先查 15175。资料列表不可读先查 18087 的 `GET /api/source-questions/sources`，问答返回 503 则检查 18091 和模型夹具端口 18092。Maven 测试若显示跳过，检查环境变量；若数据库连接失败，查 15434。走读 [Java 的轮流选证据和模型结果复核](../../server/src/main/java/dev/citeweave/source/TranscriptQuestionService.java)，再看 [Web 的原文定位](../../web/src/DocumentReader.tsx)。需要能解释资料、版本和证据 ID 如何关联。迁移问题是「如果 PDF 的证据改为 OCR 识别，旧链接该怎样避免跳错文本？」把实测记录在[学习日志](LEARNING_LOG.md)。

## 实验 20 · 同一个关键词为什么只返回选中资料的证据

**预计 15–20 分钟。** 从仓库根目录用 Windows PowerShell 运行。版本需包含 `POST /api/source-evidence/search`。服务为独立 Web 15175、Java 18087 与 PostgreSQL 15434，使用上一轮预置的合成样例，无需配置模型。本实验只读。也可以直接打开[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)，搜索 `混选浏览器验收-1795397658638`，只勾选文档，再点击「查证据」。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
$base = 'http://127.0.0.1:15175'
# 用标题定位独立库中已存在的合成字幕与 Markdown，稍后只改变所选资料 ID。
$items = Invoke-RestMethod "$base/api/source-questions/sources"
$md = $items | Where-Object title -eq '混选浏览器验收-1795397658638文档'
$srt = $items | Where-Object title -eq '混选浏览器验收-1795397658638字幕'
if (!$md -or !$srt) { throw '验收样例不存在，请核对独立资料库。' }
# 查候选时只选 Markdown。看 sourceId、paragraph 和原句。
$body = @{sourceIds=@($md.id);query='Cache rebuild'} | ConvertTo-Json -Compress
$hits = Invoke-RestMethod "$base/api/source-evidence/search" -Method Post -ContentType 'application/json' -Body $body
$hits | Format-List sourceId, paragraph, text
# 只改变 sourceIds，检索词保持相同。看同一个词如何回到字幕时间轴。
$body = @{sourceIds=@($srt.id);query='Cache rebuild'} | ConvertTo-Json -Compress
Invoke-RestMethod "$base/api/source-evidence/search" -Method Post -ContentType 'application/json' -Body $body |
  Format-List sourceId, startMs, endMs, text
# 只改变检索词，保留字幕范围。观察无命中数组。
$body = @{sourceIds=@($srt.id);query='ocean reef coral'} | ConvertTo-Json -Compress
(Invoke-WebRequest "$base/api/source-evidence/search" -Method Post -ContentType 'application/json' -Body $body).Content
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 在 Markdown 里搜索 `Cache rebuild` | | 只返回该文档第 2 段，原句为 `Cache rebuild follows a version update.` | | | |
| 检索词相同，改选字幕 | | 只返回该字幕 1000–2400 ms，原句为 `Cache rebuild follows eviction.` | | | |
| 保留字幕，改用无关词 | | HTTP 200 与 `[]` | | | |

先检查 15175 是否可访问、候选列表是否有上述标题。Java 端口为 18087，独立库为 15434。接口正常但输出不是预期时，检查提交的 `sourceIds` 和样例正文。走读 [Controller 如何确定范围](../../server/src/main/java/dev/citeweave/source/TranscriptQuestionController.java) 和 [如何排序候选](../../server/src/main/java/dev/citeweave/source/TranscriptQuestionService.java)。本轮需要能解释「先确定范围，再排相关性」：如果以后把词面检索换成全库向量索引，应该在哪里限制资料 ID，才能防止未选资料挤占候选名额？把预期和实际分开填，用户实测附在[学习日志](LEARNING_LOG.md)。

## 实验 21 · 模型能否自行扩大证据范围

**预计 15–20 分钟。** 在 Windows PowerShell 从仓库根运行。版本需包含 `POST /internal/v1/research`。使用独立 Web 15175、Java 18087、Python 18091、合成模型 18093，以及预置的合成字幕和 Markdown。只读取测试库，模型响应由本机夹具生成。也可从 [Python 接口页](http://127.0.0.1:18091/docs) 用同一 JSON 操作。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
# 通过资料标题取当前隔离库的两个 ID。不要用个人资料替换。
$items = Invoke-RestMethod 'http://127.0.0.1:15175/api/source-questions/sources'
$srt = $items | Where-Object title -eq '混选浏览器验收-1795397658638字幕'
$md = $items | Where-Object title -eq '混选浏览器验收-1795397658638文档'
if (!$srt -or !$md) { throw '缺少验收样例' }
$endpoint = 'http://127.0.0.1:18091/internal/v1/research'
$request = @{sourceIds=@($srt.id,$md.id);question='Compare conditions for Cache rebuild'}
# 正常组。观察 1 次搜索、2 次回读，evidenceIds 应对应两个实际读回的证据。
Invoke-RestMethod $endpoint -Method Post -ContentType 'application/json; charset=utf-8' `
  -Body ($request | ConvertTo-Json -Compress) | ConvertTo-Json -Depth 6
# 范围保持不变，只加夹具触发词，让模型在搜索后提议读取未选资料。
$request.question += ' 越界'
Invoke-RestMethod $endpoint -Method Post -ContentType 'application/json; charset=utf-8' `
  -Body ($request | ConvertTo-Json -Compress) -SkipHttpErrorCheck -StatusCodeVariable researchStatus |
  ConvertTo-Json -Depth 6
$researchStatus
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 允许范围内搜索、精确读取并回答 | | HTTP 200；轨迹依序为 `search_evidence`、两次 `read_evidence`；两条引用 | | | |
| 所选资料不变，夹具提议越界读取 | | HTTP 502 `RESEARCH_EVIDENCE_OUT_OF_SCOPE`；轨迹只有已完成的搜索 | | | |

先检查 18091 的 `/healthz`，再检查 15175 的候选列表和 Java 18087。收到 `RESEARCH_DISABLED` 时核对隔离进程的启用开关。隔离服务已停时，从 `ai` 目录用 `uv run --offline --locked pytest -q tests/test_research.py` 做无服务的协议对照。走读 [运行循环和证据范围校验](../../ai/src/citeweave_ai/research.py)，需要能解释「谁决定范围、谁提出调用、谁执行」。迁移问题是「将来恢复一个挂起任务时，用户已失去部分资料访问权，原检查点中的证据可否继续使用？」把观察和解释附在[学习日志](LEARNING_LOG.md)。

## 实验 22 · 研究中途失败，页面为什么还能显示已完成的步骤

**预计 15–20 分钟。** 使用包含 `POST /api/research` 和「开始研究」按钮的版本。独立环境需启动 Web 15175、Java 18087、Python 18091、模型夹具 18093 与 PostgreSQL 15434。合成样例已预置，本实验只读。HTTP 对照从仓库根用 PowerShell 7 操作。

1. 打开[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)，搜索 `混选浏览器验收-1795397658638`，按字幕、文档的顺序勾选。输入 `Compare conditions for Cache rebuild`，点击「开始研究」。核对三步轨迹和两条原文引用；原文页刷新后仍应定位。
2. 返回问答页保持或恢复相同的资料选择，问题只加 ` 越界`。夹具在搜索完成后提议一次范围外读取。观察页面保留几步，以及是否还有答案或引用。删除 ` 越界` 后重试，观察旧错误是否清除。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
# 查询独立资料库，用两个合成标题取得当前 ID。此命令只读。
$base = 'http://127.0.0.1:15175'
$items = Invoke-RestMethod "$base/api/source-questions/sources"
$srt = $items | Where-Object title -eq '混选浏览器验收-1795397658638字幕'
$md = $items | Where-Object title -eq '混选浏览器验收-1795397658638文档'
if (!$srt -or !$md) { throw '验收样例未找到' }
$body = @{sourceIds=@($srt.id,$md.id);question='Compare conditions for Cache rebuild'}
# 读成功响应的状态、证据坐标和 trace。不会写入资料或会话。
Invoke-RestMethod "$base/api/research" -Method Post -ContentType 'application/json; charset=utf-8' -Body ($body | ConvertTo-Json -Compress) |
  ConvertTo-Json -Depth 7
# 只改问题的夹具触发词。错误响应仍可读取，观察 code 和 trace 数组。
$body.question += ' 越界'
$failure = Invoke-RestMethod "$base/api/research" -Method Post -ContentType 'application/json; charset=utf-8' -Body ($body | ConvertTo-Json -Compress) -SkipHttpErrorCheck -StatusCodeVariable researchHttp
$researchHttp
$failure | ConvertTo-Json -Depth 7
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 两份合成资料执行研究 | | HTTP 200，1 次搜索、2 次回读，回到 1000–2400 ms 和文档第 2 段 | | | |
| 范围相同，问题末尾加「越界」 | | HTTP 502 `RESEARCH_EVIDENCE_OUT_OF_SCOPE`，只有搜索一步，页面无答案引用 | | | |
| 去掉触发词再试 | | 旧错误消失，正常结果和轨迹恢复 | | | |

按钮无效时先核对资料选择与问题。Web 15175 正常但提示服务不可用时，检查 Java 18087 的候选端点、Python 18091 的 `/healthz` 和本地夹具 18093。若问题涉及引用，走读 [Java 如何核验轨迹并回填引用](../../server/src/main/java/dev/citeweave/source/ResearchService.java)。需要能解释为什么失败响应只显示已完成步骤。迁移问题是「如果搜索完成后 Java 重启，什么状态必须持久化，重试时才能可靠地区分已完成和未开始？」把实测与解释附到[学习日志](LEARNING_LOG.md)。

## 实验 23 · 重开研究记录会再次调用模型吗

**预计 15–20 分钟。** 使用含研究历史的版本和已应用 V7 的独立库。Web 15175、Java 18087、Python 18091 与模型夹具 18093 已用于验收。用 Windows PowerShell 7 或浏览器操作。第一次提交会保存一条合成研究记录，随后查看、刷新、打开引用均为只读。

1. 打开[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)，搜索 `混选浏览器验收-1795397658638`，按字幕、文档的顺序勾选。用 `Compare conditions for Cache rebuild` 发起研究，点击「打开已保存的研究」。记录 URL 中的 UUID。
2. 开启浏览器开发者工具的「网络」面板，刷新研究记录。按 `/api/research` 过滤。对照第一次的 `POST` 和重开的 `GET`。核对 UUID、问题、三步轨迹和两条引用。再通过「最近研究」点开同一条。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
$base = 'http://127.0.0.1:15175'
# 查看本机个人工作区最近 20 条记录，取刚才保存的 UUID。
$records = Invoke-RestMethod "$base/api/research"
$records | Select-Object -First 3 id, question, createdAt
# 将下面的 ID 换成浏览器 URL 或上一步返回的真实 UUID，读取同一快照。
$id = Read-Host '输入已保存的研究 UUID'
$saved = Invoke-RestMethod "$base/api/research/$id"
$saved.record
$saved.trace | Format-Table tool, callId
$saved.citations | Format-Table sourceId, locationType, startMs, paragraph
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 点击「开始研究」 | | 一次 `POST /api/research`，成功响应有 UUID，「最近研究」出现对应问题 | | | |
| 保持同一 UUID，刷新记录页 | | `GET /api/research/{id}`；没有新 POST；三步轨迹和两条引用不变 | | | |

若列表读不到，先确认 15175 和 Java 18087 是否就绪，再查隔离库 15434 的 Flyway 日志中是否已应用 V7。若只在首次运行时失败，再查 Python 18091 和夹具 18093。走读 [研究历史写入与回读](../../server/src/main/java/dev/citeweave/source/ResearchHistoryService.java)，解释为何只保存引用 ID，以及为何 GET 无需模型。迁移问题是「如果服务已写入快照但浏览器没收到响应，怎样避免重试后重复生成和保存？」把实测附到[学习日志](LEARNING_LOG.md)。

## 实验 24 · 响应丢失后，为什么重复提交只保存一条

**预计 15–20 分钟。** 在项目根目录用 Windows PowerShell 7。需要含 V8 的版本、独立 Web 15175、Java 18087、Python 18091、合成模型 18093 与独立 PostgreSQL 15434。使用已预置的合成字幕和文档，会在独立库新增一条终态记录。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
$base = 'http://127.0.0.1:15175'
# 获取预置样例 ID 并保留字幕在前、文档在后。顺序会影响候选排序。
$sources = Invoke-RestMethod "$base/api/source-questions/sources"
$srt = $sources | Where-Object title -eq '混选浏览器验收-1795397658638字幕'
$md = $sources | Where-Object title -eq '混选浏览器验收-1795397658638文档'
if (!$srt -or !$md) { throw '缺少独立库合成样例' }
$key = [guid]::NewGuid().ToString()
$payload = @{sourceIds = @($srt.id, $md.id); question = "Compare conditions for Cache rebuild · Lab24 $key"}
$headers = @{'Idempotency-Key' = $key}
$body = $payload | ConvertTo-Json -Compress
# 首次完成后，模拟调用者没收到响应。再次提交同样的键和内容。
$first = Invoke-RestMethod "$base/api/research" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
$retry = Invoke-RestMethod "$base/api/research" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
[pscustomobject]@{ FirstId = $first.record.id; RetryId = $retry.record.id }
# 只改问题，保持键和资料顺序，查看 HTTP 状态和稳定错误码。
$payload.question += ' changed'
$different = Invoke-RestMethod "$base/api/research" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ($payload | ConvertTo-Json -Compress) -SkipHttpErrorCheck -StatusCodeVariable retryStatus
[pscustomobject]@{ Status = $retryStatus; Code = $different.code }
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 同一个键和内容提交两次 | | 两次均 HTTP 200，记录 ID 相同，「最近研究」只出现一条 Lab24 记录 | | | |
| 保留请求键，只修改问题 | | HTTP 409 `RESEARCH_REQUEST_KEY_CONFLICT` | | | |

接口不可用时先查 15175 和 18087，再核对隔离库是否已应用 V8。若返回 `RESEARCH_REQUEST_IN_PROGRESS`，等待初始请求完成后保留原键重试；如果服务刚重启，查看「最近研究」是否有已提交记录。走读[认领与发布事务](../../server/src/main/java/dev/citeweave/source/ResearchHistoryRepository.java)。需要能解释为什么唯一约束必须发生在模型调用前。迁移问题是「进程在模型已经收到请求、数据库尚未发布结果时退出，接管者怎样区分可安全重试和可能重复收费？」将实测填入[学习日志](LEARNING_LOG.md)。

## 实验 25 · 上游结果未知，何时可以重跑

**预计 15–20 分钟。** 从项目根用 Windows PowerShell 7 或浏览器。需要含 V9 的版本、独立 Web 15175、Java 18087、Python 18091、模型夹具 18093 和独立 PostgreSQL 15434。合成夹具对每个包含「中断夹具」的新问题首次返回上游错误。第一次请求会留下未知状态。只有明确重跑成功才会新增一条历史。

1. 打开[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)，搜索 `混选浏览器验收-1795397658638`，按字幕、文档顺序勾选。输入 `Compare conditions for Cache rebuild 中断夹具` 并追加自己选的唯一序号，点击「开始研究」。
2. 用浏览器网络面板过滤 `/api/research`。提示结果未知后，先点击「查看最近研究」，再点击「用新请求重新研究」。比较 POST 请求的 `Idempotency-Key` 和响应。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
$base = 'http://127.0.0.1:15175'
# 只查已预置的两份合成资料，保留字幕在前。
$items = Invoke-RestMethod "$base/api/source-questions/sources"
$srt = $items | Where-Object title -eq '混选浏览器验收-1795397658638字幕'
$md = $items | Where-Object title -eq '混选浏览器验收-1795397658638文档'
if (!$srt -or !$md) { throw '缺少隔离样例' }
$key = [guid]::NewGuid().ToString()
$body = @{sourceIds=@($srt.id,$md.id);question="Compare conditions for Cache rebuild 中断夹具 $key"} | ConvertTo-Json -Compress
$headers = @{'Idempotency-Key'=$key}
# 固定内容和键发两次。两次都应显示 409，并且不新增历史。
1..2 | ForEach-Object {
  $result = Invoke-RestMethod "$base/api/research" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck -StatusCodeVariable outcomeHttp
  [pscustomobject]@{Status=$outcomeHttp; Code=$result.code}
}
# 只有在你决定重新运行时改变键。问题与范围保持原样。
$headers['Idempotency-Key'] = [guid]::NewGuid().ToString()
$saved = Invoke-RestMethod "$base/api/research" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
$saved.record
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 首次发生上游故障，原样重发 | | 两次均为 HTTP 409 `RESEARCH_REQUEST_OUTCOME_UNKNOWN`，历史无新记录 | | | |
| 查看最近研究 | | 不发起研究 POST | | | |
| 同一个问题，明确使用新键 | | HTTP 200，只新增一条记录 | | | |

排查时先检查 15175 和 18087，再检查 18091 的健康检查、18093 是否运行故障版本。若第一次就成功，给问题换一个以前未用的序号。走读[发布条件中的数据库时钟](../../server/src/main/java/dev/citeweave/source/ResearchHistoryRepository.java)和[未知状态判断](../../server/src/main/java/dev/citeweave/source/ResearchHistoryService.java)。迁移问题是「未来如果续租并允许新 worker 接管，需要什么标识防止旧 worker 在新租约生效后继续发布？」把实际结果附到[学习日志](LEARNING_LOG.md)。

## 实验 26 · 下一次模型调用失败后，上一步为什么还能读到

**预计 15–20 分钟。** 在项目根用 Windows PowerShell 7。需要含 V10 的 Java 与 Python、独立 Web 15175、Java 18087、Python 18091、合成模型 18093 和独立 PostgreSQL 15434。使用预置的合成字幕与 Markdown。一个新问题触发一次故障，留下请求轨迹；明确重跑成功后新增一条合成研究记录。

1. 打开[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)，搜索 `混选浏览器验收-1795397658638`，依序选择字幕和文档。问题用 `Compare conditions for Cache rebuild 检查点夹具`，末尾加一个未使用的序号。点击「开始研究」，观察结果未知提示下的轨迹。
2. 通过下面的接口对照，同键原样重发。检查两次响应的 `trace` 是否相同，以及是否都只包含搜索。浏览器也可点击「用新请求重新研究」，核对正常运行有三步。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
$base = 'http://127.0.0.1:15175'
# 只读获取合成样例，用原始顺序提交。
$items = Invoke-RestMethod "$base/api/source-questions/sources"
$srt = $items | Where-Object title -eq '混选浏览器验收-1795397658638字幕'
$md = $items | Where-Object title -eq '混选浏览器验收-1795397658638文档'
if (!$srt -or !$md) { throw '缺少隔离样例' }
$key = [guid]::NewGuid().ToString()
$headers = @{'Idempotency-Key'=$key}
$body = @{sourceIds=@($srt.id,$md.id);question="Compare conditions for Cache rebuild 检查点夹具 $key"} | ConvertTo-Json -Compress
# 第一次推进到故障；第二次保持同一请求，不重新执行工具。观察状态与 trace。
1..2 | ForEach-Object {
  $result = Invoke-RestMethod "$base/api/research" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body -SkipHttpErrorCheck -StatusCodeVariable checkpointHttp
  [pscustomobject]@{Status=$checkpointHttp; Code=$result.code; Steps=$result.trace.Count}
  $result.trace | Format-Table tool,callId
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 搜索完成后夹具返回故障 | | HTTP 409，只显示一条 `search_evidence`，没有答案 | | | |
| 保持原键原内容重发 | | HTTP 409，轨迹的调用 ID 与首次相同 | | | |
| 明确发起一轮新请求 | | HTTP 200，一次搜索、两次回读，引用可打开 | | | |

查 15175 和 18087，再查 Python 18091 是否加载回调逻辑，以及模型夹具 18093 是否启用该触发词。如果返回 409 但没有搜索步骤，优先检查 Python 和 Java 的内部回调日志。走读 [Python 保存后再继续](../../ai/src/citeweave_ai/research.py)与 [Java 的条件更新](../../server/src/main/java/dev/citeweave/source/ResearchHistoryRepository.java)。迁移问题是「如果要从已完成搜索继续，除了工具和证据 ID，还要保存哪些消息、预算和权限信息？」把实测写到[学习日志](LEARNING_LOG.md)。

## 实验 27 · 为什么重送检查点不能改掉已确认的参数

**预计 15 分钟。** 从仓库根用 PowerShell 7，需要含 V11 的 Java、Maven 离线缓存，以及隔离 PostgreSQL 15434。集成测试在独立库新增合成记录，无需外部模型，Web 与 Python 可保持关闭。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
# 确认只使用独立容器，数据库端口为 15434。
docker compose -p citeweave-summary-qa -f infra/compose.yaml ps
$env:CITEWEAVE_ISOLATED_PG_TEST = '1'
try {
  # 同一次测试内对照相同回调、检索词被改写和剩余步数错误。
  mvn.cmd -o -B -f server/pom.xml "-Dtest=ResearchHistoryIntegrationTest#checkpointIsOrderedAuthorizedAndSurvivesLostUpstreamResult" test
} finally {
  Remove-Item Env:CITEWEAVE_ISOLATED_PG_TEST
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 按原样重送同一步 | | HTTP 204，库存仍只有一步，剩余 2 步 | | | |
| 步骤与证据不变，只改已确认的检索词 | | HTTP 409，原检索词保留 | | | |
| 恢复原检索词，只提交错误步数 | | HTTP 400 | | | |

看到跳过时，核对环境变量。连接失败时，先核对独立容器和 15434。走读 [参数校验和旧前缀比对](../../server/src/main/java/dev/citeweave/source/ResearchHistoryService.java)。迁移问题是「如果搜索后文档更新，能否重新搜索来替代原工具结果？这样会如何影响可复现性？」把观察附到[学习日志](LEARNING_LOG.md)。

## 实验 28 · 原证据版本失效后还能重建吗

**预计 15 分钟。** 需要包含 `rebuild_context` 的版本、Windows PowerShell 和已同步的 Python 依赖。从项目的 `ai` 目录运行离线夹具。无需启动服务，无项目数据库写入。

```powershell
Set-Location 'D:\Work\Project\CiteWeave\ai'
# 两个用例用相同检查点。正常组只回读一次，失效组仅改变证据服务状态为 404。
uv run --offline --locked pytest -q tests/test_research.py -k "rebuild_restores or rebuild_stops"
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 按原 ID 回读 | | 一次证据 GET，还原六条消息，搜索和读取仍配对原调用 ID | | | |
| 仅将证据回读状态改为 404 | | 抛出 `RESEARCH_EVIDENCE_UNAVAILABLE`，没有后续读取 | | | |

若依赖读取失败，先核对工作目录和 uv 缓存；若断言失败，查看测试中 `restore` 拦截的请求。走读 [重建函数](../../ai/src/citeweave_ai/research.py)。需要能解释为何恢复搜索结果时只回读原候选。迁移问题是「改用当前索引重搜，即使找到相同条数，为什么也可能改变后续决策？」把实测附到[学习日志](LEARNING_LOG.md)。

## 实验 29 · 续跑是否保留了第一次的搜索

**预计 15–20 分钟。** 需要含 V12 的隔离 Web 15175、Java 18087、Python 18091、合成夹具 18093 和独立 PostgreSQL 15434。用浏览器访问[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)。此实验用新问题触发一次故障，成功续跑会在隔离库新增一条研究记录。

1. 搜索 `混选浏览器验收-1795397658638`，按字幕、文档顺序勾选，输入 `Compare conditions for Cache rebuild 检查点夹具` 并追加未使用的序号。
2. 打开浏览器开发者工具的网络面板，保留日志并过滤 `/api/research`。点击「开始研究」，记录失败响应的搜索调用 ID。保持问题和资料不变，点「从检查点继续」，对照请求头、轨迹和引用。
3. 可在 `ai` 目录运行只改变证据可用性的离线对照。观察恢复成功时的 GET 与模型调用，以及 404 时模型调用是否发生。

```powershell
Set-Location 'D:\Work\Project\CiteWeave\ai'
# 用同一检查点，正常组允许原证据回读，失效组仅把 Java 响应改成 404。
uv run --offline --locked pytest -q tests/test_research.py -k test_resume_passes_rebuilt_pairs
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 初次发起后夹具中断 | | 409，已有一条搜索及原调用 ID | | | |
| 从断点继续 | | 新请求键，`Research-Resume-From` 为原键；200、搜索仍只有一次、总轨迹三步两引用 | | | |
| 离线对照只让证据失效 | | 报 `RESEARCH_EVIDENCE_UNAVAILABLE`，模型未被调用 | | | |

若没有续跑按钮，先看失败响应是否含已确认的步骤。连不上时核对 15175、18087、18091 和隔离库的 V12。走读 [续跑认领](../../server/src/main/java/dev/citeweave/source/ResearchHistoryService.java)和[Python 恢复逻辑](../../ai/src/citeweave_ai/research.py)。迁移问题是「如果首次续跑已提交、浏览器没收到响应，为什么应复用续跑键，而不是再从旧键分叉？」把观察附到[学习日志](LEARNING_LOG.md)。

## 实验 30 · 刷新会重新发起模型请求吗

**预计 15 分钟。** 使用包含 V13 的独立 Web 15175、Java 18087、Python 18091、合成模型 18093 和隔离 PostgreSQL。无需终端即可走完。刷新只读；手动续跑成功会在隔离库新增一条研究。

1. 打开[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)，搜索 `混选浏览器验收-1795397658638`，按字幕、文档顺序选中。用 `Compare conditions for Cache rebuild 检查点夹具` 加一个新序号启动研究。
2. 显示未知后，复制带 `?pending=` 的地址。在网络面板开启保留日志，清空已有请求，刷新。核对表单、勾选项和已确认步骤，再点击「从检查点继续」。
3. 成功后重开原地址，从「查看已发起的续跑」打开完成记录。对照两个页面的原问题、轨迹和现有引用。

```powershell
# 可选，只读核对。把复制链接中 pending= 后面的 UUID 传给状态端点。
$key = Read-Host '输入恢复链接的 UUID'
Invoke-RestMethod "http://127.0.0.1:15175/api/research/requests/$key" | ConvertTo-Json -Depth 6
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 原链接刷新 | | 恢复原问题、2 个勾选项和 1 步轨迹；只有 GET，无研究 POST | | | |
| 点击「从检查点继续」 | | 才发出 POST，返回已完成记录 | | | |
| 再开原地址 | | 能打开已发起的续跑所对应的完成记录 | | | |

恢复失败先检查地址是否保留完整 UUID、隔离环境的 Java 端点与 V13。旧 V12 请求缺原问题时，已完成记录仍从「最近研究」打开。走读 [Java 状态查询](../../server/src/main/java/dev/citeweave/source/ResearchHistoryRepository.java) 与 [Web 的 GET 恢复逻辑](../../web/src/MultiTranscriptPage.tsx)。迁移问题是「为什么查询过期状态可以返回 UNKNOWN，同时不修改数据库中的运行状态？」把观察记入[学习日志](LEARNING_LOG.md)。

## 实验 31 · 为什么旧标签页不能覆盖新笔记

**预计 15–20 分钟。** 使用含 V14 的独立 Web 15175、Java 18087 与独立 PostgreSQL 15434。从[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)的「最近研究」打开已完成的合成研究。生成和编辑测试笔记会写隔离库；打开引用和导出只读。

1. 点「保存为笔记」，把笔记链接复制到第二个浏览器标签页。两页应读到同一修订号。用自己的合成标题，避免改到其他笔记。
2. 标签页 A 把正文改为 `## 我的结论` 加一行说明并保存，确认提示「笔记已保存」。标签页 B 保持旧数据，改同一处并保存。用网络面板查看两个 PUT 的 `revision`。
3. 回到 A 刷新，下载已保存 Markdown，核对正文、原句、资料版本和证据 ID。点引用，确认仍定位到原字幕或文档。

```powershell
# 可选，只读取测试笔记的当前版本。将链接中 /notes/research/ 后的 UUID 填入。
$id = Read-Host '输入研究笔记的 UUID'
$value = Invoke-RestMethod "http://127.0.0.1:15175/api/research-notes/$id"
$value.note | Format-List title,revision,updatedAt
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| A 用当前修订号保存 | | HTTP 200，修订号加一 | | | |
| B 用旧修订号保存 | | HTTP 409 `RESEARCH_NOTE_REVISION_CONFLICT`，B 的输入仍在，A 的已保存稿保留 | | | |
| 刷新、导出并点击原文 | | 最新正文可读；Markdown 保留资料版本和证据 ID，引用能定位 | | | |

按钮不可用时先检查原研究状态。列表或保存接口报错先查 15175、18087 和独立库 V14。遇到版本冲突，先复制未保存内容，再按需重新加载。走读 [Repository 更新条件](../../server/src/main/java/dev/citeweave/source/ResearchNoteRepository.java)。迁移问题是「如果让系统自动保存，每次并发保存应该如何处理冲突？」把观察记入[学习日志](LEARNING_LOG.md)。

## 实验 32 · 为什么重试后任务 ID 没变，执行次数变了

**预计 15–20 分钟。** 使用含 V15 的独立 Web 15175、Java worker 18087、Python 18091、本地模型夹具 18093 与隔离 PostgreSQL。从项目根使用 PowerShell 7。实验新建一个临时 SRT、一条合成资料、一条任务和一份摘要。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
# 生成自己的合成字幕。夹具标记使第一次摘要请求返回 429。
$tag = [guid]::NewGuid().ToString()
$file = Join-Path ([System.IO.Path]::GetTempPath()) "citeweave-task-$tag.srt"
@"
1
00:00:01,000 --> 00:00:02,400
[任务验收夹具] $tag 缓存过期后重建。
"@ | Set-Content -LiteralPath $file -Encoding utf8
$file
```

1. 在[导入页](http://127.0.0.1:15175/#/import)上传该 SRT，点「后台生成摘要」。在任务中心等到「失败 · 已执行 1 次」。打开网络面板，查看提交的 `202` 和 `Location`。
2. 刷新任务中心，再对同一行点击「重试任务」。等到「已完成 · 已执行 2 次」，确认重试使用原任务 ID。点击「打开摘要」，再点引用。

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 入队并触发限流 | | 提交返回 202 和任务 ID；最终失败、尝试 1、`SUMMARY_MODEL_RATE_LIMITED` | | | |
| 同一任务手动重试 | | 原 ID 成功、尝试 2 | | | |
| 打开摘要并点引用 | | 时间轴恢复到 1000–2400 ms 的原句 | | | |

一直待处理先检查 Java worker 是否开启，再查 18091。第一次就成功时确认夹具版本和字幕标记。走读 [领取、尝试号与租约](../../server/src/main/java/dev/citeweave/source/SummaryTaskRepository.java)。迁移问题是「旧 worker 晚于新 worker 结束时，为什么只比较任务 ID 不够？」将观察写到[学习日志](LEARNING_LOG.md)。

## 实验 33 · 哪些查询由 MyBatis-Plus 生成

**预计 15 分钟。** 使用已经接入 MyBatis-Plus 的版本和正在运行的隔离 Web 15175。从[任务中心](http://127.0.0.1:15175/#/tasks)选择一条已完成的合成任务。下列操作只读取。

```powershell
# 列表使用定制排序及上限，从现有合成任务复制一个 UUID。
$base = 'http://127.0.0.1:15175'
Invoke-RestMethod "$base/api/summary-tasks" | Select-Object -First 3 id,sourceId,status,attempt
$id = Read-Host '输入合成任务 UUID'
# 详情走 BaseMapper.selectById。仅替换请求 UUID 验证不存在的情况。
Invoke-RestMethod "$base/api/summary-tasks/$id"
$missing = [guid]::NewGuid()
Invoke-RestMethod "$base/api/summary-tasks/$missing" -SkipHttpErrorCheck -StatusCodeVariable responseStatus
$responseStatus
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 查现有合成任务详情 | | HTTP 200，状态和尝试号等于列表 | | | |
| 只换成未使用的 UUID | | HTTP 404 `TASK_NOT_FOUND` | | | |

先核对 15175 的 Java 代理与隔离库。走读 [Repository 的主键查询](../../server/src/main/java/dev/citeweave/source/SummaryTaskRepository.java) 和 [Mapper XML 的原子领取](../../server/src/main/resources/mapper/SummaryTaskMapper.xml)。迁移问题是「把领取拆成一次 BaseMapper 查询和一次更新，中间两个 worker 同时运行时会发生什么？」把实测附到[学习日志](LEARNING_LOG.md)。

## 实验 34 · 任务失败时能否读到半成品摘要

**预计 15–20 分钟。** 在项目根目录打开 PowerShell 7。使用本轮版本，需运行隔离 Web 15175、Java 18087、AI Engine 18091、本地模型夹具 18093 和隔离 PostgreSQL 15434。命令会在 `.tool-cache` 创建一份合成 SRT，并在隔离库新增一条资料和相关任务。夹具对每份带指定标记的字幕第一次返回限流，同一任务重试后返回有时间区间的合成摘要。

```powershell
# 新建唯一文件名，避免和之前的验收文件或已有任务混淆。
$base = 'http://127.0.0.1:15175'
$sample = Join-Path (Resolve-Path .tool-cache).Path ("atomic-" + [guid]::NewGuid() + ".srt")
[IO.File]::WriteAllText($sample, "1`n00:00:01,000 --> 00:00:02,400`n[任务验收夹具] Cache rebuild after expiry.`n")
# 通过 Web 代理导入隔离样例，从响应取得后续要沿用的资料 ID。
$source = Invoke-RestMethod "$base/api/sources" -Method Post -Form @{ file = (Get-Item -LiteralPath $sample) }
$sourceId = $source.source.id
$task = Invoke-RestMethod "$base/api/sources/$sourceId/summary-task" -Method Post
$taskId = $task.id

# 等待任务进入终态。若仍是 QUEUED 或 RUNNING，稍后重复执行这一行。
Invoke-RestMethod "$base/api/summary-tasks/$taskId" | Select-Object status, attempt, errorCode
# 首次限流后查摘要，只看 HTTP 状态。预期为 204。
(Invoke-WebRequest "$base/api/sources/$sourceId/summary").StatusCode

# 只重试原任务。沿用同一个 UUID，不导入第二份资料。
Invoke-RestMethod "$base/api/summary-tasks/$taskId/retry" -Method Post | Select-Object id, status
# 到 SUCCEEDED 后读回引用。若仍在执行，稍后重复查询。
Invoke-RestMethod "$base/api/summary-tasks/$taskId" | Select-Object status, attempt
(Invoke-RestMethod "$base/api/sources/$sourceId/summary").citations | Select-Object startMs, endMs
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 首次限流后读摘要 | | `FAILED`、尝试 1、`SUMMARY_MODEL_RATE_LIMITED`，摘要 HTTP 204 | | | |
| 只重试同一 UUID | | 同一任务以尝试 2 变成 `SUCCEEDED`，摘要 HTTP 200，引用 1000–2400 ms | | | |

15175 无响应先检查 Web 代理、18087 的 `GET /api/summary-tasks` 和 18091 的 `/healthz`。若返回 `SUMMARY_MODEL_NOT_CONFIGURED`，先核对隔离 AI Engine 是否指向 18093。任务完成却读不回摘要时，走读 [发布事务](../../server/src/main/java/dev/citeweave/source/TranscriptSummaryRepository.java)及[写入后注入故障的集成测试](../../server/src/test/java/dev/citeweave/source/SummaryTaskIntegrationTest.java)。页面实验验证上游失败与重试，集成测试验证摘要已经执行写入但尚未提交时的回滚。迁移问题是「为什么事务应从模型返回后开始？如果写入后进程退出，谁保证旧 worker 的结果不能覆盖重试？」将实测填入表并记到[学习日志](LEARNING_LOG.md)。

## 实验 35 · 改后缀能否通过视频检查

**预计 15 分钟。** 在仓库根目录开一个 PowerShell 7 窗口。使用包含「本地视频」入口的版本，启动 Web 15175 和 Java 18087。只用本机 FFmpeg 生成两份隔离样例，预检过程没有入库操作，也不调用模型。执行前确保 `ffmpeg -version` 和 `ffprobe -version` 都能返回版本。

```powershell
# 新建独立目录，给两个样本相同后缀。FFmpeg 用 -n 禁止覆盖已有文件。
$labVideo = Join-Path (Resolve-Path .tool-cache).Path ("video-lab-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $labVideo | Out-Null
$goodVideo = Join-Path $labVideo 'real.mp4'
$fakeVideo = Join-Path $labVideo 'renamed.mp4'
# 生成蓝色画面和 440Hz 音轨。-t 1 让两条轨在 1 秒处结束。
ffmpeg -n -v error -f lavfi -i 'color=c=blue:s=160x90:r=25' -f lavfi -i 'sine=frequency=440:sample_rate=44100' -t 1 -c:v mpeg4 -c:a aac $goodVideo
[IO.File]::WriteAllText($fakeVideo, 'This is plain text.')

# 两次发送同一个 multipart 字段，只有文件内容变化。对照输出为 200 和 422。
$previewApi = 'http://127.0.0.1:15175/api/videos/local/preview'
Invoke-RestMethod $previewApi -Method Post -Form @{file = (Get-Item -LiteralPath $goodVideo)} -StatusCodeVariable resultCode
$resultCode
Invoke-RestMethod $previewApi -Method Post -Form @{file = (Get-Item -LiteralPath $fakeVideo)} -SkipHttpErrorCheck -StatusCodeVariable resultCode
$resultCode
```

打开「导入资料 → 本地视频」，建议先选择 `renamed.mp4`，点「检查视频」后换成 `real.mp4`。观察错误是否清除以及新结果是否出现。以下是预计效果，实际输出由你填写。

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 检查伪装 MP4 | | HTTP 422 `VIDEO_INVALID`，页面仍可换文件 | | | |
| 换成合成 MP4 | | HTTP 200，1000 ms、160×90、MPEG-4 视频和 AAC 音轨，资料库不新增记录 | | | |

若返回 503 `VIDEO_PROBE_UNAVAILABLE`，在 Java 的启动环境核对 `CITEWEAVE_FFPROBE` 和工具路径。若只有 Web 入口失败，用 Java 18087 直连同一路径，区分代理和后端。走读 [LocalVideoProbe.preview](../../server/src/main/java/dev/citeweave/video/local/LocalVideoProbe.java)。迁移问题是「如果支持 WebM，为什么只在前端 accept 中加 .webm 不够？」把你的解释写进[学习日志](LEARNING_LOG.md)。

## 实验 36 · 同一个视频换名字，能否再创建一条记录

**预计 15 分钟。** 在仓库根目录打开 PowerShell 7，使用 V16 的独立 Web 15185 和 Java 18097。实验生成一个合成 MP4 并复制字节，用 Web 代理提交到独立视频验收库。只新增一条视频记录和一个媒体对象。需要 `ffmpeg` 和后台 Java 可调用的 FFprobe。

```powershell
# 以唯一目录和文件名生成合成短片，-n 保证不覆盖已有文件。
$case = [guid]::NewGuid().ToString('N')
$sampleDir = Join-Path (Resolve-Path .tool-cache).Path "video-dedup-$case"
New-Item -ItemType Directory -Path $sampleDir | Out-Null
$first = Join-Path $sampleDir 'first.mp4'
$second = Join-Path $sampleDir 'renamed.mp4'
ffmpeg -n -v error -f lavfi -i 'color=c=blue:s=160x90:r=25' -t 1 -c:v mpeg4 -metadata "comment=$case" $first
Copy-Item -LiteralPath $first -Destination $second

# 先提交第一份，再只换文件名。看状态码、ID 和 SHA-256。
$api = 'http://127.0.0.1:15185/api/videos/local'
$saved = Invoke-RestMethod $api -Method Post -Form @{file=(Get-Item -LiteralPath $first)} -StatusCodeVariable code
$code
$saved | Select-Object id, filename, sha256
$duplicate = Invoke-RestMethod $api -Method Post -Form @{file=(Get-Item -LiteralPath $second)} -StatusCodeVariable code
$code
$duplicate | Select-Object id, filename, sha256

# 用第一次响应的 ID 查询，确认服务器仍能找到已发布对象。
Invoke-RestMethod "$api/$($saved.id)"
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 提交新内容 | | HTTP 201，有稳定 ID 和 SHA-256 | | | |
| 同字节换文件名 | | HTTP 200，ID、摘要和原文件名不变 | | | |
| 从列表刷新后读回 | | HTTP 200，原记录仍在，分辨率 160×90 | | | |

15185 不通先核对 Java 18097 与代理。若 503 且错误码是 `VIDEO_STORAGE_UNAVAILABLE`，检查 V16、媒体目录和文件系统是否支持硬链接。若是 `VIDEO_PROBE_UNAVAILABLE`，检查 Java 进程的 FFprobe 路径。走读 [LocalVideoStorage](../../server/src/main/java/dev/citeweave/video/local/LocalVideoStorage.java) 与[唯一约束](../../server/src/main/resources/db/migration/V16__record_local_videos.sql)。迁移问题是「文件已经写完但数据库提交超时，下一次上传为什么能继续？如果两次上传真的同时发生，谁保证只有一条记录？」实测及解释写回表格。

## 实验 37 · 字幕时间超出本地视频会怎样

**预计 15 分钟。** 使用带 V17 的独立 Web 15186 和 Java 18098。在仓库根目录打开 PowerShell 7。独立库需已有一条 1 秒的合成视频。实验只新增一条有效字幕，顺带试一次无效绑定。

```powershell
# 用 API 读取 1 秒视频，记录后面使用的视频 UUID。
$base = 'http://127.0.0.1:15186'
$video = (Invoke-RestMethod "$base/api/videos/local") | Where-Object { $_.metadata.durationMs -eq 1000 } | Select-Object -First 1
if (-not $video) { throw '请先在本地视频页导入一个 1 秒的合成样例。' }
$endpoint = "$base/api/videos/local/$($video.id)/subtitles"

# 创建两个文件，唯一有意义的改动是字幕结束时间。两者使用同一句合成字幕。
$caseId = [guid]::NewGuid().ToString('N')
$path = Join-Path (Resolve-Path .tool-cache).Path "subtitle-lab-$caseId"
New-Item -ItemType Directory -Path $path | Out-Null
$within = Join-Path $path 'within.srt'
$outside = Join-Path $path 'outside.srt'
$cue = "1`n00:00:00,100 --> END`n学习实验 $caseId。`n"
[IO.File]::WriteAllText($within, $cue.Replace('END', '00:00:00,900'))
[IO.File]::WriteAllText($outside, $cue.Replace('END', '00:00:01,001'))

# 首次上传记录 sourceId，之后从旧的字幕接口验证毫秒，而不是从文件名推断。
$saved = Invoke-RestMethod $endpoint -Method Post -Form @{file=(Get-Item -LiteralPath $within)} -StatusCodeVariable status
$status
Invoke-RestMethod "$base/api/sources/$($saved.sourceId)/transcript"
# 只替换结束区间。预期失败后，前一条轨道仍在列表里。
Invoke-RestMethod $endpoint -Method Post -Form @{file=(Get-Item -LiteralPath $outside)} -SkipHttpErrorCheck -StatusCodeVariable status
$status
Invoke-RestMethod $endpoint
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 上传 100–900 ms 轨道 | | HTTP 201，读回同一毫秒区间 | | | |
| 只把结束时间改到 1001 ms | | HTTP 422 `SOURCE_SUBTITLE_OUTSIDE_VIDEO`，原轨道仍可打开 | | | |

若 15186 无响应，先查 18098 是否监听以及是否应用 V17。如果返回 404，先核对视频 ID 是否来自本轮环境。走读 [LocalVideoSubtitleService](../../server/src/main/java/dev/citeweave/source/LocalVideoSubtitleService.java) 和 [V17 关系](../../server/src/main/resources/db/migration/V17__bind_local_video_subtitles.sql)。迁移问题是「删除绑定写入前的行锁，还剩哪些数据库保证？冲突时字幕正文能不能孤立留下？」将实测与解释填写在表里。

## 实验 38 · 生成 VTT 需要哪些输入

**预计 15–20 分钟。** 仓库根目录，PowerShell 7。使用本轮 Python 版本、已准备好的本机 Whisper 模型和 FFmpeg/FFprobe。可复用开发验收目录 `.tool-cache/asr-paddlespeech-zh` 中的官方演示样本 `demo.mp4` 和去掉音轨的 `no-audio.mp4`。只运行命令无需启动服务。想验证导入时，再打开隔离 Web 15186。本实验只把结果写到新目录，不改动素材及模型。

```powershell
# 单独的 Python 环境与现有服务隔离。设置 UTF-8 以便终端正确显示中文。
$cache = (Resolve-Path .tool-cache).Path
$env:UV_PROJECT_ENVIRONMENT = Join-Path $cache 'asr-locked-env'
$env:UV_CACHE_DIR = Join-Path $cache 'uv'
$env:PYTHONPATH = (Resolve-Path ai/src).Path
$env:HF_HUB_OFFLINE = '1'
$env:PYTHONUTF8 = '1'
$fixtures = Join-Path $cache 'asr-paddlespeech-zh'
$model = Join-Path $env:USERPROFILE '.cache/huggingface/hub/models--Systran--faster-whisper-small/snapshots/536b0662742c02347bc0e980a01041f333bce120'
$id = [guid]::NewGuid().ToString('N')
$good = Join-Path $fixtures "lab-$id"
$bad = Join-Path $fixtures "lab-no-audio-$id"

# 4 线程、两轮推理。打开报告核对毫秒片段和实时率，样本不上传给模型供应商。
uv run --directory ai --locked --no-default-groups --group asr python -m citeweave_ai.local_asr (Join-Path $fixtures 'demo.mp4') --model-dir $model --output $good --threads 4 --runs 2 --reference (Join-Path $fixtures 'reference.txt')
Get-Content -LiteralPath (Join-Path $good 'report.json')

# 只换成相同画面但没有音轨的视频。核对退出码与是否留下产物。
uv run --directory ai --locked --no-default-groups --group asr python -m citeweave_ai.local_asr (Join-Path $fixtures 'no-audio.mp4') --model-dir $model --output $bad --threads 4 --runs 2
$LASTEXITCODE
Test-Path -LiteralPath $bad
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 转写含中文音轨的样本 | | 生成 VTT 与报告，片段在视频范围内。报告有两轮 RTF | | | |
| 只移除音轨 | | 提示无法抽取第一条音轨、退出码 1、无输出目录 | | | |

若模型目录不完整，先核对 `model.bin`、`config.json` 和 `tokenizer.json`。若失败在抽轨，先用 `ffprobe` 确认文件有音轨并检查 FFmpeg 是否在 PATH。走读 [local_asr.py](../../ai/src/citeweave_ai/local_asr.py)。迁移问题是「为什么计时必须遍历所有片段？如果只计调用 transcribe 的时间，会漏算哪部分？」将实测填入上表。

## 实验 39 · 后台转写重复提交会不会多写字幕

**预计 15–20 分钟。** 从仓库根目录打开 PowerShell 7。启用 V18 的 Web 15187、Java 18099、FFmpeg 与已配置的本机模型。使用实验 38 已准备的 `.tool-cache/asr-paddlespeech-zh/demo.mp4`。本实验给合成视频加唯一元数据，只在本轮独立环境保留一条新媒体记录和一条机器字幕。

```powershell
# 用 -c copy 保持音轨和画面不变，唯一注释让本次实验得到新的媒体 ID。
$fixture = Join-Path (Resolve-Path .tool-cache).Path 'asr-paddlespeech-zh'
$key = [guid]::NewGuid().ToString('N')
$file = Join-Path $fixture "asr-lab-$key.mp4"
ffmpeg -n -v error -i (Join-Path $fixture 'demo.mp4') -map 0 -c copy -metadata "comment=$key" $file
$base = 'http://127.0.0.1:15187'
$video = Invoke-RestMethod "$base/api/videos/local" -Method Post -Form @{file=(Get-Item -LiteralPath $file)}
$task = "$base/api/videos/local/$($video.id)/transcription"

# 第一次排队后立即重复提交同一 URL。记录两次 HTTP 202 和 videoId。
Invoke-RestMethod $task -Method Post -StatusCodeVariable code
$code
Invoke-RestMethod $task -Method Post -StatusCodeVariable code
$code

# 轮询到 SUCCEEDED 后查看状态与轨道，只应有一条 MACHINE_ASR。
Invoke-RestMethod $task
Invoke-RestMethod "$base/api/videos/local/$($video.id)/subtitles"
```

也可从「本地视频 → 管理字幕」打开对应文件。任务完成后刷新，检查「打开机器字幕」还在，轨道有时间戳。若任务尚未结束，等几秒后重复最后两条命令。

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 首次提交 | | HTTP 202，同一个视频任务从排队走向终态 | | | |
| 立刻重复同一提交 | | HTTP 202、相同视频 ID；最终尝试次数仍为 1，只有一个机器轨道 | | | |
| 刷新并打开轨道 | | `SUCCEEDED`，资料来源显示机器转写，能打开毫秒时间轴 | | | |

若状态 `available=false`，先核对 18099 的环境开关、Python 和模型快照路径。若失败码为 `ASR_PROCESS_FAILED`，用实验 38 的命令在同一个 Python 环境复跑，区分模型与任务错误。走读 [LocalVideoAsrService](../../server/src/main/java/dev/citeweave/video/local/LocalVideoAsrService.java) 的发布事务。迁移问题是「如果 worker 推理完时已经超期，哪里会让新字幕一起回滚？」在表里保留实测。

## 实验 40 · 同一份资料为什么只建一个词面索引

**预计 15 分钟。** 在仓库根目录开 PowerShell 7。使用 V19 的 Web 15188、Java 18100 和独立 PostgreSQL。实验新增一份合成文本，两次查询只建立一代派生索引。数据库查询是只读。

```powershell
# 新建只用于实验的文字资料，取得本次唯一的正整数 ID。
$root = 'http://127.0.0.1:15188'
$title = "索引实验 " + [guid]::NewGuid().ToString('N')
$source = Invoke-RestMethod "$root/api/sources" -Method Post -ContentType 'application/json; charset=utf-8' -Body (
  @{title=$title; content="Redis 缓存失效后回填。`n`n监控只记录容量。"} | ConvertTo-Json
)
$id = [long]$source.source.id

# 同一个问题查两遍，核对证据 ID 和段号是否一致。
$positive = @{sourceIds=@($id); query='缓存失效'} | ConvertTo-Json
$first = Invoke-RestMethod "$root/api/source-evidence/search" -Method Post -ContentType 'application/json; charset=utf-8' -Body $positive
$second = Invoke-RestMethod "$root/api/source-evidence/search" -Method Post -ContentType 'application/json; charset=utf-8' -Body $positive
$first[0] | Select-Object evidenceId, sourceVersionId, paragraph
$second[0] | Select-Object evidenceId, sourceVersionId, paragraph

# 只替换检索词，预期 JSON 为空数组。
$negative = @{sourceIds=@($id); query='海洋珊瑚'} | ConvertTo-Json
(Invoke-WebRequest "$root/api/source-evidence/search" -Method Post -ContentType 'application/json; charset=utf-8' -Body $negative).Content

# 只查本次资料的已提交索引，预期只有一行。
docker compose -p citeweave-summary-qa -f infra/compose.yaml exec -T postgres psql -U citeweave_summary_qa -d citeweave_lexical_index_ui -c "SELECT source_id,index_version,count(*) FROM lexical_evidence_generations WHERE source_id=$id GROUP BY source_id,index_version"
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 连续两次检索「缓存失效」 | | 证据 ID 与资料版本相同，段号 1，索引只有一代 | | | |
| 只替换为「海洋珊瑚」 | | HTTP 200 与空数组 `[]`，索引仍为一代 | | | |

15188 无响应先确认 18100 监听及 Web 代理。查证据有结果却没有版本行时，核对 V19 和 `CITEWEAVE_LEXICAL_INDEX_ENABLED`。走读 [EvidenceLexicalIndex](../../server/src/main/java/dev/citeweave/source/EvidenceLexicalIndex.java) 的事务。迁移问题是「为什么版本行和片段要在一个事务里提交？后到的检索如何知道先到的检索已经构建完成？」把结果写回表格。

## 实验 41 · 同义改写换成向量以后，查到了什么

**预计 15–20 分钟。** 在仓库根目录打开 PowerShell 7。使用 Java 21、Maven、锁定的 Python 实验环境与本机 BGE 模型。只读合成题集，不启动数据库或应用，不调用云模型。报告写入 `.tool-cache` 下新文件。

```powershell
# 用 Java 的生产切片和词面排序导出两套冻结题，避免比较不同原文。
mvn.cmd -o -B -f server/pom.xml '-Dtest=TranscriptRetrievalBenchmarkTest' test
$original = (Resolve-Path server/target/transcript-dense-original-input.json).Path
$holdout = (Resolve-Path server/target/transcript-dense-holdout-input.json).Path
$cache = (Resolve-Path .tool-cache).Path
$env:UV_PROJECT_ENVIRONMENT = Join-Path $cache 'retrieval-locked-env'
$env:UV_CACHE_DIR = Join-Path $cache 'uv'
$env:PYTHONPATH = (Resolve-Path ai/src).Path
$env:PYTHONUTF8 = '1'
$model = Join-Path $cache 'bge-small-zh-46fbe35fd4374a00fee7de77dfddaeb6dd6a2c59'
$run = [guid]::NewGuid().ToString('N')

# 只改变候选上限。两轮均用同一模型、两套资料、4 个 CPU 推理线程。
foreach ($limit in 5,2) {
    $output = Join-Path $cache "dense-lab-$run-top$limit.json"
    uv run --directory ai --offline --locked --no-default-groups --group retrieval python -m citeweave_ai.evaluate_dense --model-dir $model --input $original --input $holdout --top-k $limit --output $output
    $output
}
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 比较 Top 5 的原题与保留集 | | 维度 512；完全命中词面 23/30 与 3/8，向量 29/30 与 7/8 | | | |
| 只将候选减到 Top 2 | | 向量完全命中 28/30 与 6/8，检查新增漏题 P03-P 和 H04 | | | |
| 在报告看无答案题 | | 两套共 16 道，无答案也出现向量候选 | | | |

如果找不到模型，检查 `tokenizer.json`、`model_optimized.onnx`，并参照[固定仓库版本](https://huggingface.co/Qdrant/bge-small-zh-v1.5/tree/46fbe35fd4374a00fee7de77dfddaeb6dd6a2c59)下载。若依赖缓存缺失，先按[开发环境](../development/DEVELOPMENT.md)准备。代码走读 [evaluate_dense.py](../../ai/src/citeweave_ai/evaluate_dense.py) 的候选 Scope、命中计分与 RRF。迁移问题是「向量最相似为什么不等于问题能回答？哪一组观察支持这个判断？」将实测填入表格。

## 实验 42 · pgvector 会不会选中范围外的最相似片段

**预计 15 分钟。** 从仓库根目录打开 PowerShell 7。启动 Docker Desktop，复用实验 41 的 BGE 模型和 Java 导出文件。本实验只在回环 15436 的独立库用临时表，正式库无需启动。

```powershell
# 新启动指定名称的实验库，等待扩展可用。
docker compose -f infra/compose.vector-lab.yaml up -d --wait
mvn.cmd -o -B -f server/pom.xml '-Dtest=TranscriptRetrievalBenchmarkTest' test
$cache = (Resolve-Path .tool-cache).Path
$env:UV_CACHE_DIR = Join-Path $cache 'uv'
$env:UV_PROJECT_ENVIRONMENT = Join-Path $cache 'vector-lab-env'
$env:PYTHONPATH = (Resolve-Path ai/src).Path
$model = Join-Path $cache 'bge-small-zh-46fbe35fd4374a00fee7de77dfddaeb6dd6a2c59'
$inputFile = (Resolve-Path server/target/transcript-dense-original-input.json).Path
$report = Join-Path $cache ("pgvector-lab-" + [guid]::NewGuid() + '.json')

# 写入 512 维片段和四条范围外的相同向量，检查排序一致和错维拒绝。
uv run --directory ai --locked --no-default-groups --group vector-lab python -m citeweave_ai.evaluate_pgvector --model-dir $model --input $inputFile --output $report
(Get-Content -LiteralPath $report -Raw | ConvertFrom-Json).results | Select-Object matched, scopeDecoys, wrongDimensionSqlState
```

| 操作 | 我的预测 | 通过条件 | 我的实测输出 | 我的解释 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 同范围内使用精确余弦 | | 42/42 排序一致，误差小于 10⁻⁶ | | | |
| 单独改变干扰项的一个范围字段 | | 四种干扰取各自范围时可读，原范围未混入 | | | |
| 仅降低向量维度 | | 拒绝写入，SQLSTATE 22000 | | | |

若 Docker 或扩展不可用，先看 `docker compose -f infra/compose.vector-lab.yaml ps` 和日志。若向量维度异常，核对模型目录与实验 41 的模型指纹。走读 [evaluate_pgvector.py](../../ai/src/citeweave_ai/evaluate_pgvector.py)。迁移问题是「为什么要先过滤版本再按距离排序？」将结果填入表格。

## 实际记录模板

| 项目 | 填写内容 |
| --- | --- |
| 实验与日期 | 例如实验 01，实际执行日期 |
| 运行版本与入口 | `git rev-parse --short HEAD`、端口、使用的样例与任何改动 |
| 第一次观察 | 请求或操作、状态、错误码及核心字段 |
| 对照观察 | 改变的唯一条件、响应或数据变化 |
| 与预期的差异 | 写出差异及排查步骤 |
| 自己的解释 | 关联到具体的代码或配置，列出仍不确定的问题 |
