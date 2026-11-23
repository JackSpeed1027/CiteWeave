import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, api } from './api'
import type { VideoPreview } from './api'

/** 仅允许把用户文件绑定到最近一次预览中确认的分 P。 */
export function BilibiliImport({ saved }: { saved: (id: number) => void }) {
  const [input, setInput] = useState('')
  const [preview, setPreview] = useState<VideoPreview>()
  const [page, setPage] = useState(0)
  const [file, setFile] = useState<File>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<'preview' | 'upload'>()
  const active = useRef(false)
  useEffect(() => {
    active.current = true
    return () => { active.current = false }
  }, [])
  const part = preview?.parts.find((item) => item.page === page)

  async function checkVideo() {
    setError('')
    setBusy('preview')
    try {
      const info = await api.previewBilibili(input.trim())
      setPreview(info)
      setPage(info.suggestedPart)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '无法预览视频。')
    } finally {
      setBusy(undefined)
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    if (!preview) {
      await checkVideo()
      return
    }
    if (!part || !file) return
    setError('')
    setBusy('upload')
    try {
      const { sourceId } = await api.uploadBilibiliSubtitle(preview.bvid, page, part.cid, file)
      if (active.current && window.location.hash === '#/import') saved(sourceId)
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === 'BILIBILI_PART_CHANGED') setPreview(undefined)
      setError(failure instanceof Error ? failure.message : '字幕导入失败。')
    } finally {
      setBusy(undefined)
    }
  }

  return <form onSubmit={submit}>
    <fieldset className="form-body" disabled={!!busy}>
      <legend className="sr-only">Bilibili 视频字幕</legend>
      <label className="field">视频链接或 BV / AV 编号
        <input placeholder="https://www.bilibili.com/video/…" required maxLength={2048} value={input}
          onChange={(event) => { setInput(event.target.value); setPreview(undefined); setFile(undefined); setError('') }} />
      </label>
      {!preview && <div className="form-actions">
        <button type="submit" className="button primary" disabled={!input.trim() || !!busy}>
          {busy === 'preview' ? '正在查询…' : '预览视频'}
        </button>
      </div>}
      {preview && <>
        <div><h3>{preview.title}</h3><p className="muted">{preview.bvid}</p></div>
        <label className="field video-field">选择分 P
          <select value={page} onChange={(event) => {
            setPage(Number(event.target.value)); setFile(undefined); setError('')
          }}>
            {preview.parts.map((item) => <option value={item.page} key={item.page}>P{item.page} · {item.title}</option>)}
          </select>
        </label>
        {part && <p className="muted">已选 P{part.page} · CID {part.cid} · {part.durationSeconds} 秒</p>}
        <label className="field">该分 P 的字幕文件
          <input key={page} type="file" accept=".srt,.vtt" onChange={(event) => {
            const selected = event.target.files?.[0]
            setFile(undefined)
            setError('')
            if (!selected) return
            if (!/\.(srt|vtt)$/i.test(selected.name) || !selected.size || selected.size > 10 * 1024 * 1024) {
              setError('请选择非空的 SRT 或 VTT 文件，最大 10 MB。')
              return
            }
            setFile(selected)
          }} />
        </label>
        <p className="muted">请上传你有权使用的字幕。当前由你指定字幕对应的分 P。</p>
        <button type="submit" className="button primary" disabled={!part || !file || !!busy}>
          {busy === 'upload' ? '正在绑定字幕…' : '确认分 P 并导入字幕'}
        </button>
      </>}
      {busy && <p role="status" className="muted">{busy === 'preview' ? '正在获取视频分 P…' : '正在复核分 P 并保存…'}</p>}
      {error && <div className="notice" role="alert">{error}</div>}
    </fieldset>
  </form>
}
