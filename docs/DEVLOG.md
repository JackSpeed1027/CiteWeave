# 开发日志

## 2026-11-19 · 首次资料导入链路

从空目录建立 React + TypeScript 和 Java 21 + Spring Boot 工程。通过 Compose 提供 PostgreSQL，使用 Flyway 创建资料表。新增 Markdown、SRT 和 VTT 示例，自动化测试覆盖真实 PDF 文本提取、字幕时间戳保留、错误编码及粘贴文本校验。

运行和手动验收指令见 README。

### 验证结果

- Java 21.0.12.1、Maven 3.9.11、Node.js 24.22.0、npm 11.19.1 和 Docker Compose 5.6.1 实机验证。PostgreSQL 17.12 容器通过健康检查，Flyway V1 成功应用。
- Maven 打包通过，4 项测试全部通过。直连 PostgreSQL 的 HTTP 验证覆盖文件和文本入库、列表与详情、404/413/415/422 返回及 Java 重启后的同记录回读。
- 浏览器验证 Markdown 文件上传、原文显示、刷新回读、标题搜索与类型筛选。损坏 PDF 显示可操作的错误消息，首页仍显示 5 份验收样本。
- 由于本机 8080 已占用，Java API 和 Vite 代理默认配对到 8087。
