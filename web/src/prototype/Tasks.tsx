import { Link, useSearchParams } from 'react-router-dom'
import { Activity, CircleCheck, CircleX, LoaderCircle, RotateCcw, Square } from 'lucide-react'
import { useWorkspace } from './state'
import { dateLabel, stepIndex } from './services'
import { Banner, Button, Empty, Modal, PageHead, Status } from './ui'

export function Tasks() {
  const {model,now,taskAction}=useWorkspace()
  const [params,setParams]=useSearchParams()
  const filter=params.get('status')??'all'
  const shown=model.tasks.filter(t=>filter==='all'||(filter==='active' ? ['queued','running','action'].includes(t.stage):t.stage===filter))
  const selected=model.tasks.find(t=>t.id===params.get('id'))
  const index=selected?.stage==='done'?selected.steps.length:selected?.stage==='running'?stepIndex(selected.started,selected.steps.length,now):selected?.stage==='failed'?2:0
  const progress=selected ? Math.round(Math.min(1, (index+(selected.stage==='running'?0.4:0))/selected.steps.length)*100) : 0
  const tabs=[['all','全部任务'],['active','待处理与进行中'],['done','已完成'],['failed','失败'],['cancelled','已取消']]
  return <>
    <PageHead title="任务中心" subtitle="追踪导入和处理进度，查看每一步的执行状态。" actions={<Link className="cw-button primary" to="/import">导入资料</Link>}/>
    <div className="panel">
      <div className="tabs">{tabs.map(([id,label])=><button aria-pressed={filter===id} key={id} onClick={()=>setParams({status:id})}>{label}</button>)}</div>
      {!shown.length&&<Empty title="没有符合条件的任务" text="切换筛选或从导入中心开启一个任务。"/>}
      {shown.map(t=><button type="button" className="item-row full-row" key={t.id} onClick={()=>setParams({status:filter,id:t.id})}><span className="quick-icon purple"><Activity size={18}/></span><span className="row-label"><strong>{t.title}</strong><small>{dateLabel(t.started)} · 模拟处理</small></span><Status stage={t.stage}/></button>)}
    </div>
    {selected&&<Modal drawer title={selected.title} description="演示任务 · 处理详情" close={()=>setParams({status:filter})}>
      <div className="task-summary"><Status stage={selected.stage}/><span>{dateLabel(selected.started)}</span></div>
      <div className="task-progress"><progress aria-label="模拟处理进度" value={progress} max={100}/><span>{progress}%</span></div>
      {selected.stage==='action'&&<Banner>{selected.detail}</Banner>}
      {selected.stage==='failed'&&<Banner error>演示故障：处理服务超时。重试将从新的模拟任务尝试开始。</Banner>}
      <ol className="task-steps">{selected.steps.map((step,i)=>{
        return <li key={step}>
          <span className={'step-dot '+(i<index?'done':i===index&&selected.stage==='running'?'current':'')}>
            {i<index?<CircleCheck size={20}/>:i===index&&selected.stage==='running'?<LoaderCircle className="spin" size={18}/>:i===index&&selected.stage==='failed'?<CircleX size={20}/>:i+1}
          </span><span><strong>{step}</strong><small>{i<index?'模拟步骤完成':i===index&&selected.stage==='running'?'正在处理…':i===index&&selected.stage==='failed'?'模拟超时':'等待处理'}</small></span>
        </li>
      })}</ol>
      <div className="toolbar">
        {selected.stage==='done'&&<Link className="cw-button primary" to={'/source/'+selected.sourceId}>打开资料工作区</Link>}
        {['queued','running'].includes(selected.stage)&&<Button onClick={()=>taskAction(selected.id,'cancel')}><Square size={15}/>取消任务</Button>}
        {['failed','cancelled'].includes(selected.stage)&&<Button variant="primary" onClick={()=>taskAction(selected.id,'retry')}><RotateCcw size={15}/>重新执行</Button>}
        {selected.stage==='action'&&<Link className="cw-button primary" to={'/import?kind='+(model.sources.find(s=>s.id===selected.sourceId)?.kind??'bili')+'&resume='+selected.id}>返回导入补充字幕</Link>}
      </div>
    </Modal>}
  </>
}
