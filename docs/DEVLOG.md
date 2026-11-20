# 开发日志

## 2026-11-19 · 首次资料导入链路

从空目录建立 React + TypeScript 和 Java 21 + Spring Boot 工程。通过 Compose 提供 PostgreSQL，使用 Flyway 创建资料表。新增 Markdown、SRT 和 VTT 示例，自动化测试覆盖真实 PDF 文本提取、字幕时间戳保留、错误编码及粘贴文本校验。

运行和手动验收指令见 README。

### 验证结果

- Java 21.0.12.1、Maven 3.9.11、Node.js 24.22.0、npm 11.19.1 和 Docker Compose 5.6.1 实机验证。PostgreSQL 17.12 容器通过健康检查，Flyway V1 成功应用。
- Maven 打包通过，4 项测试全部通过。直连 PostgreSQL 的 HTTP 验证覆盖文件和文本入库、列表与详情、404/413/415/422 返回及 Java 重启后的同记录回读。
- 浏览器验证 Markdown 文件上传、原文显示、刷新回读、标题搜索与类型筛选。损坏 PDF 显示可操作的错误消息，首页仍显示 5 份验收样本。
- 由于本机 8080 已占用，Java API 和 Vite 代理默认配对到 8087。

## 2026-11-19 · 接管审计与字幕时间轴

- 核实 GitHub 仓库无引用，关联 origin，保存本地原型基线 71e351e，从该提交创建 feature/video-transcript。
- 增加时间轴读接口和上传前校验。真实 PostgreSQL HTTP 检查通过，SRT/VTT 新旧记录均可回读，非字幕 404、倒序字幕 422，失败时资料数不变。两轮验证创建了四条标为 API transcript verification 的样本。
- Maven 打包通过，16 项测试通过。Vite 生产构建通过。用户人工字幕实验和答题待记录。
- 完成视频候选对照，验证 Windows 的 yt-dlp 对一个公开样例可读元数据。没有拿到可用字幕，后续用获授权样例验证多 P 与有字幕分支。

## 2026-11-20 · 正式开发底座

- 从已验收原型的提交 cd53c89 建立正式集成基线，保留现有分支、原型入口和开发数据卷。
- 实跑 PostgreSQL 17.12、Java 8087、Vite 5173 与 Python 8091。Vite 转发的资料列表与 Java 直连一致，FastAPI 健康和 OpenAPI 入口可访问，原型端口 5175 可用。
- Maven 打包通过，16 项测试通过。uv 锁一致性检查通过，Python 健康端点测试 1 项通过。Vite 生产构建通过。
- 增加仓库规范、Java 和 Python 并行计划、设计模式适用边界与环境隔离小实验。AI Engine 的首个能力为 HTTP 健康检查，模型业务接口在后续功能交付。
