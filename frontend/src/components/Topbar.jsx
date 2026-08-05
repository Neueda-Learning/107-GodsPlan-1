import { Menu, Plus } from 'lucide-react'
import { useLocation } from 'react-router-dom'

export default function Topbar({ onMenu, onCreate }) {
  const { pathname } = useLocation()
  const title = pathname === '/' ? 'Overview' : pathname === '/customers' ? 'Customer details' : pathname === '/analytics' ? 'Analytics' : /^\/payments\/\d+/.test(pathname) ? 'Payment details' : 'Payments'
  return (
    <header className="sticky top-0 z-30 flex h-20 items-center justify-between border-b border-line bg-white px-4 sm:px-6 lg:px-8">
      <div className="flex items-center gap-3"><button onClick={onMenu} className="grid size-10 place-items-center rounded-lg border border-line text-ink-muted md:hidden" aria-label="Open navigation"><Menu className="size-5" /></button>
        <div><p className="text-xs font-medium uppercase tracking-wider text-ink-muted">Payment operations</p><h1 className="text-lg font-bold text-ink">{title}</h1></div></div>
      <button className="btn-primary" onClick={onCreate}><Plus className="size-4" /><span className="hidden sm:inline">New payment</span><span className="sm:hidden">New</span></button>
    </header>
  )
}
