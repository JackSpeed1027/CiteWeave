export type Kind = 'bili' | 'video' | 'document' | 'web' | 'github'
export type Stage = 'queued' | 'running' | 'done' | 'failed' | 'action' | 'cancelled'
export type Destination = 'github' | 'obsidian' | 'local'
export type Format = 'md' | 'txt' | 'json' | 'pdf' | 'docx'
export type Segment = { label: string; title: string; text: string; seconds?: number }
export type Source = { id: string; kind: Kind; title: string; origin: string; url?: string; collection: string; tags: string[]; favorite: boolean; status: Stage; date: number; detail: string; segments: Segment[]; size?: string }
export type Task = { id: string; sourceId: string; title: string; stage: Stage; steps: string[]; started: number; fail?: boolean; detail?: string }
export type Folder = { id: string; name: string; parent: string; color: string; type: 'collection' | 'folder' }
export type Version = { date: number; title: string; body: string }
export type Note = { id: string; title: string; body: string; sources: string[]; date: number; tags: string[]; collection: string; versions: Version[] }
export type Run = { id: string; query: string; sources: string[]; started: number; status: 'running' | 'done' | 'cancelled' | 'failed'; answer: string; goal?: Destination }
export type Chat = { id: string; title: string; sources: string[]; runs: Run[] }
export type Target = { connected: boolean; account: string; repo: string; branch: string; dir: string }
export type Receipt = { id: string; destination: Destination; path: string; date: number; body: string; real: boolean }
export type Model = { sources: Source[]; tasks: Task[]; folders: Folder[]; tags: string[]; notes: Note[]; chats: Chat[]; targets: Record<Destination, Target>; receipts: Receipt[]; settings: { name: string; model: string; endpoint: string; theme: string; defaultTarget: Destination } }

export const kindLabels: Record<Kind, string> = { bili: 'Bilibili', video: '本地视频', document: '文档', web: '网页', github: 'GitHub' }
export const stageLabels: Record<Stage, string> = { queued: '待处理', running: '处理中', done: '已完成', failed: '失败', action: '需补充资料', cancelled: '已取消' }
export const targetLabels: Record<Destination, string> = { github: 'GitHub', obsidian: 'Obsidian', local: 'Local Workspace' }
export const biliParts = [
  { title: '消息可靠性的边界', length: '18:42', subtitles: true },
  { title: 'ACK、重试与死信队列', length: '22:16', subtitles: false },
  { title: '幂等消费的工程实践', length: '16:08', subtitles: true },
]
const videoSegments: Segment[] = [
  { label: '00:00', seconds: 0, title: '问题与背景', text: '异步消息跨越生产者、Broker 和消费者。要讨论可靠性，先明确每个阶段的确认点，以及哪些错误可以重试。' },
  { label: '03:24', seconds: 204, title: '生产侧的确认', text: 'Publisher Confirm 只能说明 Broker 对发布操作的确认。业务数据库和消息发布之间仍然有失败窗口，事务 Outbox 可以把发布意图保存在同一事务中。' },
  { label: '08:52', seconds: 532, title: '消费者幂等', text: '消费者可能在业务提交后、ACK 前退出。重投递时应让业务更新与去重记录落在一个事务中，避免重复副作用。' },
  { label: '14:06', seconds: 846, title: '恢复与排查', text: '用可观察的重试次数和失败原因区分瞬时故障与永久错误。为永久错误设置死信处理流程并保留人工恢复入口。' },
]
export const samples: Record<Kind, { title: string; origin: string; detail: string; segments: Segment[] }> = {
  bili: { title: 'RabbitMQ 消息可靠性实战', origin: '架构学习室 · 3 个分 P', detail: '一份围绕生产确认、消费幂等与失败恢复的演示课程。', segments: videoSegments },
  video: { title: 'Worker 故障恢复演示', origin: '本地视频 · MP4', detail: '演示 Worker 中断之后任务如何通过租约过期恢复。', segments: [
    { label: '00:00', seconds: 0, title: '任务抢占', text: '两个 Worker 同时竞争相同任务，条件更新使一次抢占只由一个 Worker 成功。' },
    { label: '02:18', seconds: 138, title: '租约恢复', text: 'Worker 崩溃后通过租约超时重新调度。旧 Worker 恢复时必须校验所有权后再发布结果。' },
  ] },
  document: { title: 'Designing Data-Intensive Applications · 读书摘记', origin: 'PDF · 阅读摘记', detail: '比较日志、复制和事务的边界，连接到数据处理系统的可恢复性。', segments: [
    { label: '第 2 页', title: '日志与重放', text: '追加日志使处理顺序可追踪。消费者能从检查点恢复，但下游写入的幂等性依然重要。' },
    { label: '第 5 页', title: '一致性与失败', text: '事务为一组业务操作提供原子性。跨存储的协作需要显式定义确认与恢复机制。' },
    { label: '第 8 页', title: '学习建议', text: '通过重复执行、进程终止与延迟注入验证系统行为，再根据观测结果选择实现。' },
  ] },
  web: { title: '从一次投递到可恢复的工作流', origin: '工程手记 · 文章样本', detail: '从任务状态机、阶段产物和重试策略三个角度拆解可靠流水线。', segments: [
    { label: '段落 1', title: '持久化的工作单元', text: '保存任务输入及阶段状态，使 Worker 的内存状态可以被替换。' },
    { label: '段落 2', title: '产物复用', text: '将解析、字幕和生成结果分别保存。摘要模板变动时，可复用已经提取的字幕。' },
  ] },
  github: { title: 'LangGraph · 运行时阅读笔记', origin: 'GitHub · README.md', detail: '图状态、工具调用和 Checkpoint 的演示阅读摘记。', segments: [
    { label: 'README.md:18', title: '状态与节点', text: '节点读取共享状态并返回更新。流程通过边连接，可根据节点输出决定下一步。' },
    { label: 'README.md:42', title: '持久化执行', text: '保留每个关键阶段的检查点，有助于恢复长任务并观察运行轨迹。' },
  ] },
}
const now = Date.now()
const source = (kind: Kind, id: string, tags: string[], days: number, favorite = false): Source => ({
  ...samples[kind], kind, id, collection: kind === 'github' ? 'ai' : 'backend', tags, favorite, status: 'done', date: now - days * 86_400_000,
})
export const initial: Model = {
  sources: [source('bili','rabbit',['Java','架构'],0,true), source('document','ddia',['架构','阅读'],1), source('web','workflow',['架构'],2), source('github','graphs',['AI'],3,true), source('video','worker',['Java'],4)],
  tasks: [{ id: 'initial-task', sourceId: 'rabbit', title: 'RabbitMQ 消息可靠性实战', stage: 'done', steps: ['获取字幕','生成摘要','构建索引'], started: now - 80000 }],
  folders: [
    { id: 'backend', name: '后端工程', parent: '', color: 'violet', type: 'collection' },
    { id: 'ai', name: 'AI 与 Agent', parent: '', color: 'blue', type: 'collection' },
    { id: 'systems', name: '分布式系统', parent: 'backend', color: 'amber', type: 'folder' },
  ],
  tags: ['Java','架构','阅读','AI'],
  notes: [
    { id:'study',title:'RabbitMQ 可靠消息 · 学习笔记', body: '# RabbitMQ 可靠消息\n\n消息的确认边界决定了故障恢复的策略。\n\n## 核心提问\n\n1. 生产侧如何保存待发送意图？\n2. 消费侧如何避免重复副作用？\n\n> 把失败窗口设计成可以观察、可以恢复的状态。', sources:['rabbit'], date:now, tags:['Java'], collection:'backend', versions:[] },
    { id:'design',title:'可恢复任务系统 · 设计摘记',body:'# 可恢复任务系统\n\n| 阶段 | 可观察结果 |\n| --- | --- |\n| 抢占 | 持有者与租约 |\n| 完成 | 可重复读取的产物 |',sources:['worker','workflow'],date:now-86_400_000,tags:['架构'],collection:'backend',versions:[] },
  ],
  chats: [{ id:'research',title:'消息投递与幂等设计', sources:['rabbit','ddia'], runs:[{
    id:'sample-research',query:'结合视频和阅读摘记，说明确认与去重之间的关系。',
    sources:['rabbit','ddia'],started:now-90_000,status:'done',
    answer:'## 确认与幂等各自解决什么\n\n- **确认边界**：Broker 对发布的确认与业务数据库提交属于不同的边界。\n- **重复处理**：消费者在业务提交后、ACK 前退出时，重投递仍可能发生，把去重与业务更新放进同一个事务可避免重复副作用。\n\n可以回到以下演示资料定位相关片段。',
  }] }],
  targets: {
    github: { connected:false,account:'demo-researcher',repo:'learning-notes',branch:'main',dir:'Research' },
    obsidian: { connected:false,account:'Learning Vault',repo:'',branch:'',dir:'Java' },
    local: { connected:false,account:'学习资料',repo:'',branch:'',dir:'CiteWeave' },
  },
  receipts: [],
  settings: { name:'林同学', model:'CiteWeave Demo', endpoint:'', theme:'light', defaultTarget:'obsidian' },
}
export const localVideoSteps = ['上传文件','提取音频','处理字幕','理解内容','生成摘要','构建索引']
export const researchSteps = [
  ['search_sources','查找所选资料'], ['read_segments','读取证据片段'], ['compare_sources','对照与整理证据'], ['create_artifact','准备文档草稿'],
]
