# CiteWeave 接口文档

本页记录已实现的 HTTP 端点。示例数据用于说明返回形状。请求回复格式及错误字段的通用约定见[接口规范](API_CONTRACT.md)。

## 服务与接口目录

| 服务与入口 | 方法与路径 | 用途 |
| --- | --- | --- |
| Java Core · 8087，或经 Vite 5173 | `GET /api/sources` | 查询资料列表 |
| Java Core | `GET /api/sources/{id}` | 读取资料详情和正文 |
| Java Core | `GET /api/sources?favorite=true` | 只列已收藏资料 |
| Java Core | `GET/PUT /api/sources/{id}/favorite` | 读取或设置收藏状态 |
| Java Core | `GET /api/sources/{id}/transcript` | 读取字幕时间轴 |
| Java Core | `GET /api/sources/{id}/summary` | 读取已保存字幕摘要 |
| Java Core | `POST /api/sources/{id}/summary` | 生成或复用字幕摘要 |
| Java Core | `POST /api/sources/{id}/summary-task`、`GET /api/summary-tasks` | 将已有字幕交给后台并浏览任务 |
| Java Core | `GET /api/summary-tasks/{id}`、`POST /api/summary-tasks/{id}/retry` | 查看或重试后台摘要 |
| Java Core | `POST /api/sources/{id}/questions` | 在一份字幕内检索并回答 |
| Java Core | `POST /api/transcript-questions` | 对选中的字幕资料问答 |
| Java Core | `GET /api/source-questions/sources` | 列出可参与跨资料问答的资料 |
| Java Core | `POST /api/source-questions` | 对选中的字幕、段落或 PDF 混合问答 |
| Java Core | `POST /api/research` | 发起单次只读研究，返回轨迹和可跳转引用 |
| Java Core | `GET /api/research`、`GET /api/research/{id}` | 浏览并重开近期研究记录 |
| Java Core | `POST /api/research/{id}/note`、`GET /api/research-notes` | 保存研究笔记和查看列表 |
| Java Core | `GET/PUT /api/research-notes/{id}`、`GET /api/research-notes/{id}/export/markdown` | 读回、编辑与导出研究笔记 |
| Java Core | `POST /api/source-evidence/search` | 在所选资料中查找原文候选 |
| Java Core | `GET /api/sources/{id}/evidence` | 精确读回当前版本的一条证据 |
| Java Core | `GET /api/sources/{id}/transcript/evidence` | 校验引用并定位字幕原句 |
| Java Core | `GET /api/sources/{id}/paragraphs` | 读取版本化文本段落 |
| Java Core | `GET /api/sources/{id}/pdf-pages` | 读取 PDF 页码证据 |
| Java Core | `POST /api/sources/{id}/document-questions` | 文本问答与段落引用 |
| Java Core | `POST /api/sources/{id}/summary/note` | 保存或复用摘要笔记 |
| Java Core | `GET /api/notes`、`GET /api/notes/{id}` | 浏览与读取摘要笔记 |
| Java Core | `GET /api/notes/{id}/export/markdown` | 导出笔记和引用 |
| Java Core | `POST /api/sources`，JSON | 从粘贴的文本创建资料 |
| Java Core | `POST /api/sources`，multipart | 上传文件并创建资料 |
| Java Core | `POST /api/video-imports/bilibili/normalize` | 识别并规范化 Bilibili 输入 |
| Java Core | `POST /api/video-imports/bilibili/preview` | 查询公开视频标题与分 P |
| Java Core | `POST /api/video-imports/bilibili/subtitles` | 绑定所选分 P 与手动字幕 |
| Java Core | `POST /api/videos/local/preview` | 检查本地 MP4/MOV 的时长及媒体轨道 |
| Java Core | `POST /api/videos/local`、`GET /api/videos/local`、`GET /api/videos/local/{id}` | 保存、列出和查看本地视频 |
| Java Core | `GET/POST /api/videos/local/{id}/subtitles` | 读取或绑定本地视频的字幕 |
| Java Core | `GET/POST /api/videos/local/{id}/transcription`、`POST /api/videos/local/{id}/transcription/retry` | 查询、提交或重试本地 CPU 转写 |
| Python AI Engine · 8091 | `GET /healthz` | 检查 AI Engine HTTP 进程 |
| Python AI Engine | `POST /internal/v1/research` | 本机受控验收的只读研究运行 |

以上为本机开发端口，启动方式见[开发环境](DEVELOPMENT.md)。Java API 通过本机入口访问，当前为本地开发用途。Python 提供运行检查和仅供 Java 调用的内部字幕摘要端点。

## Java Core

### 本地视频预检

`POST /api/videos/local/preview` 接受 `multipart/form-data`，必填字段为 `file`。以 FFprobe 读取实际 MP4/MOV 容器和视频轨，封面图和纯音频不算视频轨。默认限制为 10 MiB 和 10 分钟。调用返回 `200` 和 `Cache-Control: no-store`，预检请求不产生资料、任务或永久存储。

```json
{"sizeBytes":12412,"durationMs":1000,"width":160,"height":90,"videoCodec":"mpeg4","audioCodec":"aac"}
```

时长为向上取整的整数毫秒，分辨率为视频轨编码尺寸。只返回首条可识别的视频和音频轨，静音视频的 `audioCodec` 为 `null`。

| HTTP | 稳定错误码 | 场景 |
| --- | --- | --- |
| 400 | `REQUEST_INVALID` / `VIDEO_EMPTY` | 缺少文件字段或空文件 |
| 413 | `UPLOAD_TOO_LARGE` / `VIDEO_TOO_LARGE` | 超过 HTTP 上传或预检大小上限 |
| 415 | `VIDEO_FORMAT_UNSUPPORTED` | 扩展名不是 MP4 或 MOV |
| 422 | `VIDEO_INVALID` / `VIDEO_DURATION_EXCEEDED` | 容器无法读取、无有效视频轨或超过时长上限 |
| 503 | `VIDEO_PROBE_BUSY` / `VIDEO_PROBE_UNAVAILABLE` | 并发名额已满，或本地工具/暂存不可用 |
| 504 | `VIDEO_PROBE_TIMEOUT` | FFprobe 超过执行时限 |

### 保存本地视频

`POST /api/videos/local` 使用同一 multipart `file` 字段。Java 对实际提交的字节重新预检，验证成功后保存到服务端媒体目录。请求成功返回 `201 Created` 和指向详情的 `Location`。同一工作区内同内容文件（包含换文件名再上传）返回 `200` 和原记录，保留初次导入的文件名。

返回结构为 `id`（UUID）、`filename`、`sha256`、`createdAt`、`metadata`（同预检结构）。`GET /api/videos/local` 返回最近创建的最多 50 条，`GET /api/videos/local/{id}` 同时检查媒体对象仍存在且大小一致。未知 ID 返回 `404 VIDEO_NOT_FOUND`，数据库或受控存储不可用返回 `503 VIDEO_STORAGE_UNAVAILABLE`。其余失败沿用预检错误。

### 本地视频字幕绑定

`POST /api/videos/local/{id}/subtitles` 接受 `multipart/form-data` 的 `file` 字段，支持 UTF-8 SRT 和 VTT。所有字幕结束时间必须在视频时长以内。创建返回 `201` 及 `Location: /api/sources/{sourceId}`。同一视频重复上传相同格式及解析后正文时返回 `200` 和原轨道，正文变化会生成新轨道。字幕与绑定关系在同一事务提交。

```json
{"sourceId":42,"title":"lesson","filename":"lesson.srt","kind":"SRT"}
```

`GET /api/videos/local/{id}/subtitles` 返回最近 50 条轨道，可由 `sourceId` 进入既有字幕时间轴、摘要、问答和引用接口。列表项与资料详情的 `localVideoOrigin` 都标注 `trackSource`，取值 `USER_UPLOAD` 或 `MACHINE_ASR`。原有用户上传仍为 `USER_UPLOAD`。不存在的视频返回 `404 VIDEO_NOT_FOUND`，超过时长返回 `422 SOURCE_SUBTITLE_OUTSIDE_VIDEO`，解析和文件类型错误沿用资料上传错误。

### 后台转写本地视频

启用本机模型后，`POST /api/videos/local/{id}/transcription` 无请求体，返回 `202`、任务及 `Location`。同一视频只建一条任务，重复提交回读当前状态。任务失败后经 `POST /api/videos/local/{id}/transcription/retry` 重新排队，沿用原视频 ID；非失败状态返回 `409 ASR_TASK_NOT_RETRYABLE`。没有音轨或视频太长返回 `422 ASR_NO_AUDIO` 或 `422 ASR_VIDEO_TOO_LONG`；未启用或模型路径缺失返回 `503 ASR_UNAVAILABLE`。

`GET /api/videos/local/{id}/transcription` 返回 `{available, task}`，无任务时 `task` 为 `null`。任务包含 `videoId,status,attempt,errorCode,sourceId,updatedAt`。状态为 `QUEUED`、`RUNNING`、`FAILED` 或 `SUCCEEDED`。完成后以 `sourceId` 打开机器字幕。超期尝试不能发布，超期转为 `ASR_LEASE_EXPIRED`。执行超时、子进程失败、发布失败分别显示 `ASR_TIMEOUT`、`ASR_PROCESS_FAILED`、`ASR_PUBLICATION_FAILED`。机器轨道和用户轨道分别去重，发布与任务成功状态同时提交。

### 资料字段

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | 数字 | 资料标识，详情路径使用该值 |
| `title` | 字符串 | 资料标题 |
| `kind` | 字符串 | `TEXT`、`TXT`、`MARKDOWN`、`PDF`、`SRT` 或 `VTT` |
| `filename` | 字符串或 `null` | 上传时保存的文件名，粘贴文本为 `null` |
| `createdAt` | 字符串 | 带时区的创建时间 |

### 查询资料列表

`GET /api/sources` 无需请求体或查询参数。返回 `200 application/json` 和资料字段数组，按导入时间倒序排列，无资料时为 `[]`。列表仅返回元数据。可重复读取。

```json
[{"id":42,"title":"学习笔记","kind":"TEXT","filename":null,"createdAt":"2026-11-20T12:00:00Z"}]
```

### 查询资料详情

`GET /api/sources/{id}` 的路径参数为数字资料 ID。成功返回 `200`。查询不存在的资料返回 `404 SOURCE_NOT_FOUND`，ID 格式有误时返回 `400 REQUEST_INVALID`。

```json
{
  "source": {"id":42,"title":"学习笔记","kind":"TEXT","filename":null,"createdAt":"2026-11-20T12:00:00Z"},
  "content": "这里是存储后的正文。"
}
```

### 查询字幕时间轴

`GET /api/sources/{id}/transcript` 成功返回 `200` 和字幕片段数组。用 `sequence` 获取原片段顺序，`startMs`、`endMs` 以毫秒表示。只适用于已保存的 SRT/VTT。资料缺失时返回 `404 SOURCE_NOT_FOUND`，无字幕轨道时返回 `404 SOURCE_TRANSCRIPT_NOT_FOUND`，时间轴内容不合法时返回 `422`。历史资料仍可通过详情接口读取原文。

```json
[{"sequence":1,"startMs":1200,"endMs":5400,"text":"启动时先检查配置。"}]
```

### 粘贴文本创建资料

向 `POST /api/sources` 发送 `application/json`。必填 `title` 与 `content`，标题去掉首尾空白后须为 1–160 字符，正文必填且受长度限制。成功返回 `201 Created`、`Location: /api/sources/{id}`，响应体使用资料详情形状。

```json
{"title":"学习笔记","content":"这里是资料正文。"}
```

常见错误有 `400 REQUEST_INVALID`、`400 SOURCE_TITLE_INVALID`、`422 SOURCE_CONTENT_INVALID`。该请求会创建记录。重复提交可能新增一份资料，网络超时时可先查询列表再决定是否重试。

### 上传文件创建资料

向 `POST /api/sources` 发送 `multipart/form-data`。必填文件字段 `file`，可选 `title`，留空时从文件名推导标题。支持 PDF、TXT、Markdown、SRT 与 VTT。文件上限为 10 MB，PDF 有 500 页上限，扫描件需要先做 OCR 再导入。成功形状与粘贴文本一致。

```powershell
curl.exe -i -F "file=@sample-data/research-captions.srt" http://127.0.0.1:8087/api/sources
```

主要失败有 `400` 请求无效、标题无效或文件不可读，`413 UPLOAD_TOO_LARGE`、`415 SOURCE_FILE_TYPE_UNSUPPORTED`，以及 `422` 正文或字幕不合法。完整映射见[错误码表](API_CONTRACT.md#当前资料端点错误码)。提交会创建记录，重复执行的约束与粘贴文本相同。

## Bilibili 输入规范化

`POST /api/video-imports/bilibili/normalize` 接受 JSON `{"input":"…"}`。支持 BV/AV 编号，`bilibili.com`、`www.bilibili.com`、`m.bilibili.com` 的 `/video/…`，以及 `b23.tv` 短链。允许 HTTP/HTTPS 输入，返回 HTTPS 规范地址。链接的追踪字段和片段标记会移除，`p` 单独保留为正整数分 P 提示。无 `p` 时返回 `null`。

```json
{"input":"https://m.bilibili.com/video/BV1xx411c7mD/?p=2&spm_id_from=example"}
```

`200 application/json` 示例。

```json
{
  "kind": "BV",
  "value": "BV1xx411c7mD",
  "canonicalUrl": "https://www.bilibili.com/video/BV1xx411c7mD",
  "requestedPart": 2
}
```

`kind` 还可为 `AV` 或 `SHORT`。短链返回的 `value` 是短码，`canonicalUrl` 仍为短链。响应仅代表格式已识别，视频存在性和实际分 P 需要由后续平台预览确认。本接口不访问平台或写入资料，可重复调用。

空输入、未知主机、非法路径、认证信息、自定义端口和非法 `p` 返回 `400 BILIBILI_INPUT_INVALID`。无法读取 JSON 时返回 `400 REQUEST_INVALID`。参见 [Java HTTP 测试](../../server/src/test/java/dev/citeweave/video/bilibili/BilibiliInputHttpTest.java)。

## Bilibili 公开元数据预览

`POST /api/video-imports/bilibili/preview` 接受 JSON `{"input":"BV1xx411c7mD"}`。支持 BV/AV 编号和对应视频页，`p` 用于建议选择。未指定 `p` 时建议返回的第一项。本接口每次实时查询平台，可重复请求，不创建资料或处理任务。

`200 application/json` 响应示例（合成夹具）。

```json
{
  "bvid": "BV1xx411c7mD",
  "title": "合成视频样例",
  "parts": [
    {"page": 1, "cid": "9007199254740993", "title": "上篇", "durationSeconds": 61},
    {"page": 2, "cid": "12", "title": "下篇", "durationSeconds": 82}
  ],
  "suggestedPart": 1
}
```

`page` 和 `suggestedPart` 从 1 起算，`durationSeconds` 为整数秒。CID 作为字符串返回，避免 JavaScript 大整数精度丢失。平台分 P 标题为空时，单 P 使用视频标题，多 P 使用分 P 编号，例如 `P2`。

| 状态 | 错误码 | 含义 |
| --- | --- | --- |
| 400 | `REQUEST_INVALID` / `BILIBILI_INPUT_INVALID` | 无法读取 JSON 或输入格式无效 |
| 404 | `BILIBILI_VIDEO_NOT_FOUND` | 平台明确返回视频未找到 |
| 422 | `BILIBILI_SHORT_LINK_UNSUPPORTED` | 短链需先在浏览器打开，从视频页复制完整链接 |
| 422 | `BILIBILI_PART_NOT_FOUND` | `p` 对应的分 P 不在平台返回的列表中 |
| 502 | `BILIBILI_UPSTREAM_UNAVAILABLE` | 平台拒绝、网络失败或响应不符合预览契约 |
| 503 | `BILIBILI_RATE_LIMITED` | 平台返回 HTTP 429 |
| 504 | `BILIBILI_UPSTREAM_TIMEOUT` | 连接或读取超时 |

仅处理匿名访问可用的公开元数据。预览结果用于用户确认，字幕获取与确认后的入库入口待后续交付。见 [Java 预览契约测试](../../server/src/test/java/dev/citeweave/video/bilibili/BilibiliPreviewHttpTest.java)。

## Python AI Engine

`GET /healthz` 无需请求体，正常时返回 `200 application/json`。

```json
{"service":"citeweave-ai","status":"ok"}
```

它反映 HTTP 进程可响应。可以通过 [FastAPI 自带接口页面](http://127.0.0.1:8091/docs) 查看该端点。模型可用性和跨服务业务链路需要各自的验证。

字幕摘要的模型字段见[证据与引用契约](AI_EVIDENCE_CONTRACT.md)。内部实现见下方字幕摘要接口。

## 维护和验证

新增、修改或废弃接口时，在这里补调用入口、必填字段、成功与失败示例、权限及重试约定。同步更新契约测试。现有仓库参考为 [Java HTTP 契约测试](../../server/src/test/java/dev/citeweave/source/SourceHttpContractTest.java)与 [Python 健康检查测试](../../ai/tests/test_health.py)。


## 字幕摘要

`GET /api/sources/{id}/summary` 读取当前字幕版本的摘要，无保存结果时返回 `204`。 `POST` 同路径无请求正文，首次成功生成并保存返回 `200`，再次调用复用已保存结果。并发的首次请求可能分别调用模型，数据库只接受首个有效结果。失败不会创建摘要。重新生成和删除接口待实现。

```json
{
  "summary": "整理要点时保留可回查的出处。",
  "citations": [{
    "evidenceId": "42:example-version:2",
    "sourceId": "42",
    "sourceVersionId": "example-version",
    "locationType": "transcript",
    "startMs": 1000,
    "endMs": 2400,
    "text": "整理要点时保留可回查的出处。"
  }],
  "truncated": false
}
```

示例仅展示字段，实际版本为 SHA-256。引用只接受同一次模型请求中实际发送的片段，引用原句和定位由 Java 填充。

| HTTP | 稳定错误码 | 含义 |
| --- | --- | --- |
| 404 | `SOURCE_NOT_FOUND` / `SOURCE_TRANSCRIPT_NOT_FOUND` | 找不到资料或资料类型不是字幕 |
| 409 | `SUMMARY_SOURCE_CHANGED` | 生成期间资料版本变化且当前版本无结果 |
| 422 | `SUMMARY_EVIDENCE_INSUFFICIENT` | 预算内没有完整可发送的片段 |
| 502 | `SUMMARY_INVALID_CITATION` / `SUMMARY_INVALID_OUTPUT` | 模型引用或输出格式未通过校验 |
| 502 | `SUMMARY_AI_UNAVAILABLE` | AI Engine 或供应商调用失败 |
| 503 | `SUMMARY_MODEL_NOT_CONFIGURED` / `SUMMARY_MODEL_RATE_LIMITED` | 模型配置缺失或上游限流 |
| 504 | `SUMMARY_MODEL_TIMEOUT` | 模型或 AI 请求超时 |

内部 Java–Python 端点为 `POST /internal/v1/transcript-summaries`。输入为 `EvidenceBatch`，输出为 `SummaryDraft` 加 `inputEvidenceIds`。Python 业务失败返回 JSON `{"code":"..."}`，由 Java 规范化为 Problem Details。该端点仅供本机或受控私网的 Java 调用。见[证据与引用契约](AI_EVIDENCE_CONTRACT.md)。

## Bilibili 手动字幕绑定

`POST /api/video-imports/bilibili/subtitles` 使用 multipart，字段为预览返回的 `bvid`、选中的整数 `page`、字符串 `cid` 及用户字幕文件 `file`（UTF-8 SRT/VTT，最多 10 MiB）。Java 向固定平台端点复核分 P 与 CID，随后在一个事务中写入字幕和来源。每次成功上传创建独立资料，标题沿用普通文件导入的文件名规则。

成功返回 `201 Created`，`Location: /api/sources/{id}`，正文为 `{"sourceId":42}`。读取 `GET /api/sources/{id}` 时，多出 `videoOrigin` 字段，包含 `bvid`、`page`、`cid`、`videoTitle`、`partTitle` 和 `trackSource: "USER_UPLOAD"`。独立上传的资料沿用原响应，引用、摘要及字幕时间轴使用同一资料 ID。

- `409 BILIBILI_PART_CHANGED` 表示提交的分 P 与复核结果不一致。页面要求再预览，失败没有写入资料。
- 缺参数或参数格式无效返回 `400 REQUEST_INVALID` 或 `BILIBILI_INPUT_INVALID`。超限、文件扩展名错误及字幕损坏使用现有 `413`、`415` 和 `422` 契约。
- 平台不可用继续返回预览接口的相关错误。服务不会获取平台字幕或判断手动字幕与视频语音是否一致。

## 从字幕摘要保存笔记

`POST /api/sources/{id}/summary/note` 不需要请求正文。Java 从当前资料版本的已发布摘要复制正文、引用 ID、原句和毫秒区间，并记录本机工作区。首次保存返回 `201` 与 `Location: /api/notes/{id}`，同一个工作区与资料版本重复提交返回 `200` 与同一条笔记。并发首次提交由数据库唯一键保护。

`GET /api/notes` 返回笔记头部数组，按保存时间倒序。 `GET /api/notes/{id}` 使用 UUID，返回头部及快照。示例数据仅用于展示格式。

```json
{
  "note": {
    "id": "38ddb4c6-cd53-4dd8-ad13-24430ceb283b",
    "sourceId": 42,
    "title": "研究字幕",
    "createdAt": "2026-11-21T00:00:00Z"
  },
  "snapshot": {
    "summary": "保留原句与时间区间。",
    "citations": [{
      "evidenceId": "42:example-version:1",
      "sourceId": "42",
      "sourceVersionId": "example-version",
      "locationType": "transcript",
      "startMs": 1000,
      "endMs": 2400,
      "text": "保留原句与时间区间。"
    }],
    "truncated": false
  }
}
```

已存在的字幕尚无发布摘要时返回 `409 NOTE_SUMMARY_REQUIRED`。找不到笔记返回 `404 NOTE_NOT_FOUND`，路径 UUID 格式无效返回 `400 REQUEST_INVALID`。资料找不到或类型不是字幕继续使用资料 API 的 404 错误码。当前为本机个人工作区，笔记为不可变摘要快照。编辑、删除、分页和多工作区鉴权随对应功能交付。

## 单字幕资料问答

`POST /api/sources/{id}/questions` 接收 JSON `{"question":"缓存如何失效？"}`。问题去除首尾空白后须为 1–500 字符。Java 只检索该资料版本的字幕，最多传 5 段候选；Python 核验模型引用实际发出的片段，Java 再核验并回填原句和时间。

成功返回 `200`。`ANSWERED` 包含 `answer` 和使用摘要原有位置字段的非空 `citations`。无词匹配或模型判断证据不够时返回 `{"status":"INSUFFICIENT_EVIDENCE","answer":null,"citations":[]}`。无词匹配会跳过模型。只匹配关键词，不保证同义改写召回或语义支持可靠。回答与问题随请求使用，当前无会话历史或持久化。

Java 可按部署配置开启 `citeweave.question.alias-fallback=true`。该实验开关默认关闭；启用后仅在词面没有候选时补召回已配置的同义术语，原有响应和引用校验保持一致。

| HTTP | 稳定错误码 | 含义 |
| --- | --- | --- |
| 400 | `QUESTION_INVALID` / `REQUEST_INVALID` | 问题无效或请求格式不合法 |
| 404 | `SOURCE_NOT_FOUND` / `SOURCE_TRANSCRIPT_NOT_FOUND` | 无资料或无字幕 |
| 422 | `QUESTION_EVIDENCE_INSUFFICIENT` | 候选无法放入模型证据预算 |
| 502 | `QUESTION_INVALID_CITATION` / `QUESTION_INVALID_OUTPUT` / `QUESTION_AI_UNAVAILABLE` | 引用越界、模型格式错误或上游不可用 |
| 503 | `QUESTION_MODEL_NOT_CONFIGURED` / `QUESTION_MODEL_RATE_LIMITED` | 模型未配置或限流 |
| 504 | `QUESTION_MODEL_TIMEOUT` | 模型请求超时 |

Java 调用内部 `POST /internal/v1/transcript-questions`，输入为 `question` 和 `segments`。模型结果的 `evidenceIds` 与 Python 实际发送的 `inputEvidenceIds` 由 Java 复查。参见[模型证据契约](AI_EVIDENCE_CONTRACT.md)。

## 资料收藏

`GET /api/sources?favorite=true` 返回当前工作区的已收藏资料，字段和排序与默认列表相同。未传 `favorite` 或传 `false` 时仍返回全部资料。空结果为 `[]`。

- `GET /api/sources/{id}/favorite` 返回 `200 {"favorited":false}` 或 `200 {"favorited":true}`。
- `PUT /api/sources/{id}/favorite` 接收 `{"favorited":true}` 或 `{"favorited":false}`，返回 `200` 和保存后的状态。只接受 JSON 布尔值，缺失或其他类型返回 `400 SOURCE_FAVORITE_INVALID`，请求格式无法解析返回 `400 REQUEST_INVALID`。资料不存在返回 `404 SOURCE_NOT_FOUND`。
- 同一资料和工作区只有一条状态记录。相同目标的重复提交得到相同的收藏状态。不同目标的并发请求以数据库实际写入顺序为准。取消只更新收藏字段，原资料、摘要与笔记仍可打开。当前作用域为本机个人空间。

## 笔记 Markdown 导出

`GET /api/notes/{id}/export/markdown` 使用与笔记详情相同的工作区读取规则。成功返回 `200`、`Content-Type: text/markdown;charset=UTF-8`、`Cache-Control: no-store` 和 `Content-Disposition: attachment; filename="citeweave-note-{id}.md"`。导出读取已保存的快照，包含标题、摘要、资料标识和版本、每条证据的原句及毫秒区间。模型文本、用户标题及字幕使用 Markdown 字面文本块。文件可离线读，定位字段供回到原资料核对。

不存在或当前空间不可读的笔记返回 `404 NOTE_NOT_FOUND`，无效 UUID 返回 `400 REQUEST_INVALID`。导出无需模型，也不写数据库或外部工作区。

## 跨字幕问答与证据回跳

`POST /api/transcript-questions` 接收 JSON，例如 `{"sourceIds":[42,61],"question":"两份字幕如何解释租约？"}`。只接受 2–3 个互不重复的正整数资料 ID。资料必须存在且为字幕；工作区沿用本机资料访问边界。无效选择返回 `400 QUESTION_SOURCE_IDS_INVALID`。其余返回字段及模型错误语义沿用单字幕问答。

Java 在每份选定字幕中独立召回，再按提交顺序轮流取证据，每份最多两段，总数最多六段。无匹配时返回 `200 INSUFFICIENT_EVIDENCE`，跳过模型。有匹配时，Python 接收问题与候选，并保留各候选的 `sourceId`。即使两条证据处于同一毫秒范围，其资料和版本标识仍不同。未选资料的引用会返回 `502 QUESTION_INVALID_CITATION`。回答随请求返回，当前没有持久化或会话历史。

`GET /api/sources/{id}/transcript/evidence?evidenceId={id}` 按资料 ID、当前版本和完整证据 ID 读取一段字幕。成功返回 `200` 和原有证据字段。资料不存在或非字幕沿用相应 404；引用属于其他资料、旧版本或不存在的片段时返回 `404 SOURCE_EVIDENCE_NOT_FOUND`。用于笔记链接和跨资料问答的时间轴定位。

## 文本段落与问答

`GET /api/sources/{id}/paragraphs` 返回粘贴文本、TXT、Markdown 的正文段落，按空行分段，段号从 1 开始。Markdown 按源文本显示。返回的证据包含 `locationType:"paragraph"`、`paragraph`、`text`、`sourceId`、`sourceVersionId` 和 `evidenceId`，省略 `startMs`、`endMs`。证据 ID 绑定资料、`paragraph-v1` 正文版本和段号。PDF 通过下文独立的页码接口读取。

`POST /api/sources/{id}/document-questions` 接收 `{"question":"缓存如何失效？"}`，问题校验、证据不足响应和模型错误沿用字幕问答。关键词检索最多选 5 段，跳过超过 1800 UTF-16 单元的单个段落；它们仍可正常阅读。返回的引用由 Java 从当前段落回填，页面按完整证据 ID 定位，回答不保存。

资料不存在返回 `404 SOURCE_NOT_FOUND`；非支持类型返回 `404 SOURCE_PARAGRAPHS_NOT_FOUND`。原有字幕时间轴和问答位置字段不变。

## PDF 页码引用

V6 后新导入的文本层 PDF 同时保存合并正文与逐页提取文本。导入校验沿用已有规则。`GET /api/sources/{id}/pdf-pages` 返回可引用的文本片段，字段为 `locationType:"page"`、`page`、`text`、`sourceId`、`sourceVersionId` 和 `evidenceId`。空白页不生成证据，后续页保持物理页码。从同一页分出的长文本片段有不同证据 ID，但共用原页码。资料详情仍可读取合并正文。

`POST /api/sources/{id}/document-questions` 同时接受有页码证据的 PDF。回答引用使用真实页码，点击后定位到该页提取的原文片段。历史 PDF 只有合并正文时不会推断页码，页码及问答端点返回 `404 SOURCE_PDF_PAGES_UNAVAILABLE`。历史资料仍可读取，重新上传原 PDF 可生成新资料的页码证据。当前未接入 OCR、页面图像或 PDF 原件查看。

## 跨资料问答

`GET /api/source-questions/sources` 返回 `200` 和与资料库相同结构、排序的资料列表。候选包括 SRT、VTT、粘贴文本、TXT、Markdown 和已有页码证据的 PDF。历史 PDF 可从资料库查看。

`POST /api/source-questions` 接收 `{"sourceIds":[42,61,73],"question":"Cache rebuild"}`。必须显式选择 2–3 个不同的正整数资料 ID，问题限 1–500 个字符。Java 验证全部资料后，对每份资料分别做词面召回，先按选择顺序取每份有命中的首条，再取各份第二条，总数最多 6 条。没有命中时返回 `200 INSUFFICIENT_EVIDENCE`，跳过模型。模型预算可进一步缩短候选。选中某份资料并不保证有相关证据可引用。

有据回答返回 `200`，结构为 `{"status":"ANSWERED","answer":"…","citations":[…]}`。每条引用都包含 `sourceId`、`sourceVersionId`、`evidenceId`、`text` 和 `locationType`。三种位置分别使用 `transcript` 的 `startMs`、`endMs`，`paragraph` 的 `paragraph`，以及 `page` 的 `page`。页面将证据 ID 写入资料链接，按当前版本给出的正文片段定位；PDF 跳到资料中的该页文本。跨资料回答随请求返回，无会话持久化。

无效选择返回 `400 QUESTION_SOURCE_IDS_INVALID`，空白或超限问题返回 `400 QUESTION_INVALID`，不存在的资料返回 `404 SOURCE_NOT_FOUND`，历史 PDF 返回 `404 SOURCE_PDF_PAGES_UNAVAILABLE`。模型越界引用返回 `502 QUESTION_INVALID_CITATION`。其余模型错误沿用单资料问答。`POST /api/transcript-questions` 仍只接受字幕。

## 查找和读取证据

- `POST /api/source-evidence/search` 接收 `{"sourceIds":[42,61],"query":"Cache rebuild"}`。查询必须选择 1–3 个不同的正整数资料 ID，检索词限 1–500 个字符。Java 使用跨资料问答相同的词面候选排序，按所选资料轮流取结果，每份最多两条。成功返回 `200` 和证据数组，无命中返回 `[]`。这些是生成前的候选，模型预算可能进一步缩减问答实际使用的证据。
- 开启 `CITEWEAVE_LEXICAL_INDEX_ENABLED` 时，查证据、混合问答与研究工具共享 PostgreSQL 词面索引。首次检索可写入派生索引，原文与证据 ID 保持兼容，后续检索按资料当前版本读索引。引用回读仍由原文接口验证资料与证据版本。
- `GET /api/sources/{id}/evidence?evidenceId={evidenceId}` 使用检索结果的 `sourceId` 作为路径 ID，并原样传入完整 `evidenceId`。成功返回 `200` 和当前版本的单条证据，字段与跨资料问答引用一致。另一个资料、旧版本、缺失的片段或超过 160 字符的 ID 返回 `404 SOURCE_EVIDENCE_NOT_FOUND`；有效 ID 形式但资料不存在时返回 `404 SOURCE_NOT_FOUND`。
- 搜索选择无效返回 `400 EVIDENCE_SOURCE_IDS_INVALID`，检索词无效返回 `400 EVIDENCE_QUERY_INVALID`。不存在的所选资料返回 `404 SOURCE_NOT_FOUND`，缺页码的历史 PDF 返回 `404 SOURCE_PDF_PAGES_UNAVAILABLE`。搜索会先校验所有选中的资料。两个端点都不调用模型或写入数据，适用当前本机资料访问边界。

## 只读研究内部接口

`POST /internal/v1/research` 接收 `{"sourceIds":[42,61],"question":"Compare conditions for Cache rebuild"}`。限 1–3 个不同的正整数资料 ID，问题去除外侧空白后限 1–500 字符。非法请求返回 FastAPI 的 `422` 校验响应。

模型通过 Chat Completions 在 `search_evidence`、`read_evidence` 中提出调用。搜索范围来自调用方，模型只传查询词。读取时必须照抄本轮搜索返回的资料和证据 ID；Python 再向 Java 读取，并核对当前版本、原句和定位。搜索只回传每条原句的前 240 个字符作预览。最多执行 3 次工具、请求 4 次模型；整体超时 25 秒、模型单次输出上限 800 tokens、消息及工具定义序列化 UTF-8 上限 32,000 字节。

```json
{
  "status": "ANSWERED",
  "answer": "示例回答",
  "evidenceIds": ["42:version:2"],
  "trace": [
    {"callId":"call_search","tool":"search_evidence","evidenceIds":["42:version:2"]},
    {"callId":"call_read","tool":"read_evidence","evidenceIds":["42:version:2"]}
  ]
}
```

证据不足时返回 `200`、`status:"INSUFFICIENT_EVIDENCE"`、`answer:null` 和 `evidenceIds:[]`。执行错误返回 `{code,trace}`，轨迹仅包含已完成步骤。未知工具、非法参数、重复调用 ID、范围外证据、证据变化、非法引用和步数超限返回 `502`，对应 `RESEARCH_TOOL_NOT_ALLOWED`、`RESEARCH_TOOL_ARGUMENTS_INVALID`、`RESEARCH_TOOL_DUPLICATE`、`RESEARCH_EVIDENCE_OUT_OF_SCOPE`、`RESEARCH_EVIDENCE_CHANGED`、`RESEARCH_CITATION_INVALID`、`RESEARCH_STEP_LIMIT`。Java 资料或证据不可用返回 `404 RESEARCH_EVIDENCE_UNAVAILABLE`，全程超时返回 `504 RESEARCH_TIMEOUT`。默认关闭返回 `503 RESEARCH_DISABLED`。

启用方式见[开发环境](DEVELOPMENT.md#本机只读研究验收)。接口适用于可信本机调用方。正式 Web 通过 Java 预检资料范围后调用本接口。会话鉴权、检查点与恢复待后续接入。

## Web 单次只读研究

`POST /api/research` 接收 `{"sourceIds":[42,61],"question":"Compare conditions for Cache rebuild"}`。接受 1–3 个不同的正整数资料 ID 和 1–500 字符的问题。Java 预检所有所选资料是否存在且有可引用类型，之后才向已配置的 Python 发起一次研究请求。

成功返回 `200` 和 `status`、`answer`、`citations`、`trace`。轨迹包含已完成的搜索或读取步骤，字段为 `callId`、`tool`、`evidenceIds`。Java 核验轨迹和最终引用范围，为已回读的证据从当前版本回填原句、毫秒、段号或页码。证据不足返回 `INSUFFICIENT_EVIDENCE`，答案为 `null`，引用数组为空。两个正常终态会保存记录，响应追加 `record:{id,question,createdAt}` 和 `sourceIds`。失败时只返回错误与本轮已完成的轨迹。

| HTTP | `code` | 行为 |
| --- | --- | --- |
| 400 | `RESEARCH_REQUEST_INVALID` | 修正问题或资料选择 |
| 400 | `RESEARCH_REQUEST_KEY_INVALID` | 提供标准 UUID 格式的请求键 |
| 404 | `RESEARCH_SOURCE_UNAVAILABLE`、`RESEARCH_EVIDENCE_UNAVAILABLE` | 重新核对资料及其原文 |
| 409 | `RESEARCH_REQUEST_IN_PROGRESS` | 稍后用同一键重试或查看最近研究 |
| 409 | `RESEARCH_REQUEST_OUTCOME_UNKNOWN` | 查看最近研究，明确重新运行时使用新键 |
| 409 | `RESEARCH_REQUEST_KEY_CONFLICT`、`RESEARCH_REQUEST_PREVIOUS_FAILED` | 修改问题或明确重跑时使用新的键 |
| 503 | `RESEARCH_DISABLED`、`RESEARCH_NOT_CONFIGURED`、`RESEARCH_RATE_LIMITED` | 检查本机开关或供应商状态 |
| 504 | `RESEARCH_TIMEOUT` | 缩小范围后再试 |
| 502 | `RESEARCH_EVIDENCE_OUT_OF_SCOPE`、`RESEARCH_TOOL_REJECTED`、`RESEARCH_STEP_LIMIT` | 研究中止，响应保留已完成且可复核的轨迹 |
| 502 | `RESEARCH_CITATION_INVALID`、`RESEARCH_INVALID_RESPONSE`、`RESEARCH_ENGINE_UNAVAILABLE` | 检查服务日志和模型配置 |

研究业务错误返回 Problem Details，扩展字段 `trace` 为经 Java 核验的已完成步骤。非法上游轨迹不会传给浏览器。当前适用本机个人资料访问边界；会话鉴权和运行恢复待接入。

**请求去重。** 可发送请求头 `Idempotency-Key: 1c501e40-809a-4b03-a14e-c4ed24021023`。V8 在同一工作区按 UUID 认领请求，并绑定去除首尾空白后的问题和资料 ID 顺序。一条正在执行的请求只允许原认领者调用模型。正常完成后，重发相同请求返回同一个记录 ID，按当前资料版本回填；响应丢失也可使用原键重试。同键改写问题或变更资料及其顺序会冲突。已知失败关闭原键，重新执行使用新键。没有请求头的旧调用仍按独立执行处理。

Web 会在当前页面发生网络失败或收到处理中响应时保留请求键。页面刷新后可在「最近研究」找回已落库结果。V9 为有键请求设定 PostgreSQL 认领后 60 秒的截止时间。超过截止时间后，原执行无法发布结果；同键查询发现过期时将请求记为结果未知。上游超时或通信失败也会将有键请求标为结果未知，因为无法确认上游是否接受调用。原键保持 409，显式重跑需要新键。当前未实现自动接管和步骤恢复。无键请求保留原有超时错误语义。

**已确认步骤。** V10 在有键研究中为每个已完成的工具先保存步骤，再让模型继续。网络故障、请求仍在运行或同键重试返回 409 时，`trace` 可读回 PostgreSQL 已确认的步骤。失去模型响应不会抹掉这些步骤。检查点仅包含调用 ID、工具名和证据 ID，不能续跑模型对话。

**内部回调。** Python 从 Java 内部请求中的 `checkpoint:{requestKey,token}` 获取回调授权。每次执行后向 Java `POST /internal/v1/research/{requestKey}/checkpoints`，请求头 `X-CiteWeave-Checkpoint-Token` 和正文 `{trace:[…]}`。轨迹一次只追加一步，原样重送已确认前缀可返回 `204`。令牌无效返回 `404`，过期、乱序或改写返回 `409`，非法证据范围或形状返回 `400`。令牌只用于内部请求，Java 仅存其摘要。旧的无键请求兼容原内部调用。

V11 要求回调另外携带 `context:{version:1,remainingToolSteps,actions:[…]}`；每个动作按轨迹顺序只提供 `{query}` 或 `{sourceId,evidenceId}`。Java 校验预算等于 3 减已完成步数，读取参数与轨迹证据一致，且已提交参数不可改写。上下文与轨迹在同一条条件更新中保存，不进入公开响应。既有 V10 历史可正常读回，旧运行不具备 V11 上下文。

**内部重建预检。** `POST /internal/v1/research/context/verify` 仅在受信任的本机环境启用，接收原 `question`、有序 `sourceIds`、`trace` 与 V11 `context`。沿原证据顺序精确回读，重建助手调用与工具结果消息。成功只返回 `messageCount`、`candidateCount`、`verifiedCount`、`remainingToolSteps`、`contextBytes`。其中字节数包括消息和工具声明，口径与运行前检查一致。当前版本不可读返回 `404 RESEARCH_EVIDENCE_UNAVAILABLE`；越界、排序或预算不一致返回 422；超时返回 504。该端点无模型调用，不生成回答或更新数据库。

**手动续跑。** 原请求已标记结果未知且具备 V11 检查点时，以相同问题和有序资料重新提交 `POST /api/research`，同时携带新 `Idempotency-Key` 与旧 `Research-Resume-From`。Java 在当前本机工作区绑定一个直接续跑请求，新键重放沿用同一结果。没有可用检查点、输入改变或已有不同续跑键时返回 `409 RESEARCH_RESUME_UNAVAILABLE`。Python 从原证据重建后使用剩余步数，证据失效返回 `404 RESEARCH_EVIDENCE_UNAVAILABLE`，在下一次模型调用前停止。旧键仍保留原状态。续跑由用户确认，可能再次产生模型费用。

**刷新恢复。** `GET /api/research/requests/{key}` 返回本机个人工作区的 `status`、`question`、有序 `sourceIds`、已确认 `trace`、`canResume` 和可空的 `recordId`、`nextRequestKey`。响应带 `Cache-Control: no-store`。超过期限时读取显示 `UNKNOWN`，无需写库或调用模型。原请求已有续跑时，使用 `nextRequestKey` 打开子请求。未找到或 V13 前缺少原问题时返回 `404 RESEARCH_RECORD_NOT_FOUND`；资料不可用返回 `404 RESEARCH_SOURCE_UNAVAILABLE`。

Web 运行期间在地址栏保存 `?pending={key}`，刷新或重开只发 GET。查看完成状态会打开现有研究记录。V13 只为新建有键请求保存原问题，既有研究历史仍可打开。

### 可编辑研究笔记

- `POST /api/research/{id}/note` 从本机空间内已有的有据研究创建笔记，首次返回 201 及 `Location`，重复创建返回原笔记和 200。研究或证据不可读时返回 `404 RESEARCH_NOTE_RESEARCH_UNAVAILABLE`，证据不足返回 `409 RESEARCH_NOTE_ANSWER_REQUIRED`。
- `GET /api/research-notes` 从最近编辑时间起返回最多 50 条，字段为 `id,researchId,title,revision,updatedAt`。`GET /api/research-notes/{id}` 与创建成功返回相同形状 `{"note":{…},"markdown":"…","citations":[…]}`。笔记不存在返回 `404 RESEARCH_NOTE_NOT_FOUND`。
- `PUT /api/research-notes/{id}` 接收 `{"title":"研究结论","markdown":"## 我的结论","revision":1}`，保存成功将修订号加一。旧修订号返回 `409 RESEARCH_NOTE_REVISION_CONFLICT`。标题限 160 字，正文限 50,000 UTF-16 单元。非法输入返回 `400 RESEARCH_NOTE_INVALID`。引用在创建时核验并保存，编辑请求不能覆盖引用。
- `GET /api/research-notes/{id}/export/markdown` 下载 UTF-8 附件，包含已保存 Markdown、原句、资料版本、证据 ID 和位置。所有操作无需再调用模型。数据使用 V14；原字幕摘要笔记接口保持兼容。

## 后台字幕摘要任务

- `POST /api/sources/{id}/summary-task` 仅接受已入库的字幕 ID，返回 `202`、任务 JSON 和 `Location: /api/summary-tasks/{id}`。同资料的任务 ID 固定，重复提交返回已有状态；失败后用显式重试入口。不存在或非字幕沿用资料 404。
- `GET /api/summary-tasks` 返回最近更新的最多 50 条；详情用 `GET /api/summary-tasks/{id}`。字段为 `id,sourceId,status,attempt,errorCode,updatedAt`，未知 ID 返回 `404 TASK_NOT_FOUND`。状态为 `QUEUED`、`RUNNING`、`SUCCEEDED` 或 `FAILED`，`attempt` 在领取时递增。
- `POST /api/summary-tasks/{id}/retry` 只接受失败态，返回原 ID 的待处理任务和 202；其他状态返回 `409 TASK_NOT_RETRYABLE`。超期状态为 `TASK_LEASE_EXPIRED`，模型错误沿用摘要错误码。完成后从原资料摘要接口读取真实成果。
- 后台首次发布摘要与任务状态变为 `SUCCEEDED` 属于同一次提交。提交失败时原资料摘要接口仍返回 `204`，若此前已经有相同资料版本的摘要，重试可直接复用。后台租约默认 60 秒，配置见开发指南。
- V15 使用 PostgreSQL 队列，Java worker 默认每次完成后间隔 1 秒继续领取，租约 60 秒。过期或旧尝试无法发布，异常中断后等待显式重试。此入口适用本机个人资料，当前只支持后台字幕摘要。

## 最近研究与详情

- `GET /api/research` 返回本机工作区最近 20 条已保存研究，按 `createdAt`、`id` 降序。每项包含 `id`、`question`、`createdAt`，列表为空时为 `[]`。
- `GET /api/research/{id}` 返回与创建成功相同的结构。快照仅保存引用 ID，GET 根据所选资料和当前证据版本重新填充原文。不存在或不属于当前工作区的记录返回 `404 RESEARCH_RECORD_NOT_FOUND`；所选资料或原证据不可用分别返回 `404 RESEARCH_SOURCE_UNAVAILABLE` 和 `404 RESEARCH_EVIDENCE_UNAVAILABLE`；UUID 无法解析返回 `400 REQUEST_INVALID`。
- 两条 GET 均无需模型，成功响应带 `Cache-Control: no-store`。历史写入使用 V7 的 PostgreSQL 表。
