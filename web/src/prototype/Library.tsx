import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ChevronRight, Folder, FolderPlus, LayoutGrid, List, Plus, RotateCcw, Search, Star, Tags, Trash2 } from 'lucide-react'
import { useWorkspace } from './state'
import { kindLabels, type Folder as FolderModel, type Kind, type Source } from './mock'
import { dateLabel, uid } from './services'
import { Button, Empty, IconButton, Modal, PageHead, Select, Status, TypeIcon } from './ui'

export function SourceRow({source, checked, toggle}: {source:Source;checked?:boolean;toggle?:()=>void}) {
  const {update,toast}=useWorkspace()
  return <div className="item-row library-row">
    {toggle&&<input type="checkbox" checked={checked} aria-label={'选择 '+source.title} onChange={toggle}/>}
    <Link className="row-source" to={'/source/'+source.id}><TypeIcon kind={source.kind}/>
      <span className="row-label"><strong>{source.title}</strong><small>{kindLabels[source.kind]} · {source.origin} {source.tags.length?' · '+source.tags.join('、'):''}</small></span>
    </Link>
    <Status stage={source.status}/><span className="row-date">{dateLabel(source.date)}</span>
    <IconButton label={source.favorite?'取消收藏 '+source.title:'收藏 '+source.title} onClick={()=>{update(m=>({...m,sources:m.sources.map(s=>s.id===source.id?{...s,favorite:!s.favorite}:s)}));toast(source.favorite?'已取消收藏':'已收藏资料')}}><Star size={17} fill={source.favorite?'currentColor':'none'} className={source.favorite?'gold':''}/></IconButton>
  </div>
}

export function Library() {
  const {model,update,toast}=useWorkspace()
  const [query,setQuery]=useState('')
  const [kind,setKind]=useState('')
  const [tag,setTag]=useState('')
  const [folder,setFolder]=useState('')
  const [order,setOrder]=useState('recent')
  const [favorites,setFavorites]=useState(false)
  const [grid,setGrid]=useState(false)
  const [selected,setSelected]=useState<string[]>([])
  const [dialog,setDialog]=useState<'move'|'tag'|'delete'>()
  const [value,setValue]=useState('')
  const shown=model.sources.filter(s=>(!kind||s.kind===kind)&&(!tag||s.tags.includes(tag))&&(!folder||s.collection===folder)&&(!favorites||s.favorite)&&
    (s.title+' '+s.origin+' '+s.tags.join(' ')).toLowerCase().includes(query.trim().toLowerCase())).sort((a,b)=>order==='recent'?b.date-a.date:a.date-b.date)
  const visibleIds=shown.filter(s=>selected.includes(s.id)).map(s=>s.id)
  const clear=()=>{setQuery('');setTag('');setFolder('');setKind('');setFavorites(false)}
  function apply() {
    if (!dialog||!visibleIds.length) return
    const tagValue=value.trim()
    if (dialog==='tag'&&!tagValue) return
    update(m=>{
      if (dialog==='delete') return {...m,sources:m.sources.filter(s=>!visibleIds.includes(s.id)),tasks:m.tasks.filter(t=>!visibleIds.includes(t.sourceId))}
      return {...m,tags:dialog==='tag'&&!m.tags.includes(tagValue)?[...m.tags,tagValue]:m.tags,
        sources:m.sources.map(s=>!visibleIds.includes(s.id)?s:dialog==='move'?{...s,collection:value}:{...s,tags:[...new Set([...s.tags,tagValue])]})}
    })
    toast(dialog==='delete'?'已移除所选演示资料':dialog==='move'?'资料已移动':'标签已添加')
    setDialog(undefined)
    setSelected([])
  }
  const toggle=(id:string)=>setSelected(ids=>ids.includes(id)?ids.filter(v=>v!==id):[...ids,id])
  function retry() {
    update(m=>{
      const ids=visibleIds.filter(id=>!m.tasks.some(t=>t.sourceId===id && ['queued','running'].includes(t.stage)))
      return {...m,tasks:[...m.sources.filter(s=>ids.includes(s.id)).map(s=>({
        id:uid(),sourceId:s.id,title:s.title,stage:'queued' as const,steps:['读取资料','处理内容','生成摘要','构建索引'],started:Date.now(),
      })),...m.tasks.filter(t=>!ids.includes(t.sourceId))],sources:m.sources.map(s=>ids.includes(s.id)?{...s,status:'queued'}:s)}
    })
    toast('已提交可运行的资料进行模拟处理')
    setSelected([])
  }
  return <>
    <PageHead title="知识库" subtitle="搜索、归类和继续阅读你的所有资料。" actions={<Link to="/import" className="cw-button primary"><Plus size={17}/> 导入资料</Link>}/>
    <section className="panel">
      <div className="filter-bar">
        <label className="search"><Search size={17}/><input value={query} onChange={e=>setQuery(e.target.value)} aria-label="搜索资料标题" placeholder="搜索标题、来源或标签"/></label>
        <Select label="资料类型" value={kind} change={setKind}><option value="">全部类型</option>{Object.entries(kindLabels).map(([k,v])=><option value={k} key={k}>{v}</option>)}</Select>
        <Select label="按标签筛选" value={tag} change={setTag}><option value="">全部标签</option>{model.tags.map(t=><option key={t}>{t}</option>)}</Select>
        <Select label="按文件夹筛选" value={folder} change={setFolder}><option value="">所有分类</option>{model.folders.map(f=><option value={f.id} key={f.id}>{f.name}</option>)}</Select>
        <Select label="按时间排序" value={order} change={setOrder}><option value="recent">最新导入</option><option value="old">最早导入</option></Select>
        <div className="filter-actions"><IconButton label="只看收藏" aria-pressed={favorites} onClick={()=>setFavorites(!favorites)}><Star size={17} fill={favorites?'currentColor':'none'}/></IconButton>
        <IconButton label={grid?'列表视图':'网格视图'} onClick={()=>setGrid(!grid)}>{grid?<List size={18}/>:<LayoutGrid size={17}/>}</IconButton></div>
      </div>
      {shown.length>0&&<div className="list-toolbar"><label className="checkline"><input type="checkbox" checked={visibleIds.length===shown.length} onChange={()=>setSelected(visibleIds.length===shown.length?[]:shown.map(s=>s.id))}/> {visibleIds.length?'已选 '+visibleIds.length+' 项':shown.length+' 份资料'}</label>
        {visibleIds.length>0&&<div className="toolbar">
          <Button variant="ghost" onClick={()=>{setValue('');setDialog('move')}}><Folder size={15}/> 移动</Button>
          <Button variant="ghost" onClick={()=>{setValue('');setDialog('tag')}}><Tags size={15}/> 添加标签</Button>
          <Button variant="ghost" onClick={retry}><RotateCcw size={15}/> 重新处理</Button>
          <Button variant="ghost" onClick={()=>setDialog('delete')}><Trash2 size={15}/> 删除</Button>
        </div>}
      </div>}
      {!shown.length&&<Empty title="没有匹配的资料" text="尝试清除筛选或修改搜索关键词。"><Button onClick={clear}>清除筛选</Button></Empty>}
      {grid?<div className="library-grid">{shown.map(s=><SourceRow source={s} key={s.id} checked={selected.includes(s.id)} toggle={()=>toggle(s.id)}/>)}</div>:
        shown.map(s=><SourceRow source={s} key={s.id} checked={selected.includes(s.id)} toggle={()=>toggle(s.id)}/>)}
    </section>
    {dialog&&<Modal title={dialog==='move'?'移动到分类':dialog==='tag'?'添加标签':'移除演示资料'} description={'当前已选择 '+visibleIds.length+' 份资料。'} close={()=>setDialog(undefined)}>
      {dialog==='move'&&<Select label="目标分类" value={value} change={setValue}><option value="">未分类</option>{model.folders.map(f=><option key={f.id} value={f.id}>{f.name}</option>)}</Select>}
      {dialog==='tag'&&<label className="field"><span>标签名称</span><input maxLength={28} value={value} onChange={e=>setValue(e.target.value)} list="library-tags"/><datalist id="library-tags">{model.tags.map(x=><option key={x} value={x}/>)}</datalist></label>}
      {dialog==='delete'&&<p>确认从此原型的知识库和任务列表移除所选条目？关联笔记仍可打开。</p>}
      <div className="modal-actions"><Button onClick={()=>setDialog(undefined)}>取消</Button><Button variant={dialog==='delete'?'danger':'primary'} disabled={dialog==='tag'&&!value.trim()} onClick={apply}>{dialog==='delete'?'确认移除':'确认'}</Button></div>
    </Modal>}
  </>
}

export function Collections() {
  const {model,update,toast}=useWorkspace()
  const [params,setParams]=useSearchParams()
  const id=params.get('id')??''
  const selected=model.folders.find(f=>f.id===id)
  const [modal,setModal]=useState<'collection'|'folder'|'tags'|'add'|'rename'>()
  const [name,setName]=useState('')
  const [parent,setParent]=useState('')
  const [add,setAdd]=useState('')
  const [tagToRemove,setTagToRemove]=useState('')
  const show=(which:NonNullable<typeof modal>)=>{setName(which==='rename'?selected?.name??'':'');setParent(which==='folder'?id:'');setAdd('');setTagToRemove('');setModal(which)}
  function create() {
    if (modal==='rename'&&selected&&name.trim()) {
      update(m=>({...m,folders:m.folders.map(f=>f.id===id?{...f,name:name.trim()}:f)}))
      setModal(undefined)
      toast('分类名称已更新')
      return
    }
    if (!name.trim()|| (modal!=='folder'&&modal!=='collection')) return
    const folder:FolderModel={id:uid(),name:name.trim(),parent,color:parent?'amber':'violet',type:modal}
    update(m=>({...m,folders:[...m.folders,folder]}))
    setParams({id:folder.id})
    setModal(undefined)
    toast('已创建 '+folder.name)
  }
  function move(sourceId:string) {
    if (!model.sources.some(s=>s.id===sourceId)) return
    update(m=>({...m,sources:m.sources.map(s=>s.id===sourceId?{...s,collection:id}:s)}))
    toast('已移动资料到 '+selected?.name)
    setModal(undefined)
  }
  const sources=model.sources.filter(s=>!id||s.collection===id)
  const folders=(parentId:string,depth:number):React.ReactNode=>model.folders.filter(f=>f.parent===parentId).map(f=><div key={f.id}>
    <button className={'folder-link '+(f.id===id?'selected':'')} style={{paddingInlineStart:12+14*depth}} onClick={()=>setParams({id:f.id})}><Folder size={16}/>{f.name}<small>{model.sources.filter(s=>s.collection===f.id).length}</small></button>
    {folders(f.id,depth+1)}
  </div>)
  return <>
    <PageHead title="收藏夹与分类" subtitle="为持续研究的主题建立收藏夹，也可以用文件夹细分资料。" actions={<><Button onClick={()=>show('tags')}><Tags size={16}/> 管理标签</Button><Button onClick={()=>show('folder')}><FolderPlus size={16}/> 新建文件夹</Button><Button variant="primary" onClick={()=>show('collection')}><Plus size={17}/> 新建收藏夹</Button></>}/>
    <div className="collection-layout">
      <section className="panel folder-tree"><h2>分类</h2><button className={'folder-link '+(!id?'selected':'')} onClick={()=>setParams({})}><LayoutGrid size={16}/> 全部资料</button>{folders('',0)}</section>
      <section className="panel" onDragOver={e=>{if(selected)e.preventDefault()}} onDrop={e=>{e.preventDefault();if(selected)move(e.dataTransfer.getData('text/plain'))}}>
        <div className="section-head"><h2>{selected?.name||'全部资料'}</h2>{selected&&<div className="toolbar">
          <Button onClick={()=>show('rename')}>重命名</Button>
          <Button onClick={()=>show('add')}><Plus size={16}/> 添加已有资料</Button>
          <Link to={'/agent?collection='+encodeURIComponent(selected.id)} className="cw-button primary">在 Agent 中研究</Link>
        </div>}</div>
        {model.folders.filter(f=>f.parent===id&&id).map(f=><button type="button" className="item-row full-row" onClick={()=>setParams({id:f.id})} key={f.id}><Folder size={19}/><strong className="row-label">{f.name}</strong><ChevronRight size={16}/></button>)}
        {sources.map(s=><SourceRow source={s} key={s.id}/>)}
        {!sources.length&&<Empty title="这个分类还没有资料" text="你可以添加已有资料，或从知识库批量移动。">{selected&&<Button onClick={()=>show('add')}>添加资料</Button>}</Empty>}
      </section>
    </div>
    {modal&&<Modal title={modal==='rename'?'重命名分类':modal==='tags'?'管理标签':modal==='add'?'添加已有资料':modal==='folder'?'新建文件夹':'新建收藏夹'} description="这些更改会保存在当前浏览器的原型工作区。" close={()=>setModal(undefined)}>
      {modal==='tags'?<>
        <div className="tag-list">{model.tags.map(tag=><Button key={tag} aria-pressed={tagToRemove===tag} onClick={()=>setTagToRemove(tagToRemove===tag?'':tag)}>{tag}</Button>)}</div>
        {tagToRemove&&<div className="toolbar"><Button variant="danger" onClick={()=>{update(m=>({...m,tags:m.tags.filter(t=>t!==tagToRemove),sources:m.sources.map(s=>({...s,tags:s.tags.filter(t=>t!==tagToRemove)})),notes:m.notes.map(n=>({...n,tags:n.tags.filter(t=>t!==tagToRemove)}))}));setTagToRemove('');toast('已移除标签')}}>确认移除标签「{tagToRemove}」</Button></div>}
        <label className="field"><span>新标签</span><input value={name} maxLength={28} onChange={e=>setName(e.target.value)}/></label>
        <div className="modal-actions"><Button disabled={!name.trim()||model.tags.includes(name.trim())} variant="primary" onClick={()=>{update(m=>({...m,tags:[...m.tags,name.trim()]}));setName('');toast('已添加标签')}}>添加标签</Button></div>
      </>:modal==='add'?<>
        <Select label="选择资料" value={add} change={setAdd}><option value="">请选择一份资料</option>{model.sources.filter(s=>s.collection!==id).map(s=><option key={s.id} value={s.id}>{s.title}</option>)}</Select>
        <div className="modal-actions"><Button variant="primary" disabled={!add} onClick={()=>move(add)}>移动到此处</Button></div>
      </>:<form onSubmit={e=>{e.preventDefault();create()}}>
        <label className="field"><span>名称</span><input autoFocus value={name} onChange={e=>setName(e.target.value)} maxLength={60}/></label>
        {modal==='folder'&&<label className="field"><span>上级分类</span><Select label="上级分类" value={parent} change={setParent}><option value="">顶层</option>{model.folders.map(f=><option value={f.id} key={f.id}>{f.name}</option>)}</Select></label>}
        <div className="modal-actions"><Button type="submit" variant="primary" disabled={!name.trim()}>{modal==='rename'?'保存名称':'创建'}</Button></div>
      </form>}
    </Modal>}
  </>
}
