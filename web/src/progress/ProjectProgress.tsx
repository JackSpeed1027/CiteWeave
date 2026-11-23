import { CircleCheck, CircleDashed, FlaskConical, Link2, PanelsTopLeft } from 'lucide-react'
import data from './milestones.json'

const icons = {
  workspace: CircleCheck,
  integration: Link2,
  offline: FlaskConical,
  fixture: FlaskConical,
  prototype: PanelsTopLeft,
  planned: CircleDashed,
}

/** 展示已记录的里程碑证据，与资料 API 的加载状态独立。 */
export function ProjectProgress() {
  return <section className="panel project-progress" aria-labelledby="project-progress-heading">
    <div className="panel-heading">
      <h2 id="project-progress-heading">项目进度</h2>
      <time dateTime={data.asOf}>更新于 {data.asOf}</time>
    </div>
    <dl className="progress-overview">
      <div><dt>现在能用</dt><dd>{data.available}</dd></div>
      <div><dt>最近验证</dt><dd>{data.latest}</dd></div>
      <div><dt>下一道验收</dt><dd>{data.next}</dd></div>
    </dl>
    <ol className="progress-milestones">
      {data.milestones.map((milestone) => {
        const stageKey = milestone.stage as keyof typeof data.stages
        const stage = data.stages[stageKey]
        const Icon = icons[stageKey]
        return <li className="progress-milestone" key={milestone.id}>
          <div className="progress-stage">
            <h3>{milestone.title}</h3>
            <span className={`progress-badge tone-${stage.tone}`}><Icon size={14} aria-hidden="true" />{stage.label}</span>
          </div>
          <p className="progress-evidence">{milestone.evidence}</p>
          <div className="progress-next">
            <p><strong>{milestone.owner} · </strong>{milestone.next}</p>
            <p className="progress-acceptance">{milestone.acceptance}</p>
          </div>
        </li>
      })}
    </ol>
  </section>
}
