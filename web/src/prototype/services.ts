import { initial, type Model, type Note, type Run, type Source, type Task, type Target, type Destination, type Format, type Folder } from './mock'

export const maxResearchSources = 6

export function folderScope(folders: Folder[], root: string) {
  const ids = new Set([root])
  const pending = [root]
  while (pending.length) {
    const parent = pending.pop()
    for (const folder of folders) {
      if (folder.parent === parent && !ids.has(folder.id)) {
        ids.add(folder.id)
        pending.push(folder.id)
      }
    }
  }
  return ids
}

const key = 'citeweave-high-fidelity-v1'
export const uid = () => crypto.randomUUID()
export function load(): Model {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : initial
  } catch {
    return initial
  }
}
export function persist(value: Model) { localStorage.setItem(key, JSON.stringify(value)) }
export const dateLabel = (n: number) => new Intl.DateTimeFormat('zh-CN', { month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' }).format(n)
export const stepIndex = (start: number, count: number, now: number) => Math.min(count, Math.floor(Math.max(0, now - start) / 650))
export function advance(model: Model, now: number): Model {
  let changed = false
  const tasks = model.tasks.map((task): Task => {
    if (task.stage !== 'running' && task.stage !== 'queued') return task
    if (now < task.started) return task
    const index = stepIndex(task.started, task.steps.length, now)
    const stage = task.fail && index >= 2 ? 'failed' : index === task.steps.length ? 'done' : 'running'
    if (stage === task.stage) return task
    changed = true
    return { ...task, stage }
  })
  const chats = model.chats.map(chat => {
    const runs = chat.runs.map((run): Run => {
      if (run.status !== 'running' || now - run.started < 2600) return run
      changed = true
      return { ...run, status: run.sources.length ? 'done' : 'failed' }
    })
    return runs.some((run,i) => run !== chat.runs[i]) ? { ...chat,runs } : chat
  })
  if (!changed) return model
  const statuses = new Map(tasks.map(t => [t.sourceId, t.stage]))
  return { ...model, tasks, chats, sources:model.sources.map(s => statuses.has(s.id) ? { ...s,status:statuses.get(s.id)! } : s) }
}
export function makeRun(query: string, sources: Source[]): Run {
  const goal = /obsidian|vault/i.test(query) ? 'obsidian' : /github|仓库|提交/i.test(query) ? 'github' : /本地|指定目录/i.test(query) ? 'local' : undefined
  const evidence = sources.map(s => {
    const segment = s.segments[Math.min(1, s.segments.length - 1)]
    return segment ? '- **' + s.title + '** · ' + segment.text : '- **' + s.title + '** · 暂无可用片段。'
  }).join('\n')
  const subject = /比较|对比|联合/.test(query) ? '多资料对照' : /笔记|报告|整理/.test(query) ? '研究笔记草稿' : '证据摘要'
  const answer = '## ' + subject + '\n\n' + evidence + '\n\n### 可以继续验证\n\n回到来源定位相关段落，再结合资料中的前提比较结论。'
  return { id:uid(),query, sources:sources.map(s => s.id),status:'running',started:Date.now(),answer,goal }
}
export function noteFrom(title: string, body: string, sources: string[]): Note {
  return { id:uid(),title,body,sources,date:Date.now(),tags:[],collection:'',versions:[] }
}
export function safeFilename(name: string) {
  return name.trim().replace(/[<>:"/\\|?*\x00-\x1f]/g,'-').replace(/[. ]+$/g,'').slice(0,120) || 'CiteWeave'
}
export function pathParts(dir: string) {
  const parts = dir.trim().split(/[\\/]/).filter(Boolean)
  if (/^[\\/]/.test(dir.trim()) || parts.some(p => p === '.' || p === '..' || /[<>:"|?*\x00-\x1f]/.test(p))) throw new Error('请输入授权根目录下的相对目录，例如 Research/Java。')
  return parts
}
export function destinationPath(d: Destination, t: Target, dir: string, filename: string) {
  const root = d === 'github' ? t.account + '/' + t.repo + ' · ' + t.branch : t.account
  return [root, ...pathParts(dir), filename].join('/')
}
export function renderedText(note: Note, sources: Source[], dest?: Destination) {
  const citations = note.sources.map(id => sources.find(s=>s.id===id)).filter((s): s is Source => Boolean(s))
    .map(s => '- ' + s.title + ' · 演示位置 ' + s.segments.map(x=>x.label).join('、')
      + (s.url ? ' · [导入入口](' + s.url + ')' : ''))
  const front = dest === 'obsidian' ? '---\ntitle: ' + JSON.stringify(note.title) + '\ntags: ' + JSON.stringify(note.tags) + '\nsource: CiteWeave\ncreated: ' + new Date(note.date).toISOString().slice(0,10) + '\n---\n\n' : ''
  return front + note.body + (citations.length ? '\n\n## 来源\n\n> 以下片段来自原型样例。\n\n' + citations.join('\n') : '')
}
export async function exportBlob(format: Exclude<Format,'pdf'>, body: string, note: Note) {
  if (format === 'docx') {
    const { Document, Paragraph, Packer, HeadingLevel, TextRun } = await import('docx')
    let code = false
    const children = body.split('\n').map(line => {
      if (line.startsWith('```')) { code = !code; return new Paragraph({text:''}) }
      const heading = /^(#{1,3}) (.*)/.exec(line)
      if (!code && heading) return new Paragraph({ text:heading[2],heading:[HeadingLevel.HEADING_1,HeadingLevel.HEADING_2,HeadingLevel.HEADING_3][heading[1].length-1] })
      return new Paragraph({children:[new TextRun({text:line,font:code ? 'Consolas' : undefined})]})
    })
    return Packer.toBlob(new Document({sections:[{children}]}))
  }
  const content = format === 'json' ? JSON.stringify({ title: note.title, content:body, sources:note.sources },null,2)
    : format === 'txt' ? body.replace(/^#{1,6}\s+/gm,'').replace(/\*\*([^*]+)\*\*/g,'$1').replace(/^\s*```.*$/gm,'') : body
  return new Blob([content], {type:format === 'json' ? 'application/json' : 'text/plain;charset=utf-8'})
}
export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1500)
}

type Writable = { write(blob: Blob): Promise<void>; close(): Promise<void> }
export type BrowserFile = FileSystemFileHandle & { createWritable(): Promise<Writable> }
export type BrowserDirectory = FileSystemDirectoryHandle & {
  getDirectoryHandle(name: string, options?: { create?: boolean }): Promise<BrowserDirectory>
  getFileHandle(name: string, options?: { create?: boolean }): Promise<BrowserFile>
}
export function directoryPicker() {
  return (window as Window & {showDirectoryPicker?: (options: {mode:'readwrite'})=>Promise<BrowserDirectory>}).showDirectoryPicker
}
export async function existingText(root: BrowserDirectory, dir: string, name: string) {
  try {
    let folder = root
    for (const part of pathParts(dir)) folder = await folder.getDirectoryHandle(part)
    return await (await (await folder.getFileHandle(name)).getFile()).text()
  } catch (error) {
    if (error instanceof DOMException && error.name === 'NotFoundError') return undefined
    throw error
  }
}
export async function writeFile(root: BrowserDirectory, dir: string, name: string, blob: Blob, checked: string | undefined) {
  const current = await existingText(root,dir,name)
  if (current !== checked) throw new Error('文件在预览后发生变化，请重新检查再确认。')
  let folder = root
  for (const part of pathParts(dir)) folder = await folder.getDirectoryHandle(part,{create:true})
  const writer = await (await folder.getFileHandle(name,{create:true})).createWritable()
  await writer.write(blob)
  await writer.close()
}
