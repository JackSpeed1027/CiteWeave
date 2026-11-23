# CiteWeave

## 项目进度看板

<!-- progress:start -->

更新于 2026-11-23

- **现在能用** · 可导入文档、文本和字幕，并从资料库读回原文。
- **最近验证** · 独立 pgvector 验证 512 维精确检索，54 道冻结题排序与离线结果一致。
- **下一道验收** · Codex 准备真实模型和获准多 P 素材；用户集中验证两集导入、引用回跳和刷新读回。

| 交付里程碑 | 当前进度 | 已有证据 | 负责人及下一道验收 |
| --- | --- | --- | --- |
| 资料入库与读回 | 🟢 正式页面已验证 | Markdown 上传、搜索、原文显示及刷新读回已通过浏览器验证。 个人收藏也已验证筛选和重启读回。 [开发日志](docs/planning/DEVLOG.md) | **用户** · 按最小验收导入样例，从资料库重新打开并刷新。<br>用户正式页面验收结果待记录 |
| 视频与字幕来源 | 🟡 夹具联调通过 | 公开 BV 预览和合成字幕分 P 校验已联调。本地视频支持保存、字幕绑定与后台转写，单样本已在 CPU 离线实测。 [视频接口](docs/development/API_REFERENCE.md) | **Codex** · 使用获准的真实多 P 视频及对应 SRT/VTT，核对来源身份与摘要回跳。<br>真实多 P 素材与用户体验待验收 |
| 字幕证据与可引用摘要 | 🟡 夹具联调通过 | 合成供应商已验证引用回跳和重启读回。隔离 PostgreSQL 已验证写入后故障回滚、原任务重试和摘要原子提交。 [证据契约](docs/development/AI_EVIDENCE_CONTRACT.md) | **Codex、用户** · 配置获准模型后生成真实摘要，用户核对引用原句、时间区间与刷新结果。<br>真实模型及用户集中体验待验收 |
| 问答与笔记回用 | 🟡 夹具联调通过 | 可先查证据再提问。混合问答已验证三类引用。字幕摘要与研究笔记均可重开并下载带定位的 Markdown，研究笔记可编辑。 [开发日志](docs/planning/DEVLOG.md) | **Codex、用户** · 用授权模型复跑固定合成题，再人工核对拒答和回答依据。<br>真实模型与用户体验待验收 |
| Agent 跨会话续做 | ⚪ 待实现 | 已有研究历史、同键重试及步骤检查点。中断后已确认步骤可读回，续跑与记忆待接入。 [记忆方案](docs/planning/AGENT_MEMORY.md) | **Codex、用户** · 实现有来源的记忆与召回，用户开启新会话续研并验证撤销。<br>真实任务与用户体验待验收 |

状态来自[里程碑数据](web/src/progress/milestones.json)。更新方法见[开发指南](docs/development/DEVELOPMENT.md#维护项目进度看板)。

<!-- progress:end -->

[文档索引](docs/README.md) · [HTTP API 与异常规范](docs/development/API_CONTRACT.md)

正式开发从[本机环境与启动指南](docs/development/DEVELOPMENT.md)开始。Java、Python 智能体与前端的并行安排见[正式开发计划](docs/planning/IMPLEMENTATION_PLAN.md)，代码和协作规范见 [AGENTS.md](AGENTS.md)。

高保真交互原型可独立启动。在 `web` 内运行 `npm.cmd run prototype` 后打开 [原型工作台](http://127.0.0.1:5175/prototype.html)。使用说明、六条验收路线与模拟范围见[原型交付说明](docs/product/PROTOTYPE.md)。

[原型使用手册](docs/product/PROTOTYPE_USER_MANUAL.md)提供各功能的场景、操作案例、边界和常见问题。

CiteWeave 是一个多源资料工作台。本次交付包含工作台、资料库、导入页和资料工作区，可将文档、字幕及粘贴文本保存到 PostgreSQL，再按资料读取提取后的正文。

## Windows 本地启动

需要 Java 21、Maven 3.9、Node.js 22.12+ 或 24，以及运行中的 Docker Desktop Linux 引擎。从项目根目录在 PowerShell 中执行。

```powershell
docker compose -f infra/compose.yaml up -d --wait
```

在第二个终端启动 Java 服务。

```powershell
mvn.cmd -f server/pom.xml spring-boot:run
```

在第三个终端启动前端。

```powershell
Set-Location web
npm.cmd ci
npm.cmd run dev
```

打开 [http://127.0.0.1:5173](http://127.0.0.1:5173)。Vite 将 `/api` 转发至 `127.0.0.1:8087`。Compose 默认映射 PostgreSQL 到本机 5434，Flyway 会在 Java 启动时创建表。默认配置可直接使用。如需覆盖，参照 `.env.example` 将相同的 `DB_*` 变量设置在运行 Compose 和 Java 的 PowerShell 会话中。上述 Java 和 Vite 命令使用固定的默认 API 配对端口。

停止开发服务时用 Ctrl+C。需要暂停数据库时可执行 `docker compose -f infra/compose.yaml stop`，下次启动会沿用命名卷。

## 最小验收

1. 在工作台点「导入资料」，选择 `sample-data/research-notes.md` 并提交。
2. 确认看到「已导入并保存到资料库」、Markdown 源文本及资料信息。
3. 在资料库搜索「research」，切换 Markdown 类型，点击结果重新进入详情。
4. 刷新页面，再重启 Java 服务。两次都应能读回同一份资料。
5. 上传示例 SRT 或 VTT，确认工作区保留时间戳。切换「粘贴文本」再导入一份中文资料。
6. 尝试空文件、不支持的扩展名，或扩展名为 PDF 的损坏文件，确认出现错误且资料库数量没有增长。

## 构建与测试

```powershell
mvn.cmd -f server/pom.xml package
Push-Location web
npm.cmd ci
npm.cmd run build
Pop-Location
```

`package` 会执行正文解析与导入校验测试。导入支持 PDF 文本层、UTF-8 TXT/Markdown/SRT/VTT 和粘贴文本。文件上限 10 MiB，PDF 上限 500 页，提取后正文上限 50 万 UTF-16 单元。工作区显示存入数据库的文本，Markdown 源格式和字幕时间戳随正文保存。扫描件需要预先 OCR。

资料导入、持久化和读取已验收。字幕摘要已用合成模型完成隔离联调，真实模型生成与个人体验待验收。字幕摘要笔记已有隔离联调记录。单字幕资料问答已用合成模型验证。个人收藏完成隔离验收，分组收藏夹继续按计划推进。

[产品范围](docs/product/PRODUCT.md) · [架构](docs/architecture/ARCHITECTURE.md) · [路线](docs/planning/ROADMAP.md) · [决策](docs/architecture/DECISIONS.md) · [开发日志](docs/planning/DEVLOG.md)

上传有效 SRT/VTT 后，`GET /api/sources/{id}/transcript` 返回毫秒片段。资料页仍显示保存的原始字幕。测试时间区间与失败边界可用[字幕实验](docs/learning/LABS.md)。更多接管材料见[现状审计](docs/planning/AUDIT.md)、[视频选型](docs/architecture/VIDEO_RESEARCH.md)与[测试计划](docs/quality/TEST_PLAN.md)。
