import { useEffect, useState } from 'react'
import { api } from './api'
import type { AsrStatus, LocalVideo } from './api'

const labels = { QUEUED: '排队中', RUNNING: '正在转写', FAILED: '转写失败', SUCCEEDED: '机器字幕已保存' }
const errors: Record<string, string> = {
  ASR_LEASE_EXPIRED: '执行中断或已超时，可以重试。',
  ASR_TIMEOUT: '转写耗时超过上限，可以重试或上传字幕。',
  ASR_PROCESS_FAILED: '本机转写无法生成字幕，请检查模型或改用手动上传。',
  ASR_PUBLICATION_FAILED: '无法保存生成的字幕，可以重试。',
}

/** 恢复已提交的转写任务。仅在任务进行中轮询。 */
export function LocalVideoTranscription({ video, onPublished }: { video: LocalVideo; onPublished: () => void }) {
  const [data, setData] = useState<AsrStatus>()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const task = data?.task

  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout>
    async function read() {
      try {
        const next = await api.videoTranscription(video.id)
        if (!active) return
        setData(next)
        setError('')
        if (next.task?.status === 'QUEUED' || next.task?.status === 'RUNNING') {
          timer = setTimeout(() => void read(), 1200)
        }
      } catch (failure) {
        if (active) setError(failure instanceof Error ? failure.message : '读取转写任务失败。')
      }
    }
    void read()
    return () => { active = false; clearTimeout(timer) }
  }, [video.id, revision])

  const published = task?.sourceId
  useEffect(() => {
    if (published != null) onPublished()
  }, [onPublished, published])

  async function submit(retry: boolean) {
    setPending(true)
    setError('')
    try {
      const next = await api.submitVideoTranscription(video.id, retry)
      setData({ available: true, task: next })
      setRevision((value) => value + 1)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '提交转写失败。')
    } finally {
      setPending(false)
    }
  }

  return <div className="video-subtitles">
    {!data && !error && <p className="muted" role="status">正在读取转写状态…</p>}
    {data && !data.available && <p className="muted">本机自动转写未启用，可上传字幕。</p>}
    {data?.available && !task && video.metadata.audioCodec === null && <p className="muted">视频无音轨，可上传字幕。</p>}
    {data?.available && !task && video.metadata.audioCodec !== null &&
      <button className="button secondary" type="button" disabled={pending}
        onClick={() => void submit(false)}>{pending ? '正在提交…' : '后台转写中文字幕'}</button>}
    {task && <p role="status" className="muted">{labels[task.status]} · 第 {task.attempt} 次执行</p>}
    {task?.status === 'FAILED' && <>
      <p role="alert" className="notice">{errors[task.errorCode ?? ''] ?? '转写失败，请稍后重试。'}</p>
      {data?.available && <button className="button secondary" type="button" disabled={pending}
        onClick={() => void submit(true)}>重试转写</button>}
    </>}
    {published != null && <a className="video-source-link" href={`#/sources/${published}`}>打开机器字幕</a>}
    {error && <div>
      <p role="alert" className="notice">{error}</p>
      <button className="button secondary" type="button" onClick={() => setRevision((value) => value + 1)}>刷新任务状态</button>
    </div>}
  </div>
}
