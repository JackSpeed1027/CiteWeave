import { useEffect, useState } from 'react'
import { Star } from 'lucide-react'
import { api } from './api'

/** 只在服务器确认后显示新状态，失败时保留状态并允许重试。 */
export function SourceFavorite({ id }: { id: number }) {
  const [favorited, setFavorited] = useState<boolean>()
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setError('')
    api.favorite(id)
      .then((result) => { if (active) setFavorited(result.favorited) })
      .catch((failure: Error) => { if (active) setError(failure.message) })
    return () => { active = false }
  }, [id, attempt])

  async function save() {
    if (busy || favorited === undefined) return
    setBusy(true)
    setError('')
    try {
      const result = await api.setFavorite(id, !favorited)
      setFavorited(result.favorited)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '收藏状态保存失败。')
    } finally {
      setBusy(false)
    }
  }

  return <div className="source-favorite">
    {favorited === undefined && !error && <p className="muted" role="status">正在读取收藏状态…</p>}
    {favorited !== undefined && <button type="button" className="button secondary" disabled={busy} onClick={save}>
      <Star size={16} aria-hidden="true" fill={favorited ? 'currentColor' : 'none'} />
      {busy ? '正在保存…' : favorited ? '取消收藏' : '收藏资料'}
    </button>}
    {favorited !== undefined && <span className="sr-only" aria-live="polite">{favorited ? '已收藏' : '未收藏'}</span>}
    {error && <p role="alert" className="notice">{error}</p>}
    {favorited === undefined && error && <button type="button" className="button secondary"
      onClick={() => setAttempt((value) => value + 1)}>重读收藏状态</button>}
  </div>
}
