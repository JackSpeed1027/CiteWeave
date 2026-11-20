# CiteWeave

高保真交互原型可独立启动。在 `web` 内运行 `npm.cmd run prototype` 后打开 [原型工作台](http://127.0.0.1:5175/prototype.html)。使用说明、六条验收路线与模拟范围见[原型交付说明](docs/PROTOTYPE.md)。

[原型使用手册](docs/PROTOTYPE_USER_MANUAL.md)提供各功能的场景、操作案例、边界和常见问题。

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

当前验收范围是资料导入、持久化和读取。AI 摘要、资料问答、收藏夹和笔记列在完整 MVP 的下一批验收项中。

[产品范围](docs/PRODUCT.md) · [架构](docs/ARCHITECTURE.md) · [路线](docs/ROADMAP.md) · [决策](docs/DECISIONS.md) · [开发日志](docs/DEVLOG.md)

上传有效 SRT/VTT 后，`GET /api/sources/{id}/transcript` 返回毫秒片段。资料页仍显示保存的原始字幕。测试时间区间与失败边界可用[字幕实验](docs/LABS.md)。更多接管材料见[现状审计](docs/AUDIT.md)、[视频选型](docs/VIDEO_RESEARCH.md)与[测试计划](docs/TEST_PLAN.md)。
