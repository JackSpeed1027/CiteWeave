import { useEffect, useState } from 'react'
import { Link, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Activity, BookOpen, ChevronRight, Command, FileText, FolderOpen, House, Layers3, Menu, Plug, Plus, Search, Settings2, Sparkles, Upload, X } from 'lucide-react'
import { Home } from './Home'
import { Collections, Library } from './Library'
import { ImportPage } from './Import'
import { Tasks } from './Tasks'
import { Agent, SourceWorkspace } from './Research'
import { Notes } from './Notes'
import { Integrations } from './Destinations'
import { Settings } from './Settings'
import { Empty, IconButton, Modal, TypeIcon } from './ui'
import { useWorkspace } from './state'

const mainNav = [
  ['/', '工作台', House], ['/library','知识库',BookOpen], ['/collections','收藏夹',FolderOpen],
  ['/import','导入中心',Upload], ['/agent','Agent 工作台',Sparkles], ['/notes','笔记与文档',FileText],
] as const
const extraNav = [['/tasks','任务中心',Activity],['/integrations','外部集成',Plug],['/settings','设置',Settings2]] as const

export function App() {
  const {model,message}=useWorkspace()
  const {pathname}=useLocation()
  const navigate=useNavigate()
  const [search,setSearch]=useState(false)
  const [query,setQuery]=useState('')
  const [menu,setMenu]=useState(false)
  const active=[...mainNav,...extraNav].find(([path])=>path==='/'?pathname==='/':pathname.startsWith(path))
  const pending=model.tasks.filter(t=>['queued','running','action'].includes(t.stage)).length
  const openSearch=()=>{setQuery('');setSearch(true)}
  const jump=(path:string)=>{setSearch(false);setMenu(false);navigate(path)}
  useEffect(()=>{
    const onKey=(event:KeyboardEvent)=>{
      if ((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='k') {
        event.preventDefault()
        setSearch(open=>!open)
        setQuery('')
      }
    }
    window.addEventListener('keydown',onKey)
    return ()=>window.removeEventListener('keydown',onKey)
  },[])
  useEffect(()=>{document.documentElement.dataset.theme=model.settings.theme},[model.settings.theme])
  useEffect(()=>{window.scrollTo(0,0)},[pathname])
  const q=query.toLowerCase().trim()
  const sources=model.sources.filter(s=>(s.title+' '+s.origin).toLowerCase().includes(q)).slice(0,6)
  const notes=model.notes.filter(n=>n.title.toLowerCase().includes(q)).slice(0,5)
  return <div className="app">
    {menu&&<button className="mobile-scrim" aria-label="关闭导航" onClick={()=>setMenu(false)}/>}
    <aside className={'sidebar '+(menu?'mobile-open':'')}>
      <div className="brand-line"><Link className="brand" to="/" onClick={()=>setMenu(false)}><span className="brand-icon"><Layers3 size={22}/></span><strong>CiteWeave</strong></Link><span className="mobile-close"><IconButton label="关闭导航面板" onClick={()=>setMenu(false)}><X size={18}/></IconButton></span></div>
      <button className="sidebar-search" onClick={openSearch}><Search size={16}/><span>搜索工作区</span><kbd><Command size={11}/> K</kbd></button>
      <nav aria-label="工作区导航"><div className="nav-label">工作空间</div>
        {mainNav.map(([path,label,Icon])=><NavLink end={path==='/'} key={path} to={path} onClick={()=>setMenu(false)}><Icon size={18}/><span>{label}</span></NavLink>)}
        <div className="nav-label secondary-label">管理</div>
        {extraNav.map(([path,label,Icon])=><NavLink key={path} to={path} onClick={()=>setMenu(false)}><Icon size={18}/><span>{label}</span>{path==='/tasks'&&pending>0&&<small>{pending}</small>}</NavLink>)}
      </nav>
      <div className="sidebar-footer"><Link to="/settings" className="profile" onClick={()=>setMenu(false)}><span className="avatar">{model.settings.name[0]||'C'}</span><span><strong>{model.settings.name}</strong><small>个人空间</small></span><ChevronRight size={14}/></Link></div>
    </aside>
    <div className="app-main">
      <header className="topbar">
        <div className="top-path"><span className="mobile-menu"><IconButton label="打开导航" onClick={()=>setMenu(true)}><Menu size={20}/></IconButton></span><span className="top-workspace">个人空间</span><ChevronRight className="top-arrow" size={14}/><strong>{active?.[1]??'资料工作区'}</strong></div>
        <div className="top-actions"><span className="mode"><i/> 演示模式</span><Link to="/tasks" aria-label="查看处理任务" title="查看处理任务" className="cw-icon-button top-task"><Activity size={18}/></Link><Link to="/import" className="cw-button primary top-add"><Plus size={17}/> 导入</Link></div>
      </header>
      <main className="content">
        <Routes>
          <Route path="/" element={<Home/>}/><Route path="/library" element={<Library/>}/>
          <Route path="/collections" element={<Collections/>}/><Route path="/import" element={<ImportPage/>}/>
          <Route path="/tasks" element={<Tasks/>}/><Route path="/source/:id" element={<SourceWorkspace/>}/>
          <Route path="/agent" element={<Agent/>}/><Route path="/agent/:id" element={<Agent/>}/>
          <Route path="/notes" element={<Notes/>}/><Route path="/notes/:id" element={<Notes/>}/>
          <Route path="/integrations" element={<Integrations/>}/><Route path="/settings" element={<Settings/>}/>
          <Route path="*" element={<Empty title="找不到这个页面" text="从工作台继续你的研究。"><Link className="cw-button primary" to="/">返回工作台</Link></Empty>}/>
        </Routes>
      </main>
    </div>
    {message&&<div className="toast" role="status"><span className="toast-mark"><Layers3 size={16}/></span>{message}</div>}
    {search&&<Modal title="搜索工作区" description="按标题查找资料与笔记，或快速前往常用页面。" close={()=>setSearch(false)}>
      <label className="search"><Search size={19}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="搜索资料与笔记…" autoFocus aria-label="全局搜索"/></label>
      {!q&&<div className="toolbar"><button className="cw-button ghost" onClick={()=>jump('/import')}><Plus size={16}/> 导入资料</button><button className="cw-button ghost" onClick={()=>jump('/agent')}><Sparkles size={16}/> 新建研究</button></div>}
      {sources.map(s=><button className="item-row full-row" key={s.id} onClick={()=>jump('/source/'+s.id)}><TypeIcon kind={s.kind}/><span className="row-label"><strong>{s.title}</strong><small>{s.origin}</small></span><ChevronRight size={15}/></button>)}
      {notes.map(n=><button className="item-row full-row" key={n.id} onClick={()=>jump('/notes/'+n.id)}><FileText size={19}/><span className="row-label"><strong>{n.title}</strong><small>笔记</small></span><ChevronRight size={15}/></button>)}
      {!sources.length&&!notes.length&&<Empty title="没有匹配的结果" text="试试其他标题关键词。"/>}
    </Modal>}
  </div>
}
