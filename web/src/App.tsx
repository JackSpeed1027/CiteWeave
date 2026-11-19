import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import {
  AlignLeft, AudioLines, BookOpen, ChevronRight, CircleAlert, CircleCheck, FileText,
  FolderOpen, House, Layers3, LoaderCircle, Plus, Search, UploadCloud, X,
} from 'lucide-react'
import { api } from './api'
import type { Kind, Source, SourceDetail } from './api'

const kinds: Record<Kind, string> = {
  TEXT: '粘贴文本', TXT: 'TXT', MARKDOWN: 'Markdown', PDF: 'PDF', SRT: 'SRT', VTT: 'VTT',
}

const date = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
const errorText = (error: unknown) => error instanceof Error ? error.message : '操作失败，请重试。'
const hash = () => window.location.hash.slice(1) || '/'
const sourceLink = (id: number) => '#/sources/' + id
const subtitle = (kind: Kind) => kind === 'SRT' || kind === 'VTT'

function useLoad<T>(load: () => Promise<T>) {
  const [value, setValue] = useState<T>()
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    setError('')
    setValue(undefined)
    load().then((data) => { if (active) setValue(data) })
      .catch((failure: unknown) => { if (active) setError(errorText(failure)) })
    return () => { active = false }
  }, [load, attempt])
  return { value, error, retry: () => setAttempt((previous) => previous + 1) }
}

function SourceIcon({ kind }: { kind: Kind }) {
  const Icon = subtitle(kind) ? AudioLines : kind === 'TEXT' ? AlignLeft : FileText
  return <span className={'source-icon ' + kind.toLowerCase()}><Icon size={19} /></span>
}

function Notice({ children }: { children: ReactNode }) {
  return <div role="alert" className="notice"><CircleAlert size={18} />{children}</div>
}

function Loading() {
  return <div role="status" className="loading"><LoaderCircle size={18} className="spin" /> 正在读取资料…</div>
}

function Empty({ filtered = false }: { filtered?: boolean }) {
  return <div className="empty">
    <span className="empty-icon"><FolderOpen size={25} /></span>
    <h3>{filtered ? '没有匹配的资料' : '你的资料库还是空的'}</h3>
    <p>{filtered ? '试试其他关键词或资料类型。' : '上传文件或粘贴文字，开始建立你的资料库。'}</p>
    {!filtered && <a className="button secondary" href="#/import"><Plus size={16} /> 导入资料</a>}
  </div>
}

function SourceList({ sources }: { sources: Source[] }) {
  return <div className="source-list">
    {sources.map((source) => <a className="source-row" href={sourceLink(source.id)} key={source.id}>
      <SourceIcon kind={source.kind} />
      <span className="source-name"><strong>{source.title}</strong><small>{source.filename || '手动粘贴'}</small></span>
      <span className="kind">{kinds[source.kind]}</span>
      <time dateTime={source.createdAt}>{date.format(new Date(source.createdAt))}</time>
      <ChevronRight size={16} />
    </a>)}
  </div>
}

function ListStatus({ state, sources, filtered }: {
  state: ReturnType<typeof useLoad<Source[]>>
  sources: Source[]
  filtered?: boolean
}) {
  if (state.error) return <div className="panel-body"><Notice>{state.error}</Notice>
    <button className="button secondary" onClick={state.retry}>重新加载</button></div>
  if (!state.value) return <Loading />
  return sources.length ? <SourceList sources={sources} /> : <Empty filtered={filtered} />
}

function Dashboard() {
  const state = useLoad(api.list)
  const sources = state.value ?? []
  const stats = [
    { icon: Layers3, label: '全部资料', count: sources.length },
    { icon: FileText, label: '文档与文本', count: sources.filter((item) => !subtitle(item.kind)).length },
    { icon: AudioLines, label: '字幕资料', count: sources.filter((item) => subtitle(item.kind)).length },
  ]
  return <>
    <section className="hero">
      <div className="hero-copy">
        <h1>你的知识工作台</h1>
        <p>把散落的文档、文本和字幕收进同一个资料库。随时回到原文，接着读下去。</p>
        <a className="button primary" href="#/import"><Plus size={18} /> 导入资料</a>
      </div>
      <div className="hero-art" aria-hidden="true">
        <div className="orbit orbit-outer" /><div className="orbit orbit-inner" />
        <div className="art-sheet back-sheet"><span /><span /><span /></div>
        <div className="art-sheet front-sheet"><div className="quote">“</div><span /><span /><span /></div>
        <div className="art-node"><BookOpen size={20} /></div>
        <div className="art-dot" />
      </div>
    </section>
    <div className="stats">
      {stats.map(({ icon: Icon, label, count }) => <div className="stat" key={label}>
        <div className="stat-icon"><Icon size={19} /></div>
        <div><div className="stat-label">{label}</div><strong>{state.value ? count : '—'}</strong></div>
      </div>)}
    </div>
    <section className="panel">
      <div className="panel-heading"><h2>最近导入</h2><a href="#/library">查看资料库</a></div>
      <ListStatus state={state} sources={sources.slice(0, 5)} />
    </section>
  </>
}

function Library() {
  const state = useLoad(api.list)
  const [query, setQuery] = useState('')
  const [kind, setKind] = useState('')
  const term = query.trim().toLocaleLowerCase()
  const sources = (state.value ?? []).filter((item) =>
    (!kind || item.kind === kind) && [item.title, item.filename].some((part) =>
      part?.toLocaleLowerCase().includes(term)))
  return <>
    <div className="page-heading">
      <div><h1>资料库</h1><p>集中查找和阅读已导入的资料。</p></div>
      <a className="button primary" href="#/import"><Plus size={18} /> 导入资料</a>
    </div>
    <section className="panel">
      <div className="filters">
        <label className="search"><Search size={18} /><input value={query}
          onChange={(event) => setQuery(event.target.value)} placeholder="搜索标题或文件名" aria-label="搜索资料" /></label>
        <select aria-label="资料类型" value={kind} onChange={(event) => setKind(event.target.value)}>
          <option value="">全部类型</option>
          {Object.entries(kinds).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select>
        {state.value && <span className="muted count">{sources.length} 份资料</span>}
      </div>
      <ListStatus state={state} sources={sources} filtered={Boolean(term || kind)} />
    </section>
  </>
}

function ImportPage({ saved }: { saved: (item: SourceDetail) => void }) {
  const [mode, setMode] = useState<'file' | 'paste'>('file')
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [file, setFile] = useState<File>()
  const [dragging, setDragging] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const input = useRef<HTMLInputElement>(null)

  function selectFile(selected?: File) {
    setError('')
    setFile(undefined)
    if (!selected) return
    if (!/\.(pdf|txt|md|markdown|srt|vtt)$/i.test(selected.name)) {
      setError('请选择 PDF、TXT、Markdown、SRT 或 VTT 文件。')
      return
    }
    if (!selected.size || selected.size > 10 * 1024 * 1024) {
      setError('请选择非空文件，大小不能超过 10 MB。')
      return
    }
    setFile(selected)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy || (mode === 'file' && !file)) return
    setBusy(true)
    setError('')
    try {
      const detail = mode === 'file' && file ? await api.upload(file, title) : await api.paste(title, content)
      if (hash() === '/import') saved(detail)
    } catch (failure) {
      setError(errorText(failure))
    } finally {
      setBusy(false)
    }
  }

  return <>
    <div className="page-heading"><div><h1>导入资料</h1><p>上传本地文档或字幕，或直接粘贴文本。</p></div></div>
    <section className="panel import-panel">
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <legend className="sr-only">导入方式</legend>
          <div className="tabs">
            <button className={mode === 'file' ? 'selected' : ''} type="button" aria-pressed={mode === 'file'}
              onClick={() => { setMode('file'); setError('') }}><UploadCloud size={17} /> 上传文件</button>
            <button className={mode === 'paste' ? 'selected' : ''} type="button" aria-pressed={mode === 'paste'}
              onClick={() => { setMode('paste'); setError('') }}><AlignLeft size={17} /> 粘贴文本</button>
          </div>
          <div className="form-body">
            <label className="field">资料标题 {mode === 'file' && <small>选填</small>}
              <input value={title} onChange={(event) => setTitle(event.target.value)}
                required={mode === 'paste'} maxLength={160} placeholder={mode === 'file' ? '默认使用文件名' : '为这份资料起个名字'} />
            </label>
            {mode === 'file' ? <>
              <input ref={input} hidden type="file" aria-label="选择文件" accept=".pdf,.txt,.md,.markdown,.srt,.vtt"
                onChange={(event) => { selectFile(event.target.files?.[0]); event.target.value = '' }} />
              <button type="button" className={'dropzone ' + (dragging ? 'dragging' : '')}
                onClick={() => input.current?.click()}
                onDragOver={(event) => { event.preventDefault(); if (!busy) setDragging(true) }}
                onDragLeave={() => setDragging(false)}
                onDrop={(event) => {
                  event.preventDefault()
                  setDragging(false)
                  if (!busy) selectFile(event.dataTransfer.files[0])
                }}>
                <span className="empty-icon"><UploadCloud size={24} /></span>
                <strong>选择文件或拖到这里</strong>
                <span>PDF、TXT、Markdown、SRT、VTT · 最大 10 MB</span>
              </button>
              {file && <div className="chosen"><FileText size={18} /><span>{file.name}</span>
                <button type="button" className="icon-button" aria-label="移除所选文件" onClick={() => setFile(undefined)}><X size={17} /></button></div>}
            </> : <label className="field">正文
              <textarea required maxLength={500_000} value={content} placeholder="将资料正文粘贴到这里…"
                onChange={(event) => setContent(event.target.value)} />
            </label>}
            {error && <Notice>{error}</Notice>}
            <div className="form-actions">
              <a className="button secondary" href="#/library">返回资料库</a>
              <button type="submit" className="button primary"
                disabled={busy || (mode === 'file' ? !file : !title.trim() || !content.trim())}>
                {busy ? <LoaderCircle className="spin" size={17} /> : <Plus size={17} />}
                {busy ? '正在导入…' : '导入资料'}
              </button>
            </div>
          </div>
        </fieldset>
      </form>
    </section>
  </>
}

function Workspace({ id, imported }: { id: number; imported: boolean }) {
  const [detail, setDetail] = useState<SourceDetail>()
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    setError('')
    api.get(id).then((data) => { if (active) setDetail(data) })
      .catch((failure: unknown) => { if (active) setError(errorText(failure)) })
    return () => { active = false }
  }, [id, attempt])
  return <>
    <div className="breadcrumbs"><a href="#/library">资料库</a><ChevronRight size={14} /> 资料工作区</div>
    {error && <div className="panel-body"><Notice>{error}</Notice>
      <button className="button secondary" onClick={() => setAttempt(attempt + 1)}>重新加载</button></div>}
    {!error && !detail && <Loading />}
    {detail && <>
      <div className="workspace-heading"><SourceIcon kind={detail.source.kind} />
        <div><h1>{detail.source.title}</h1><p>{detail.source.filename || '手动粘贴'}</p></div>
      </div>
      {imported && <div className="success" role="status"><CircleCheck size={18} /> 已导入并保存到资料库</div>}
      <div className="workspace-grid">
        <article className="panel">
          <div className="panel-heading"><h2><BookOpen size={18} />{subtitle(detail.source.kind) ? '原始字幕' : '资料正文'}</h2></div>
          <pre className="reader">{detail.content}</pre>
        </article>
        <aside className="panel details">
          <div className="panel-heading"><h2>资料信息</h2></div>
          <dl>
            <dt>类型</dt><dd>{kinds[detail.source.kind]}</dd>
            <dt>导入日期</dt><dd>{date.format(new Date(detail.source.createdAt))}</dd>
            <dt>正文字数</dt><dd>{Array.from(detail.content).length.toLocaleString('zh-CN')} 字符</dd>
          </dl>
        </aside>
      </div>
    </>}
  </>
}

export function App() {
  const [route, setRoute] = useState(hash)
  const [imported, setImported] = useState<number>()
  const main = useRef<HTMLElement>(null)
  useEffect(() => {
    const change = () => {
      setRoute(hash())
      window.scrollTo(0, 0)
      main.current?.focus()
    }
    window.addEventListener('hashchange', change)
    return () => window.removeEventListener('hashchange', change)
  }, [])

  const match = route.match(/^\/sources\/(\d+)$/)
  const id = match ? Number(match[1]) : undefined
  const section = route === '/' ? '工作台' : route === '/import' ? '导入资料' : id ? '资料工作区' : '资料库'
  const navigation = [
    { path: '/', label: '工作台', icon: House },
    { path: '/library', label: '资料库', icon: FolderOpen },
    { path: '/import', label: '导入资料', icon: UploadCloud },
  ]

  return <div className="app-shell">
    <aside className="sidebar">
      <a href="#/" className="brand"><span className="brand-mark"><Layers3 size={23} /></span><span>CiteWeave</span></a>
      <nav aria-label="主导航">{navigation.map(({ path, label, icon: Icon }) => {
        const active = route === path || (path === '/library' && id !== undefined)
        return <a key={path} className={active ? 'active' : ''} href={'#' + path}
          aria-current={active ? 'page' : undefined}><Icon size={18} />{label}</a>
      })}</nav>
      <div className="sidebar-footer"><span className="workspace-mark">C</span><span>个人空间</span></div>
    </aside>
    <div className="main-shell">
      <header className="topbar"><span>个人空间</span><ChevronRight size={14} /><strong>{section}</strong></header>
      <main ref={main} tabIndex={-1} className="content">
        {route === '/' ? <Dashboard /> : route === '/import' ?
          <ImportPage saved={(item) => { setImported(item.source.id); window.location.hash = sourceLink(item.source.id) }} />
          : id !== undefined ? <Workspace key={id} id={id} imported={imported === id} /> : <Library />}
      </main>
    </div>
  </div>
}
