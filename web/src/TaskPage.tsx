import { useEffect, useState } from 'react'
import { api } from './api'
import type { Source, SummaryTask } from './api'

const statusLabels = { QUEUED: '待处理', RUNNING: '进行中', SUCCEEDED: '已完成', FAILED: '失败' }
const failureMessages: Record<string, string> = {
  SUMMARY_MODEL_NOT_CONFIGURED: '摘要模型尚未配置。',
  SUMMARY_MODEL_TIMEOUT: '生成摘要超时。',
  SUMMARY_MODEL_RATE_LIMITED: '模型限流，请稍后重试。',
  TASK_LEASE_EXPIRED: '原执行已超期。重试后会重新确认摘要是否已生成。',
}

/** 读取实际任务状态，轮询仅在有在途任务时进行。 */
export function TaskPage() {
  const [tasks, setTasks] = useState<SummaryTask[]>()
  const [sources, setSources] = useState<Source[]>([])
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [retrying, setRetrying] = useState<string>()
  const [stateFilter, setStateFilter] = useState('ALL')

  useEffect(() => {
    let active = true
    let timer: number | undefined
    async function load() {
      try {
        const values = await api.summaryTasks()
        if (!active) return
        setTasks(values)
        setError('')
        if (values.some((task) => task.status === 'QUEUED' || task.status === 'RUNNING')) {
          timer = window.setTimeout(() => void load(), 1200)
        }
      } catch (failure) {
        if (active) setError(failure instanceof Error ? failure.message : '任务状态读取失败。')
      }
    }
    void load()
    return () => { active = false; window.clearTimeout(timer) }
  }, [attempt])

  useEffect(() => {
    let active = true
    api.list().then((value) => { if (active) setSources(value) }).catch(() => {})
    return () => { active = false }
  }, [])

  async function retry(id: string) {
    setRetrying(id)
    setError('')
    try {
      const queued = await api.retrySummaryTask(id)
      setTasks((items) => items?.map((task) => task.id === id ? queued : task))
      setAttempt((value) => value + 1)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '任务重试失败。')
    } finally {
      setRetrying(undefined)
    }
  }

  const visible = tasks?.filter((task) => stateFilter === 'ALL' || task.status === stateFilter)
  return <>
    <div className="page-heading"><div><h1>任务中心</h1><p>查看后台字幕摘要，完成后可从原资料打开。</p></div>
      <button type="button" className="button secondary" onClick={() => setAttempt((value) => value + 1)}>刷新任务</button>
    </div>
    <section className="panel import-panel" aria-label="后台任务">
      <div className="panel-body"><label className="field">任务状态
        <select value={stateFilter} onChange={(event) => setStateFilter(event.target.value)}>
          <option value="ALL">全部任务</option>
          {Object.entries(statusLabels).map(([status, label]) => <option key={status} value={status}>{label}</option>)}
        </select>
      </label></div>
      {error && <p className="panel-body notice" role="alert">{error}</p>}
      {!error && !tasks && <p className="loading" role="status">正在读取任务…</p>}
      {visible?.length === 0 && <div className="panel-body">
        <p>{tasks?.length ? '没有符合此状态的任务。' : '还没有后台任务。'}</p>
        <a className="button secondary" href="#/library">打开资料库</a>
      </div>}
      <ul className="note-citations task-list">
        {visible?.map((task) => <li key={task.id}>
          <strong>{sources.find((source) => source.id === task.sourceId)?.title ?? `资料 ${task.sourceId}`}</strong>
          <p className="muted" role="status">{statusLabels[task.status]} · 已执行 {task.attempt} 次</p>
          {task.errorCode && <p className="notice">{failureMessages[task.errorCode] ?? '摘要生成失败。'} <code>{task.errorCode}</code></p>}
          <div className="library-actions">
            <a className="button secondary" href={`#/sources/${task.sourceId}`}>{task.status === 'SUCCEEDED' ? '打开摘要' : '打开资料'}</a>
            {task.status === 'FAILED' && <button type="button" className="button secondary" disabled={Boolean(retrying)}
              onClick={() => void retry(task.id)}>{retrying === task.id ? '正在重试…' : '重试任务'}</button>}
          </div>
        </li>)}
      </ul>
    </section>
  </>
}
