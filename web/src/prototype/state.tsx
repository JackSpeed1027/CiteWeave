import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { advance, load, persist, type BrowserDirectory } from './services'
import type { Destination, Model, Stage } from './mock'

type Store = {
  model: Model
  update: (fn: (model: Model) => Model) => void
  now: number
  toast: (message: string) => void
  message: string
  roots: Partial<Record<Destination, BrowserDirectory>>
  setRoot: (key: Destination, root?: BrowserDirectory) => void
  taskAction: (id: string, action: 'retry' | 'cancel') => void
}
const Context = createContext<Store | null>(null)

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [model, update] = useState(load)
  const [now, setNow] = useState(Date.now)
  const [message, setMessage] = useState('')
  const [roots, setRoots] = useState<Store['roots']>({})
  const toast = useCallback((text: string) => setMessage(text), [])
  useEffect(() => {
    try { persist(model) } catch { toast('浏览器存储不可用，本次修改可能无法在刷新后恢复。') }
  }, [model,toast])
  useEffect(() => {
    if (!message) return
    const timer = setTimeout(() => setMessage(''),3700)
    return () => clearTimeout(timer)
  }, [message])
  const active = model.tasks.some(t => t.stage === 'running' || t.stage === 'queued') || model.chats.some(c => c.runs.some(r => r.status === 'running'))
  useEffect(() => {
    if (!active) return
    function tick() {
      const next = Date.now()
      setNow(next)
      update(previous => advance(previous,next))
    }
    tick()
    const timer = setInterval(tick,125)
    return () => clearInterval(timer)
  }, [active])
  const taskAction: Store['taskAction'] = (id, action) => {
    update(m => {
      const task = m.tasks.find(t => t.id===id)
      if (!task) return m
      const stage: Stage = action==='cancel' ? 'cancelled' : 'running'
      return {
        ...m,
        tasks:m.tasks.map(t => t.id === id ? { ...t,stage,started:Date.now(),fail:false } : t),
        sources:m.sources.map(s => s.id===task.sourceId ? {...s,status:stage} : s),
      }
    })
    toast(action==='cancel' ? '已取消演示任务' : '已重新开始演示任务')
  }
  return <Context.Provider value={{model,update,now,toast,message,roots,setRoot:(key,root)=>setRoots(previous=>({...previous,[key]:root})),taskAction}}>{children}</Context.Provider>
}

export function useWorkspace() {
  const value = useContext(Context)
  if (!value) throw new Error('Workspace provider is missing')
  return value
}
