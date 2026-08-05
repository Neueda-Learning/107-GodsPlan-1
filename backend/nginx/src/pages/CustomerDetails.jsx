import { Loader2, LockKeyhole, LogOut, ShieldCheck, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { apiMessage, authApi, customerApi } from '../api/client'
import CustomerCard from '../components/CustomerCard'
import EmptyState from '../components/EmptyState'
import LoadingSkeleton from '../components/LoadingSkeleton'
import Pagination from '../components/Pagination'

function StaffLogin({ onAuthenticated }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const submit = async (event) => {
    event.preventDefault(); setLoading(true); setError('')
    try { await authApi.login(email, password); onAuthenticated(await authApi.me()) }
    catch (err) { setError(apiMessage(err)) }
    finally { setLoading(false) }
  }
  return <div className="mx-auto grid min-h-[calc(100vh-12rem)] max-w-md place-items-center">
    <section className="card w-full p-6 sm:p-8"><span className="grid size-12 place-items-center rounded-xl bg-primary-light text-primary-hover"><LockKeyhole className="size-6" /></span>
      <h2 className="mt-5 text-2xl font-bold text-ink">Staff sign in</h2><p className="mt-2 text-sm leading-6 text-ink-muted">Customer information is restricted to authorized administrators and staff.</p>
      <form className="mt-6 space-y-4" onSubmit={submit}><label className="block"><span className="label">Staff email</span><input className="input" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label className="block"><span className="label">Password</span><input className="input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
        {error && <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">{error}</p>}
        <button className="btn-primary w-full" disabled={loading}>{loading && <Loader2 className="size-4 animate-spin" />}{loading ? 'Signing in…' : 'Sign in securely'}</button></form>
      <p className="mt-5 flex items-center gap-2 text-xs text-ink-muted"><ShieldCheck className="size-4 text-success" />Session protected with CSRF and HTTP-only cookies.</p>
    </section>
  </div>
}

export default function CustomerDetails() {
  const [user, setUser] = useState(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [customers, setCustomers] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    authApi.me().then((current) => active && setUser(current)).catch(() => {}).finally(() => active && setAuthLoading(false))
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!user) return
    let active = true
    customerApi.list({ page: 0, size: 8 }).then((data) => active && setCustomers(data))
      .catch((err) => active && setError(apiMessage(err))).finally(() => active && setLoading(false))
    return () => { active = false }
  }, [user])

  const changePage = async (page) => {
    setLoading(true); setError('')
    try { setCustomers(await customerApi.list({ page: page - 1, size: 8 })) }
    catch (err) { setError(apiMessage(err)) }
    finally { setLoading(false) }
  }
  const logout = async () => {
    try { await authApi.logout() } finally { setUser(null); setCustomers(null); setLoading(true) }
  }

  if (authLoading) return <div className="mx-auto max-w-5xl"><div className="card"><LoadingSkeleton rows={6} /></div></div>
  if (!user) return <StaffLogin onAuthenticated={setUser} />
  if (!['ADMIN', 'STAFF'].includes(user.role)) return <div className="mx-auto max-w-xl"><div className="card p-8 text-center"><LockKeyhole className="mx-auto size-10 text-danger" /><h2 className="mt-4 text-xl font-bold">Access restricted</h2><p className="mt-2 text-sm text-ink-muted">Administrator or staff access is required.</p></div></div>

  return <div className="mx-auto max-w-7xl space-y-6">
    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><p className="text-sm font-medium text-primary-hover">Customer management</p><h2 className="mt-1 text-2xl font-bold tracking-tight text-ink">Customer details</h2><p className="mt-1 text-sm text-ink-muted">Review customer accounts and securely masked payment activity.</p></div>
      <div className="flex items-center gap-3"><div className="text-right"><p className="text-sm font-semibold text-ink">{user.fullName}</p><p className="text-xs text-ink-muted">{user.role}</p></div><button className="btn-secondary h-10" onClick={logout}><LogOut className="size-4" />Sign out</button></div></div>
    <div className="flex items-center gap-2 rounded-card border border-blue-200 bg-primary-light p-4 text-sm text-blue-800"><ShieldCheck className="size-5 shrink-0" /><span>Card numbers are masked by the server. Full card data and security codes are never stored or returned.</span></div>
    {error && <div className="rounded-card border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert"><p>{error}</p><button className="mt-2 font-semibold underline" onClick={() => changePage((customers?.page || 0) + 1)}>Try again</button></div>}
    {loading ? <div className="grid gap-5 lg:grid-cols-2"><div className="card"><LoadingSkeleton rows={5} /></div><div className="card"><LoadingSkeleton rows={5} /></div></div>
      : customers?.content.length ? <><section className="grid gap-5 lg:grid-cols-2" aria-label="Registered customers">{customers.content.map((customer) => <CustomerCard key={customer.id} customer={customer} />)}</section>
        <div className="card"><Pagination page={customers.page + 1} pages={customers.totalPages} total={customers.totalElements} onChange={changePage} label="customers" /></div></>
        : <div className="card"><EmptyState title="No customers found" message="There are no registered customers available for this staff account." /></div>}
    {!loading && <p className="flex items-center gap-2 text-xs text-ink-muted"><Users className="size-4" />The authenticated staff user is automatically excluded from this list.</p>}
  </div>
}
