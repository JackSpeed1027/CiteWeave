import { useCallback, useEffect, useState } from 'react'
import { BookOpen } from 'lucide-react'
import { ApiError, api } from './api'
import type { DocumentCitation } from './api'
import { SourceQuestion } from './TranscriptQuestion'

/** 展示已有原文和服务器提供的段落或 PDF 页码证据。 */
export function DocumentReader({ id, content, pdf, requestedEvidence }: {
  id: number; content: string; pdf: boolean; requestedEvidence?: string
}) {
  const [locations, setLocations] = useState<DocumentCitation[]>()
  const [selected, setSelected] = useState<string>()
  const [error, setError] = useState('')
  const [legacyPdf, setLegacyPdf] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    setError('')
    setLegacyPdf(false)
    const read = pdf ? api.pdfPages : api.paragraphs
    read(id).then((result) => { if (active) setLocations(result) })
      .catch((failure: Error) => {
        if (!active) return
        if (failure instanceof ApiError && failure.code === 'SOURCE_PDF_PAGES_UNAVAILABLE') setLegacyPdf(true)
        else setError(failure.message)
      })
    return () => { active = false }
  }, [id, attempt, pdf])

  const locateEvidence = useCallback((evidenceId: string) => {
    // 只定位当前资料接口返回的完整版本 ID，拒绝别的资料或旧版本中的位置。
    const target = locations?.find((item) => item.evidenceId === evidenceId && item.sourceId === String(id))
    if (!target) {
      setSelected(undefined)
      setError('当前资料版本中找不到这条引用。')
      return
    }
    setError('')
    setSelected(target.evidenceId)
    const node = document.getElementById(target.evidenceId)
    node?.focus({ preventScroll: true })
    node?.scrollIntoView({ block: 'center' })
  }, [id, locations])

  useEffect(() => {
    if (requestedEvidence && (locations || legacyPdf)) locateEvidence(requestedEvidence)
  }, [requestedEvidence, locations, legacyPdf, locateEvidence])

  function label(citation: DocumentCitation) {
    return citation.locationType === 'page' ? `第 ${citation.page} 页` : `第 ${citation.paragraph} 段`
  }

  return <article className="panel">
    <div className="panel-heading"><h2><BookOpen size={18} /> 资料正文</h2></div>
    {locations && locations.length > 0 && <SourceQuestion subject="资料"
      request={(question) => api.askDocument(id, question)}
      location={label} onCite={(citation) => locateEvidence(citation.evidenceId)} />}
    {legacyPdf && <div className="panel-body"><p className="muted">此 PDF 只保留了历史正文。重新导入后可使用页码引用。</p></div>}
    {error && <div className="panel-body">
      <p className="notice" role="alert">{error}</p>
      <button className="button secondary" type="button" onClick={() => setAttempt((value) => value + 1)}>
        重新读取引用位置
      </button>
    </div>}
    {locations?.length ? <ol className="document-paragraphs">
      {locations.map((item, index) => <li id={item.evidenceId} key={item.evidenceId} tabIndex={-1}
        className={selected === item.evidenceId ? 'paragraph-selected' : undefined}>
        {(index === 0 || label(locations[index - 1]) !== label(item)) && <span className="muted">{label(item)}</span>}
        <pre className="reader">{item.text}</pre>
      </li>)}
    </ol> : <>
      {!error && !legacyPdf && !locations && <p className="loading" role="status">正在读取引用位置…</p>}
      <pre className="reader">{content}</pre>
    </>}
  </article>
}
