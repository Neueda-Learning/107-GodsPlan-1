import { CreditCard, LayoutDashboard, Users, X } from 'lucide-react'
import { NavLink } from 'react-router-dom'

const links = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/customers', label: 'Customer Details', icon: Users },
]

export default function Sidebar({ open, onClose }) {
  return (
    <>
      {open && <button className="fixed inset-0 z-40 bg-slate-900/30 md:hidden" onClick={onClose} aria-label="Close navigation" />}
      <aside className={`fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-line bg-white transition-transform duration-200 md:translate-x-0 md:w-20 lg:w-64 ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex h-20 items-center gap-3 border-b border-line px-5 md:justify-center md:px-3 lg:justify-start lg:px-5">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary text-white shadow-sm"><CreditCard className="size-5" /></span>
          <div className="md:hidden lg:block"><p className="text-sm font-bold leading-tight text-ink">God&apos;s Plan</p><p className="text-xs text-ink-muted">Payments</p></div>
          <button className="ml-auto rounded p-1 text-ink-muted md:hidden" onClick={onClose} aria-label="Close menu"><X className="size-5" /></button>
        </div>
        <nav className="flex-1 space-y-1 p-3" aria-label="Main navigation">
          {links.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={onClose} title={label}
            className={({ isActive }) => `flex h-11 items-center gap-3 rounded-lg px-3 text-sm font-medium transition md:justify-center lg:justify-start ${isActive ? 'bg-primary-light text-primary-hover' : 'text-ink-muted hover:bg-slate-50 hover:text-ink'}`}>
            <Icon className="size-5 shrink-0" /><span className="md:hidden lg:inline">{label}</span>
          </NavLink>)}
        </nav>
        <div className="border-t border-line p-4 md:px-2 lg:px-4"><div className="rounded-lg bg-canvas p-3 text-xs text-ink-muted md:text-center lg:text-left"><span className="md:hidden lg:inline">Training workspace</span><span className="mt-1 flex items-center gap-2 font-medium text-ink md:justify-center lg:justify-start"><span className="size-2 rounded-full bg-primary" />Demo mode</span></div></div>
      </aside>
    </>
  )
}
