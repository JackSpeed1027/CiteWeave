# 本地正式开发环境

Windows PowerShell 中从项目根目录启动。各服务仅在回环地址监听。已在本机确认 Java 21.0.12.1、Maven 3.9.11、Node.js 24.22.0、npm 11.19.1、Python 3.13.16、uv 0.13.1、Docker Compose 5.6.1、FFmpeg 9.0.2。可通过 `python --version` 检查 PATH 中的解释器，本机的 `py` 启动器未登记该解释器。开发时使用 Python 3.13，本阶段依赖声明兼容 3.11–3.13。

## 四个终端

启动 Docker Desktop Linux 引擎后运行数据库。

```powershell
docker compose -f infra/compose.yaml up -d --wait
docker compose -f infra/compose.yaml ps
```

Java 业务服务。

```powershell
mvn.cmd -f server/pom.xml spring-boot:run
```

Python AI Engine。

```powershell
Set-Location ai
uv sync --locked --python 3.13
uv run --locked uvicorn citeweave_ai.app:app --app-dir src --host 127.0.0.1 --port 8091
```

前端正式入口。

```powershell
Set-Location web
npm.cmd ci
npm.cmd run dev
```

| 入口 | 默认端口 | 就绪检查 |
| --- | --- | --- |
| PostgreSQL | 5434 | Compose 服务为 healthy |
| Java Core | 8087 | [GET /api/sources](http://127.0.0.1:8087/api/sources) |
| Python Engine | 8091 | [GET /healthz](http://127.0.0.1:8091/healthz)、[OpenAPI 浏览](http://127.0.0.1:8091/docs) |
| 正式开发前端 | 5173 | [首页](http://127.0.0.1:5173/)，其 `/api` 由 Vite 转发给 Java |
| 已验收原型 | 5175 | `npm.cmd run prototype` 后访问 [原型](http://127.0.0.1:5175/prototype.html) |

Python 的健康检查仅检验本地 HTTP 进程。模型、检索及 Agent 尚待接入。两个后端可各自启动和验证，前端当前调用 Java。保留已有数据库卷，暂停数据库时用 `docker compose -f infra/compose.yaml stop`。端口冲突时先确认占用方，可在独立终端选择备用端口并同步相关代理配置。

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

相关文档为 [正式开发计划](IMPLEMENTATION_PLAN.md)、[本地实验](LABS.md)、[代码协作规范](../AGENTS.md) 和[整体架构](ARCHITECTURE.md)。
