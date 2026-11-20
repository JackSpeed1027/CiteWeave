import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Download, Plug, Save } from 'lucide-react'
import { useWorkspace } from './state'
import { targetLabels, type Destination } from './mock'
import { download } from './services'
import { Banner, Button, PageHead, Select } from './ui'

export function Settings() {
  const {model,update,toast}=useWorkspace()
  const [settings,setSettings]=useState(model.settings)
  const patch=(k:keyof typeof settings, v:string)=>setSettings(o=>({...o,[k]:v}))
  const save=(e:React.FormEvent)=>{
    e.preventDefault()
    if(!settings.name.trim())return
    update(m=>({...m,settings:{...settings,name:settings.name.trim()}}))
    toast('原型偏好已保存')
  }
  return <>
    <PageHead title="设置" subtitle="管理个人资料、偏好与原型数据。"/>
    <div className="settings-grid">
      <form className="panel form-panel" onSubmit={save}><h2>个人资料与工作区</h2>
        <label className="field"><span>显示名称</span><input value={settings.name} maxLength={40} required onChange={e=>patch('name',e.target.value)}/></label>
        <label className="field"><span>主题</span><Select label="主题" value={settings.theme} change={s=>patch('theme',s)}><option value="light">浅色</option><option value="dark">深色</option></Select></label>
        <label className="field"><span>默认保存目的地</span><Select label="默认保存目的地" value={settings.defaultTarget} change={s=>setSettings(o=>({...o,defaultTarget:s as Destination}))}>{Object.entries(targetLabels).map(([k,v])=><option value={k} key={k}>{v}</option>)}</Select></label>
        <h2>模型与 API</h2>
        <Banner>模型配置仅用于体验设置流程。请勿输入密钥或凭据。</Banner>
        <label className="field"><span>演示模型名称</span><input value={settings.model} onChange={e=>patch('model',e.target.value)} placeholder="CiteWeave Demo"/></label>
        <label className="field"><span>API 地址（演示）</span><input type="url" value={settings.endpoint} onChange={e=>patch('endpoint',e.target.value)} placeholder="https://api.example.com/v1"/></label>
        <div className="form-actions"><span/><Button type="submit" variant="primary"><Save size={17}/> 保存设置</Button></div>
      </form>
      <aside className="panel side-panel"><h2>集成与数据</h2><p>本原型将工作区数据保存在当前浏览器。</p>
        <Link className="cw-button secondary" to="/integrations"><Plug size={16}/> 管理集成</Link>
        <Button onClick={()=>{download(new Blob([JSON.stringify(model,null,2)],{type:'application/json'}),'citeweave-prototype-backup.json');toast('已发起原型数据下载')}}><Download size={16}/> 下载工作区数据</Button>
      </aside>
    </div>
  </>
}
