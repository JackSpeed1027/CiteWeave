# 实验 01 · 字幕导入的失败边界

目的为独立观察 Java 校验和 SQL 插入时序，并验证毫秒转换。你亲自操作并解释结果。准备为按 README 启动数据库和新版 Java，从项目根目录运行下列 PowerShell 命令。实验保留一个带实验标题的资料。

```powershell
$api = 'http://127.0.0.1:8087/api/sources'
$before = (Invoke-RestMethod $api).Count
$created = Invoke-RestMethod $api -Method Post -Form @{
  title = '字幕实验 · 有效样本'
  file = Get-Item sample-data/research-captions.srt
}
(Invoke-RestMethod "$api/$($created.source.id)/transcript") | Format-Table
$afterValid = (Invoke-RestMethod $api).Count

$bad = Join-Path ([IO.Path]::GetTempPath()) ("citeweave-" + [guid]::NewGuid() + ".srt")
Set-Content -LiteralPath $bad -Encoding utf8 -Value "1`n00:00:03,000 --> 00:00:02,000`n倒序片段"
curl.exe -sS -w "\nHTTP %{http_code}\n" -F "file=@$bad" "$api"
$afterInvalid = (Invoke-RestMethod $api).Count
[pscustomobject]@{ Before = $before; AfterValid = $afterValid; AfterInvalid = $afterInvalid }
```

记录两个时间区间、错误状态和消息、三次计数。追踪 SourceService.upload，指出哪里抛错以及哪一步因此未发生。你的观察与解释等待回填。

## 实验 02 · 通过四个端口理解应用边界

**目标**。区分浏览器入口、Java 业务、PostgreSQL 和 Python HTTP 边界。记录观测并用实际文件说明调用路线。

在 [本地开发环境](../development/DEVELOPMENT.md)启动服务。先在仓库根目录执行以下只读检查。输出只包含 HTTP 状态与 PostgreSQL 健康情况。

```powershell
docker compose -f infra/compose.yaml ps
curl.exe -sS -o NUL -w "Vite page %{http_code}\n" http://127.0.0.1:5173/
curl.exe -sS -o NUL -w "Vite API %{http_code}\n" http://127.0.0.1:5173/api/sources
curl.exe -sS -o NUL -w "Java API %{http_code}\n" http://127.0.0.1:8087/api/sources
curl.exe -sS http://127.0.0.1:8091/healthz
```

在自己新开的终端使用 8092 启动一份临时 Python 进程。若该端口已被占用，改用明确空闲的回环端口。运行下列命令后，从另一终端探测 8092 健康路径和 Vite API。再到临时终端按 Ctrl+C，重复检查。整个过程无需停止开始前运行的服务，也无需操作数据库内容。

```powershell
Set-Location ai
uv run --locked uvicorn citeweave_ai.app:app --app-dir src --host 127.0.0.1 --port 8092
```

| 观察点 | 第一次 | 停止自己的临时 Python 后 |
| --- | --- | --- |
| 8092 的 `/healthz` | 待记录 | 待记录 |
| 5173 的 `/api/sources` | 待记录 | 待记录 |
| Compose health | 待记录 | 待记录 |

走读 `web/vite.config.ts`、`SourceController`、`SourceRepository`、`ai/src/citeweave_ai/app.py`。提交实际输出后回答 KNOWLEDGE_CHECK 的环境问题。

## 实验 03 · 从业务异常追踪到 HTTP 响应

启动新版 Java Core 后，在 PowerShell 执行。前两条请求读取非法或缺失的资料 ID，最后一条发送破损 JSON，各请求均不会创建资料。

```powershell
curl.exe -isS http://127.0.0.1:8087/api/sources/not-a-number
curl.exe -isS http://127.0.0.1:8087/api/sources/9223372036854775807
curl.exe -isS -H "Content-Type: application/json" --data-binary "{" http://127.0.0.1:8087/api/sources
```

记录每个请求的状态、媒体类型、`code` 和 `detail`。结合 `SourceService.find` 和 `SourceErrorHandler` 解释哪些失败发生在业务层，哪些在 HTTP 参数或正文解析阶段就被拦截。如果客户端要显示中文提示和决定是否重试，分别选哪个字段？对照 [HTTP API 规范](../development/API_CONTRACT.md)，先记录实际观察，再讨论原因。
