import { ArrowLeftRight, Building2, ChevronLeft, ChevronRight, CreditCard, LayoutDashboard, Landmark, UserRoundCheck, X } from 'lucide-react'
import { NavLink } from 'react-router-dom'

const links = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/customers', label: 'Beneficiaries', icon: UserRoundCheck },
  { to: '/accounts', label: 'Accounts', icon: Landmark },
  { to: '/transactions', label: 'Transactions', icon: ArrowLeftRight },
]

const initials = (name) => name?.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'GP'

export default function Sidebar({ open, onClose, collapsed, onCollapse, currentUser }) {
  return (
    <>
      {open && <button className="fixed inset-0 z-40 bg-slate-950/55 backdrop-blur-sm md:hidden" onClick={onClose} aria-label="Close navigation" />}
      <aside className={`fixed inset-y-0 left-0 z-50 m-0 flex w-[288px] flex-col overflow-visible bg-[#111827] text-slate-200 shadow-2xl transition-[width,transform] duration-300 md:m-3 md:h-[calc(100vh-24px)] md:translate-x-0 md:rounded-[24px] ${collapsed ? 'md:w-16' : 'md:w-[248px]'} ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className={`flex h-20 items-center gap-3 px-5 ${collapsed ? 'md:justify-center md:px-2' : ''}`}>
          <span className="grid size-10 shrink-0 place-items-center rounded-[14px] bg-primary text-white shadow-lg shadow-primary/25"><Building2 className="size-5" /></span>
          <div className={`min-w-0 ${collapsed ? 'md:hidden' : ''}`}><p className="truncate text-sm font-bold tracking-tight text-white">God&apos;s Plan</p><p className="truncate text-[11px] text-slate-400">Global payments</p></div>
          <button className="ml-auto rounded-xl p-2 text-slate-400 transition hover:bg-white/10 hover:text-white md:hidden" onClick={onClose} aria-label="Close menu"><X className="size-5" /></button>
        </div>
        <div className={`px-5 pb-3 pt-4 text-[10px] font-bold uppercase tracking-[.18em] text-slate-500 ${collapsed ? 'md:sr-only' : ''}`}>Workspace</div>
        <nav className="flex-1 space-y-1.5 px-3" aria-label="Main navigation">
          {links.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={onClose} title={label}
            className={({ isActive }) => `group relative flex h-12 items-center gap-3 rounded-[14px] px-3 text-sm font-medium transition duration-200 ${collapsed ? 'md:justify-center md:px-0' : ''} ${isActive ? 'bg-white/10 text-white shadow-inner shadow-white/5' : 'text-slate-400 hover:bg-white/[.06] hover:text-white'}`}>
            {({ isActive }) => <><span className={`absolute left-0 h-5 w-1 rounded-r-full bg-primary transition-opacity ${isActive ? 'opacity-100' : 'opacity-0'}`} /><Icon className="size-[19px] shrink-0" /><span className={collapsed ? 'md:hidden' : ''}>{label}</span>{collapsed && <span className="pointer-events-none absolute left-[calc(100%+14px)] z-[70] hidden whitespace-nowrap rounded-lg bg-slate-950 px-3 py-2 text-xs font-semibold text-white opacity-0 shadow-xl transition group-hover:opacity-100 md:block">{label}</span>}</>}
          </NavLink>)}
        </nav>
        <div className="p-3">
          <div className={`flex items-center gap-3 rounded-[16px] border border-white/[.08] bg-white/[.05] p-3 ${collapsed ? 'md:justify-center md:border-0 md:bg-transparent md:px-0' : ''}`}>
            <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-primary to-indigo-400 text-xs font-bold text-white">{initials(currentUser?.fullName)}</span>
            <div className={`min-w-0 flex-1 ${collapsed ? 'md:hidden' : ''}`}><p className="truncate text-xs font-semibold text-white">{currentUser?.fullName || 'Payment workspace'}</p><p className="truncate text-[10px] text-slate-400">{currentUser?.role ? `${currentUser.role} access` : 'Secure public access'}</p></div>
          </div>
        </div>
        <button type="button" onClick={onCollapse} className="absolute -right-3 top-24 hidden size-7 place-items-center rounded-full border border-slate-200 bg-white text-slate-600 shadow-lg transition hover:scale-105 hover:text-primary md:grid" aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
          {collapsed ? <ChevronRight className="size-4" /> : <ChevronLeft className="size-4" />}
        </button>
      </aside>
    </>
  )
}
