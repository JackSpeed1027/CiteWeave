# HTTP API 请求、响应与异常规范

适用于浏览器访问 Java 的正式接口，以及后续 Java 调用 Python 的业务接口。当前资料 API 已采用以下成功形状及资料业务错误码。Python 目前只有 `/healthz`，后续业务端点按此规范接入。

## 请求

- JSON 请求使用 `Content-Type: application/json`。创建和修改请求用专门的 DTO，声明必填字段、长度、允许值及空值语义。HTTP 层校验传输格式，业务层校验业务条件。Java 使用 record 或明确 DTO，Python 用 Pydantic，前端给请求参数和响应定义类型。
- 上传使用 `multipart/form-data`，由浏览器为 `FormData` 生成边界。资料导入的字段是必填 `file` 和可选 `title`。文件类型和长度由服务器再次校验。
- 路径参数定位具体资源，查询参数负责筛选、排序和分页。任务、状态、排序用有限的枚举值。布尔字段使用 JSON 布尔值，JSON 字段统一 lowerCamelCase。
- 日期时间使用带时区的 ISO 8601 文本，服务端优先输出 UTC。字幕和媒体位置用整数毫秒，例如 `startMs`。既有资料 ID 保持数值，与前端的现有类型兼容；新业务的不透明 ID 优先用字符串。
- 修改已使用的字段名、类型、错误码含义或成功形状，按兼容性变更管理。新增字段应考虑旧消费者，破坏性变化先定义迁移和版本。现有 `/api/sources` 保持可用。

```http
POST /api/sources
Content-Type: application/json

{"title":"研究摘记","content":"这里是资料正文。"}
```

## 成功响应

以 HTTP 状态表达操作结果。读取或返回更新后资源使用 `200` 和直接 JSON 资源。同步创建使用 `201` 和 `Location`，无响应体的成功操作用 `204`。将来需要异步处理时使用 `202` 返回任务标识、初始状态和查询位置，任务完成由查询状态确认。

```http
HTTP/1.1 201 Created
Content-Type: application/json
Location: /api/sources/42

{
  "source": {
    "id": 42,
    "title": "研究摘记",
    "kind": "TEXT",
    "filename": null,
    "createdAt": "2026-11-20T12:00:00Z"
  },
  "content": "这里是资料正文。"
}
```

现有资料列表与字幕列表直接返回 JSON 数组，空列表是 `[]`。未来新增分页接口可约定 `items`、`nextCursor`，并说明排序与游标边界。文件下载和事件流使用各自的媒体类型。成功体直接表达资源，客户端以 HTTP 状态处理结果。

## 错误响应

失败通过 `4xx` 或 `5xx` 返回。对本项目主动映射的错误使用 `application/problem+json`，采用 RFC 9457 的 Problem Details，并扩展稳定的 `code`。客户端按 `status` 和 `code` 决定下一步，`detail` 用于用户反馈。框架生成的其他错误、代理异常和网络失败需要容错处理，`code` 在这些情形可能缺失。[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) · [Spring MVC Problem Details](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)

```json
{
  "type": "urn:citeweave:problem:source-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "找不到这份资料。",
  "instance": "/api/sources/42",
  "code": "SOURCE_NOT_FOUND"
}
```

| 字段 | 约定 |
| --- | --- |
| `type` | 项目映射使用 `urn:citeweave:problem:` 与小写连字符错误名 |
| `title` | 简短的错误类别，可由 HTTP 框架提供 |
| `status` | 与实际 HTTP 状态一致 |
| `detail` | 可展示的说明，包含能安全公开的原因与处理动作 |
| `instance` | 可选，HTTP 层可以提供请求路径 |
| `code` | 稳定的大写下划线错误码。优先用业务域前缀 |
| `errors` | 将来需要同时报告多个字段时，约定为 `[{field, code, message}]`，当前资料端点未返回此字段 |
| `traceId` | 追踪接入后可选，仅传可公开的关联标识 |

### 当前资料端点错误码

| 状态 | 错误码 | 行为 |
| --- | --- | --- |
| 400 | `REQUEST_INVALID` | JSON 无法读取、必需的文件部分不存在、参数类型不匹配。修正请求 |
| 400 | `SOURCE_TITLE_INVALID` | 修正资料标题 |
| 400 | `SOURCE_FILE_INVALID`、`SOURCE_FILE_UNREADABLE` | 检查文件名、文件非空与可读性 |
| 404 | `SOURCE_NOT_FOUND`、`SOURCE_TRANSCRIPT_NOT_FOUND` | 资料不存在，或该资料没有字幕轨道 |
| 413 | `UPLOAD_TOO_LARGE` | 缩小上传体积 |
| 415 | `SOURCE_FILE_TYPE_UNSUPPORTED` | 选择支持的资料文件格式。HTTP 请求媒体类型不匹配时由框架处理 |
| 422 | `SOURCE_CONTENT_INVALID`、`SOURCE_TRANSCRIPT_INVALID` | 修正正文、编码、PDF 或字幕内容 |

新接口遇到已有状态冲突用 `409`，权限问题按 `401` 或 `403` 区分，限流用 `429`，上游不可用按实际情况选 `502`、`503` 或 `504`。未知内部故障返回 `500` 与概括提示，日志保留排障所需而且允许记录的上下文。请求正文、用户文档、Cookie、令牌、堆栈及内部 SQL 不进入客户端响应。并发写入和重试应先明确幂等边界。

## 异常类型与分层

- **Java 业务层**。资料模块使用 `SourceException` 和 `SourceException.Code`。服务和解析器抛出有意义的异常，例如缺字幕用 `SOURCE_TRANSCRIPT_NOT_FOUND`。仓储和解析底层的受控错误可在业务边界转换，保留原因链。编程错误不统一转换成业务异常。
- **Java HTTP 层**。`SourceErrorHandler` 将资料业务异常映射为状态和 Problem Details，也处理当前适用的上传超限与请求格式错误。新增业务域时按其可恢复的错误分支定义类型，优先使用一个异常类和一组稳定错误码。Controller 负责 HTTP 契约，领域代码不依赖 HTTP 状态。
- **Python HTTP 层**。后续业务端点使用 Pydantic DTO。模型供应商超时、失败或非法结果在适配层转成明确的应用异常，由 FastAPI 边界映射为 Problem Details。区分请求校验、上游故障和内部错误，解析上游响应时只提取允许公开的字段。
- **React API 层**。`ApiError` 保存状态和可选错误码，既有页面读取 message。网络中断与超时没有 HTTP 状态，使用通用连接说明。5xx 对用户显示通用提示。浏览器与 Python 接口的业务汇合按计划通过 Java。

## 新增端点的交付检查

写清方法、路径、请求类型、示例、成功响应、主要错误和可重试性。测试正常请求、缺字段或非法格式、已知业务失败、状态、响应体，以及失败时是否留下部分写入。新增契约与旧客户端兼容性要同时检查。资料模块的 `SourceHttpContractTest` 可作为 HTTP 边界示例。
