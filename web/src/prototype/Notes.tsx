import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { BookOpen, Copy, Download, Eye, FileText, History, Pencil, Plus, Save, Search } from 'lucide-react'
import { dateLabel, noteFrom } from './services'
import type { Destination, Note } from './mock'
import { Reference } from './Research'
import { useWorkspace } from './state'
import { Button, Empty, Markdown, Modal, PageHead, Select } from './ui'
import { ExportModal, SaveModal } from './Destinations'

export function Notes() {
  const {model,update,toast}=useWorkspace()
  const {id}=useParams()
  const [params,setParams]=useSearchParams()
  const [term,setTerm]=useState('')
  const [newDialog,setNewDialog]=useState(false)
  const [title,setTitle]=useState('')
  const navigate=useNavigate()
  const note=model.notes.find(n=>n.id===id)
  const showNew=params.has('new')||newDialog
  const closeNew=()=>{setNewDialog(false);setParams({});setTitle('')}
  function create() {
    if (!title.trim()) return
    const next=noteFrom(title.trim(),'# '+title.trim()+'\n\n',[])
    update(m=>({...m,notes:[next,...m.notes]}))
    closeNew()
    toast('已创建笔记')
    navigate('/notes/'+next.id)
  }
  if (note) return <Editor key={note.id} note={note}/>
  return <>
    <PageHead title="笔记与文档" subtitle="继续编辑研究成果、查看版本并保存到你的目的地。" actions={<Button variant="primary" onClick={()=>setNewDialog(true)}><Plus size={17}/> 新建笔记</Button>}/>
    <section className="panel">
      <div className="filter-bar"><label className="search"><Search size={17}/><input value={term} onChange={e=>setTerm(e.target.value)} aria-label="搜索笔记" placeholder="按标题或标签搜索"/></label></div>
      {model.notes.filter(n=>(n.title+n.tags.join(' ')).toLowerCase().includes(term.trim().toLowerCase())).map(n=>
        <Link className="item-row" key={n.id} to={'/notes/'+n.id}><span className="quick-icon purple"><FileText size={18}/></span><span className="row-label"><strong>{n.title}</strong><small>{n.tags.length?n.tags.join('、')+' · ':''}{n.sources.length} 份来源</small></span><span className="row-date">{dateLabel(n.date)}</span></Link>)}
      {model.notes.every(n=>!(n.title+n.tags.join(' ')).toLowerCase().includes(term.trim().toLowerCase()))&&<Empty title="找不到笔记" text="更换关键词，或新建一篇笔记。"/>}
    </section>
    {showNew&&<Modal title="新建笔记" description="用 Markdown 编辑内容，修改自动保存在当前浏览器。" close={closeNew}>
      <form onSubmit={e=>{e.preventDefault();create()}}><label className="field"><span>笔记名称</span><input value={title} onChange={e=>setTitle(e.target.value)} maxLength={120}/></label><div className="modal-actions"><Button variant="primary" disabled={!title.trim()} type="submit">创建笔记</Button></div></form>
    </Modal>}
  </>
}

function Editor({note}: {note:Note}) {
  const {model,update,toast}=useWorkspace()
  const navigate=useNavigate()
  const [params,setParams]=useSearchParams()
  const [view,setView]=useState<'split'|'edit'|'preview'>('split')
  const [modal,setModal]=useState<'export'|'history'|'details'>()
  const [save,setSave]=useState<Destination>()
  const [name,setName]=useState(note.title)
  const [folder,setFolder]=useState(note.collection)
  const [tags,setTags]=useState(note.tags.join('、'))
  const [saving,setSaving]=useState(false)
  const [restore,setRestore]=useState<number>()
  const routeSave=params.get('save') as Destination|null
  const saveTarget=save||(routeSave&&['obsidian','github','local'].includes(routeSave)?routeSave:undefined)
  const patch=(fields:Partial<Note>)=>update(m=>({...m,notes:m.notes.map(n=>n.id===note.id?{...n,...fields,date:Date.now()}:n)}))
  useEffect(()=>{
    if(!saving)return
    const timer=setTimeout(()=>setSaving(false),450)
    return ()=>clearTimeout(timer)
  },[saving,note.body])
  function version() {
    const checkpoint={date:Date.now(),title:note.title,body:note.body}
    patch({versions:[checkpoint,...note.versions]})
    toast('已保存文档版本')
  }
  function duplicate() {
    const clone={...noteFrom(note.title+' · 副本',note.body,note.sources),tags:[...note.tags],collection:note.collection}
    update(m=>({...m,notes:[clone,...m.notes]}))
    toast('已保存副本')
    navigate('/notes/'+clone.id)
  }
  function saveDetails() {
    const labels=[...new Set(tags.split(/[,，、]/).map(t=>t.trim()).filter(Boolean))].slice(0,12)
    update(m=>({...m,tags:[...new Set([...m.tags,...labels])],notes:m.notes.map(n=>n.id===note.id?{...n,title:name.trim(),tags:labels,collection:folder,date:Date.now()}:n)}))
    setModal(undefined)
    toast('文档属性已保存')
  }
  return <>
    <div className="breadcrumb"><Link to="/notes">笔记与文档</Link> / 编辑器</div>
    <PageHead title={note.title} subtitle={saving?'正在自动保存…':'已自动保存 · '+dateLabel(note.date)} actions={<>
      <Button onClick={()=>setModal('export')}><Download size={17}/> 导出</Button><Button variant="primary" onClick={()=>setSave(model.settings.defaultTarget)}><Save size={17}/> 保存到…</Button>
    </>}/>
    <section className="panel editor">
      <div className="editor-head"><div className="tabs">
        <button aria-pressed={view==='split'} onClick={()=>setView('split')}>双栏</button>
        <button aria-pressed={view==='edit'} onClick={()=>setView('edit')}><Pencil size={15}/> 编辑</button>
        <button aria-pressed={view==='preview'} onClick={()=>setView('preview')}><Eye size={16}/> 预览</button>
      </div><div className="toolbar">
        <Button variant="ghost" onClick={()=>{setName(note.title);setFolder(note.collection);setTags(note.tags.join('、'));setModal('details')}}>属性</Button>
        <Button variant="ghost" onClick={()=>setSave(model.settings.defaultTarget)}>另存为</Button>
        <Button variant="ghost" onClick={duplicate}><Copy size={15}/> 保存副本</Button>
        <Button variant="ghost" onClick={()=>{setRestore(undefined);setModal('history')}}><History size={16}/> 版本</Button>
      </div></div>
      <div className={'edit-grid '+view}>
        {view!=='preview'&&<div className="editor-side"><span className="edit-label">MARKDOWN</span><textarea aria-label="Markdown 编辑器" value={note.body} onChange={e=>{setSaving(true);patch({body:e.target.value})}} spellCheck={false}/></div>}
        {view!=='edit'&&<div className="editor-side"><span className="edit-label">文档预览</span><Markdown text={note.body}/></div>}
      </div>
      <div className="editor-footer"><span><BookOpen size={16}/> 引用来源</span><div className="reference-list">
        {note.sources.map(id=>model.sources.find(s=>s.id===id)).map((s,i)=>s?<Reference source={s} index={Math.min(1,s.segments.length-1)} key={s.id}/>:<small className="muted" key={i}>原型来源已移除</small>)}
        {!note.sources.length&&<small className="muted">这篇笔记没有关联资料。</small>}
      </div></div>
    </section>
    {saveTarget&&<SaveModal note={note} initial={saveTarget} close={()=>{setSave(undefined);setParams({})}}/>}
    {modal==='export'&&<ExportModal note={note} close={()=>setModal(undefined)}/>}
    {modal==='details'&&<Modal title="文档属性" description="重命名并设置分类与标签。" close={()=>setModal(undefined)}>
      <label className="field"><span>文档名称</span><input value={name} maxLength={120} onChange={e=>setName(e.target.value)}/></label>
      <label className="field"><span>标签，用顿号或逗号分隔</span><input value={tags} maxLength={180} onChange={e=>setTags(e.target.value)}/></label>
      <label className="field"><span>分类</span><Select label="笔记分类" value={folder} change={setFolder}><option value="">未分类</option>{model.folders.map(f=><option key={f.id} value={f.id}>{f.name}</option>)}</Select></label>
      <div className="modal-actions"><Button variant="primary" disabled={!name.trim()} onClick={saveDetails}>保存属性</Button></div>
    </Modal>}
    {modal==='history'&&<Modal drawer title="版本记录" description="可保存一个检查点，或预览并恢复先前版本。" close={()=>setModal(undefined)}>
      <Button variant="primary" onClick={version}>保存当前版本</Button>
      {!note.versions.length&&<Empty title="暂无版本" text="创建检查点后可比较并恢复内容。"/>}
      {note.versions.map((v,i)=><button className="item-row full-row" key={v.date+'-'+i} onClick={()=>setRestore(i)}><History size={16}/><span className="row-label"><strong>{v.title}</strong><small>{dateLabel(v.date)}</small></span></button>)}
      {restore!==undefined&&note.versions[restore]&&<><Markdown text={note.versions[restore].body}/><div className="modal-actions"><Button variant="primary" onClick={()=>{const v=note.versions[restore];patch({title:v.title,body:v.body,versions:[{title:note.title,body:note.body,date:Date.now()},...note.versions]});setRestore(undefined);toast('已恢复版本，并保留恢复前内容')}}>确认恢复此版本</Button></div></>}
    </Modal>}
  </>
}
