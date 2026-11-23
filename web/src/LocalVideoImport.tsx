import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { CircleCheck, LoaderCircle, Video } from 'lucide-react'
import { api } from './api'
import type { LocalVideo, LocalVideoPreview } from './api'
import { LocalVideoSubtitles } from './LocalVideoSubtitles'

/** 检查本地视频的真实媒体信息，保留所选文件以便遇到可恢复错误时重试。 */
export function LocalVideoImport() {
  const [file, setFile] = useState<File>()
  const [busy, setBusy] = useState<'checking' | 'saving'>()
  const [preview, setPreview] = useState<LocalVideoPreview>()
  const [error, setError] = useState('')
  const [records, setRecords] = useState<LocalVideo[]>()
  const [listError, setListError] = useState('')
  const [savedMessage, setSavedMessage] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    setListError('')
    api.localVideos().then((items) => {
      if (active) setRecords(items)
    }).catch((failure: unknown) => {
      if (active) setListError(failure instanceof Error ? failure.message : '视频记录读取失败。')
    })
    return () => { active = false }
  }, [revision])

  async function check(event: FormEvent) {
    event.preventDefault()
    if (!file || busy) return
    setBusy('checking')
    setError('')
    setPreview(undefined)
    setSavedMessage('')
    try {
      setPreview(await api.previewLocalVideo(file))
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '视频检查失败，请重试。')
    } finally {
      setBusy(undefined)
    }
  }

  async function save() {
    if (!file || !preview || busy) return
    setBusy('saving')
    setError('')
    try {
      const { video, created } = await api.saveLocalVideo(file)
      setSavedMessage(created ? `「${video.filename}」已保存。` : `已存在同内容视频「${video.filename}」，本次复用原记录。`)
      setRevision((previous) => previous + 1)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '保存视频失败，请重试。')
    } finally {
      setBusy(undefined)
    }
  }

  return <><form className="form-body" onSubmit={check}>
    <p className="muted">先检查 MP4 或 MOV 的时长和轨道，再确认保存到 CiteWeave 的媒体存储。</p>
    <label className="field">选择本地视频
      <input type="file" accept=".mp4,.mov" disabled={Boolean(busy)} onChange={(event) => {
        setFile(event.target.files?.[0])
        setError('')
        setPreview(undefined)
        setSavedMessage('')
      }} />
    </label>
    {error && <p className="notice" role="alert">{error}</p>}
    {preview && <section aria-label="预检结果" role="status">
      <p className="success"><CircleCheck size={16} aria-hidden="true" /> <strong>视频可读取</strong></p>
      <dl className="video-preview-metadata">
        <div><dt>时长</dt><dd>{(preview.durationMs / 1000).toLocaleString('zh-CN', { maximumFractionDigits: 3 })} 秒</dd></div>
        <div><dt>分辨率</dt><dd>{preview.width} × {preview.height}</dd></div>
        <div><dt>视频编码</dt><dd>{preview.videoCodec}</dd></div>
        <div><dt>音轨</dt><dd>{preview.audioCodec ?? '无音轨'}</dd></div>
        <div><dt>大小</dt><dd>{preview.sizeBytes.toLocaleString('zh-CN')} 字节</dd></div>
      </dl>
    </section>}
    <div className="form-actions video-actions">
      <a className="button secondary" href="#/library">返回资料库</a>
      <button type="submit" className={`button ${preview ? 'secondary' : 'primary'}`} disabled={!file || Boolean(busy)}>
        {busy === 'checking' ? <LoaderCircle className="spin" size={17} /> : <Video size={17} />}
        {busy === 'checking' ? '正在检查…' : '检查视频'}
      </button>
      {preview && <button type="button" className="button primary" disabled={Boolean(busy) || Boolean(savedMessage)}
        onClick={() => void save()}>{busy === 'saving' ? '正在保存…' : '保存视频'}</button>}
    </div>
    {savedMessage && <p className="success" role="status">{savedMessage}</p>}
  </form>
    <section className="form-body" aria-label="最近保存的视频">
      <h2>最近保存的视频</h2>
      {!records && !listError && <p className="loading" role="status">正在读取视频记录…</p>}
      {listError && <div><p className="notice" role="alert">{listError}</p>
        <button type="button" className="button secondary" onClick={() => setRevision((value) => value + 1)}>重新读取</button></div>}
      {!listError && records?.length === 0 && <p className="muted">还没有保存的视频。</p>}
      {records && records.length > 0 && <ul className="note-citations local-video-records">
        {records.map((video) => <li key={video.id}>
          <strong>{video.filename}</strong>
          <p className="muted">{video.metadata.width} × {video.metadata.height} · {(video.metadata.durationMs / 1000).toLocaleString('zh-CN')} 秒 · {new Date(video.createdAt).toLocaleString('zh-CN')}</p>
          <LocalVideoSubtitles video={video} />
        </li>)}
      </ul>}
    </section>
  </>
}
