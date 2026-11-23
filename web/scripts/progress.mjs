import { readFileSync, writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

const dataUrl = new URL('../src/progress/milestones.json', import.meta.url)
const readmeUrl = new URL('../../README.md', import.meta.url)
const title = '## 项目进度看板'
const start = '<!-- progress:start -->'
const end = '<!-- progress:end -->'

/** 将版本化证据渲染为 README 看板，并拒绝未知的状态或重复的里程碑。 */
export function renderProgress(data) {
  const seen = new Set()
  const rows = data.milestones.map(({ id, title, stage, evidence, reference, owner, next, acceptance }) => {
    if (seen.has(id) || !Object.hasOwn(data.stages, stage)) {
      throw new Error(`里程碑 ID 重复或状态未知 · ${id} / ${stage}`)
    }
    seen.add(id)
    const { label, symbol } = data.stages[stage]
    return `| ${title} | ${symbol} ${label} | ${evidence} [${reference.label}](${reference.path}) | **${owner}** · ${next}<br>${acceptance} |`
  })
  return [
    start, '',
    `更新于 ${data.asOf}`, '',
    `- **现在能用** · ${data.available}`,
    `- **最近验证** · ${data.latest}`,
    `- **下一道验收** · ${data.next}`, '',
    '| 交付里程碑 | 当前进度 | 已有证据 | 负责人及下一道验收 |',
    '| --- | --- | --- | --- |',
    ...rows, '',
    '状态来自[里程碑数据](web/src/progress/milestones.json)。更新方法见[开发指南](docs/development/DEVELOPMENT.md#维护项目进度看板)。', '',
    end,
  ].join('\n')
}

/** 只替换生成区域，保留 README 的其他内容和原有换行格式。 */
export function updateReadme(readme, progress) {
  const newline = readme.includes('\r\n') ? '\r\n' : '\n'
  const from = readme.indexOf(start)
  const to = readme.indexOf(end)
  const heading = readme.indexOf(title)
  if (heading === -1 || from < heading || to < from
      || from !== readme.lastIndexOf(start) || to !== readme.lastIndexOf(end)) {
    throw new Error('README 中的进度看板标记缺失或重复。')
  }
  return readme.slice(0, from) + progress.replaceAll('\n', newline) + readme.slice(to + end.length)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const data = JSON.parse(readFileSync(dataUrl, 'utf8'))
  const original = readFileSync(readmeUrl, 'utf8')
  const updated = updateReadme(original, renderProgress(data))
  if (process.argv[2] === '--write') {
    if (updated !== original) writeFileSync(readmeUrl, updated)
  } else if (updated !== original) {
    console.error('README 进度与 Web 不一致。请在 web 目录运行 npm run progress:write。')
    process.exitCode = 1
  } else {
    console.log('README 与 Web 里程碑一致。')
  }
}
