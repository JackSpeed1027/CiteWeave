import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { renderProgress, updateReadme } from './progress.mjs'

const data = JSON.parse(readFileSync(new URL('../src/progress/milestones.json', import.meta.url), 'utf8'))
const readme = readFileSync(new URL('../../README.md', import.meta.url), 'utf8')

test('版本化数据与仓库 README 一致', () => {
  assert.equal(updateReadme(readme, renderProgress(data)), readme)
})

test('在内存中改变一个状态，发现 README 过期并只同步生成区域', () => {
  const fixture = structuredClone(data)
  const item = fixture.milestones.find(({ id }) => id === 'cited-summary')
  item.stage = 'integration'
  const updated = updateReadme(readme, renderProgress(fixture))
  assert.notEqual(updated, readme)
  assert.ok(updated.includes(`| ${item.title} | 🔵 真实接口已联调 |`))
  const closing = '<!-- progress:end -->'
  assert.equal(updated.split(closing)[1], readme.split(closing)[1])
  assert.equal(updateReadme(updated, renderProgress(fixture)), updated)
})

test('拒绝未知的阶段', () => {
  const fixture = structuredClone(data)
  fixture.milestones[0].stage = 'unknown'
  assert.throws(() => renderProgress(fixture), /状态未知/)
})
