import { Bell, ChevronDown, CircleHelp, Menu, Moon, Plus, Search, Sun, UserRound } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

const pageNames = {
  '/': 'Dashboard',
  '/payments': 'Payments',
  '/customers': 'Beneficiaries',
  '/accounts': 'Accounts',
  '/transactions': 'Transactions',
  '/analytics': 'Analytics',
}

const initials = (name) => name?.split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'GP'

export default function Topbar({ onMenu, onCreate, theme, onTheme, currentUser }) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [themeOpen, setThemeOpen] = useState(false)
  const [userOpen, setUserOpen] = useState(false)
  const menus = useRef(null)
  const title = /^\/payments\/\d+/.test(pathname) ? 'Payment details' : pageNames[pathname] || 'Payments'

  useEffect(() => {
    const close = (event) => {
      if (!menus.current?.contains(event.target)) { setThemeOpen(false); setUserOpen(false) }
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [])

  const submitSearch = (event) => {
    event.preventDefault()
    const term = search.trim()
    navigate(term ? `/transactions?search=${encodeURIComponent(term)}` : '/transactions')
  }

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-line/80 bg-surface/85 px-4 backdrop-blur-xl sm:px-5 lg:px-6">
      <button onClick={onMenu} className="icon-button md:hidden" aria-label="Open navigation"><Menu className="size-5" /></button>
      <div className="mr-auto min-w-0 sm:hidden"><p className="truncate text-sm font-semibold text-ink">{title}</p><p className="text-[11px] text-ink-muted">Payment operations</p></div>
      <form className="hidden w-full max-w-xl sm:block" role="search" onSubmit={submitSearch}>
        <label className="relative block"><span className="sr-only">Search payments</span><Search className="pointer-events-none absolute left-3.5 top-3 size-[18px] text-ink-muted" /><input className="input h-11 border-transparent bg-canvas pl-11 pr-16 shadow-inner" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search payments, references, transactions…" /><span className="pointer-events-none absolute right-3 top-2.5 rounded-md border border-line bg-surface px-1.5 py-0.5 text-[10px] font-semibold text-ink-muted">Enter</span></label>
      </form>
      <div ref={menus} className="ml-auto flex items-center gap-2">
        <button className="icon-button hidden lg:grid" aria-label="Help" title="Help"><CircleHelp className="size-[18px]" /></button>
        <button className="icon-button hidden sm:grid" aria-label="Notifications" title="Notifications"><Bell className="size-[18px]" /></button>
        <div className="relative">
          <button className="icon-button" onClick={() => { setThemeOpen((value) => !value); setUserOpen(false) }} aria-label="Change theme" aria-expanded={themeOpen}>{theme === 'dark' ? <Moon className="size-[18px]" /> : <Sun className="size-[18px]" />}</button>
          {themeOpen && <div className="absolute right-0 top-12 w-44 animate-enter rounded-2xl border border-line bg-elevated p-1.5 shadow-soft" role="menu">
            <button className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition ${theme === 'light' ? 'bg-primary-light font-semibold text-primary' : 'text-ink-muted hover:bg-canvas hover:text-ink'}`} onClick={() => { onTheme('light'); setThemeOpen(false) }}><Sun className="size-4" />Light Mode</button>
            <button className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition ${theme === 'dark' ? 'bg-primary-light font-semibold text-primary' : 'text-ink-muted hover:bg-canvas hover:text-ink'}`} onClick={() => { onTheme('dark'); setThemeOpen(false) }}><Moon className="size-4" />Dark Mode</button>
          </div>}
        </div>
        <button className="btn-primary hidden h-10 px-3.5 lg:inline-flex" onClick={onCreate}><Plus className="size-4" />Send payment</button>
        <div className="relative">
          <button className="flex items-center gap-2 rounded-xl p-1.5 transition hover:bg-primary-light" onClick={() => { setUserOpen((value) => !value); setThemeOpen(false) }} aria-label="Open user menu" aria-expanded={userOpen}><span className="grid size-8 place-items-center rounded-[10px] bg-gradient-to-br from-primary to-indigo-400 text-[10px] font-bold text-white">{initials(currentUser?.fullName)}</span><ChevronDown className="hidden size-3.5 text-ink-muted xl:block" /></button>
          {userOpen && <div className="absolute right-0 top-12 w-64 animate-enter rounded-2xl border border-line bg-elevated p-2 shadow-soft"><div className="rounded-xl bg-canvas p-3"><p className="text-sm font-semibold text-ink">{currentUser?.fullName || 'Payment workspace'}</p><p className="mt-0.5 text-xs text-ink-muted">{currentUser?.email || 'Public payment access'}</p></div><div className="mt-1 flex items-center gap-2 px-3 py-2 text-xs text-ink-muted"><UserRound className="size-4" />{currentUser?.role ? `${currentUser.role} session` : 'No staff session active'}</div></div>}
        </div>
      </div>
    </header>
  )
}
