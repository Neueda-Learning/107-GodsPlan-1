import { ArrowLeftRight, ArrowRight, Check, CircleDollarSign, Download, Globe2, Plus, RefreshCw, Send, ShieldCheck, XCircle } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Area, AreaChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { analyticsApi, apiMessage, paymentApi } from '../api/client'
import EmptyState from '../components/EmptyState'
import LoadingSkeleton from '../components/LoadingSkeleton'
import PaymentTable from '../components/PaymentTable'
import StatusBadge from '../components/StatusBadge'
import { exportCsv } from '../utils/table'
import { formatDate, formatMoney } from '../utils/format'

const stages = [
  { status: 'CREATED', label: 'Initiated', icon: CircleDollarSign },
  { status: 'VALIDATED', label: 'Validated', icon: ShieldCheck },
  { status: 'SENT', label: 'Network', icon: Send },
  { status: 'COMPLETED', label: 'Completed', icon: Check },
]
const statusNames = ['COMPLETED', 'FAILED', 'SENT', 'VALIDATED', 'CREATED']
const statusColors = { COMPLETED: '#22C55E', PROCESSING: '#F59E0B', FAILED: '#F87171' }

function localIso(date) {
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return shifted.toISOString().slice(0, 10)
}

function analyticsRange(period) {
  const to = new Date(); const from = new Date(to)
  if (period === 'today') return { from: localIso(to), to: localIso(to) }
  from.setDate(from.getDate() - ({ week: 6, month: 29, quarter: 89 }[period] || 29))
  return { from: localIso(from), to: localIso(to) }
}

function PaymentJourney({ payment, history }) {
  if (!payment) return null
  const events = Array.isArray(history) ? history : []
  const current = stages.findIndex((stage) => stage.status === payment.status)
  const failed = payment.status === 'FAILED'
  return <section id="payment-journey" className="card overflow-hidden p-4">
    <div className="flex items-center justify-between gap-3"><div><p className="eyebrow">Latest payment journey</p><p className="mt-1 text-sm font-semibold text-ink">Payment #{payment.id}</p></div><StatusBadge status={payment.status} /></div>
    <div className="mt-4 overflow-x-auto"><ol className="grid min-w-[560px] grid-cols-4">{stages.map((stage, index) => {
      const Icon = stage.icon; const complete = !failed && current >= index
      const event = events.find((item) => item.toStatus === stage.status)
      return <li key={stage.status} className="relative text-center">{index < stages.length - 1 && <span className={`absolute left-1/2 top-3.5 h-px w-full ${complete && index < current ? 'bg-success' : 'bg-line'}`} />}<span className={`relative z-10 mx-auto grid size-7 place-items-center rounded-full ${complete ? 'bg-success text-white' : 'bg-elevated text-ink-muted'}`}><Icon className="size-3.5" /></span><p className="mt-1.5 text-[10px] font-semibold text-ink">{stage.label}</p><time className="block text-[9px] text-ink-muted">{event?.createdAt ? new Date(event.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Pending'}</time></li>
    })}</ol></div>
    {failed && <p className="mt-3 flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700"><XCircle className="size-4" />{payment.errorDescription || 'Processing failed. Open the payment for details.'}</p>}
  </section>
}

export default function Dashboard() {
  const { refreshToken, openCreate } = useOutletContext()
  const navigate = useNavigate()
  const [paymentPage, setPaymentPage] = useState(null)
  const [counts, setCounts] = useState({})
  const [history, setHistory] = useState([])
  const [overview, setOverview] = useState(null)
  const [period, setPeriod] = useState('month')
  const [loading, setLoading] = useState(true)
  const [analyticsLoading, setAnalyticsLoading] = useState(false)
  const [error, setError] = useState('')
  const [updatedAt, setUpdatedAt] = useState(null)

  const loadPayments = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [page, ...statusPages] = await Promise.all([
        paymentApi.list({ size: 12, sort: 'createdAt,desc' }),
        ...statusNames.map((status) => paymentApi.list({ status, size: 1, sort: 'createdAt,desc' })),
      ])
      setPaymentPage(page)
      setCounts(Object.fromEntries(statusNames.map((status, index) => [status, statusPages[index].totalElements])))
      if (page.content?.[0]) {
        const result = await paymentApi.history(page.content[0].id).catch(() => [])
        setHistory(result.history || result.items || (Array.isArray(result) ? result : []))
      } else setHistory([])
      setUpdatedAt(new Date())
    } catch (requestError) { setError(apiMessage(requestError)) }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { const timer = setTimeout(loadPayments, 0); return () => clearTimeout(timer) }, [loadPayments, refreshToken])
  useEffect(() => {
    let active = true
    const timer = setTimeout(() => {
      setAnalyticsLoading(true)
      analyticsApi.overview(analyticsRange(period)).then((data) => active && setOverview(data)).catch(() => active && setOverview(null)).finally(() => active && setAnalyticsLoading(false))
    }, 0)
    return () => { active = false; clearTimeout(timer) }
  }, [period, refreshToken])

  const payments = paymentPage?.content || []
  const processing = Number(counts.CREATED || 0) + Number(counts.VALIDATED || 0) + Number(counts.SENT || 0)
  const attempts = Number(counts.COMPLETED || 0) + Number(counts.FAILED || 0)
  const successRate = attempts ? Number(counts.COMPLETED || 0) / attempts * 100 : null
  const breakdown = [
    { name: 'Completed', status: 'COMPLETED', value: Number(counts.COMPLETED || 0) },
    { name: 'Processing', status: 'PROCESSING', value: processing },
    { name: 'Failed', status: 'FAILED', value: Number(counts.FAILED || 0) },
  ].filter((item) => item.value)
  const breakdownTotal = breakdown.reduce((sum, item) => sum + item.value, 0)
  const completedAngle = breakdownTotal ? (Number(counts.COMPLETED || 0) / breakdownTotal) * 360 : 0
  const processingAngle = breakdownTotal ? completedAngle + (processing / breakdownTotal) * 360 : completedAngle
  const donutBackground = `conic-gradient(${statusColors.COMPLETED} 0deg ${completedAngle}deg, ${statusColors.PROCESSING} ${completedAngle}deg ${processingAngle}deg, ${statusColors.FAILED} ${processingAngle}deg 360deg)`
  const kpis = useMemo(() => new Map((overview?.kpis || []).map((item) => [item.key, item])), [overview])
  const volume = kpis.get('totalPaymentVolume')
  const average = kpis.get('averageTransactionValue')
  const trendData = overview?.transactionsOverTime || []
  const international = payments.filter((payment) => payment.destinationCurrency && payment.destinationCurrency !== payment.currency).slice(0, 4)
  const paymentColumns = [
    { key: 'id', label: 'Payment ID' }, { key: 'amount', label: 'Amount' }, { key: 'currency', label: 'Currency' },
    { key: 'status', label: 'Status' }, { key: 'createdAt', label: 'Created at' },
  ]
  const ribbon = [
    ['Total payments', Number(paymentPage?.totalElements || 0).toLocaleString(), 'All records'],
    ['Completed', Number(counts.COMPLETED || 0).toLocaleString(), 'Settled'],
    ['Processing', processing.toLocaleString(), 'Active stages'],
    ['Failed', Number(counts.FAILED || 0).toLocaleString(), 'Needs attention'],
    ...(successRate === null ? [] : [['Success rate', `${successRate.toFixed(1)}%`, successRate >= 95 ? 'Excellent' : successRate >= 80 ? 'Monitor' : 'At risk']]),
    ...(volume ? [['Volume', formatMoney(volume.value, overview.baseCurrency), overview.baseCurrency]] : []),
    ...(average ? [['Average value', formatMoney(average.value, overview.baseCurrency), overview.baseCurrency]] : []),
  ]

  return <div className="mx-auto max-w-[1600px] space-y-4 animate-enter">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-xl font-semibold tracking-tight text-ink">Payment operations</h2><p className="mt-0.5 text-xs text-ink-muted">Live processing overview{updatedAt ? ` · Updated ${updatedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}` : ''}</p></div><div className="flex items-center gap-2"><button className="btn-secondary h-9 px-3" onClick={loadPayments} disabled={loading}><RefreshCw className={`size-3.5 ${loading ? 'animate-spin' : ''}`} />Refresh</button>{payments.length > 0 && <button className="btn-secondary h-9 px-3" onClick={() => exportCsv(payments, paymentColumns, 'dashboard-payments')}><Download className="size-3.5" />Export</button>}<button className="btn-primary h-9 px-3" onClick={openCreate}><Plus className="size-3.5" />Send payment</button></div></div>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs text-red-700" role="alert">{error}</div>}

    {loading && !paymentPage ? <div className="card"><LoadingSkeleton rows={3} /></div> : paymentPage && <>
      <section className="card flex divide-x divide-line overflow-x-auto" aria-label="Payment summary">{ribbon.map(([label, value, helper]) => <div key={label} className="min-w-32 flex-1 px-4 py-3"><p className="truncate text-[10px] font-bold uppercase tracking-wider text-ink-muted">{label}</p><p className="mt-1 truncate text-xl font-semibold tabular-nums text-ink" title={value}>{value}</p><p className="mt-0.5 truncate text-[10px] text-ink-muted">{helper}</p></div>)}</section>

      <div className="grid auto-rows-min gap-4 xl:grid-cols-12">
        <section className="card min-w-0 overflow-hidden xl:col-span-8"><div className="flex items-center justify-between border-b border-line px-4 py-3"><div><p className="text-sm font-semibold text-ink">Recent transactions</p><p className="text-[10px] text-ink-muted">Latest recorded payment activity</p></div><button className="text-xs font-semibold text-primary" onClick={() => navigate('/transactions')}>View all</button></div>{payments.length ? <PaymentTable payments={payments.slice(0, 5)} compact tableId="dashboard-payments" /> : <EmptyState title="No recent payments" message="New payment activity will appear here." />}</section>
        {(breakdown.length > 0 || international.length > 0) && <div className="space-y-4 xl:col-span-4">
          {breakdown.length > 0 && <section className="card p-4"><div className="flex items-start justify-between"><div><p className="text-sm font-semibold text-ink">Processing health</p><p className="text-[10px] text-ink-muted">Outcome distribution</p></div>{successRate !== null && <span className={`rounded-lg px-2 py-1 text-xs font-bold ${successRate >= 80 ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>{successRate.toFixed(1)}%</span>}</div><div className="mt-2 grid grid-cols-[140px_1fr] items-center"><div className="grid size-28 place-items-center justify-self-center rounded-full p-3 animate-enter" style={{ background: donutBackground }} role="img" aria-label={`Completed ${counts.COMPLETED || 0}, processing ${processing}, failed ${counts.FAILED || 0}`}><div className="grid size-full place-items-center rounded-full bg-surface text-center"><span><strong className="block text-lg tabular-nums text-ink">{breakdownTotal}</strong><small className="block text-[9px] text-ink-muted">payments</small></span></div></div><div className="space-y-2">{breakdown.map((item) => <div key={item.status} className="flex items-center gap-2 text-xs"><span className="size-2 rounded-full" style={{ background: statusColors[item.status] }} /><span className="flex-1 text-ink-muted">{item.name}</span><strong className="tabular-nums text-ink">{item.value}</strong></div>)}</div></div></section>}
          {international.length > 0 && <section className="card p-4"><div className="flex items-center justify-between"><div><p className="text-sm font-semibold text-ink">International payments</p><p className="text-[10px] text-ink-muted">Latest currency corridors</p></div><Globe2 className="size-4 text-primary" /></div><div className="mt-2 divide-y divide-line">{international.map((payment) => <button key={payment.id} onClick={() => navigate(`/payments/${payment.id}`)} className="flex w-full items-center gap-3 py-2 text-left"><span className="grid size-7 place-items-center rounded-lg bg-primary-light text-primary"><ArrowLeftRight className="size-3.5" /></span><span className="min-w-0 flex-1"><strong className="block text-xs text-ink">{payment.currency} <ArrowRight className="inline size-3" /> {payment.destinationCurrency}</strong><small className="block truncate text-[9px] text-ink-muted">{formatMoney(payment.amount, payment.currency)} · {formatDate(payment.createdAt)}</small></span><StatusBadge status={payment.status} /></button>)}</div></section>}
        </div>}

        <div className="xl:col-span-12"><PaymentJourney payment={payments[0]} history={history} /></div>

        {trendData.length > 0 && <section className="card min-w-0 p-4 xl:col-span-12"><div className="flex items-center justify-between gap-3"><div><p className="text-sm font-semibold text-ink">Transaction trend</p><p className="text-[10px] text-ink-muted">Backend-aggregated activity</p></div><div className="flex rounded-lg bg-canvas p-0.5">{['today', 'week', 'month', 'quarter'].map((item) => <button key={item} onClick={() => setPeriod(item)} className={`rounded-md px-2 py-1 text-[10px] font-semibold capitalize ${period === item ? 'bg-surface text-primary shadow-sm' : 'text-ink-muted'}`}>{item}</button>)}</div></div>{analyticsLoading ? <div className="mt-3 h-44 skeleton-shimmer rounded-xl" /> : <div className="mt-3 h-44"><ResponsiveContainer><AreaChart data={trendData}><defs><linearGradient id="denseTrend" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#3B5BFF" stopOpacity=".3" /><stop offset="1" stopColor="#3B5BFF" stopOpacity="0" /></linearGradient></defs><XAxis dataKey="period" tick={{ fontSize: 9, fill: '#94A3B8' }} axisLine={false} tickLine={false} minTickGap={26} /><YAxis tick={{ fontSize: 9, fill: '#94A3B8' }} axisLine={false} tickLine={false} allowDecimals={false} /><Tooltip contentStyle={{ borderRadius: 10, border: '1px solid rgb(var(--line))', background: 'rgb(var(--elevated))', fontSize: 11 }} /><Area type="monotone" dataKey="transactions" stroke="#3B5BFF" fill="url(#denseTrend)" strokeWidth={2} /></AreaChart></ResponsiveContainer></div>}</section>}
      </div>
    </>}
  </div>
}
