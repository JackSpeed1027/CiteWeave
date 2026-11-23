import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from './api'
import type { ResearchHeader, ResearchResult, ResearchStep, Source, SourceCitation, TranscriptAnswer } from './api'

type Mode = 'evidence' | 'answer' | 'research'
type Result = { kind: 'evidence'; citations: SourceCitation[] } |
  ({ kind: 'answer' } & TranscriptAnswer<SourceCitation>) |
  ({ kind: 'research' } & ResearchResult)

/** 保留三种证据坐标的原始单位。 */
function citationLocation(citation: SourceCitation) {
  switch (citation.locationType) {
    case 'page': return `第 ${citation.page} 页`
    case 'paragraph': return `第 ${citation.paragraph} 段`
    case 'transcript': return `${citation.startMs}–${citation.endMs} ms`
  }
}

/** 展示成功和失败请求中经服务端确认的已完成步骤。 */
function ResearchTrace({ steps, sources }: { steps: ResearchStep[]; sources: Source[] }) {
  return <section className="research-trace" aria-label="研究轨迹">
    <h2>研究轨迹</h2>
    <ol className="note-citations">
      {steps.map((step) => {
        const sourceId = step.evidenceIds[0]?.split(':')[0]
        const title = sources.find((source) => String(source.id) === sourceId)?.title
        return <li key={step.callId}>
          {step.tool === 'search_evidence'
            ? `搜索证据 · ${step.evidenceIds.length} 条候选`
            : `读取原句 · ${title ?? '1 条证据'}`}
        </li>
      })}
    </ol>
  </section>
}

/** 历史与即时结果复用原资料的版本化定位链接。 */
export function Citations({ citations, sources, label = '引用' }: {
  citations: SourceCitation[]; sources: Source[]; label?: string
}) {
  if (!citations.length) return null
  return <ol className="note-citations">
    {citations.map((citation, index) => <li key={citation.evidenceId}>
      <a className="button secondary multi-citation-link"
        href={`#/sources/${citation.sourceId}?evidence=${encodeURIComponent(citation.evidenceId)}`}>
        {label} {index + 1} · {sources.find((item) => String(item.id) === citation.sourceId)?.title ?? citation.sourceId}
        {' · '}{citationLocation(citation)}
      </a>
      <p>{citation.text}</p>
    </li>)}
  </ol>
}

function researchLink(id: string) {
  return `#/transcript-questions?research=${encodeURIComponent(id)}`
}

const researchForm = '#/transcript-questions'

function pendingLink(key: string) {
  return `${researchForm}?pending=${encodeURIComponent(key)}`
}

/** 近期记录和研究表单独立加载，历史查询失败时仍然可以搜索证据。 */
function RecentResearch({ revision }: { revision: number }) {
  const [records, setRecords] = useState<ResearchHeader[]>()
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    let active = true
    setError('')
    api.recentResearch().then((items) => { if (active) setRecords(items) })
      .catch((failure: Error) => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [revision, retry])

  return <section id="recent-research" tabIndex={-1} className="panel import-panel" aria-label="最近研究">
    <div className="panel-heading"><h2>最近研究</h2></div>
    {error ? <div className="panel-body">
      <p className="notice" role="alert">{error}</p>
      <button className="button secondary" type="button" onClick={() => setRetry((value) => value + 1)}>重试读取记录</button>
    </div> : !records ? <p className="loading" role="status">正在读取研究记录…</p>
      : records.length ? <ul className="note-list">
        {records.map((record) => <li key={record.id}><a href={researchLink(record.id)}>
          <strong>{record.question}</strong>
          <time dateTime={record.createdAt}>{new Date(record.createdAt).toLocaleDateString('zh-CN')}</time>
        </a></li>)}
      </ul> : <p className="panel-body muted">完成一次研究后，可在这里重新打开结果。</p>}
  </section>
}

/** 将已持久化的研究结果保存为可编辑笔记。 */
function SaveResearchNote({ id }: { id: string }) {
  const [creating, setCreating] = useState(false)
  const [noteError, setNoteError] = useState('')

  async function openNote() {
    setCreating(true)
    setNoteError('')
    try {
      const note = await api.createResearchNote(id)
      window.location.hash = `/notes/research/${note.note.id}`
    } catch (failure) {
      setNoteError(failure instanceof Error ? failure.message : '保存研究笔记失败。')
    } finally {
      setCreating(false)
    }
  }

  return <>
    <button type="button" className="button primary" disabled={creating}
      onClick={() => void openNote()}>{creating ? '正在保存…' : '保存为笔记'}</button>
    {noteError && <p className="notice" role="alert">{noteError}</p>}
  </>
}

/** 按链接读回已保存的记录。 */
export function SavedResearchPage({ id }: { id: string }) {
  const [data, setData] = useState<ResearchResult>()
  const [sources, setSources] = useState<Source[]>([])
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    let active = true
    setError('')
    api.researchRecord(id).then((record) => { if (active) setData(record) })
      .catch((failure: Error) => { if (active) setError(failure.message) })
    api.questionSources().then((items) => { if (active) setSources(items) }).catch(() => {})
    return () => { active = false }
  }, [id, retry])

  return <>
    <div className="breadcrumbs"><a href="#/transcript-questions">返回跨资料问答</a></div>
    <div className="page-heading note-page-heading"><div>
      <h1>{data?.record.question ?? '研究记录'}</h1>
      {data && <p>研究记录 · <time dateTime={data.record.createdAt}>{new Date(data.record.createdAt).toLocaleString('zh-CN')}</time></p>}
    </div></div>
    {error && <div className="panel-body"><p className="notice" role="alert">{error}</p>
      <button className="button secondary" type="button" onClick={() => setRetry((value) => value + 1)}>重新加载</button>
    </div>}
    {!data && !error && <p className="loading" role="status">正在读取研究记录…</p>}
    {data && <article className="panel summary-content">
      <p className="summary-prose">{data.answer ?? '所选资料中证据不足，无法回答。'}</p>
      {data.status === 'ANSWERED' && <SaveResearchNote id={id} />}
      {data.trace.length > 0 && <ResearchTrace steps={data.trace} sources={sources} />}
      <Citations citations={data.citations} sources={sources} />
    </article>}
  </>
}

/** 显式限定本轮范围，并用服务端返回的资料与证据 ID 生成原文入口。 */
export function MultiTranscriptPage({ pendingKey }: { pendingKey?: string }) {
  const [sources, setSources] = useState<Source[]>()
  const [loadError, setLoadError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [selected, setSelected] = useState<number[]>([])
  const [filter, setFilter] = useState('')
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState<Result>()
  const [error, setError] = useState('')
  const [errorTrace, setErrorTrace] = useState<ResearchStep[]>([])
  const [outcomeUnknown, setOutcomeUnknown] = useState(false)
  const [requestRunning, setRequestRunning] = useState(false)
  const [canResume, setCanResume] = useState(false)
  const [nextRequestKey, setNextRequestKey] = useState<string>()
  const [restoreAttempt, setRestoreAttempt] = useState(0)
  const [restoring, setRestoring] = useState(Boolean(pendingKey))
  const [busy, setBusy] = useState<Mode>()
  const [historyRevision, setHistoryRevision] = useState(0)
  const researchRequest = useRef<{ payload: string; key: string; resumeFrom?: string } | undefined>(undefined)

  useEffect(() => {
    if (!pendingKey) return
    let active = true
    setRestoring(true)
    setError('')
    api.researchRequestStatus(pendingKey).then((status) => {
      if (!active) return
      if (status.recordId) {
        window.location.replace(researchLink(status.recordId))
        return
      }
      setQuestion(status.question)
      setSelected(status.sourceIds)
      setErrorTrace(status.trace)
      setRequestRunning(status.status === 'RUNNING')
      setOutcomeUnknown(status.status === 'UNKNOWN')
      setCanResume(status.canResume)
      setNextRequestKey(status.nextRequestKey ?? undefined)
      setError({
        RUNNING: '研究仍在处理中。可以稍后重新查询状态。',
        UNKNOWN: '原研究请求结果未知。请查看已确认步骤，再决定是否继续。',
        FAILED: '原研究请求已失败。可以发起新一轮研究。',
        COMPLETED: '',
      }[status.status])
      if (status.status === 'UNKNOWN') {
        researchRequest.current = { key: pendingKey, payload: JSON.stringify([status.sourceIds, status.question]) }
      }
    }).catch((failure: Error) => { if (active) setError(failure.message) })
      .finally(() => { if (active) setRestoring(false) })
    return () => { active = false }
  }, [pendingKey, restoreAttempt])

  useEffect(() => {
    let active = true
    setLoadError('')
    api.questionSources().then((items) => {
      if (active) setSources(items)
    }).catch((failure: Error) => { if (active) setLoadError(failure.message) })
    return () => { active = false }
  }, [attempt])

  /** 修改输入意味着准备新研究，原请求仍可通过已复制的链接查看。 */
  function clearRequestFeedback() {
    setResult(undefined)
    setError('')
    setErrorTrace([])
    setOutcomeUnknown(false)
    setRequestRunning(false)
    setCanResume(false)
    setNextRequestKey(undefined)
    window.history.replaceState(null, '', researchForm)
  }

  function select(id: number, checked: boolean) {
    clearRequestFeedback()
    setSelected((previous) => checked
      ? (previous.length < 3 ? [...previous, id] : previous)
      : previous.filter((item) => item !== id))
  }

  /** 三个操作共享明确选中的范围，查证据无需向模型提交内容。 */
  async function run(mode: Mode, recovery?: 'restart' | 'resume') {
    if (busy || restoring || selected.length < (mode === 'answer' ? 2 : 1) || !question.trim()
      || (mode === 'research' && requestRunning)
      || (mode === 'research' && outcomeUnknown && !recovery)) return
    setBusy(mode)
    setResult(undefined)
    setError('')
    setErrorTrace([])
    setOutcomeUnknown(false)
    try {
      if (mode === 'evidence') {
        setResult({ kind: mode, citations: await api.searchEvidence(selected, question.trim()) })
      } else if (mode === 'answer') {
        setResult({ kind: mode, ...await api.askSources(selected, question.trim()) })
      } else {
        const text = question.trim()
        const payload = JSON.stringify([selected, text])
        // 同一页面内，响应丢失或服务仍在处理时保留旧键供手动重试。
        if (recovery || researchRequest.current?.payload !== payload) {
          const resumeFrom = recovery === 'resume' ? researchRequest.current?.key : undefined
          researchRequest.current = { payload, key: crypto.randomUUID(), resumeFrom }
        }
        const { key, resumeFrom } = researchRequest.current
        // 地址只保存 UUID；刷新时通过 GET 重新加载，避免无意重发模型请求。
        window.history.replaceState(null, '', pendingLink(key))
        const saved = await api.runResearch(selected, text, key, resumeFrom)
        window.history.replaceState(null, '', researchForm)
        researchRequest.current = undefined
        setResult({ kind: mode, ...saved })
        setHistoryRevision((value) => value + 1)
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '请求失败，请重试。')
      if (mode === 'research' && failure instanceof ApiError) {
        setErrorTrace(failure.trace)
        const unknown = failure.code === 'RESEARCH_REQUEST_OUTCOME_UNKNOWN'
        setOutcomeUnknown(unknown)
        setCanResume(unknown && failure.trace.length > 0)
        // 服务端已有明确业务结论时，下一次点击通常代表开始新一轮。
        if (!unknown && failure.code?.startsWith('RESEARCH_') && failure.code !== 'RESEARCH_REQUEST_IN_PROGRESS') {
          researchRequest.current = undefined
        }
      }
    } finally {
      setBusy(undefined)
    }
  }

  function ask(event: FormEvent) {
    event.preventDefault()
    void run('answer')
  }

  const visible = sources?.filter((item) => selected.includes(item.id) ||
    item.title.toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()))
  return <>
    <div className="breadcrumbs"><a href="#/library">返回资料库</a></div>
    <div className="page-heading"><div><h1>跨资料问答</h1><p>选择字幕、文本、Markdown 或有页码证据的 PDF。查证据和研究需 1–3 份，提问需 2–3 份。</p></div></div>
    {loadError && <div className="panel-body"><p role="alert" className="notice">{loadError}</p>
      <button type="button" className="button secondary" onClick={() => setAttempt((value) => value + 1)}>重试读取资料</button></div>}
    {!sources && !loadError && <p role="status" className="loading">正在读取可引用资料…</p>}
    {restoring && <p role="status" className="loading">正在恢复研究请求…</p>}
    {sources?.length === 0 && <div className="panel-body"><p>先导入一份可引用的资料。</p>
      <a className="button secondary" href="#/import">导入资料</a></div>}
    {sources && sources.length > 0 && <section className="panel import-panel">
      <form onSubmit={ask}>
        <fieldset disabled={Boolean(busy) || restoring} className="form-body">
          <legend className="sr-only">选择资料与问题</legend>
          <label className="field">搜索资料
            <input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="输入资料标题" />
          </label>
          <p className="muted" role="status">已选 {selected.length} / 3 份</p>
          <div className="multi-source-options">
            {visible?.map((source) => {
              const checked = selected.includes(source.id)
              return <label key={source.id} className="multi-source-option">
                <input type="checkbox" checked={checked} disabled={!checked && selected.length === 3}
                  onChange={(event) => select(source.id, event.target.checked)} />
                <span>{source.title} <small className="muted">· {source.kind}</small></span>
              </label>
            })}
            {!visible?.length && <p className="muted">没有符合搜索条件的资料。</p>}
          </div>
          <label className="field">问题
            <input value={question} onChange={(event) => {
              setQuestion(event.target.value)
              clearRequestFeedback()
            }}
              required maxLength={500} placeholder="例如：这些资料分别如何解释缓存失效？" />
          </label>
          <p className="muted">查证据无需模型。提问和研究会使用已配置的模型供应商处理问题与相关证据。</p>
          <div className="library-actions">
            <button type="button" className="button secondary" disabled={!selected.length || !question.trim()}
              onClick={() => void run('evidence')}>
              {busy === 'evidence' ? '正在检索…' : '查证据'}
            </button>
            <button className="button primary" disabled={selected.length < 2 || !question.trim()}>
              {busy === 'answer' ? '正在回答…' : '提问'}
            </button>
            <button type="button" className="button secondary" disabled={!selected.length || !question.trim() || outcomeUnknown || requestRunning}
              onClick={() => void run('research')}>
              {busy === 'research' ? '正在研究…' : '开始研究'}
            </button>
          </div>
        </fieldset>
      </form>
      {error && <div className="summary-content">
        <p className="notice" role="alert">{error}</p>
        {requestRunning && <button type="button" className="button secondary" disabled={restoring}
          onClick={() => setRestoreAttempt((value) => value + 1)}>重新查询状态</button>}
        {nextRequestKey && <a className="button secondary" href={pendingLink(nextRequestKey)}>查看已发起的续跑</a>}
        {outcomeUnknown && <>
          <p className="muted">续跑沿用已确认步骤。从头重跑会重新执行工具。两种操作都可能再次调用模型。</p>
          <div className="library-actions">
            <button type="button" className="button secondary" onClick={() => {
              const recent = document.getElementById('recent-research')
              recent?.focus({ preventScroll: true })
              recent?.scrollIntoView({ block: 'start' })
            }}>查看最近研究</button>
            {canResume && <button type="button" className="button primary"
              onClick={() => void run('research', 'resume')}>从检查点继续</button>}
            <button type="button" className="button secondary" onClick={() => void run('research', 'restart')}>
              用新请求重新研究
            </button>
          </div>
        </>}
        {errorTrace.length > 0 && <ResearchTrace steps={errorTrace} sources={sources} />}
      </div>}
      {result && <div className="summary-content" aria-live="polite">
        {result.kind === 'evidence'
          ? <p>{result.citations.length ? `找到 ${result.citations.length} 条候选证据` : '所选资料中没有匹配的证据。'}</p>
          : <p className="summary-prose">{result.answer ?? '所选资料中证据不足，无法回答。'}</p>}
        {result.kind === 'research' && <>
          <a className="button secondary" href={researchLink(result.record.id)}>打开已保存的研究</a>
          {result.status === 'ANSWERED' && <SaveResearchNote id={result.record.id} />}
          {result.trace.length > 0 && <ResearchTrace steps={result.trace} sources={sources} />}
        </>}
        <Citations citations={result.citations} sources={sources} label={result.kind === 'evidence' ? '证据' : '引用'} />
      </div>}
    </section>}
    <RecentResearch revision={historyRevision} />
  </>
}
