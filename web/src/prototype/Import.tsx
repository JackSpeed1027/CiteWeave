import { useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Clapperboard, CloudUpload, FileText, LoaderCircle, Plus, Search, X } from 'lucide-react'
import { Banner, Button, IconButton, PageHead, Select, TypeIcon } from './ui'
import { biliParts, kindLabels, localVideoSteps, samples, type Kind, type Source, type Task } from './mock'
import { uid } from './services'
import { useWorkspace } from './state'

const formats = { video:'.mp4,.mov,.mkv,.webm', document:'.pdf,.docx,.txt,.md,.markdown' }
type Upload = { file:File; duration?: number }
type Preview = { href: string; site: string }
function validURL(text: string, kind: Kind): Preview {
  if (kind==='bili' && /^(BV[A-Za-z0-9]{10}|av[0-9]+)$/i.test(text)) text='https://www.bilibili.com/video/'+text
  let url: URL
  try { url=new URL(text) } catch { throw new Error('请输入完整的 HTTP 或 HTTPS 链接。') }
  if (!['http:','https:'].includes(url.protocol)||url.username||url.password) throw new Error('请输入无需登录信息的 HTTP 或 HTTPS 链接。')
  if (kind==='bili' && !(['www.bilibili.com','bilibili.com','m.bilibili.com'].includes(url.hostname) && /^\/video\/(?:BV[A-Za-z0-9]{10}|av[0-9]+)(?:\/|$)/i.test(url.pathname) || url.hostname==='b23.tv' && url.pathname.length>1)) {
    throw new Error('请使用 Bilibili 视频页、b23.tv 短链接或 BV/AV 编号。')
  }
  if (kind==='github' && (url.hostname!=='github.com'||url.pathname.split('/').filter(Boolean).length<2)) throw new Error('请输入 GitHub 的 owner/repository 链接。')
  return {href:url.href,site:url.hostname}
}
function probeDuration(file: File): Promise<number | undefined> {
  return new Promise(resolve=>{
    const video=document.createElement('video')
    const url=URL.createObjectURL(file)
    const done=()=>{
      const duration=Number.isFinite(video.duration)?video.duration:undefined
      clearTimeout(timer)
      video.onloadedmetadata=null
      video.onerror=null
      video.removeAttribute('src')
      URL.revokeObjectURL(url)
      resolve(duration)
    }
    const timer=setTimeout(done,2500)
    video.preload='metadata'
    video.onloadedmetadata=done
    video.onerror=done
    video.src=url
  })
}
function size(n: number) { return n>=1048576 ? (n/1048576).toFixed(1)+' MB' : Math.max(1,Math.round(n/1024))+' KB' }

export function ImportPage() {
  const [params] = useSearchParams()
  const kind = Object.keys(kindLabels).includes(params.get('kind') ?? '') ? params.get('kind') as Kind : 'bili'
  const resume=params.get('resume')??''
  return <ImportForm kind={kind} resume={resume} key={kind+resume}/>
}

function ImportForm({kind,resume}: {kind:Kind;resume:string}) {
  const {model,update,toast}=useWorkspace()
  const pending=model.tasks.find(t=>t.id===resume&&t.stage==='action')
  const pendingSource=model.sources.find(s=>s.id===pending?.sourceId)
  const navigate=useNavigate()
  const [url,setUrl]=useState(pendingSource?.url??'')
  const [part,setPart]=useState('0')
  const [preview,setPreview]=useState<Preview>()
  const [files,setFiles]=useState<Upload[]>([])
  const [subtitle,setSubtitle]=useState<File>()
  const [method,setMethod]=useState('auto')
  const [error,setError]=useState('')
  const [looking,setLooking]=useState(false)
  const [busy,setBusy]=useState(false)
  const [drag,setDrag]=useState(false)
  const [fail,setFail]=useState(false)
  const picker=useRef<HTMLInputElement>(null)
  const isFile=kind==='video'||kind==='document'
  const hasSubs=kind==='bili' && biliParts[Number(part)].subtitles
  const needsAction=(kind==='bili'||kind==='video') && (pending ? true : !hasSubs) && !subtitle && method==='auto'
  const choose=async (list: File[])=>{
    setError('')
    const allowed=kind==='video' ? /\.(mp4|mov|mkv|webm)$/i : /\.(pdf|docx|txt|md|markdown)$/i
    const failed=list.find(f=>!allowed.test(f.name)||!f.size||f.size>500*1024*1024)
    if (failed) {setError(failed.name+' 无法加入，请检查扩展名、非空文件和 500 MB 大小限制。');return}
    const selected=kind==='video'?list.slice(0,1):list
    setFiles(previous=>kind==='video'?selected.map(file=>({file})): [...previous,...selected.map(file=>({file}))])
    if(kind==='video' && selected[0]){
      const duration=await probeDuration(selected[0])
      setFiles(current=>current.map(item=>item.file===selected[0]?{...item,duration}:item))
    }
  }
  async function lookup(event:React.FormEvent) {
    event.preventDefault()
    setError('')
    setPreview(undefined)
    try {
      const result=validURL(url.trim(),kind)
      setLooking(true)
      await new Promise(resolve=>setTimeout(resolve,430))
      if (/unavailable|missing\.example|404-demo/.test(result.href)) throw new Error('演示情景：来源暂时不可用。请更换链接，或改用文档上传。')
      setPreview(result)
    } catch (failure) {setError((failure as Error).message)}
    finally {setLooking(false)}
  }
  function enqueue() {
    if (busy) return
    if (pending) {
      if (needsAction) return
      setBusy(true)
      const steps=pending.steps.map(step=>/字幕|ASR/.test(step)?subtitle?'导入用户字幕':'模拟 ASR 转录':step)
      update(m=>({...m,tasks:m.tasks.map(t=>t.id===pending.id?{...t,steps,stage:'queued',started:Date.now(),fail,detail:subtitle?'字幕文件：'+subtitle.name:undefined}:t),
        sources:m.sources.map(s=>s.id===pending.sourceId?{...s,status:'queued'}:s)}))
      toast('已补充处理方式，任务继续执行')
      navigate('/tasks?id='+pending.id)
      return
    }
    if ((!isFile && !preview) || (isFile&&!files.length)) return
    setBusy(true)
    const sample=samples[kind]
    const input=isFile?files:[undefined]
    const now=Date.now()
    const sources:Source[]=input.map(entry=>({
      ...sample,id:uid(),kind,date:now,title:entry?.file.name || (kind==='bili'?sample.title+' · P'+(Number(part)+1)+' '+biliParts[Number(part)].title:kind==='github' ? new URL(preview!.href).pathname.split('/').filter(Boolean).slice(0,2).join('/') : sample.title),
      url:preview?.href,origin:entry ? entry.file.name+' · '+(entry.duration ? Math.round(entry.duration)+' 秒' : '本机文件') : kind==='bili' ? '架构学习室 · P'+(Number(part)+1)+' · '+biliParts[Number(part)].length : preview!.site,
      size:entry?size(entry.file.size):undefined,status:needsAction?'action':'queued',favorite:false,tags:[],collection:'',
    }))
    const steps=kind==='video'?localVideoSteps.map(step=>step==='处理字幕'&&method==='asr'?'ASR 转录':step):kind==='bili'?['读取视频信息',subtitle?'导入用户字幕':method==='asr'?'模拟 ASR 转录':'获取字幕','理解内容','生成摘要','构建索引']:['读取资料','提取正文','生成摘要','构建索引']
    const tasks:Task[]=sources.map((s,index)=>({
      id:uid(),sourceId:s.id,title:s.title,stage:s.status,steps,started:now+index*300,fail,
      detail:needsAction?'当前演示分 P 没有可用字幕。返回导入选择上传字幕或模拟 ASR。':subtitle?'字幕文件：'+subtitle.name:undefined,
    }))
    update(m=>({...m,sources:[...sources,...m.sources],tasks:[...tasks,...m.tasks]}))
    toast(needsAction?'任务已记录，请补充字幕或选择模拟 ASR':'已加入模拟处理队列')
    navigate('/tasks?id='+tasks[0].id)
  }

  return <>
    <PageHead title="导入中心" subtitle="把视频、网页和文档放进同一工作空间。"/>
    <div className="import-types">{(Object.keys(kindLabels) as Kind[]).map(type=><Link key={type} to={'/import?kind='+type} className={'import-type '+(type===kind?'active':'')} aria-current={type===kind?'page':undefined}><TypeIcon kind={type}/><strong>{kindLabels[type]}</strong></Link>)}</div>
    <div className="import-layout">
      <section className="panel form-panel">
        <h2>{kindLabels[kind]}导入</h2>
        <Banner>演示处理。链接内容、摘要和索引使用样例，上传文件仅在当前浏览器校验。</Banner>
        {pending ? <div className="source-preview"><TypeIcon kind={kind}/><div><h3>{pending.title}</h3><p>补充字幕后继续原有任务。</p></div></div> : isFile ? <>
          <div className={'dropzone '+(drag?'drag':'')} onDragOver={e=>{e.preventDefault();setDrag(true)}} onDragLeave={()=>setDrag(false)}
            onDrop={e=>{e.preventDefault();setDrag(false);void choose(Array.from(e.dataTransfer.files))}}>
            <input ref={picker} type="file" aria-label="选择待导入文件" accept={formats[kind]} multiple={kind==='document'} hidden onChange={e=>{void choose(Array.from(e.target.files??[]));e.target.value=''}}/>
            <span className="drop-icon"><CloudUpload size={26}/></span><strong>拖放{kind==='video'?'视频':'文档'}到这里</strong><p>{kind==='video'?'MP4、MOV、MKV、WebM':'PDF、DOCX、TXT、Markdown · 可批量导入'} · 每个文件最大 500 MB</p>
            <Button onClick={()=>picker.current?.click()}>选择文件</Button>
          </div>
          {files.map(({file,duration},i)=><div className="uploaded" key={file.name+i}><FileText size={18}/><span className="row-label"><strong>{file.name}</strong><small>{size(file.size)}{kind==='video'?' · '+(duration!==undefined?Math.round(duration)+' 秒':'时长不可用'):''} · 待处理</small></span><IconButton label={'移除 '+file.name} onClick={()=>setFiles(v=>v.filter((_,index)=>i!==index))}><X size={16}/></IconButton></div>)}
        </> : <>
          <form className="link-entry" onSubmit={lookup}><label className="field"><span>{kind==='bili'?'Bilibili 视频链接或编号':kind==='github'?'GitHub 仓库 URL':'文章 URL'}</span><input value={url} onChange={e=>{setUrl(e.target.value);setPreview(undefined);setError('')}} placeholder={kind==='bili'?'https://www.bilibili.com/video/BV…':kind==='github'?'https://github.com/owner/repository':'https://example.com/article'}/></label>
            <Button type="submit" disabled={!url.trim()||looking}>{looking?<LoaderCircle className="spin" size={16}/>:<Search size={16}/>} {looking?'正在准备样例…':'预览样例'}</Button></form>
          <button className="text-link sample-link" onClick={()=>{setUrl(kind==='bili'?'https://www.bilibili.com/video/BV1xx411c7mD':kind==='github'?'https://github.com/langchain-ai/langgraph':'https://example.com/reliable-workflows');setError('');setPreview(undefined)}}>填入示例链接</button>
          {preview&&<div className="source-preview">
            <div className={'cover-small '+kind}><Clapperboard size={24}/></div>
            <div><span className="eyebrow">演示预览</span><h3>{samples[kind].title}</h3><p>{kind==='bili'?'架构学习室 · '+biliParts[Number(part)].length:preview.site}</p><p>{samples[kind].detail}</p></div>
          </div>}
          {kind==='bili'&&preview&&<div className="field"><span>视频分 P</span><Select value={part} label="视频分 P" change={setPart}>{biliParts.map((p,i)=><option key={p.title} value={i}>P{i+1} · {p.title} · {p.length}</option>)}</Select></div>}
        </>}
        {(pending||(kind==='video'&&files.length>0)||(kind==='bili'&&preview))&&<div className="method-box">
          <div><strong>字幕与处理方式</strong><p>{subtitle?subtitle.name:hasSubs&&!pending?'样例包含中文字幕':'没有可用字幕'}</p></div>
          <Select label="处理方式" value={method} change={setMethod}><option value="auto">优先使用已有字幕</option><option value="asr">模拟 ASR 转录</option></Select>
          <label className="field"><span>附加 SRT / VTT 字幕</span><input type="file" accept=".srt,.vtt" onChange={e=>{
            const file=e.target.files?.[0]
            if (file&&(!/\.(srt|vtt)$/i.test(file.name)||!file.size||file.size>10*1024*1024)) {setError('字幕须为非空 SRT 或 VTT，最大 10 MB。');setSubtitle(undefined);e.target.value='';return}
            setError('');setSubtitle(file)
          }}/></label>
        </div>}
        {error&&<Banner error>{error}</Banner>}
        {(preview||files.length>0)&&<label className="checkline"><input type="checkbox" checked={fail} onChange={e=>setFail(e.target.checked)}/> 演练一次处理失败与重试</label>}
        <div className="form-actions"><Link className="cw-button ghost" to="/library">查看资料库</Link><Button variant="primary" disabled={busy||looking||(pending?needsAction:isFile?!files.length:!preview)} onClick={enqueue}><Plus size={17}/>{pending?'继续原任务':needsAction?'登记待补充任务':'开始导入'}</Button></div>
      </section>
      <aside className="panel side-panel"><h2>处理流程</h2><p>在任务中心可查看进度、失败原因和重试入口。</p>
        <ol className="mini-steps">{(kind==='video'?localVideoSteps:kind==='bili'?['预览视频','选择分 P','准备字幕','理解内容','构建索引']:['校验来源','读取正文','生成摘要','构建索引']).map((name,i)=><li key={name}><span>{i+1}</span>{name}</li>)}</ol>
        <Link className="text-link" to="/tasks">打开任务中心</Link>
      </aside>
    </div>
  </>
}
