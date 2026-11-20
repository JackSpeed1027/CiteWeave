import { useState } from 'react'
import { CheckCircle2, Download, FolderOpen, Github, HardDrive, Plug, Save, ShieldCheck, Unplug } from 'lucide-react'
import { useWorkspace } from './state'
import { dateLabel, destinationPath, directoryPicker, download, existingText, exportBlob, pathParts, renderedText, safeFilename, uid, writeFile } from './services'
import { targetLabels, type Destination, type Format, type Note, type Receipt, type Target } from './mock'
import { Banner, Button, Empty, Markdown, Modal, PageHead, Select } from './ui'

const icons = { github:Github, obsidian:FolderOpen, local:HardDrive }
const descriptions = {
  github:'将 Markdown 成果整理到仓库分支中，查看文件差异和模拟提交记录。',
  obsidian:'预览含 YAML Frontmatter 和来源的 Markdown，体验 Vault 目录配置。',
  local:'选择授权目录，确认文件路径和内容后保存，或体验模拟配置。',
}
const destinations:Destination[]=['github','obsidian','local']
export const formats:Record<Format,string>={md:'Markdown',txt:'纯文本 TXT',json:'JSON',pdf:'PDF',docx:'Word DOCX'}

function TargetFields({destination, value, change}: {destination:Destination;value:Target;change:(value:Target)=>void}) {
  const github=destination==='github'
  return <div className="fields">
    <label className="field"><span>{github?'演示账户':destination==='obsidian'?'Vault 名称':'根目录名称'}</span><input value={value.account} maxLength={70} onChange={e=>change({...value,account:e.target.value})} required/></label>
    {github&&<>
      <label className="field"><span>仓库</span><Select label="仓库" value={value.repo} change={repo=>change({...value,repo})}><option value="learning-notes">learning-notes</option><option value="research-reports">research-reports</option><option value="engineering-handbook">engineering-handbook</option></Select></label>
      <label className="field"><span>分支</span><Select label="分支" value={value.branch} change={branch=>change({...value,branch})}><option value="main">main</option><option value="notes">notes</option><option value="drafts">drafts</option></Select></label>
    </>}
    <label className="field"><span>默认相对目录</span><input value={value.dir} onChange={e=>change({...value,dir:e.target.value})} placeholder="例如 Research/Java"/></label>
  </div>
}

function ReceiptDetail({receipt, close}: {receipt:Receipt;close:()=>void}) {
  return <Modal drawer title={receipt.real?'真实文件写入':'演示保存回执'} description={targetLabels[receipt.destination]+' · '+dateLabel(receipt.date)} close={close}>
    <Banner>{receipt.real?'浏览器已完成写入授权目录。':'这是演示记录，目标服务没有被调用。'}</Banner>
    <p className="path">{receipt.path}</p><Markdown text={receipt.body}/>
  </Modal>
}

export function Integrations() {
  const {model,update,toast,roots,setRoot}=useWorkspace()
  const [selected,setSelected]=useState<Destination>()
  const [draft,setDraft]=useState<Target>()
  const [error,setError]=useState('')
  const [receipt,setReceipt]=useState<Receipt>()
  const [test,setTest]=useState('')
  const [testing,setTesting]=useState(false)

  function open(d:Destination) {setDraft({...model.targets[d]});setSelected(d);setError('');setTest('');setTesting(false)}
  async function pick(d:Destination) {
    const picker=directoryPicker()
    if (!picker) {setError('此浏览器不支持目录授权。可使用模拟配置或文件下载。');return}
    try {
      const root=await picker.call(window,{mode:'readwrite'})
      setRoot(d,root)
      setDraft(draft=>draft?{...draft,account:root.name}:draft)
      setError('')
      toast('已授权本次会话中的目录 '+root.name)
    } catch (e) { if (!(e instanceof DOMException && e.name==='AbortError')) setError('浏览器无法授权该目录，请重新选择。') }
  }
  function validate() {
    if (!draft?.account.trim()) throw new Error('请输入账户或目录名称。')
    pathParts(draft.dir)
  }
  function configure() {
    if (!selected||!draft) return
    try {
      validate()
      update(m=>({...m,targets:{...m.targets,[selected]:{...draft,connected:true}}}))
      toast('已保存模拟配置')
      setSelected(undefined)
    } catch (e) {setError((e as Error).message)}
  }
  function connectionTest() {
    if (testing) return
    setError('')
    try {
      validate()
      setTesting(true)
      setTest('正在检查演示配置…')
      setTimeout(()=>{setTest('配置校验通过 · 未请求外部服务');setTesting(false)},450)
    } catch (e) {setError((e as Error).message)}
  }
  return <>
    <PageHead title="外部集成" subtitle="让笔记去往你的仓库、Vault 或授权文件夹。"/>
    <Banner>当前使用模拟连接。GitHub 不会获得权限，Obsidian 与本地目录可在支持的浏览器中另行授权写入。</Banner>
    <div className="integration-grid">{destinations.map(d=>{
      const Icon=icons[d]
      const target=model.targets[d]
      return <section className="panel integration-card" key={d}><span className={'integration-icon '+d}><Icon size={24}/></span>
        <h2>{targetLabels[d]}</h2><p>{descriptions[d]}</p><span className={'status '+(target.connected||roots[d]?'done':'queued')}><i/>{roots[d]?'已授权目录':target.connected?'已配置演示连接':'未配置'}</span>
        <div className="toolbar"><Button variant="primary" onClick={()=>open(d)}><Plug size={16}/>{target.connected?'管理配置':'设置演示连接'}</Button>
          {(target.connected||roots[d])&&<Button onClick={()=>{setRoot(d,undefined);update(m=>({...m,targets:{...m.targets,[d]:{...m.targets[d],connected:false}}}));toast('已断开当前原型配置')}}><Unplug size={15}/> 断开</Button>}</div>
      </section>
    })}</div>
    <section className="panel">
      <div className="section-head"><h2>保存历史</h2></div>
      {!model.receipts.length&&<Empty title="还没有保存记录" text="从笔记选择一个目的地，完成预览和确认后记录会显示在这里。"/>}
      {model.receipts.map(r=><button className="item-row full-row" key={r.id} onClick={()=>setReceipt(r)}><span className="quick-icon purple"><Save size={17}/></span><span className="row-label"><strong>{r.path}</strong><small>{dateLabel(r.date)} · {r.real?'真实写入':'演示记录'}</small></span><CheckCircle2 size={17}/></button>)}
    </section>
    {selected&&draft&&<Modal title={'配置 '+targetLabels[selected]} description={selected==='github'?'演示权限为读取仓库列表、写入指定目录。此过程无需 Token 或 OAuth。':'保存模拟配置，或用浏览器授权真实目录。'} close={()=>setSelected(undefined)}>
      <TargetFields destination={selected} value={draft} change={setDraft}/>
      {selected!=='github'&&<Button onClick={()=>void pick(selected)}><FolderOpen size={16}/> 授权实际目录</Button>}
      {error&&<Banner error>{error}</Banner>}{test&&<Banner>{test}</Banner>}
      <div className="modal-actions"><Button disabled={testing} onClick={connectionTest}>测试连接</Button><Button variant="primary" onClick={configure}>保存演示配置</Button></div>
    </Modal>}
    {receipt&&<ReceiptDetail receipt={receipt} close={()=>setReceipt(undefined)}/>}
  </>
}

export function printPdf(title:string, body:string) {
  const frame=document.createElement('iframe')
  frame.setAttribute('title','PDF 打印内容')
  frame.style.cssText='position:fixed;left:-3000px;width:900px;height:1100px;border:0'
  document.body.appendChild(frame)
  const doc=frame.contentDocument
  const win=frame.contentWindow
  if (!doc||!win) {frame.remove();throw new Error('无法打开打印预览。')}
  doc.title=title
  doc.body.style.cssText='font-family:system-ui,sans-serif;margin:40px;color:#201d2b'
  const content=doc.createElement('pre')
  content.style.cssText='white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;line-height:1.65'
  content.textContent=body
  doc.body.appendChild(content)
  win.addEventListener('afterprint',()=>frame.remove(),{once:true})
  win.focus()
  win.print()
}

export function ExportModal({note,close}: {note:Note;close:()=>void}) {
  const {model,toast}=useWorkspace()
  const [format,setFormat]=useState<Format>('md')
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  async function exportNote() {
    setBusy(true)
    setError('')
    try {
      const content=renderedText(note,model.sources)
      if (format==='pdf') {printPdf(note.title,content);toast('已请求打印预览，可选择另存为 PDF')}
      else {download(await exportBlob(format,content,note),safeFilename(note.title)+'.'+format);toast('已发起文件下载')}
      close()
    } catch (e) {setError((e as Error).message)}
    finally {setBusy(false)}
  }
  return <Modal title="导出文档" description="下载文件，或使用浏览器打印对话框另存 PDF。" close={close}>
    <Select label="导出格式" value={format} change={v=>setFormat(v as Format)}>{Object.entries(formats).map(([k,v])=><option value={k} key={k}>{v}</option>)}</Select>
    <p className="path">{safeFilename(note.title)}.{format}</p>
    {error&&<Banner error>{error}</Banner>}
    <div className="modal-actions"><Button onClick={close}>取消</Button><Button variant="primary" disabled={busy} onClick={()=>void exportNote()}><Download size={17}/>{format==='pdf'?'打开 PDF 打印预览':'下载文件'}</Button></div>
  </Modal>
}

export function SaveModal({note, initial, close}: {note:Note;initial:Destination;close:()=>void}) {
  const {model,update,toast,roots,setRoot}=useWorkspace()
  const [destination,setDestination]=useState(initial)
  const [target,setTarget]=useState(model.targets[initial])
  const [format,setFormat]=useState<Format>('md')
  const [name,setName]=useState(note.title)
  const [real,setReal]=useState(false)
  const [mode,setMode]=useState<'config'|'preview'|'saved'>('config')
  const [check,setCheck]=useState<{path:string;existing?:string;file:string;body:string}>()
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  const [receipt,setReceipt]=useState<Receipt>()
  const [message,setMessage]=useState('docs: 保存 '+note.title)
  const canWrite=destination!=='github'&&format!=='pdf'
  function change(v:Destination) {setDestination(v);setTarget(model.targets[v]);setFormat('md');setReal(false);setError('')}
  async function pick() {
    try {
      const picker=directoryPicker()
      if (!picker) throw new Error('当前浏览器不支持此授权方式，请使用下载或演示保存。')
      const root=await picker.call(window,{mode:'readwrite'})
      setRoot(destination,root)
      setTarget(t=>({...t,account:root.name}))
      setReal(true)
      setError('')
    } catch(e) { if (!(e instanceof DOMException&&e.name==='AbortError')) setError((e as Error).message) }
  }
  async function preview() {
    setError('')
    setBusy(true)
    try {
      if (!target.account.trim()) throw new Error('请填写保存目标的名称。')
      if (real&&(!roots[destination]||!canWrite)) throw new Error('请先选择可写目录和支持的文件格式。')
      if (destination==='github'&&!message.trim()) throw new Error('请输入模拟提交信息。')
      const file=safeFilename(name.replace(/\.(md|txt|json|pdf|docx)$/i,''))+'.'+format
      const root=roots[destination]
      const path=destinationPath(destination,real&&root?{...target,account:root.name}:target,target.dir,file)
      const existing=real&&root ? await existingText(root,target.dir,file) : model.receipts.find(r=>r.path===path&&r.destination===destination&&!r.real)?.body
      setCheck({path,existing,file,body:renderedText(note,model.sources,destination)})
      setMode('preview')
    } catch (e) {setError((e as Error).message)}
    finally {setBusy(false)}
  }
  async function commit() {
    if(!check||busy) return
    setBusy(true)
    setError('')
    try {
      if(real) {
        const root=roots[destination]
        if(!root||format==='pdf') throw new Error('目标目录的写入授权已失效。')
        await writeFile(root,target.dir,check.file,await exportBlob(format,check.body,note),check.existing)
      }
      const entry:Receipt={id:uid(),destination,body:check.body,path:check.path,date:Date.now(),real}
      update(m=>({...m,receipts:[entry,...m.receipts]}))
      setReceipt(entry)
      setMode('saved')
      toast(real?'文件已写入授权目录':'已记录演示保存结果')
    } catch(e) {setError((e as Error).message)}
    finally {setBusy(false)}
  }
  return <Modal drawer title="保存到目的地" description="确认目标和文件内容，再保存到原型历史或授权目录。" close={close}>
    {mode==='config'?<>
      <div className="destination-tabs">{destinations.map(d=>{const Icon=icons[d];return <Button aria-pressed={destination===d} key={d} onClick={()=>change(d)}><Icon size={17}/>{targetLabels[d]}</Button>})}</div>
      <Banner>{real?'实际文件写入 · 仅当前授权目录。':'演示保存 · 外部系统与本地文件不会被修改。'}</Banner>
      <TargetFields destination={destination} value={target} change={setTarget}/>
      <label className="field"><span>文件名</span><input value={name} onChange={e=>setName(e.target.value)} maxLength={120}/></label>
      {destination==='local'&&<label className="field"><span>文件格式</span><Select label="文件格式" value={format} change={v=>{setFormat(v as Format);if(v==='pdf')setReal(false)}}>{Object.entries(formats).map(([k,v])=><option key={k} value={k}>{v}</option>)}</Select></label>}
      {destination==='github'&&<label className="field"><span>提交信息</span><input value={message} onChange={e=>setMessage(e.target.value)}/></label>}
      {destination==='local'&&format==='pdf'&&<small className="muted">PDF 可模拟保存到目录。实际 PDF 请用导出中的打印预览。</small>}
      {canWrite&&<div className="toolbar"><Button onClick={()=>void pick()}><FolderOpen size={16}/> 授权实际目录</Button>{roots[destination]&&!real&&<Button onClick={()=>setReal(true)}>使用已授权目录</Button>}{real&&<Button onClick={()=>setReal(false)}>切回模拟保存</Button>}</div>}
      {error&&<Banner error>{error}</Banner>}
      <div className="modal-actions"><Button variant="primary" disabled={busy||!name.trim()} onClick={()=>void preview()}>检查路径并预览</Button></div>
    </>:mode==='preview'&&check?<>
      <Banner>{check.existing!==undefined?'目标已存在，请核对完整差异并确认更新。':'这是一个新文件。请核对位置和内容。'}</Banner>
      <p className="path">{check.path}</p>
      {destination==='github'&&<p className="muted">模拟提交信息 · {message}</p>}
      {check.existing!==undefined&&<div className="diff"><div><strong>当前内容</strong><pre>{check.existing}</pre></div><div><strong>计划更新</strong><pre>{check.body}</pre></div></div>}
      <div className="save-preview"><Markdown text={check.body}/></div>
      {error&&<Banner error>{error}</Banner>}
      <div className="modal-actions"><Button disabled={busy} onClick={()=>{setMode('config');setError('')}}>返回修改</Button><Button variant="primary" disabled={busy} onClick={()=>void commit()}><ShieldCheck size={17}/>{check.existing!==undefined?(real?'确认覆盖已授权文件':'确认模拟更新'):(real?'确认真实写入':destination==='github'?'确认模拟提交':'确认模拟保存')}</Button></div>
    </>:receipt&&<>
      <Banner>{receipt.real?'文件已真实写入浏览器授权的目录。':destination==='github'?'模拟提交已记录，未创建 GitHub 提交。':'演示保存已记录，未写入本机文件。'}</Banner>
      <p className="path">{receipt.path}</p><p className="muted">{dateLabel(receipt.date)}</p>
      <div className="modal-actions"><Button variant="primary" onClick={close}>完成</Button></div>
    </>}
  </Modal>
}
