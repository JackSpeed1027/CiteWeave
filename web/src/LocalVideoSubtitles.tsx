import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from './api'
import type { LocalVideo, LocalVideoSubtitle } from './api'
import { LocalVideoTranscription } from './LocalVideoTranscription'

/** 展开时读取当前视频的字幕。轨道链接进入既有资料工作区。 */
export function LocalVideoSubtitles({ video }: { video: LocalVideo }) {
  const [open, setOpen] = useState(false)
  const [tracks, setTracks] = useState<LocalVideoSubtitle[]>()
  const [revision, setRevision] = useState(0)
  const [file, setFile] = useState<File>()
  const [busy, setBusy] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [uploadError, setUploadError] = useState('')
  const [result, setResult] = useState<{ track: LocalVideoSubtitle; created: boolean }>()
  const reloadTracks = useCallback(() => setRevision((value) => value + 1), [])

  useEffect(() => {
    if (!open) return
    let active = true
    setLoadError('')
    api.localVideoSubtitles(video.id).then((items) => {
      if (active) setTracks(items)
    }).catch((failure: unknown) => {
      if (active) setLoadError(failure instanceof Error ? failure.message : '读取字幕失败。')
    })
    return () => { active = false }
  }, [open, revision, video.id])

  async function attach(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || !file) return
    const form = event.currentTarget
    setBusy(true)
    setResult(undefined)
    setUploadError('')
    try {
      setResult(await api.uploadLocalVideoSubtitle(video.id, file))
      form.reset()
      setFile(undefined)
      setRevision((value) => value + 1)
    } catch (failure) {
      setUploadError(failure instanceof Error ? failure.message : '绑定字幕失败。')
    } finally {
      setBusy(false)
    }
  }

  const panelId = 'video-subtitles-' + video.id
  return <>
    <button className="button secondary" type="button" aria-expanded={open} aria-controls={panelId}
      disabled={busy} onClick={() => setOpen((value) => !value)}>{open ? '收起字幕' : '管理字幕'}</button>
    {open && <section id={panelId} className="video-subtitles" aria-label={video.filename + ' 字幕'}>
      <LocalVideoTranscription video={video} onPublished={reloadTracks} />
      <form onSubmit={attach} className="video-subtitle-form">
        <label className="field">选择字幕文件
          <input type="file" accept=".srt,.vtt" disabled={busy} onChange={(event) => {
            setResult(undefined)
            setUploadError('')
            setFile(event.target.files?.[0])
          }} />
        </label>
        <button type="submit" className="button primary" disabled={!file || busy}>
          {busy ? '正在绑定…' : '绑定字幕'}
        </button>
      </form>
      {uploadError && <p className="notice" role="alert">{uploadError}</p>}
      {result && <p className="success" role="status">
        {result.created ? '字幕已绑定。' : '已复用这条字幕。'}
        <a href={`#/sources/${result.track.sourceId}`}>打开字幕工作区</a>
      </p>}
      {loadError ? <div><p className="notice" role="alert">{loadError}</p>
        <button className="button secondary" type="button" onClick={() => setRevision((value) => value + 1)}>重新读取字幕</button>
      </div> : !tracks ? <p className="muted" role="status">正在读取字幕…</p> : tracks.length === 0
        ? <p className="muted">还没有绑定字幕。上传 SRT 或 VTT 后可打开时间轴。</p>
        : <ul className="note-citations">{tracks.map((track) => <li key={track.sourceId}>
          <a className="video-source-link" href={`#/sources/${track.sourceId}`}>{track.filename}</a>
          <span className="muted"> · {track.kind} · {track.trackSource === 'MACHINE_ASR' ? '机器转写' : '用户上传'}</span>
        </li>)}</ul>}
    </section>}
  </>
}
