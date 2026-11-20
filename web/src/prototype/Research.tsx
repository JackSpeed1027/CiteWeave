import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { BookOpen, ChevronRight, CircleCheck, Clipboard, ExternalLink, FilePlus2, FileText, ListTree, LoaderCircle, Pause, Play, Plus, Quote, RotateCcw, Send, Sparkles, Square, Star } from 'lucide-react'
import { useWorkspace } from './state'
import { folderScope, makeRun, maxResearchSources, noteFrom, stepIndex, uid } from './services'
import { researchSteps, targetLabels, type Destination, type Run, type Source } from './mock'
import { Banner, Button, Empty, IconButton, Markdown, Modal, PageHead, Select, Status, TypeIcon } from './ui'

const evidenceIndex = (source: Source) => Math.max(0,Math.min(1,source.segments.length-1))
const videoTime = (seconds: number) => Math.floor(seconds/60).toString().padStart(2,'0')+':'+Math.floor(seconds%60).toString().padStart(2,'0')
export function Reference({source, index}: {source: Source; index: number}) {
  const segment=source.segments[index]
  return segment ? <Link className="reference" to={'/source/'+source.id+'?cue='+index}><Quote size={13}/>{segment.label} · {source.title}</Link> : null
}

export function SourceWorkspace() {
  const {id}=useParams()
  const {model,update,toast}=useWorkspace()
  const source=model.sources.find(s=>s.id===id)
  if (!source) return <Empty title="找不到这份资料" text="它可能已从演示知识库移除。"><Link className="cw-button primary" to="/library">返回知识库</Link></Empty>
  return <SourceReader key={source.id} source={source} model={model} update={update} toast={toast}/>
}

function SourceReader({source, model, update, toast}: {source:Source;model:ReturnType<typeof useWorkspace>['model'];update:ReturnType<typeof useWorkspace>['update'];toast:ReturnType<typeof useWorkspace>['toast']}) {
  const [params,setParams]=useSearchParams()
  const [tab,setTab]=useState('summary')
  const [modal,setModal]=useState(false)
  const [folder,setFolder]=useState(source.collection)
  const [question,setQuestion]=useState('')
  const [playing,setPlaying]=useState(false)
  const [time,setTime]=useState(0)
  const navigate=useNavigate()
  const cue=Math.max(0,Math.min(source.segments.length-1,Number(params.get('cue'))||0))
  const video=source.kind==='bili'||source.kind==='video'
  const duration=Math.max((source.segments.at(-1)?.seconds??0)+90,120)
  const task=model.tasks.find(t=>t.sourceId===source.id)
  useEffect(()=>{
    const seconds=source.segments[cue]?.seconds
    if(seconds!==undefined)setTime(seconds)
    if(params.has('cue')) document.getElementById('cue-'+cue)?.scrollIntoView({block:'nearest',behavior:'smooth'})
  },[cue,source.segments,params])
  useEffect(()=>{
    if(!playing)return
    const timer=setInterval(()=>setTime(value=>Math.min(duration,value+1)),1000)
    return ()=>clearInterval(timer)
  },[playing,duration])
  useEffect(()=>{if(time>=duration)setPlaying(false)},[time,duration])
  function ask(query='') {
    navigate('/agent?source='+source.id+(query?'&ask='+encodeURIComponent(query):''))
  }
  function generate() {
    const body='# '+source.title+'\n\n'+source.detail+'\n\n## 关键知识\n\n'+source.segments.map(s=>'- **'+s.label+' · '+s.title+'** '+s.text).join('\n')
    const note=noteFrom(source.title+' · 学习笔记',body,[source.id])
    update(m=>({...m,notes:[note,...m.notes]}))
    toast('已生成演示学习笔记')
    navigate('/notes/'+note.id)
  }
  return <>
    <div className="breadcrumb"><Link to="/library">知识库</Link><ChevronRight size={14}/> 资料工作区</div>
    <PageHead title={source.title} subtitle={source.origin} actions={<><Status stage={source.status}/><Button onClick={()=>{update(m=>({...m,sources:m.sources.map(s=>s.id===source.id?{...s,favorite:!s.favorite}:s)}));toast(source.favorite?'已取消收藏':'已收藏资料')}}><Star size={16} fill={source.favorite?'currentColor':'none'}/>{source.favorite?'已收藏':'收藏'}</Button><Button onClick={()=>{setFolder(source.collection);setModal(true)}}>加入分类</Button></>}/>
    {source.status!=='done' && <Banner>资料当前处于「{source.status==='action'?'需补充资料':source.status}」状态。{task&&<Link className="text-link" to={'/tasks?id='+task.id}>查看任务</Link>}</Banner>}
    <div className="source-layout">
      <aside className="panel chapters"><h2><ListTree size={17}/> {video?'视频章节':'资料目录'}</h2>{source.segments.map((s,i)=><button key={i} className={i===cue?'selected':''} onClick={()=>setParams({cue:String(i)})}><small>{s.label}</small>{s.title}</button>)}</aside>
      <div className="source-main">
        {video&&<div className="video-view"><div className="video-cover"><span>演示时间轴</span><button className="video-play" aria-label={playing?'暂停演示时间轴':'播放演示时间轴'} onClick={()=>setPlaying(!playing)}>{playing?<Pause size={26}/>:<Play size={26}/>}</button></div>
          <div className="video-controls"><span>{videoTime(time)}</span><input type="range" aria-label="演示视频时间" min={0} max={duration} value={time} onChange={e=>{setTime(Number(e.target.value))}}/><span>{videoTime(duration)}</span></div></div>}
        <section className="panel reader"><div className="section-head"><h2><BookOpen size={17}/> {video?'字幕片段':'资料正文'}</h2>
          {source.url&&<a href={source.url} target="_blank" rel="noopener noreferrer">原始来源 <ExternalLink size={14}/></a>}</div>
          <div className="reader-body">{source.segments.map((s,i)=><article id={'cue-'+i} key={i} className={i===cue?'current':''}><button className="cue-button" onClick={()=>setParams({cue:String(i)})}>{s.label}</button><div><h3>{s.title}</h3><p>{s.text}</p></div></article>)}</div>
        </section>
      </div>
      <aside className="panel insight">
        <div className="tabs"><button aria-pressed={tab==='summary'} onClick={()=>setTab('summary')}><Sparkles size={16}/> AI 摘要</button><button aria-pressed={tab==='ask'} onClick={()=>setTab('ask')}>资料问答</button></div>
        <div className="insight-body"><Banner>内容与引用来自演示样例。</Banner>
          {tab==='summary'?<>
            <h3>概要</h3><p>{source.detail}</p><h3>核心知识点</h3>
            {source.segments.slice(0,3).map((s,i)=><div className="insight-point" key={s.title}><strong>{s.title}</strong><p>{s.text}</p><Reference source={source} index={i}/></div>)}
            <Button variant="primary" onClick={generate}><FilePlus2 size={17}/> 生成学习笔记</Button>
          </>:<form onSubmit={e=>{e.preventDefault();ask(question.trim())}} className="ask-form">
            <label className="field"><span>围绕这份资料提问</span><textarea placeholder="这份资料最重要的结论是什么？" value={question} onChange={e=>setQuestion(e.target.value)}/></label>
            <Button type="submit" variant="primary" disabled={!question.trim()}><Send size={16}/> 发送到 Agent</Button>
            <Button onClick={()=>ask()}>在 Agent 中打开</Button>
          </form>}
        </div>
      </aside>
    </div>
    {modal&&<Modal title="加入分类" description="选择一个收藏夹或文件夹。" close={()=>setModal(false)}>
      <Select label="资料分类" value={folder} change={setFolder}><option value="">未分类</option>{model.folders.map(f=><option key={f.id} value={f.id}>{f.name}</option>)}</Select>
      <div className="modal-actions"><Button variant="primary" onClick={()=>{update(m=>({...m,sources:m.sources.map(s=>s.id===source.id?{...s,collection:folder}:s)}));setModal(false);toast('资料分类已更新')}}>保存分类</Button></div>
    </Modal>}
  </>
}

export function Agent() {
  const {model, update, now, toast}=useWorkspace()
  const {id}=useParams()
  const [params]=useSearchParams()
  const navigate=useNavigate()
  const from=params.get('source')??''
  const collection=params.get('collection')??''
  const entryFolder=model.folders.find(f=>f.id===collection)
  const prefill=params.get('ask')??''
  const chat=model.chats.find(c=>c.id===id)
  const available=model.sources.filter(s=>s.status==='done')
  const firstSources=available.slice(0,2).map(s=>s.id)
  const setupid=id??(from||collection)
  const [drafts,setDrafts]=useState<Record<string,string>>({})
  const [selections,setSelections]=useState<Record<string,string[]>>({})
  const [select,setSelect]=useState(false)
  const [filterFolder,setFilterFolder]=useState('')
  const [filterTerm,setFilterTerm]=useState('')
  const entryScope=folderScope(model.folders,collection)
  const chosen=(chat?.sources??selections[setupid]??(from?[from]:entryFolder?available.filter(s=>entryScope.has(s.collection)).map(s=>s.id):firstSources))
    .filter(id=>available.some(s=>s.id===id))
  const scope=folderScope(model.folders,filterFolder)
  const visibleSources=available.filter(s=>(!filterFolder||scope.has(s.collection))&&
    (s.title+' '+s.origin+' '+s.tags.join(' ')).toLowerCase().includes(filterTerm.trim().toLowerCase()))
  const tooMany=chosen.length>maxResearchSources
  const query=drafts[setupid]??prefill
  const active=chat?.runs.find(r=>r.status==='running')
  const latest=chat?.runs.at(-1)
  const setQuery=(value:string)=>setDrafts(m=>({...m,[setupid]:value}))
  const setChosen=(next:string[])=>{
    if (chat) update(m=>({...m,chats:m.chats.map(c=>c.id===chat.id?{...c,sources:next}:c)}))
    else setSelections(m=>({...m,[setupid]:next}))
  }
  function submit(text:string, sources=chosen) {
    text=text.trim()
    if (!text||active) return
    const selected=model.sources.filter(s=>sources.includes(s.id)&&s.status==='done')
    if (selected.length>maxResearchSources) {
      toast('每次最多研究 '+maxResearchSources+' 份资料，请缩小范围。')
      return
    }
    const run=makeRun(text,selected)
    const chatId=chat?.id??uid()
    update(m=>({...m,chats:chat?m.chats.map(c=>c.id===chat.id?{...c,runs:[...c.runs,run]}:c):
      [{id:chatId,title:text.slice(0,32),sources,runs:[run]},...m.chats]}))
    setDrafts(previous=>({...previous,[setupid]:'',[chatId]:''}))
    if(!chat) navigate('/agent/'+chatId)
  }
  function stop() {
    if(!active||!chat)return
    update(m=>({...m,chats:m.chats.map(c=>c.id===chat.id?{...c,runs:c.runs.map(r=>r.id===active.id?{...r,status:'cancelled'}:r)}:c)}))
    toast('已取消本次模拟执行')
  }
  function create(run:Run, goal?:Destination) {
    const note=noteFrom(run.query.slice(0,42), '# '+run.query+'\n\n'+run.answer,run.sources)
    update(m=>({...m,notes:[note,...m.notes]}))
    toast('演示成果已保存到笔记')
    navigate('/notes/'+note.id+(goal?'?save='+goal:''))
  }
  const progress=latest?.status==='done'?researchSteps.length:latest?.status==='running'?stepIndex(latest.started,researchSteps.length,now):0
  const prompts=['比较所选资料中的技术观点','整理成一篇学习笔记并保存到 Obsidian','生成一份研究报告，保存到 GitHub','整理报告并保存到本地目录']
  return <>
    <PageHead title="Agent 工作台" subtitle="用资料作为上下文，追踪每个步骤，再把成果带走。"/>
    <div className="agent-layout">
      <aside className="panel chat-list"><Link className="cw-button primary" to="/agent?new=1" onClick={()=>{setDrafts({});setSelections({})}}><Plus size={16}/> 新建对话</Link>
        <h2>最近对话</h2>{model.chats.map(c=><Link className={c.id===id?'selected':''} to={'/agent/'+c.id} key={c.id}>{c.title}</Link>)}</aside>
      <section className="panel agent-main">
        <div className="section-head"><h2><Sparkles size={18}/> {chat?.title??'新建研究'}</h2><span className="mode">演示模式</span></div>
        <div className="messages">
          {!chat?.runs.length&&<div className="agent-start"><span className="agent-orb"><Sparkles size={27}/></span><h2>今天想研究什么？</h2><p>选择资料后提出问题，查看证据、工具步骤和可编辑成果。</p><div className="prompts">{prompts.map(p=><button key={p} onClick={()=>setQuery(p)}>{p}</button>)}</div></div>}
          {chat?.runs.map(run=><div className="run" key={run.id}><div className="user-message">{run.query}</div>
            <div className="agent-response">
              <span className="agent-avatar"><Sparkles size={16}/></span>
              <div className="response-body">
                {run.status==='running'?<div role="status" className="loading-line"><LoaderCircle size={17} className="spin"/>正在模拟读取资料…</div>
                :run.status==='done'?<>
                  <Markdown text={run.answer}/>
                  <div className="reference-list">{run.sources.map(id=>model.sources.find(s=>s.id===id)).filter((s):s is Source=>Boolean(s)).map(s=><Reference key={s.id} source={s} index={evidenceIndex(s)}/>)}</div>
                  <div className="toolbar"><Button onClick={()=>create(run)}><FilePlus2 size={16}/> 生成文档</Button>
                    {run.goal&&<Button variant="primary" onClick={()=>create(run,run.goal)}>预览并保存到 {targetLabels[run.goal]}</Button>}
                    <IconButton label="复制回答" onClick={()=>{navigator.clipboard.writeText(run.answer).then(()=>toast('已复制回答')).catch(()=>toast('无法访问剪贴板'))}}><Clipboard size={16}/></IconButton>
                    <IconButton label="重新生成" disabled={!!active} onClick={()=>submit(run.query,run.sources)}><RotateCcw size={16}/></IconButton></div>
                </>:<Banner error={run.status==='failed'}>{run.status==='failed'?'没有可用资料。请添加已完成的来源再重试。':'本次执行已取消。'} <Button disabled={!!active} onClick={()=>submit(run.query)}>重试</Button></Banner>}
              </div>
            </div>
          </div>)}
        </div>
        <form className="composer" onSubmit={e=>{e.preventDefault();submit(query)}}>
          <div className="composer-tools"><Button onClick={()=>{setFilterFolder(entryFolder?.id??'');setFilterTerm('');setSelect(true)}}><BookOpen size={16}/> 选择资料 · {chosen.length}</Button><span>{entryFolder&&!chat?entryFolder.name+' · 含子文件夹':'预设结果 · 支持追踪步骤与引用'}</span></div>
          {tooMany&&<Banner error>每次最多使用 {maxResearchSources} 份资料，请从选择资料中缩小范围。</Banner>}
          <textarea aria-label="向 Agent 提问" placeholder="提出问题，或告诉 Agent 你想保存到哪里…" value={query} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>{
            if(e.key==='Enter'&&!e.shiftKey&&!e.nativeEvent.isComposing){e.preventDefault();submit(query)}
          }}/>
          <div className="composer-footer"><span>Enter 发送 · Shift + Enter 换行</span>{active?<Button onClick={stop}><Square size={15}/> 停止</Button>:<Button type="submit" variant="primary" disabled={!query.trim()||tooMany}><Send size={16}/> 发送</Button>}</div>
        </form>
      </section>
      <aside className="panel trace"><div className="section-head"><h2>执行过程</h2></div>
        {latest?<div className="trace-body"><span className="eyebrow">模拟执行</span><p>{latest.query}</p>
          {researchSteps.map(([tool,label],i)=><div className="trace-step" key={tool}><span className={i<progress?'complete':''}>{i<progress?<CircleCheck size={17}/>:i===progress&&latest.status==='running'?<LoaderCircle size={16} className="spin"/>:<span className="pending-dot"/>}</span><div><strong>{label}</strong><small>{tool}</small><small>{i<progress?(i<2?'演示结果 · '+latest.sources.length+' 份资料':'演示结果已准备'):i===progress&&latest.status==='running'?'模拟执行中':'等待处理'}</small></div></div>)}
          {latest.goal&&<div className="trace-step"><FileText size={17}/><div><strong>准备目标确认</strong><small>{'save_to_'+latest.goal} · 等待用户确认</small></div></div>}
        </div>:<p className="trace-empty">发送任务后在此查看资料读取、对照和生成进度。</p>}
      </aside>
    </div>
    {select&&<Modal title="选择资料" description={'只显示已完成的资料，每次最多使用 '+maxResearchSources+' 份。'} close={()=>setSelect(false)}>
      <div className="toolbar">
        <label className="search"><input aria-label="筛选研究资料" placeholder="搜索标题、来源或标签" value={filterTerm} onChange={e=>setFilterTerm(e.target.value)}/></label>
        <Select label="研究资料分类" value={filterFolder} change={setFilterFolder}><option value="">所有分类</option>{model.folders.map(f=><option key={f.id} value={f.id}>{f.name}</option>)}</Select>
      </div>
      <div className="toolbar">
        <Button disabled={!visibleSources.length||visibleSources.every(s=>chosen.includes(s.id))} onClick={()=>setChosen([...new Set([...chosen,...visibleSources.map(s=>s.id)])])}>选中全部结果</Button>
        <Button disabled={!chosen.length} onClick={()=>setChosen([])}>清空选择</Button>
      </div>
      {!visibleSources.length&&<Empty title="没有匹配的资料" text="换个关键词，或切换分类。已选资料仍会保留。"/>}
      <div className="select-sources">{visibleSources.map(s=><label key={s.id} className="item-row"><input type="checkbox" checked={chosen.includes(s.id)} onChange={()=>setChosen(chosen.includes(s.id)?chosen.filter(id=>id!==s.id):[...chosen,s.id])}/><TypeIcon kind={s.kind}/><span className="row-label"><strong>{s.title}</strong><small>{s.origin}</small></span></label>)}</div>
      {tooMany&&<Banner error>已选 {chosen.length} 份，请减至 {maxResearchSources} 份以内。</Banner>}
      <div className="modal-actions"><Button variant="primary" onClick={()=>setSelect(false)}>使用 {chosen.length} 份资料</Button></div>
    </Modal>}
  </>
}
