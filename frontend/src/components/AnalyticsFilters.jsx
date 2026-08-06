import { CalendarRange, Filter, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { defaultAnalyticsFilters } from '../utils/analytics'

const iso = (date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}
const quickRanges = [
  { label: 'Today', days: 1 }, { label: 'Last 7 days', days: 7 }, { label: 'Last 30 days', days: 30 },
  { label: 'Last 90 days', days: 90 }, { label: 'This year', year: true },
]

export default function AnalyticsFilters({ value, options, onApply, busy }) {
  const [draft, setDraft] = useState(value)
  const change = (field, next) => setDraft((current) => ({ ...current, [field]: next }))
  const quick = (range) => {
    const to = new Date(); const from = new Date(to)
    if (range.year) from.setMonth(0, 1)
    else from.setDate(from.getDate() - range.days + 1)
    const next = { ...draft, from: iso(from), to: iso(to) }
    setDraft(next); onApply(next)
  }
  const reset = () => { const next = defaultAnalyticsFilters(); setDraft(next); onApply(next) }
  return <section className="card p-4 sm:p-5" aria-label="Analytics filters">
    <div className="flex flex-col justify-between gap-3 lg:flex-row lg:items-center"><div className="flex items-center gap-2"><span className="grid size-9 place-items-center rounded-lg bg-primary-light text-primary-hover"><Filter className="size-4" /></span><div><h3 className="text-sm font-bold text-ink">Global filters</h3><p className="text-xs text-ink-muted">All metrics are recalculated by the backend.</p></div></div>
      <div className="flex flex-wrap gap-2">{quickRanges.map((range) => <button key={range.label} type="button" className="rounded-lg border border-line bg-white px-3 py-2 text-xs font-semibold text-ink-muted transition hover:border-primary hover:text-primary-hover" onClick={() => quick(range)}>{range.label}</button>)}</div></div>
    <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-6">
      <label><span className="label text-xs">From</span><input className="input" type="date" value={draft.from} max={draft.to} onChange={(e) => change('from', e.target.value)} /></label>
      <label><span className="label text-xs">To</span><input className="input" type="date" value={draft.to} min={draft.from} onChange={(e) => change('to', e.target.value)} /></label>
      <label><span className="label text-xs">Status</span><select className="input" value={draft.status} onChange={(e) => change('status', e.target.value)}><option value="">All statuses</option>{options.statuses?.map((item) => <option key={item} value={item}>{item[0] + item.slice(1).toLowerCase()}</option>)}</select></label>
      <label><span className="label text-xs">Currency</span><select className="input" value={draft.currency} onChange={(e) => change('currency', e.target.value)}><option value="">All currencies</option>{options.currencies?.map((item) => <option key={item}>{item}</option>)}</select></label>
      <label><span className="label text-xs">Payment method</span><select className="input" value={draft.paymentMethod} onChange={(e) => change('paymentMethod', e.target.value)}><option value="">All methods</option>{options.paymentMethods?.map((item) => <option key={item}>{item}</option>)}</select></label>
      <label><span className="label text-xs">Audit scope</span><select className="input" value={draft.auditScope} onChange={(e) => change('auditScope', e.target.value)}>{(options.auditScopes?.length ? options.auditScopes : ['ALL', 'PAYMENTS_ONLY', 'INSUFFICIENT_ONLY']).map((scope) => <option key={scope} value={scope}>{scope === 'ALL' ? 'All records' : scope === 'PAYMENTS_ONLY' ? 'Payments only' : 'Insufficient balance only'}</option>)}</select></label>
      <label><span className="label text-xs">Customer</span><select className="input" value={draft.customerId} onChange={(e) => change('customerId', e.target.value)}><option value="">All customers</option>{options.customers?.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      <label><span className="label text-xs">Minimum amount</span><input className="input" type="number" min="0" step="0.01" value={draft.minimumAmount} onChange={(e) => change('minimumAmount', e.target.value)} placeholder="No minimum" /></label>
      <label><span className="label text-xs">Maximum amount</span><input className="input" type="number" min="0" step="0.01" value={draft.maximumAmount} onChange={(e) => change('maximumAmount', e.target.value)} placeholder="No maximum" /></label>
      <label><span className="label text-xs">Base currency</span><select className="input" value={draft.baseCurrency} onChange={(e) => change('baseCurrency', e.target.value)}><option value="">Configured default</option>{options.currencies?.map((item) => <option key={item}>{item}</option>)}</select></label>
      <label><span className="label text-xs">Time grouping</span><select className="input" value={draft.grouping} onChange={(e) => change('grouping', e.target.value)}><option value="AUTO">Automatic</option><option value="DAILY">Daily</option><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option><option value="YEARLY">Yearly</option></select></label>
    </div>
    <div className="mt-4 flex flex-wrap justify-end gap-2"><button type="button" className="btn-secondary" onClick={reset}><RotateCcw className="size-4" />Reset</button><button type="button" className="btn-primary" disabled={busy} onClick={() => onApply(draft)}><CalendarRange className="size-4" />{busy ? 'Applying…' : 'Apply filters'}</button></div>
  </section>
}
