# 架构

浏览器中的 React + TypeScript 页面经 Vite 代理访问 Java API。Spring Boot 将 HTTP 请求交给资料服务，解析组件提取正文，JdbcClient 把提取结果写入 PostgreSQL。Flyway 在启动时应用版本化迁移。

| 方法与路径 | 职责 |
| --- | --- |
| GET /api/sources | 返回按导入时间排序的资料元数据 |
| GET /api/sources/{id} | 获取元数据和保存的正文 |
| POST /api/sources | JSON 接收 title 和 content，或 multipart 接收 file 和可选 title |

成功导入返回 201、Location 和资料详情。无记录返回 404，上传超限返回 413，不支持的格式返回 415，提取失败返回 422。查询使用参数绑定，正文以纯文本渲染。

资料元数据和正文目前共用一行，列表查询只读元数据。文件在解析过程中读取，数据库保存提取后的文本和原文件名。字幕时间戳随文本保留，PDF 页码定位和原始文件归档后续以独立模型提供。

后续由 Java 管理资料、版本和执行状态。摘要和问答先通过 Java 代理 OpenAI-compatible 模型，再按需引入 Python AI Engine、分块检索和 Agent 编排。
