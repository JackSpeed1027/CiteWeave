# CiteWeave 交互原型

面向日常使用及产品验收的完整说明见[原型使用手册](PROTOTYPE_USER_MANUAL.md)。

原型入口为 `web/prototype.html`，复用 React、TypeScript、Vite 和 Lucide。页面使用 React Router、Tailwind 与基于 Radix 的 shadcn/ui 风格对话框。既有资料工作台可从 Vite 的首页访问。

## 本地启动

在 PowerShell 从仓库根目录执行。

```powershell
Set-Location web
npm.cmd ci
npm.cmd run prototype
```

打开 [本地原型](http://127.0.0.1:5175/prototype.html)。此入口可在没有 Java、Docker、数据库和密钥的环境运行。生产打包执行 `npm.cmd run build`，生成现有工作台和原型两个 HTML 入口。

## 六条验收旅程

| 路线 | 推荐操作 | 可观察结果 |
| --- | --- | --- |
| A · Bilibili | 导入中心 → 填入示例 → 预览 → 选择 P3 → 导入 → 打开工作区 → 资料问答 → Agent → 生成文档 | 有章节、时间轴、模拟摘要。Agent 引用可定位资料片段。P2 可观察无字幕提示和原任务恢复 |
| B · 本地视频 | 选择 MP4，附加 SRT/VTT 或选择模拟 ASR，开启失败演练再重试 | 本机文件名、体积和可读取的时长真实，模拟六步、失败和恢复可见 |
| C · 多资料研究 | 再导入文档和网页，创建收藏夹，知识库批量选中并移动；Agent 选择多份资料后对比 | 对话与工具状态、可跳转引用、笔记编辑与刷新恢复 |
| D · Obsidian | 外部集成设置演示 Vault 与目录；Agent 提出保存到 Obsidian；打开成果并预览 | Markdown 含 YAML Frontmatter、标签、来源位置；确认后记录演示回执 |
| E · GitHub | 设置演示账户、仓库、分支和目录；Agent 生成面向 GitHub 的成果，预览提交并确认 | 模拟提交与回执；重复保存同路径可见旧内容与新版本并确认更新 |
| F · 本地 | Agent 生成保存到本地的成果，选择格式、相对路径、预览并确认 | 默认记录模拟回执。可用桌面浏览器中选择授权真实目录后，可实际保存受支持的文件格式 |

## 能力边界

- 页面跳转、搜索筛选、收藏和文件夹、任务状态、编辑预览、自动保存、版本、导出字节和回执管理在浏览器内运行。刷新保留资料元数据、分类、对话、笔记和模拟配置。同一浏览器的不同源端口拥有独立数据。
- Bilibili、网页和 GitHub 内容提取，ASR、摘要、Agent 研究和工具结果都是模拟。上传文件用于本机校验和演示任务。原型不会上传视频字节，字幕与正文展示使用样例。
- GitHub 连接、权限、仓库、分支、提交与更新演练使用模拟数据。没有远端提交或真实授权。Obsidian Vault 配置也默认模拟。
- 独立浏览器支持 File System Access API 时，可通过「授权实际目录」在 Obsidian Vault 或 Local Workspace 进行写入。先预览目标及已有内容，再确认写入。限制相对路径，不接受上级目录，更新前重新读取现有内容。授权句柄仅在当前会话保留。
- Markdown、TXT、JSON、DOCX 可下载；PDF 通过浏览器打印预览选择另存。PDF 到指定目录用于模拟保存流程。跨浏览器或嵌入式预览无法授权目录时，可选择本机 Edge/Chrome 独立标签页或下载。

## 代码入口

- `web/src/prototype/mock.ts` 提供演示资料、状态模型和样本。
- `web/src/prototype/services.ts` 管理浏览器持久化、演示任务推进、产物导出与目录写入。
- `web/src/prototype/App.tsx` 管理导航、全局搜索和路由。
- `web/src/prototype/Destinations.tsx` 负责配置、路径检查、预览确认和回执。

## 2026-11-20 验收结果

- TypeScript 检查和 Vite 生产构建通过。用隔离的 Edge 会话从生产预览逐一通过 A–F，三个目的地使用模拟回执。
- 验证真实 MP4 时长探测、处理失败重试、无字幕后在同一任务继续、100% 处理进度、笔记刷新恢复和 390px 宽度下的导航。
- 浏览器实际下载 Markdown 及 DOCX，并检查了 Markdown 来源部分与 DOCX ZIP 结构。
- 在 Edge 的浏览器私有文件系统验证写入、回读、冲突拦截和拒绝上级路径。系统目录选择器、实际 Obsidian Vault 与 PDF 打印对话框留作用户本机手验。

后续以实际体验反馈确定正式范围。建议先确定导入中心及知识成果保存流程，再给已验收的页面接入真实资料与视频 API。
