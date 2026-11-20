import * as Dialog from '@radix-ui/react-dialog'
import { X, Video, FileText, Globe2, Github, MonitorPlay, FolderOpen, Diamond, CircleAlert, ChevronDown } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { kindLabels, stageLabels, type Kind, type Stage } from './mock'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

export function Button({ className = '', variant = 'secondary', type = 'button', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & {variant?: 'primary' | 'secondary' | 'ghost' | 'danger'}) {
  return <button type={type} className={'cw-button ' + variant + ' ' + className} {...props} />
}
export function IconButton({ label, children, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & {label:string}) {
  return <button type="button" className="cw-icon-button" aria-label={label} title={label} {...props}>{children}</button>
}
export function Modal({ title, description, drawer, close, children }: {title:string;description:string;drawer?:boolean;close:()=>void;children:ReactNode}) {
  return <Dialog.Root open onOpenChange={open=>{if (!open) close()}}>
    <Dialog.Portal>
      <Dialog.Overlay className="modal-overlay"/>
      <Dialog.Content className={drawer ? 'modal drawer' : 'modal'}>
        <div className="modal-head"><div><Dialog.Title>{title}</Dialog.Title><Dialog.Description>{description}</Dialog.Description></div>
          <Dialog.Close asChild><IconButton label="关闭"><X size={19}/></IconButton></Dialog.Close></div>
        <div className="modal-body">{children}</div>
      </Dialog.Content>
    </Dialog.Portal>
  </Dialog.Root>
}
export function PageHead({ title, subtitle, actions }: {title:string;subtitle:string;actions?:ReactNode}) {
  return <div className="page-head"><div><h1>{title}</h1><p>{subtitle}</p></div>{actions && <div className="toolbar">{actions}</div>}</div>
}
export function Empty({ title, text, children }: {title:string;text:string;children?:ReactNode}) {
  return <div className="empty"><span className="empty-icon"><FolderOpen size={24}/></span><strong>{title}</strong><p>{text}</p>{children}</div>
}
const icons = {bili:MonitorPlay,video:Video,document:FileText,web:Globe2,github:Github}
export function TypeIcon({ kind, size=18 }: {kind:Kind;size?:number}) {
  const Icon=icons[kind]
  return <span className={'type-icon '+kind} title={kindLabels[kind]}><Icon size={size}/></span>
}
export function Status({ stage }: {stage:Stage}) {
  return <span className={'status '+stage}><i/>{stageLabels[stage]}</span>
}
export function Banner({ children, error=false }: {children:ReactNode;error?:boolean}) {
  return <div className={error?'banner error':'banner'} role={error?'alert':undefined}>{error?<CircleAlert size={17}/>:<Diamond size={16}/>}<div>{children}</div></div>
}
export function Select({ label, value, change, children }: {label:string; value:string; change:(value:string)=>void; children:ReactNode}) {
  return <label className="select-wrap"><select aria-label={label} value={value} onChange={e=>change(e.target.value)}>{children}</select><ChevronDown size={14}/></label>
}
export function Markdown({ text }: {text:string}) {
  return <div className="markdown"><ReactMarkdown remarkPlugins={[remarkGfm]} components={{
    a:props=><a {...props} target="_blank" rel="noopener noreferrer"/>,
    img:props=><img {...props} loading="lazy" referrerPolicy="no-referrer"/>,
  }}>{text}</ReactMarkdown></div>
}
