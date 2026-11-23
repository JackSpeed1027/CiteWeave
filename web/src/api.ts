export type Kind = 'TEXT' | 'TXT' | 'MARKDOWN' | 'PDF' | 'SRT' | 'VTT'

export type Source = {
  id: number
  title: string
  kind: Kind
  filename: string | null
  createdAt: string
}

/** 平台信息来自服务端复核，CID 以字符串传输。 */
export type VideoOrigin = {
  bvid: string; page: number; cid: string; videoTitle: string; partTitle: string; trackSource: 'USER_UPLOAD'
}
export type VideoPreview = {
  bvid: string; title: string; suggestedPart: number
  parts: { page: number; cid: string; title: string; durationSeconds: number }[]
}
/** 本地视频预检只返回元数据，audioCodec 为空表示没有识别到音轨。 */
export type LocalVideoPreview = {
  sizeBytes: number; durationMs: number; width: number; height: number; videoCodec: string; audioCodec: string | null
}
/** 已提交的本地媒体记录，存储路径仅由服务端管理。 */
export type LocalVideo = {
  id: string; filename: string; sha256: string; createdAt: string; metadata: LocalVideoPreview
}
export type SourceDetail = {
  source: Source; content: string; videoOrigin?: VideoOrigin
  localVideoOrigin?: { videoId: string; filename: string; trackSource?: 'USER_UPLOAD' | 'MACHINE_ASR' }
}
export type LocalVideoSubtitle = {
  sourceId: number; title: string; filename: string; kind: 'SRT' | 'VTT'; trackSource?: 'USER_UPLOAD' | 'MACHINE_ASR'
}
export type AsrTask = {
  videoId: string; status: 'QUEUED' | 'RUNNING' | 'FAILED' | 'SUCCEEDED'
  attempt: number; errorCode: string | null; sourceId: number | null; updatedAt: string
}
export type AsrStatus = { available: boolean; task: AsrTask | null }

/** 字幕时间区间以整数毫秒表示，序号来自既有解析器。 */
export type TranscriptSegment = { sequence: number; startMs: number; endMs: number; text: string }
export type SummaryCitation = Omit<TranscriptSegment, 'sequence'> & {
  evidenceId: string; sourceId: string; sourceVersionId: string; locationType: 'transcript'
}
/** 文档引用使用从 1 开始的段落编号。 */
export type ParagraphCitation = {
  evidenceId: string; sourceId: string; sourceVersionId: string; paragraph: number; text: string; locationType: 'paragraph'
}
export type PageCitation = {
  evidenceId: string; sourceId: string; sourceVersionId: string; page: number; text: string; locationType: 'page'
}
export type DocumentCitation = ParagraphCitation | PageCitation
/** 每条引用按原资料类型携带可跳转坐标。 */
export type SourceCitation = SummaryCitation | DocumentCitation
export type TranscriptSummaryResult = { summary: string; citations: SummaryCitation[]; truncated: boolean }
/** 问答区分有据回答和证据不足。 */
export type TranscriptAnswer<Citation = SummaryCitation> = {
  status: 'ANSWERED' | 'INSUFFICIENT_EVIDENCE'; answer: string | null; citations: Citation[]
}
/** 研究轨迹只包含已完成的步骤和证据标识。 */
export type ResearchStep = { callId: string; tool: 'search_evidence' | 'read_evidence'; evidenceIds: string[] }
export type ResearchHeader = { id: string; question: string; createdAt: string }
/** 已保存研究包含原问题、资料范围与经服务端核验的证据。 */
export type ResearchResult = TranscriptAnswer<SourceCitation> & {
  record: ResearchHeader; sourceIds: number[]; trace: ResearchStep[]
}
/** 只读恢复在途请求的原输入和已提交步骤。 */
export type ResearchRequestStatus = {
  status: 'RUNNING' | 'UNKNOWN' | 'FAILED' | 'COMPLETED'
  question: string; sourceIds: number[]; trace: ResearchStep[]
  recordId: string | null; nextRequestKey: string | null; canResume: boolean
}
/** 摘要笔记保存已发布版本及其证据快照。 */
export type NoteHeader = { id: string; sourceId: number; title: string; createdAt: string }
export type TranscriptNote = { note: NoteHeader; snapshot: TranscriptSummaryResult }
export type ResearchNoteHeader = { id: string; researchId: string; title: string; revision: number; updatedAt: string }
/** 编辑正文与保留原研究引用。 */
export type ResearchNote = { note: ResearchNoteHeader; markdown: string; citations: SourceCitation[] }
/** 后台摘要任务及服务端确认的最新状态。 */
export type SummaryTask = {
  id: string; sourceId: number; status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  attempt: number; errorCode: string | null; updatedAt: string
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string,
    readonly trace: ResearchStep[] = []) {
    super(message)
    this.name = 'ApiError'
  }
}

async function requestResponse(path: string, options?: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(path, { ...options, signal: AbortSignal.timeout(60_000) })
  } catch {
    throw new Error('无法连接服务或请求超时，请检查后端状态后重试。')
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    const friendly = response.status >= 500 ? '服务暂时不可用，请检查后端及数据库。' : '请求失败，请检查输入内容。'
    const detail = typeof problem?.detail === 'string' && problem.detail ? problem.detail : friendly
    const code = typeof problem?.code === 'string' ? problem.code : undefined
    const research = code?.startsWith('RESEARCH_')
    const known = research || code?.startsWith('SUMMARY_') || code?.startsWith('BILIBILI_') ||
      code?.startsWith('QUESTION_') || code?.startsWith('VIDEO_') || code?.startsWith('ASR_')
    const trace = research && Array.isArray(problem.trace) ? problem.trace : []
    throw new ApiError(response.status < 500 || known ? detail : friendly, response.status, code, trace)
  }
  return response
}

async function request<T>(path: string, options?: RequestInit): Promise<T>
async function request<T>(path: string, options: RequestInit | undefined, allowEmpty: true): Promise<T | null>
async function request<T>(path: string, options?: RequestInit, allowEmpty = false): Promise<T | null> {
  const response = await requestResponse(path, options)
  if (allowEmpty && response.status === 204) return null
  return response.json()
}

export const api = {
  previewLocalVideo: (file: File) => {
    const form = new FormData()
    form.set('file', file)
    return request<LocalVideoPreview>('/api/videos/local/preview', { method: 'POST', body: form })
  },
  saveLocalVideo: async (file: File) => {
    const form = new FormData()
    form.set('file', file)
    const response = await requestResponse('/api/videos/local', { method: 'POST', body: form })
    const video: LocalVideo = await response.json()
    return { video, created: response.status === 201 }
  },
  localVideos: () => request<LocalVideo[]>('/api/videos/local', { cache: 'no-store' }),
  localVideoSubtitles: (id: string) => request<LocalVideoSubtitle[]>(`/api/videos/local/${encodeURIComponent(id)}/subtitles`,
    { cache: 'no-store' }),
  videoTranscription: (id: string) => request<AsrStatus>(`/api/videos/local/${encodeURIComponent(id)}/transcription`,
    { cache: 'no-store' }),
  submitVideoTranscription: (id: string, retry: boolean) => request<AsrTask>(
    `/api/videos/local/${encodeURIComponent(id)}/transcription${retry ? '/retry' : ''}`, { method: 'POST' }),
  uploadLocalVideoSubtitle: async (id: string, file: File) => {
    const form = new FormData()
    form.set('file', file)
    const response = await requestResponse(`/api/videos/local/${encodeURIComponent(id)}/subtitles`, { method: 'POST', body: form })
    const track: LocalVideoSubtitle = await response.json()
    return { track, created: response.status === 201 }
  },
  list: () => request<Source[]>('/api/sources'),
  favorites: () => request<Source[]>('/api/sources?favorite=true', { cache: 'no-store' }),
  get: (id: number) => request<SourceDetail>(`/api/sources/${id}`),
  favorite: (id: number) => request<{ favorited: boolean }>(`/api/sources/${id}/favorite`, { cache: 'no-store' }),
  setFavorite: (id: number, favorited: boolean) => request<{ favorited: boolean }>(`/api/sources/${id}/favorite`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ favorited }),
  }),
  transcript: (id: number) => request<TranscriptSegment[]>(`/api/sources/${id}/transcript`),
  summary: (id: number) => request<TranscriptSummaryResult>(`/api/sources/${id}/summary`, undefined, true),
  generateSummary: (id: number) => request<TranscriptSummaryResult>(`/api/sources/${id}/summary`, { method: 'POST' }),
  queueSummary: (id: number) => request<SummaryTask>(`/api/sources/${id}/summary-task`, { method: 'POST' }),
  summaryTasks: () => request<SummaryTask[]>('/api/summary-tasks', { cache: 'no-store' }),
  retrySummaryTask: (id: string) => request<SummaryTask>(`/api/summary-tasks/${encodeURIComponent(id)}/retry`, {
    method: 'POST',
  }),
  askTranscript: (id: number, question: string) => request<TranscriptAnswer>(`/api/sources/${id}/questions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question }),
  }),
  paragraphs: (id: number) => request<ParagraphCitation[]>(`/api/sources/${id}/paragraphs`),
  pdfPages: (id: number) => request<PageCitation[]>(`/api/sources/${id}/pdf-pages`),
  askDocument: (id: number, question: string) => request<TranscriptAnswer<DocumentCitation>>(
    `/api/sources/${id}/document-questions`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ question }),
    }),
  questionSources: () => request<Source[]>('/api/source-questions/sources'),
  searchEvidence: (sourceIds: number[], query: string) => request<SourceCitation[]>('/api/source-evidence/search', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourceIds, query }),
  }),
  askSources: (sourceIds: number[], question: string) => request<TranscriptAnswer<SourceCitation>>('/api/source-questions', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourceIds, question }),
  }),
  runResearch: (sourceIds: number[], question: string, requestKey: string, resumeFrom?: string) => request<ResearchResult>('/api/research', {
    method: 'POST', headers: {
      'Content-Type': 'application/json', 'Idempotency-Key': requestKey,
      ...(resumeFrom ? { 'Research-Resume-From': resumeFrom } : {}),
    },
    body: JSON.stringify({ sourceIds, question }),
  }),
  recentResearch: () => request<ResearchHeader[]>('/api/research', { cache: 'no-store' }),
  researchRequestStatus: (key: string) => request<ResearchRequestStatus>(
    `/api/research/requests/${encodeURIComponent(key)}`, { cache: 'no-store' }),
  researchRecord: (id: string) => request<ResearchResult>(`/api/research/${encodeURIComponent(id)}`, { cache: 'no-store' }),
  createResearchNote: (id: string) => request<ResearchNote>(`/api/research/${encodeURIComponent(id)}/note`, { method: 'POST' }),
  researchNotes: () => request<ResearchNoteHeader[]>('/api/research-notes', { cache: 'no-store' }),
  researchNote: (id: string) => request<ResearchNote>(`/api/research-notes/${encodeURIComponent(id)}`, { cache: 'no-store' }),
  updateResearchNote: (id: string, title: string, markdown: string, revision: number) =>
    request<ResearchNote>(`/api/research-notes/${encodeURIComponent(id)}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title, markdown, revision }),
    }),
  transcriptEvidence: (id: number, evidenceId: string) => request<SummaryCitation>(
    `/api/sources/${id}/transcript/evidence?${new URLSearchParams({ evidenceId })}`),
  saveSummaryNote: (id: number) => request<TranscriptNote>(`/api/sources/${id}/summary/note`, { method: 'POST' }),
  notes: () => request<NoteHeader[]>('/api/notes'),
  note: (id: string) => request<TranscriptNote>(`/api/notes/${encodeURIComponent(id)}`),
  noteMarkdown: async (id: string) => {
    const response = await requestResponse(`/api/notes/${encodeURIComponent(id)}/export/markdown`)
    return response.blob()
  },
  paste: (title: string, content: string) => request<SourceDetail>('/api/sources', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title, content }),
  }),
  upload: (file: File, title: string) => {
    const body = new FormData()
    body.append('file', file)
    body.append('title', title)
    return request<SourceDetail>('/api/sources', { method: 'POST', body })
  },
  previewBilibili: (input: string) => request<VideoPreview>('/api/video-imports/bilibili/preview', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ input }),
  }),
  uploadBilibiliSubtitle: (bvid: string, page: number, cid: string, file: File) => {
    const body = new FormData()
    body.set('bvid', bvid)
    body.set('page', String(page))
    body.set('cid', cid)
    body.set('file', file)
    return request<{ sourceId: number }>('/api/video-imports/bilibili/subtitles', { method: 'POST', body })
  },
}
