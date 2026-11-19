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
