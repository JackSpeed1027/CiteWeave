import { Link } from 'react-router-dom'
import { Activity, BookOpen, ChevronRight, CircleCheck, FileText, Folder, Globe2, Link2, Plus, Sparkles, Tv, Upload } from 'lucide-react'
import { useWorkspace } from './state'
import { kindLabels } from './mock'
import { dateLabel } from './services'
import { Empty, Status, TypeIcon } from './ui'

const shortcuts = [
  { path:'bili', title:'Bilibili 视频', body:'粘贴链接，选择分 P', icon:Tv, color:'pink' },
  { path:'video', title:'上传视频', body:'本地视频与字幕', icon:Upload, color:'blue' },
  { path:'document', title:'导入文档', body:'PDF、DOCX、Markdown', icon:FileText, color:'peach' },
  { path:'web', title:'收藏网页', body:'提取文章与正文', icon:Globe2, color:'mint' },
]

export function Home() {
  const { model } = useWorkspace()
  const recent = [...model.sources].sort((a,b)=>b.date-a.date).slice(0,4)
  const latest = [...model.notes].sort((a,b)=>b.date-a.date).slice(0,2)
  const active = model.tasks.filter(t=>t.stage==='running'||t.stage==='queued')
  return <div className="home">
    <div className="home-intro"><div><h1>你好，{model.settings.name}<span className="intro-period">.</span></h1><p>在一个地方读资料、做研究，留下自己的知识成果。</p></div>
      <Link to="/tasks" className="cw-button secondary"><Activity size={16}/>任务中心</Link>
    </div>
    <section className="hero">
      <div className="hero-art" aria-hidden="true"/>
      <div className="hero-copy">
        <div className="hero-label"><Sparkles size={15}/> 研究工作台</div>
        <h2>从一份资料开始</h2>
        <p>导入视频、文章或文档。带着证据继续追问，<br className="desktop-break"/>把发现整理成可保存、可分享的笔记。</p>
        <div className="hero-actions"><Link to="/import" className="cw-button hero-main"><Plus size={18}/> 导入资料</Link>
          <Link to="/agent" className="cw-button hero-alt"><Sparkles size={16}/> 打开 Agent</Link></div>
      </div>
    </section>
    <section aria-label="快速导入" className="quick-grid">
      {shortcuts.map(({path,title,body,icon:Icon,color})=><Link className="quick" to={'/import?kind='+path} key={path}>
        <span className={'quick-icon '+color}><Icon size={19}/></span><span><strong>{title}</strong><small>{body}</small></span><ChevronRight size={15} className="quick-chevron"/>
      </Link>)}
    </section>
    <div className="home-grid">
      <section className="panel">
        <div className="section-head"><h2><BookOpen size={18}/> 最近资料</h2><Link to="/library">查看知识库 <ChevronRight size={14}/></Link></div>
        {!recent.length && <Empty title="还没有资料" text="从导入中心添加第一份资料。"/>}
        {recent.map(source=><Link className="item-row" to={'/source/'+source.id} key={source.id}>
          <TypeIcon kind={source.kind}/><span className="row-label"><strong>{source.title}</strong><small>{kindLabels[source.kind]} · {source.origin}</small></span><Status stage={source.status}/>
        </Link>)}
      </section>
      <section className="panel">
        <div className="section-head"><h2><FileText size={18}/> 最近笔记</h2><Link to="/notes">全部笔记 <ChevronRight size={14}/></Link></div>
        {latest.map(note=><Link className="item-row" to={'/notes/'+note.id} key={note.id}><span className="quick-icon purple"><FileText size={17}/></span><span className="row-label"><strong>{note.title}</strong><small>Markdown · {dateLabel(note.date)}</small></span><ChevronRight size={15}/></Link>)}
        {!latest.length && <Empty title="还没有笔记" text="从工作区或 Agent 生成你的第一篇笔记。"/>}
        <div className="panel-footer"><Link to="/notes?new=1" className="text-link"><Plus size={15}/> 新建笔记</Link></div>
      </section>
      <section className="panel">
        <div className="section-head"><h2><Activity size={18}/> 处理任务</h2><Link to="/tasks">查看全部 <ChevronRight size={14}/></Link></div>
        {active.length ? active.slice(0,3).map(t=><Link className="item-row" to={'/tasks?id='+t.id} key={t.id}><span className="row-label"><strong>{t.title}</strong><small>模拟处理</small></span><Status stage={t.stage}/></Link>)
          : <div className="idle"><CircleCheck size={19}/> 暂无正在处理的任务</div>}
      </section>
      <section className="panel">
        <div className="section-head"><h2><Folder size={18}/> 继续工作</h2><Link to="/collections">全部分类 <ChevronRight size={14}/></Link></div>
        {model.folders.filter(f=>!f.parent).slice(0,2).map(f=><Link className="item-row compact" to={'/collections?id='+f.id} key={f.id}><Folder size={17}/><span className="row-label"><strong>{f.name}</strong></span><ChevronRight size={15}/></Link>)}
        {model.chats.slice(0,1).map(c=><Link className="item-row compact" to={'/agent/'+c.id} key={c.id}><Link2 size={17}/><span className="row-label"><strong>{c.title}</strong></span><ChevronRight size={15}/></Link>)}
      </section>
    </div>
  </div>
}
