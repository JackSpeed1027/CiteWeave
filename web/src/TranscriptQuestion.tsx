import { useState } from 'react'
import type { FormEvent } from 'react'
import type { TranscriptAnswer } from './api'

/** 复用问答表单，调用方负责资料种类和引用定位。 */
export function SourceQuestion<Citation extends { evidenceId: string }>({ subject, request, location, onCite }: {
  subject: '字幕' | '资料'
  request: (question: string) => Promise<TranscriptAnswer<Citation>>
  location: (citation: Citation) => string
  onCite: (citation: Citation) => void
}) {
  const [question, setQuestion] = useState('')
  const [submitted, setSubmitted] = useState('')
  const [result, setResult] = useState<TranscriptAnswer<Citation>>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function ask(event: FormEvent) {
    event.preventDefault()
    const text = question.trim()
    if (busy || !text) return
    setBusy(true)
    setError('')
    setResult(undefined)
    setSubmitted(text)
    try {
      setResult(await request(text))
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '问题提交失败。')
    } finally {
      setBusy(false)
    }
  }

  return <section className="transcript-question" aria-labelledby="transcript-question-title">
    <h3 id="transcript-question-title">问这份{subject}</h3>
    <form className="question-form" onSubmit={ask}>
      <label className="field">问题
        <input value={question} maxLength={500} required
          placeholder={`用${subject}中出现的关键词提问`} onChange={(event) => setQuestion(event.target.value)} />
      </label>
      <button className="button primary" type="submit" disabled={busy || !question.trim()}>
        {busy ? '正在回答…' : '提问'}
      </button>
    </form>
    <p className="muted">有匹配时，会把问题和相关{subject}片段发给已配置的模型供应商。</p>
    {busy && <p role="status">正在检索并回答…</p>}
    {error && <p className="notice" role="alert">{error}</p>}
    {result && <div className="question-result" aria-live="polite">
      <p className="muted">{submitted}</p>
      {result.status === 'INSUFFICIENT_EVIDENCE' ? <p>这份{subject}中证据不足，无法回答。可尝试使用原文关键词。</p> : <>
        <p className="summary-prose">{result.answer}</p>
        <div className="summary-citations" aria-label="回答引用">
          {result.citations.map((citation, index) => <button className="button secondary" type="button"
            key={citation.evidenceId} onClick={() => onCite(citation)}>
            回答引用 {index + 1} · {location(citation)}
          </button>)}
        </div>
      </>}
    </div>}
  </section>
}
