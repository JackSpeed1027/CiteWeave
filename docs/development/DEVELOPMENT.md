# 本地正式开发环境

Windows PowerShell 中从项目根目录启动。各服务仅在回环地址监听。已在本机确认 Java 21.0.12.1、Maven 3.9.11、Node.js 24.22.0、npm 11.19.1、Python 3.13.16、uv 0.13.1、Docker Compose 5.6.1、FFmpeg 9.0.2。可通过 `python --version` 检查 PATH 中的解释器，本机的 `py` 启动器未登记该解释器。开发时使用 Python 3.13，本阶段依赖声明兼容 3.11–3.13。

## 从 Windows 桌面打开

1. 在开始菜单搜索并打开 **Docker Desktop**，等 Docker 引擎显示正在运行。Docker Desktop 提供容器引擎及状态界面。
2. 用文件资源管理器打开项目目录 `D:\Work\Project\CiteWeave`。在地址栏直接输入 `powershell` 并回车，即可在当前目录打开终端。也可以用 Windows Terminal 新建 PowerShell 标签页。
3. 按下面的步骤启动。数据库和 Java 先启动，前端随后启动。Python 当前用于独立开发和实验，体验已有资料功能只需要数据库、Java 和前端。

每段命令都写了绝对目录，可以直接在新终端执行。使用 CMD 时，把每段首行 `Set-Location '路径'` 换成 `cd /d "路径"`，下面的命令照常运行。

## 终端 1 · 启动 PostgreSQL

在项目根目录执行 Compose 命令。这里的 `infra/compose.yaml` 是相对于项目根目录的文件路径。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
docker compose -f infra/compose.yaml up -d --wait
docker compose -f infra/compose.yaml ps
```

看到 `citeweave-postgres-1` 为 `healthy` 后继续。也可以在 Docker Desktop 的 **Containers** 打开 `citeweave` 项目查看 PostgreSQL。命令里的 `-d` 会让数据库在后台运行，终端 1 可以关闭。

## 终端 2 · 启动 Java

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
mvn.cmd -f server/pom.xml spring-boot:run
```

看到 Spring Boot 启动并监听 8087 后，打开[资料 API](http://127.0.0.1:8087/api/sources)查看 JSON 列表，空库显示 `[]`。保留终端运行。

## 终端 3 · 启动前端并打开项目

```powershell
Set-Location 'D:\Work\Project\CiteWeave\web'
npm.cmd ci
npm.cmd run dev
```

`npm.cmd ci` 在首次启动或依赖锁更新后执行。看到 Vite 输出 `Local` 后，保留终端，浏览器打开 **[CiteWeave 正式入口](http://127.0.0.1:5173/)**。资料可在页面的「导入资料」进入，提交后从「资料库」打开。Vite 将资料请求转发到 Java。

如果需要体验已验收的演示原型，在另一个终端从 `D:\Work\Project\CiteWeave\web` 运行 `npm.cmd run prototype`，打开[演示原型](http://127.0.0.1:5175/prototype.html)。

## 终端 4 · 启动 Python AI Engine

```powershell
Set-Location 'D:\Work\Project\CiteWeave\ai'
uv sync --locked --python 3.13
uv run --locked uvicorn citeweave_ai.app:app --app-dir src --host 127.0.0.1 --port 8091
```

首次启动或依赖锁更新后执行 `uv sync`。看到 `Uvicorn running` 后保留终端。可打开 [Python 健康检查](http://127.0.0.1:8091/healthz)或 [API 页面](http://127.0.0.1:8091/docs)。

| 入口 | 默认端口 | 就绪检查 |
| --- | --- | --- |
| PostgreSQL | 5434 | Compose 服务为 healthy |
| Java Core | 8087 | [GET /api/sources](http://127.0.0.1:8087/api/sources) |
| Python Engine | 8091 | [GET /healthz](http://127.0.0.1:8091/healthz)、[OpenAPI 浏览](http://127.0.0.1:8091/docs) |
| 正式开发前端 | 5173 | [首页](http://127.0.0.1:5173/)，其 `/api` 由 Vite 转发给 Java |
| 已验收原型 | 5175 | `npm.cmd run prototype` 后访问 [原型](http://127.0.0.1:5175/prototype.html) |

Python 的健康检查仅检验本地 HTTP 进程。模型、检索及 Agent 尚待接入。

## 确认资料已入库

体验时在[正式前端](http://127.0.0.1:5173/)导入资料，创建后能从资料库找回，也可以打开[资料 API](http://127.0.0.1:8087/api/sources)核对标题和资料 ID。

想直接查看 PostgreSQL，在任意新终端执行以下只读命令。示例使用仓库默认数据库名和账户。

```powershell
Set-Location 'D:\Work\Project\CiteWeave'
docker compose -f infra/compose.yaml exec -T postgres psql -U citeweave -d citeweave -c "SELECT id, title, kind, created_at FROM sources ORDER BY id DESC LIMIT 5;"
```

## 重启和常见问题

- 已经能打开对应入口时，可直接使用。重新加载代码时，用 Ctrl+C 停止你自己启动的对应终端，再执行该节启动命令。
- 如果 Docker 命令提示找不到 Linux 引擎，先回到 Docker Desktop 等待引擎就绪。
- 如果首页能打开但无法加载资料，先检查 8087 的资料 API，再检查 Docker 中 PostgreSQL 是否 `healthy`。
- 只暂停数据库时，在项目根目录运行 `docker compose -f infra/compose.yaml stop`，下次启动会复用现有数据卷。

## 自动化验证

```powershell
mvn.cmd -f server/pom.xml package
Push-Location ai
uv run --locked pytest
Pop-Location
Push-Location web
npm.cmd run build
Pop-Location
```

依赖锁为 `ai/uv.lock` 与 `web/package-lock.json`。本地虚拟环境、缓存和构建结果在忽略路径。凭据通过本机环境设置，后续按实现增加配置示例。数据库的参数与示例见根目录 `.env.example`。

## 仓库导航

`server/` 管理 Java 业务和 Flyway，`ai/` 管理 Python HTTP 服务及后续 AI 实现，`web/` 包含真实前端与独立原型，`infra/` 管理本地依赖服务，`sample-data/` 保存无敏感信息的样例，`docs/` 保存决策、契约、进度、测试和用户实验。

相关文档为 [正式开发计划](../planning/IMPLEMENTATION_PLAN.md)、[本地实验](../learning/LABS.md)、[代码协作规范](../../AGENTS.md) 和[整体架构](../architecture/ARCHITECTURE.md)。

## 维护项目进度看板

正式工作台首页和根 README 使用 [里程碑数据](../../web/src/progress/milestones.json)。其中记录更新日、当前可用能力、最近实测以及每项的验证层级、证据、负责人和下一道验收。个人正式体验用 `acceptance` 单独记录，就绪的原型和离线夹具按各自验证层级标记。每通过一道实际验收后，更新受影响的里程碑和日期，并给出能回查的证据。

从仓库的 `web` 目录执行。

```powershell
# 只同步根 README 两条进度标记之间的区域。
npm.cmd run progress:write

# 检查正式 Web 与 README 是否一致，并跑合成的状态变更对照。
npm.cmd run progress:check
npm.cmd run progress:test
```

生产构建也会检查 README 一致性。开发模式下，数据更新由 Vite 重新加载；生产站点随新构建更新。工作台看板随 Web 一起交付，可在资料 API、模型和平台不可达时显示。


## 启用真实字幕摘要

在启动 Python 的终端配置 `CITEWEAVE_MODEL_BASE_URL`、`CITEWEAVE_MODEL_NAME` 和 `CITEWEAVE_MODEL_API_KEY`。地址为兼容 Chat Completions 的 API 根地址，生产配置使用 HTTPS。API Key 通过本地运行环境注入。字幕详情点击「生成摘要」会将预算内的原句送到该地址。缺少配置时，页面显示「摘要模型尚未配置」。

Java 默认在回环地址 8091 查找 AI Engine。隔离验收可用 Java 参数 `--citeweave.ai.base-url=http://127.0.0.1:18091` 指向独立进程。摘要持久化使用 V2 Flyway 新表 `transcript_summaries`，首次联调在独立卷应用。正式本机数据库变更按仓库授权规则执行。


Bilibili 手动字幕绑定需 V3 的新表。验收入口为「导入资料 → Bilibili 字幕」。预览后确认分 P 并上传对应 SRT/VTT；测试环境 V3 已独立验证，既有数据库升级时按仓库授权约定执行。


个人收藏需要 V5 新表。V5 已在独立库验收。V5 环境可从「资料详情 → 收藏资料」保存状态，在「资料库 → 仅收藏」回读。既有库升级遵循仓库授权约定。


PDF 原始页码证据依赖 V6。旧 PDF 仍可读合并正文；在具备 V6 的环境重新上传有文本层的原文件可生成页码引用。V6 仅在独立库中应用，既有库升级遵循授权约定。

## 本机只读研究验收

保留研究历史需 V7。完成后可从「最近研究」重新打开答案、轨迹和原文位置。V7 仅在独立验收库应用。

研究请求去重需 V8，已在独立验收库应用。可按[实验 24](../learning/LABS.md)对同一请求键重复提交，检查记录 ID 和状态码。

结果未知的故障对照需 V9。隔离模型夹具会对每个包含「中断夹具」的新问题首次返回上游错误。固定问题明确重跑后可恢复正常，用法见实验 25。

步骤检查点需 V10 及升级后的 Python 和 Java。在隔离模型夹具中，含「检查点夹具」的新问题会在首次搜索完成后注入上游错误，用法见实验 26。

刷新恢复链接需 V13。隔离环境可按实验 30 在中断后刷新，或复制地址到新标签页。刷新只读取 Java 状态，点击「从检查点继续」才发起新执行。

后台字幕摘要任务需 V15，Java 默认启动轮询 worker，可用 `--citeweave.tasks.worker-enabled=false` 单独运行验收驱动。隔离夹具的字幕正文以 `[任务验收夹具]` 开始时，首次摘要调用将收到 429。

Java 统一通过 MyBatis-Plus Spring Boot 3 starter 访问业务数据库。常规单表读写用 BaseMapper 和类型化条件，联合查询和数据库原子操作放在 `server/src/main/resources/mapper/`。首次构建使用可联网的 Maven 下载依赖，已有本机缓存时可用 `mvn.cmd -o -B -f server/pom.xml package`。此前的 Flyway 版本沿用。

后台摘要任务通过 `CITEWEAVE_TASK_LEASE_SECONDS` 设置领取租约，默认 60 秒。调整时须给 Java 到 AI Engine 的 32 秒读取超时及最终数据库提交留出余量。缩短租约用于隔离实验，运行中的租约继续使用领取时已经写入的到期时间。

## 本地视频预检

在启动 Java 的同一终端运行 `ffprobe -version`。若 Java 服务使用独立的 PATH，设置 `CITEWEAVE_FFPROBE` 为已安装的可执行文件绝对路径后重启 Java。Web 入口为「导入资料 → 本地视频」。运行时只需要 FFprobe，FFmpeg 用于合成测试样本。验证命令在仓库根目录执行。

```powershell
$env:CITEWEAVE_LOCAL_VIDEO_TEST = '1'
mvn.cmd -o -B -f server/pom.xml '-Dtest=LocalVideoPreviewIntegrationTest' test
```

默认最多同时运行 2 个探测进程，每次探测 5 秒，媒体时长最多 10 分钟。可用 `CITEWEAVE_VIDEO_PROBE_CONCURRENCY`、`CITEWEAVE_VIDEO_PROBE_TIMEOUT` 和 `CITEWEAVE_VIDEO_MAX_DURATION` 覆盖。上传字节数沿用 Spring multipart 的 10 MiB 限制。预检仅用于本地开发，接入共享环境前需对原生媒体工具进程增加操作系统级权限及资源隔离。

确认导入后，媒体文件保存在 `CITEWEAVE_VIDEO_STORAGE_ROOT`（默认 Java 用户目录下 `.citeweave/media`），元数据存于 Flyway V16。部署时应指定应用专用的受控本地卷，并给 Java 进程读写权限。当前发布用同卷硬链接，因此验收用支持硬链接的本地文件系统。备份同时包含媒体目录和数据库。文件发布后若数据库写入失败，相同字节的重试可复用对象；崩溃留下的暂存文件或孤立对象由后续独立的运维流程处理。

应用 V17 后，可在「导入资料 → 本地视频 → 最近保存的视频 → 管理字幕」选择对应 SRT/VTT。字幕工作区沿用正式资料入口，可查看时间轴和摘要。V17 只在新建隔离验收库应用，先前的验收入口继续使用原环境。

### 本机 CPU 转写实验

需要 FFmpeg、FFprobe 和已完整下载的 CTranslate2 模型目录。可在仓库根目录设置 `UV_PROJECT_ENVIRONMENT` 到独立目录，执行 `uv sync --directory ai --locked --group asr` 安装可选转写依赖。离线运行设置 `HF_HUB_OFFLINE=1` 及 `PYTHONPATH` 为 `ai/src` 的绝对路径。用 `uv run --directory ai --locked --group asr python -m citeweave_ai.local_asr <视频绝对路径> --model-dir <模型目录> --output <尚不存在的目录>` 生成 `machine-captions.vtt` 和 `report.json`。默认最多 120 秒，默认语言 zh、CPU 4 线程、两轮推理。当前脚本为可复现实验，生成后可手动绑定 VTT。

### 后台本机转写

V18 新增独立队列和轨道来源。默认关闭。启动 Java 前设置 `CITEWEAVE_ASR_ENABLED=true`、`CITEWEAVE_ASR_PYTHON` 为已安装可选依赖的 Python 绝对路径、`CITEWEAVE_ASR_PYTHONPATH` 为 `ai/src` 的绝对路径、`CITEWEAVE_ASR_MODEL_DIR` 为模型快照的绝对路径。FFmpeg 和 FFprobe 可沿用 PATH，或用 `CITEWEAVE_ASR_FFMPEG` 和 `CITEWEAVE_FFPROBE` 指定。

入口是「本地视频 → 管理字幕 → 后台转写中文字幕」。单个 Java 实例逐个运行 CPU 任务，Python 不接收 `数据库`或模型供应商凭据。默认 4 线程，视频最长 120 秒，进程超时 150 秒，租约 180 秒。分别可用 `CITEWEAVE_ASR_THREADS`、`CITEWEAVE_ASR_MAX_SECONDS`、`CITEWEAVE_ASR_TIMEOUT_SECONDS`、`CITEWEAVE_ASR_LEASE_SECONDS` 调整，租约须长于进程超时。启用转写的进程须运行后台调度。模型推理在数据库事务外，字幕和任务成功一起提交。

### 版本化词面索引

V19 应用后设置 `CITEWEAVE_LEXICAL_INDEX_ENABLED=true`。打开「跨资料问答 → 查证据」可触发构建。默认关闭，便于与 Java 扫描基线对照。已选资料按原文版本和 `lexical-v1` 构建，首次完成后复用。中文双字词、英文分词、分数相同时按原文顺序、每份资料最多两条等规则沿用。

本阶段按需同步构建，暂不做批量回填、定时清理或向量索引。重复请求可能重复计算冷切片，写入由数据库唯一键合并。原文工作流仍按既有不可变源模型运行。独立测试库设置 `CITEWEAVE_LEXICAL_INDEX_TEST=1` 后，运行 `mvn.cmd -o -B -f server/pom.xml '-Dtest=EvidenceLexicalIndexIntegrationTest' test`。

### 本机向量评测

从仓库根目录运行 `mvn.cmd -o -B -f server/pom.xml '-Dtest=TranscriptRetrievalBenchmarkTest' test`，在 `server/target` 导出两套合成资料。Python 可选依赖组 `retrieval` 使用 ONNX Runtime，不影响普通 AI 服务的依赖安装。实验设置 `UV_PROJECT_ENVIRONMENT` 到独立环境，运行 `uv sync --directory ai --locked --no-default-groups --group retrieval`。模型只读取本地目录的 `tokenizer.json` 和 `model_optimized.onnx`，样例命令与固定版本见实验 41。

### 独立 pgvector 验收库

在仓库根目录运行 `docker compose -f infra/compose.vector-lab.yaml up -d --wait`。默认回环端口 15436，使用独立的 `citeweave-vector-lab` 命名卷与本地实验账户。首次启动由初始化脚本启用 `vector`。Python 可选组 `vector-lab` 包含检索推理和数据库驱动，只在专用连接创建会话临时表。实验 42 给出整套复跑命令，日常应用库仍按原入口启动。

## 隔离研究环境

独立环境运行时，打开[跨资料问答](http://127.0.0.1:15175/#/transcript-questions)，按[实验 22](../learning/LABS.md)选择合成资料并点「开始研究」。依次使用 Web 15175、Java 18087、Python 18091 和本地模型夹具 18093。[Python 接口页](http://127.0.0.1:18091/docs)可单独核对内部调用。默认启动模式关闭研究接口；本机进程启用需配置 `CITEWEAVE_RESEARCH_ENABLED=1`，以及 `CITEWEAVE_RESEARCH_CORE_URL` 指向内部 Java 的 HTTP 回环地址（默认 `http://127.0.0.1:8087`）。已有 `CITEWEAVE_MODEL_*` 配置继续用于模型供应商。运行研究会向该供应商提交问题、证据预览和已读取的原句。
