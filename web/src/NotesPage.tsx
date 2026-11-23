import { useEffect, useState } from 'react'
import { Download } from 'lucide-react'
import { api } from './api'
import type { NoteHeader, TranscriptNote } from './api'
import { ResearchNoteList } from './ResearchNotePage'

/** 浏览已保存的摘要笔记，引用链接返回对应资料和字幕片段。 */
export function NotesPage({ id }: { id?: string }) {
  const [value, setValue] = useState<NoteHeader[] | TranscriptNote>()
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState('')

  useEffect(() => {
    let active = true
    setValue(undefined)
    setError('')
    const reading = id ? api.note(id) : api.notes()
    reading.then((data) => { if (active) setValue(data) })
      .catch((failure: Error) => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [id, attempt])

  async function downloadMarkdown(noteId: string) {
    setExporting(true)
    setExportError('')
    try {
      const blob = await api.noteMarkdown(noteId)
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `citeweave-note-${noteId}.md`
      link.click()
      // 浏览器启动下载后才能释放对象 URL。
      window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    } catch (failure) {
      setExportError(failure instanceof Error ? failure.message : '导出失败，请重试。')
    } finally {
      setExporting(false)
    }
  }

  return <>
    {id && <div className="breadcrumbs"><a href="#/notes">返回笔记</a></div>}
    <div className="page-heading note-page-heading"><div>
      <h1>{value && !Array.isArray(value) ? value.note.title : '笔记'}</h1>
      {id && <p>已保存的字幕摘要与原文引用。</p>}
    </div>
      {value && !Array.isArray(value) && <button type="button" className="button secondary"
        disabled={exporting} onClick={() => downloadMarkdown(value.note.id)}>
        <Download size={16} aria-hidden="true" /> {exporting ? '正在准备…' : '下载 Markdown'}
      </button>}
    </div>
    {error && <div className="panel-body">
      <p className="notice" role="alert">{error}</p>
      <button type="button" className="button secondary" onClick={() => setAttempt((value) => value + 1)}>重新加载</button>
    </div>}
    {!error && !value && <p className="loading" role="status">正在读取笔记…</p>}
    {!id && <ResearchNoteList />}
    {Array.isArray(value) && <section className="panel" aria-label="已保存的笔记">
      <div className="panel-heading"><h2>字幕摘要笔记</h2></div>
      {value.length ? <ul className="note-list">
        {value.map((item) => <li key={item.id}><a href={`#/notes/${item.id}`}>
          <strong>{item.title}</strong>
          <time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleDateString('zh-CN')}</time>
        </a></li>)}
      </ul> : <div className="empty"><h2>还没有笔记</h2>
        <a className="button secondary" href="#/library">从资料库打开字幕</a></div>}
    </section>}
    {value && !Array.isArray(value) && <article className="panel">
      <div className="panel-heading"><h2>已保存的摘要</h2><a href={`#/sources/${value.note.sourceId}`}>打开资料</a></div>
      <div className="summary-content">
        {exportError && <p role="alert" className="notice">{exportError}</p>}
        <p className="summary-prose">{value.snapshot.summary}</p>
        {value.snapshot.truncated && <p className="muted">这份摘要基于部分字幕。</p>}
        <ol className="note-citations">
          {value.snapshot.citations.map((citation, index) => <li key={citation.evidenceId}>
            <a className="button secondary"
              href={`#/sources/${value.note.sourceId}?evidence=${encodeURIComponent(citation.evidenceId)}`}>
              引用 {index + 1} · {citation.startMs}–{citation.endMs} ms
            </a>
            <p>{citation.text}</p>
          </li>)}
        </ol>
      </div>
    </article>}
  </>
}
