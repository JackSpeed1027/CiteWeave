# 视频处理选型 · 2026-11-19

| 候选 | 能力 | 维护、兼容和依赖 | 决策 |
| --- | --- | --- | --- |
| yt-dlp | Bilibili 提取器支持分 P、标题/封面、字幕转 SRT、弹幕轨道、媒体流识别 | Unlicense 源码，非归档，稳定版 2026.10.26；Windows/Linux 发行物，合并和转码按需用 FFmpeg，二进制内组件另核许可 | 固定版本的验证候选，仅试元数据与可用字幕，隔离进程 |
| bilibili-api-python | Bilibili 专用 SDK 可作为分 P、CID、语言轨道模型参考 | 上游已归档，最后 PyPI 版本 17.4.2，GPL-3.0-or-later，Python ≥3.10；上游源码探测返回 404 | 不作为运行依赖 |
| BBDown | Bilibili 专用 CLI，有分 P、字幕、封面、弹幕和媒体处理能力 | MIT，已归档，最新稳定版 1.6.3；Windows/Linux .NET 发行，常需 FFmpeg 合并 | 能力对照 |
| faster-whisper | 支持段时间戳、可选词时间戳、CPU INT8 和 GPU | MIT，非归档 v1.2.1；Python、CTranslate2、PyAV，模型另核许可 | CPU ASR 首选实测 |
| whisper.cpp | CPU/GPU、量化、原生 CLI 和 SRT/VTT 输出 | MIT，非归档 v1.9.5；CMake 或官方发行物，模型另核许可 | 用相同中文样本比较 |

一手来源：[yt-dlp](https://github.com/yt-dlp/yt-dlp)、[提取器源码](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/bilibili.py)、[PyPI](https://pypi.org/project/bilibili-api-python/17.4.2/)、[归档 SDK](https://github.com/Nemo2011/bilibili-api)、[BBDown](https://github.com/nilaoda/BBDown)、[faster-whisper](https://github.com/SYSTRAN/faster-whisper)、[whisper.cpp](https://github.com/ggml-org/whisper.cpp)。

匿名请求公开视频 BV1xx411c7mD，元数据业务码 0、1 个分 P，CID 的字幕列表业务码 0、零条可用字幕。一个样例只说明此环境的该视频元数据可访问。Linux 实跑、传递依赖漏洞扫描、有字幕的授权样例仍待验证。

Windows 以 Python 3.13.16 运行固定版 yt-dlp，忽略配置并跳过下载。同一 BV 的提取器进程退出 0，输出 ID 与 2055.637 秒时长，分 P 总数与字幕字段在本次输出中为 NA。该结果验证识别与元数据链路，无多 P 或有字幕样本的实测证据。

先实现视频预览，用户确认具体 CID 再创建。BV/AV 编号与允许的 b23 短链进入专用 Adapter。每跳校验主机和 DNS 解析地址，拒绝非公网目标，限制跳转、执行时间、并发与响应内容；提取器全部网络访问同样需隔离。能否使用基于部署环境、平台许可及授权情况决定。区分没有可用字幕、拒绝访问、限流、超时和解析异常，使用 SRT/VTT 作为可操作降级。已获权的本地音视频通过 FFprobe 和 FFmpeg 接 ASR。平台访问失败不会使用户已经导入的字幕失效。

接入前锁版本与哈希、扫描依赖和公告、禁用外部配置和 Cookie，使用最少权限。弹幕可作独立增强轨，从正文和 ASR 产物隔离。
