import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import type { SummaryCitation, TranscriptSegment, TranscriptSummaryResult } from './api'
import { SourceQuestion } from './TranscriptQuestion'

/** 在字幕资料中生成、读回摘要，并将引用定位到同一轨道的毫秒区间。 */
export function TranscriptSummary({ id, requestedEvidence }: { id: number; requestedEvidence?: string }) {
  const [result, setResult] = useState<TranscriptSummaryResult | null>()
  const [segments, setSegments] = useState<TranscriptSegment[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [queueing, setQueueing] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [selected, setSelected] = useState<number>()
  const [noteId, setNoteId] = useState<string>()
  const [savingNote, setSavingNote] = useState(false)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    Promise.all([api.summary(id), api.transcript(id)])
      .then(([summary, timeline]) => {
        if (active) {
          setResult(summary)
          setSegments(timeline)
        }
      })
      .catch((failure: Error) => { if (active) setError(failure.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, attempt])

  async function generate() {
    setGenerating(true)
    setError('')
    try {
      setResult(await api.generateSummary(id))
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '摘要生成失败。')
    } finally {
      setGenerating(false)
    }
  }

  /** 将当前字幕交给后台，任务中心可在本页关闭后继续查看。 */
  async function queueSummary() {
    setQueueing(true)
    setError('')
    try {
      await api.queueSummary(id)
      window.location.hash = '/tasks'
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '摘要任务提交失败。')
    } finally {
      setQueueing(false)
    }
  }

  const locate = useCallback((citation: SummaryCitation) => {
    // 引用 ID 的末段是片段序号，同时检查区间，避免重复时间段定位错误。
    const sequence = Number(citation.evidenceId.split(':').at(-1))
    const target = segments.find((segment) => segment.sequence === sequence &&
      segment.startMs === citation.startMs && segment.endMs === citation.endMs && segment.text === citation.text)
    if (!target) {
      setError('字幕片段无法定位，请刷新后再试。')
      return
    }
    setError('')
    setSelected(sequence)
    const cue = document.getElementById(`cue-${id}-${sequence}`)
    cue?.focus({ preventScroll: true })
    cue?.scrollIntoView({ block: 'center' })
  }, [id, segments])

  useEffect(() => {
    if (loading || !requestedEvidence || !segments.length) return
    let active = true
    setSelected(undefined)
    // 先由服务端校验资料、版本和片段编号，随后在当前时间轴定位。
    api.transcriptEvidence(id, requestedEvidence)
      .then((citation) => { if (active) locate(citation) })
      .catch((failure: Error) => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [id, loading, locate, requestedEvidence, segments.length])

  async function saveNote() {
    setSavingNote(true)
    setError('')
    try {
      setNoteId((await api.saveSummaryNote(id)).note.id)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '笔记保存失败。')
    } finally {
      setSavingNote(false)
    }
  }

  return <section className="panel transcript-summary" aria-labelledby="transcript-summary-title">
    <div className="panel-heading">
      <h2 id="transcript-summary-title">字幕摘要</h2>
      {result === null && <div className="library-actions">
        <button type="button" className="button secondary" disabled={loading || generating || queueing}
          onClick={() => void queueSummary()}>{queueing ? '正在提交…' : '后台生成摘要'}</button>
        <button type="button" className="button primary" disabled={loading || generating || queueing}
          onClick={generate}>{generating ? '正在生成…' : '生成摘要'}</button>
      </div>}
    </div>
    <div className="summary-content">
      {loading && <p role="status">正在读取字幕与摘要…</p>}
      {error && <div className="notice" role="alert">{error}</div>}
      {error && result === undefined && <button className="button secondary" type="button"
        onClick={() => setAttempt((value) => value + 1)}>重新读取</button>}
      {result === null && !generating && <p className="muted">生成时会把所选字幕片段发送给已配置的模型供应商。</p>}
      {generating && <p role="status">正在生成带引用的摘要…</p>}
      {result && <>
        <p className="summary-prose">{result.summary}</p>
        {result.truncated && <p className="muted">摘要基于预算内的部分字幕，引用只覆盖送入模型的片段。</p>}
        <div className="summary-citations" aria-label="摘要引用">
          {result.citations.map((citation, index) => <button key={citation.evidenceId} type="button"
            className="button secondary" onClick={() => locate(citation)}>
            引用 {index + 1} · {citation.startMs}–{citation.endMs} ms
          </button>)}
        </div>
        {noteId ? <a className="button secondary" href={`#/notes/${noteId}`}>打开笔记</a>
          : <button type="button" className="button secondary" disabled={savingNote} onClick={saveNote}>
            {savingNote ? '正在保存…' : '保存为笔记'}
          </button>}
      </>}
    </div>
    {segments.length > 0 && <SourceQuestion subject="字幕" request={(question) => api.askTranscript(id, question)}
      location={(citation) => `${citation.startMs}–${citation.endMs} ms`} onCite={locate} />}
    {segments.length > 0 && <div className="summary-timeline">
      <h3>字幕时间轴</h3>
      <ol>
        {segments.map((segment) => <li key={segment.sequence} id={`cue-${id}-${segment.sequence}`}
          tabIndex={-1} className={selected === segment.sequence ? 'cue-selected' : undefined}>
          <span>{segment.startMs}–{segment.endMs} ms</span>
          <p>{segment.text}</p>
        </li>)}
      </ol>
    </div>}
  </section>
}
