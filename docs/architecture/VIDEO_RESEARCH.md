# 视频处理选型 · 2026-11-19

## 开发执行决策（2026-11-21）

首个用户场景是选中一集视频，读到带引用的摘要，点击引用定位到这一集的对应字幕。实现顺序和责任如下。

| 环节 | 采用方案 | 接入与验收边界 |
| --- | --- | --- |
| P0 视频预览 | 沿用 Java 的 BilibiliMetadataClient。Java 返回分 P、字符串 CID 和标题，前端供用户确认 | 已验证 BV 预览和越界分 P。短链沿用现有引导，提示使用完整链接 |
| P0 可复现的字幕导入 | 复用现有 Java SRT/VTT 解析和毫秒时间轴。由 Java/PostgreSQL 关联工作区、视频 CID、轨道来源和版本 | 上传时显示选中的分 P。用同一视频两集内容不同的字幕检验归属，刷新后仍能从摘要引用定位到原轨道的原句 |
| 平台字幕自动读取 | 选定 yt-dlp CLI 作为唯一接入工具。由受限 worker 用固定参数启动子进程，仅请求所选分 P 的公开可用字幕，跳过媒体下载；输出转换为已有字幕契约后交 Java 校验 | 默认关闭。用允许处理且带字幕的真实分 P 核对 CID、语言、原句和时间区间，验证出站隔离、超时与输出上限后启用。缺字幕、限流或拒绝访问时显示原因，保留选中分 P 并允许补交 SRT/VTT |
| P1 授权本地媒体转写 | FFprobe 读取本地容器、音轨和时长，需要抽轨或转换时用 FFmpeg。Python worker 采用 faster-whisper，首测 CPU INT8；片段秒数统一转换为毫秒 | 用带人工对照的中文短片记录模型版本、机器配置、耗时和时间偏差。ASR 轨道标注为机器转写，产物沿用相同引用接口 |

Python 摘要工作流读取 Java 授权的证据片段，返回草稿与引用 ID。Java 校验归属和版本，前端按引用定位字幕。平台解析、媒体编解码和语音识别复用上游实现。开发者重点掌握同一 CID 的轨道选择、版本切换、失败恢复和引用校验。BBDown、bilibili-api-python 和 whisper.cpp 保留为调研记录，当前排期只集成表中选定的工具。

2026-11-23 的本地媒体增量先从正式 Web 预检开始。Java 用 FFprobe 从 MP4/MOV 读取时长、编码尺寸、视频编码和音频编码，按本机临时文件处理。导入存储与 ASR 使用独立阶段，后续再接到已有字幕和引用链路。操作见[实验 35](../learning/LABS.md#实验-35--改后缀能否通过视频检查)。

同日用 CPU INT8 完成中文单样本实验，并接入 V18 的持久化后台任务。FFprobe 确认容器时长，FFmpeg 提取首条音轨，Python 用本地 small 模型得到毫秒 VTT，Java 在当前租约内把字幕和成功状态一起提交。轨道标注机器转写，超期尝试可显式重试。语速、术语、噪声、分段精度与内存验证在扩充题集时进行。 [实测数据](../quality/BENCHMARK.md)

独立的手动字幕导入使每次上传形成一条不可变字幕资料，资料 ID 作为轨道身份。Java 重查分 P 与 CID，在一笔事务内绑定平台元数据和正文。旧的独立字幕仍以原身份访问。当前为本机单工作区，权限模型随后单独演进。

**开发 Agent 验收顺序**

1. 先用用户字幕打通「确认分 P → 上传 → 真实摘要 → 引用回跳 → 刷新读回」。分别给第 1、2 P 附上不同的样例句，检查第 2 P 摘要的每个引用都能读回第 2 P 的证据，并验证跨轨道引用被拒绝。
2. 单独验证平台自动读取。记录工具版本、样例授权范围、所选分 P、预期原句和实测区间。用固定夹具复现限流，观察页面能补交字幕并继续摘要。在线样例暂缺时保留开关关闭，继续交付第 1 步。
3. 把实际命令和观察值写进[实验手册](../learning/LABS.md)与[开发日志](../planning/DEVLOG.md)，按真实验证范围更新首页看板。P1 本地转写按正式排期独立验收。

上游核对（2026-11-21）：[yt-dlp 的 Bilibili 提取器](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/bilibili.py) · [FFprobe 文档](https://ffmpeg.org/ffprobe.html) · [faster-whisper](https://github.com/SYSTRAN/faster-whisper)

## 初次选型与试跑记录（2026-11-19）

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
