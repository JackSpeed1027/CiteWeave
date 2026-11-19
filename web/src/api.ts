export type Kind = 'TEXT' | 'TXT' | 'MARKDOWN' | 'PDF' | 'SRT' | 'VTT'

export type Source = {
  id: number
  title: string
  kind: Kind
  filename: string | null
  createdAt: string
}

export type SourceDetail = { source: Source; content: string }

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch('/api/sources' + path, { ...options, signal: AbortSignal.timeout(60_000) })
  } catch {
    throw new Error('无法连接服务或请求超时，请检查后端状态后重试。')
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    const friendly = response.status >= 500 ? '服务暂时不可用，请检查后端及数据库。' : '请求失败，请检查输入内容。'
    throw new Error(response.status < 500 && problem?.detail ? problem.detail : friendly)
  }
  return response.json()
}

export const api = {
  list: () => request<Source[]>(''),
  get: (id: number) => request<SourceDetail>('/' + id),
  paste: (title: string, content: string) => request<SourceDetail>('', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title, content }),
  }),
  upload: (file: File, title: string) => {
    const body = new FormData()
    body.append('file', file)
    body.append('title', title)
    return request<SourceDetail>('', { method: 'POST', body })
  },
}
