import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from './api'
import type { ResearchNote, ResearchNoteHeader, Source } from './api'
import { Citations } from './MultiTranscriptPage'

/** 在笔记入口浏览研究成果，同时保留原字幕笔记入口。 */
export function ResearchNoteList() {
  const [notes, setNotes] = useState<ResearchNoteHeader[]>()
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    setError('')
    api.researchNotes().then((value) => { if (active) setNotes(value) })
      .catch((failure: Error) => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [attempt])

  return <section className="panel import-panel" aria-label="研究笔记">
    <div className="panel-heading"><h2>研究笔记</h2></div>
    {error ? <div className="panel-body">
      <p className="notice" role="alert">{error}</p>
      <button type="button" className="button secondary" onClick={() => setAttempt((value) => value + 1)}>重新读取</button>
    </div> : !notes ? <p className="loading" role="status">正在读取研究笔记…</p>
      : notes.length ? <ul className="note-list">{notes.map((item) => <li key={item.id}>
        <a href={`#/notes/research/${item.id}`}><strong>{item.title}</strong>
          <time dateTime={item.updatedAt}>{new Date(item.updatedAt).toLocaleDateString('zh-CN')}</time></a>
      </li>)}</ul> : <p className="panel-body muted">从已完成的研究保存一篇笔记。</p>}
  </section>
}

/** 编辑标题和 Markdown，并保持服务器的证据快照可跳转。 */
export function ResearchNotePage({ id }: { id: string }) {
  const [saved, setSaved] = useState<ResearchNote>()
  const [title, setTitle] = useState('')
  const [markdown, setMarkdown] = useState('')
  const [sources, setSources] = useState<Source[]>([])
  const [error, setError] = useState('')
  const [conflict, setConflict] = useState(false)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState('')
  const [attempt, setAttempt] = useState(0)
  const dirty = saved && (title !== saved.note.title || markdown !== saved.markdown)

  function load(value: ResearchNote) {
    setSaved(value)
    setTitle(value.note.title)
    setMarkdown(value.markdown)
    setConflict(false)
  }

  useEffect(() => {
    let active = true
    setError('')
    setNotice('')
    api.researchNote(id).then((value) => { if (active) load(value) })
      .catch((failure: Error) => { if (active) setError(failure.message) })
    api.questionSources().then((value) => { if (active) setSources(value) }).catch(() => {})
    return () => { active = false }
  }, [id, attempt])

  async function save(event: FormEvent) {
    event.preventDefault()
    if (!saved || !dirty || saving || conflict) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      load(await api.updateResearchNote(id, title, markdown, saved.note.revision))
      setNotice('笔记已保存')
    } catch (failure) {
      setConflict(failure instanceof ApiError && failure.code === 'RESEARCH_NOTE_REVISION_CONFLICT')
      setError(failure instanceof Error ? failure.message : '保存失败。')
    } finally {
      setSaving(false)
    }
  }

  return <>
    <div className="breadcrumbs"><a href="#/notes">返回笔记</a></div>
    <div className="page-heading note-page-heading"><div><h1>{saved?.note.title ?? '研究笔记'}</h1></div>
      {saved && <a className="button secondary"
        href={`/api/research-notes/${encodeURIComponent(id)}/export/markdown`}>下载已保存 Markdown</a>}
    </div>
    {error && <div className="panel-body">
      <p className="notice" role="alert">{error}</p>
      {(conflict || !saved) && <button type="button" className="button secondary" onClick={() => {
        if (!dirty || window.confirm('放弃当前未保存的改动并重新加载？')) setAttempt((value) => value + 1)
      }}>重新加载</button>}
    </div>}
    {!error && !saved && <p className="loading" role="status">正在读取研究笔记…</p>}
    {saved && <section className="panel">
      <form onSubmit={(event) => void save(event)}>
        <fieldset disabled={saving} className="form-body">
          <legend className="sr-only">编辑研究笔记</legend>
          <label className="field">标题<input value={title} maxLength={160} required
            onChange={(event) => { setTitle(event.target.value); setNotice('') }} /></label>
          <label className="field">Markdown 正文<textarea rows={12} value={markdown} maxLength={50000}
            onChange={(event) => { setMarkdown(event.target.value); setNotice('') }} /></label>
          <div className="library-actions">
            <button className="button primary" disabled={!dirty || !title.trim() || conflict}>
              {saving ? '正在保存…' : '保存笔记'}
            </button>
            <a className="button secondary" href={`#/transcript-questions?research=${saved.note.researchId}`}>打开原研究</a>
          </div>
          <p className="muted" role="status">{notice || (dirty ? '有未保存的改动' : '')}</p>
        </fieldset>
      </form>
      <div className="summary-content">
        <h2>原文引用</h2>
        <Citations citations={saved.citations} sources={sources} />
      </div>
    </section>}
  </>
}
