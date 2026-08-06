import { useEffect, useMemo, useState } from 'react'
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ComposedChart, Legend, Line, LineChart,
  Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import EmptyState from './EmptyState'
import { formatMoney } from '../utils/format'

const tooltipStyle = { borderRadius: 12, borderColor: '#DDE7F1', fontSize: 12, boxShadow: '0 8px 24px rgba(15,23,42,.08)' }
const statusColors = { SUCCESSFUL: '#27AE60', FAILED: '#D65A65', PENDING: '#E2A93B', REFUNDED: '#8B5CF6' }
const palette = ['#2F80ED', '#27AE60', '#8B5CF6', '#E2A93B', '#D65A65', '#0EA5A4', '#64748B']

function useReducedMotion() {
  const [reduced, setReduced] = useState(false)
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const update = () => setReduced(media.matches)
    update(); media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])
  return reduced
}

function ChartCard({ title, subtitle, action, children, className = '' }) {
  return <section className={`card min-w-0 p-5 ${className}`}><div className="mb-5 flex flex-col justify-between gap-3 sm:flex-row sm:items-start"><div><h3 className="font-bold text-ink">{title}</h3><p className="mt-1 text-xs text-ink-muted">{subtitle}</p></div>{action}</div>{children}</section>
}

function axisProps() { return { tick: { fontSize: 11, fill: '#64748B' }, axisLine: false, tickLine: false } }

export function StatusDistribution({ data }) {
  const reduced = useReducedMotion(); const visible = data.filter((item) => item.count > 0)
  return <ChartCard title="Payment status distribution" subtitle="Current outcome of filtered transactions">
    {!visible.length ? <EmptyState title="No status data" message="No transactions match the selected filters." /> : <div className="grid items-center gap-4 sm:grid-cols-[1fr_180px]"><div className="h-64"><ResponsiveContainer><PieChart><Pie data={visible} dataKey="count" nameKey="status" innerRadius={62} outerRadius={94} paddingAngle={3} stroke="none" isAnimationActive={!reduced}>{visible.map((item) => <Cell key={item.status} fill={statusColors[item.status]} />)}</Pie><Tooltip contentStyle={tooltipStyle} formatter={(value, name, item) => [`${value} (${Number(item.payload.percentage).toFixed(1)}%)`, name]} /></PieChart></ResponsiveContainer></div><div className="space-y-3">{data.map((item) => <div key={item.status} className="flex items-center gap-2 text-sm"><span className="size-2.5 rounded-full" style={{ background: statusColors[item.status] }} /><span className="flex-1 text-ink-muted">{item.status[0] + item.status.slice(1).toLowerCase()}</span><strong>{item.count}</strong></div>)}</div></div>}
  </ChartCard>
}

export function RatesChart({ data }) {
  const reduced = useReducedMotion()
  return <ChartCard title="Success and failure rates" subtitle="Completed attempts grouped by the selected interval">
    <div className="h-72"><ResponsiveContainer><AreaChart data={data}><defs><linearGradient id="successGradient" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#27AE60" stopOpacity=".35"/><stop offset="100%" stopColor="#27AE60" stopOpacity=".02"/></linearGradient><linearGradient id="failureGradient" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#D65A65" stopOpacity=".25"/><stop offset="100%" stopColor="#D65A65" stopOpacity=".02"/></linearGradient></defs><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E8EEF5"/><XAxis dataKey="period" {...axisProps()} minTickGap={28}/><YAxis {...axisProps()} unit="%" domain={[0, 100]}/><Tooltip contentStyle={tooltipStyle} formatter={(value) => `${Number(value).toFixed(1)}%`}/><Legend/><Area type="monotone" dataKey="successRate" name="Success rate" stroke="#27AE60" fill="url(#successGradient)" strokeWidth={2.5} isAnimationActive={!reduced}/><Area type="monotone" dataKey="failureRate" name="Failure rate" stroke="#D65A65" fill="url(#failureGradient)" strokeWidth={2.5} isAnimationActive={!reduced}/></AreaChart></ResponsiveContainer></div>
  </ChartCard>
}

export function TransactionsChart({ data }) {
  const reduced = useReducedMotion()
  return <ChartCard title="Transactions over time" subtitle="Zero-activity intervals are included by the backend">
    <div className="h-72"><ResponsiveContainer><AreaChart data={data}><defs><linearGradient id="transactionsGradient" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#2F80ED" stopOpacity=".35"/><stop offset="100%" stopColor="#2F80ED" stopOpacity=".02"/></linearGradient></defs><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E8EEF5"/><XAxis dataKey="period" {...axisProps()} minTickGap={28}/><YAxis {...axisProps()} allowDecimals={false}/><Tooltip contentStyle={tooltipStyle}/><Area type="monotone" dataKey="transactions" name="Transactions" stroke="#2F80ED" fill="url(#transactionsGradient)" strokeWidth={2.5} isAnimationActive={!reduced}/></AreaChart></ResponsiveContainer></div>
  </ChartCard>
}

export function VolumeChart({ data, baseCurrency }) {
  const reduced = useReducedMotion()
  return <ChartCard title="Payment volume over time" subtitle={`All values converted server-side to ${baseCurrency} using stored rates`} className="xl:col-span-2">
    <div className="h-80"><ResponsiveContainer><ComposedChart data={data}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E8EEF5"/><XAxis dataKey="period" {...axisProps()} minTickGap={28}/><YAxis {...axisProps()}/><Tooltip contentStyle={tooltipStyle} formatter={(value) => formatMoney(value, baseCurrency)}/><Legend/><Bar dataKey="grossVolume" name="Gross volume" fill="#A9CCF7" radius={[4,4,0,0]} isAnimationActive={!reduced}/><Bar dataKey="successfulVolume" name="Successful" fill="#27AE60" radius={[4,4,0,0]} isAnimationActive={!reduced}/><Bar dataKey="failedAmount" name="Failed" fill="#D65A65" radius={[4,4,0,0]} isAnimationActive={!reduced}/><Bar dataKey="refundedAmount" name="Refunded" fill="#8B5CF6" radius={[4,4,0,0]} isAnimationActive={!reduced}/><Line type="monotone" dataKey="averageTransactionValue" name="Average" stroke="#1D4ED8" strokeWidth={2.5} dot={false} isAnimationActive={!reduced}/></ComposedChart></ResponsiveContainer></div>
  </ChartCard>
}

export function PaymentMethodsChart({ data, baseCurrency }) {
  const reduced = useReducedMotion(); const [metric, setMetric] = useState('transactionCount')
  const meta = { transactionCount: ['Transactions', 'count'], totalAmount: ['Amount', baseCurrency], successRate: ['Success rate', '%'] }
  const action = <select className="input h-9 w-40 text-xs" value={metric} onChange={(e) => setMetric(e.target.value)}><option value="transactionCount">Transactions</option><option value="totalAmount">Total amount</option><option value="successRate">Success rate</option></select>
  return <ChartCard title="Payment methods" subtitle="Activity grouped by methods stored on payment records" action={action}>
    {!data.length ? <EmptyState title="No payment methods" message="No matching payment-method data is available." /> : <div className="h-72"><ResponsiveContainer><BarChart data={data}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E8EEF5"/><XAxis dataKey="paymentMethod" {...axisProps()} interval={0} angle={-18} textAnchor="end" height={64}/><YAxis {...axisProps()}/><Tooltip contentStyle={tooltipStyle} formatter={(value) => metric === 'totalAmount' ? formatMoney(value, baseCurrency) : metric === 'successRate' ? `${Number(value).toFixed(1)}%` : value}/><Bar dataKey={metric} name={meta[metric][0]} radius={[6,6,0,0]} isAnimationActive={!reduced}>{data.map((item, index) => <Cell key={item.paymentMethod} fill={palette[index % palette.length]} />)}</Bar></BarChart></ResponsiveContainer></div>}
  </ChartCard>
}

export function CurrencyChart({ data }) {
  const reduced = useReducedMotion()
  return <ChartCard title="Currency distribution" subtitle="Native currency totals; currencies are never added together">
    {!data.length ? <EmptyState title="No currency data" message="No matching currencies are available." /> : <div className="h-72"><ResponsiveContainer><BarChart data={data} layout="vertical" margin={{ left: 10 }}><CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#E8EEF5"/><XAxis type="number" {...axisProps()}/><YAxis type="category" dataKey="currency" width={44} {...axisProps()}/><Tooltip contentStyle={tooltipStyle}/><Legend/><Bar dataKey="transactionCount" name="Transactions" fill="#2F80ED" radius={[0,5,5,0]} isAnimationActive={!reduced}/><Bar dataKey="paymentVolume" name="Native volume" fill="#8B5CF6" radius={[0,5,5,0]} isAnimationActive={!reduced}/><Bar dataKey="successfulVolume" name="Successful native volume" fill="#27AE60" radius={[0,5,5,0]} isAnimationActive={!reduced}/></BarChart></ResponsiveContainer></div>}
  </ChartCard>
}

export function CustomerGrowthChart({ data }) {
  const reduced = useReducedMotion()
  return <ChartCard title="Customer growth" subtitle="Creation dates and filtered transaction activity" className="xl:col-span-2">
    <div className="h-72"><ResponsiveContainer><LineChart data={data}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E8EEF5"/><XAxis dataKey="period" {...axisProps()} minTickGap={28}/><YAxis {...axisProps()} allowDecimals={false}/><Tooltip contentStyle={tooltipStyle}/><Legend/><Line type="monotone" dataKey="newCustomers" name="New" stroke="#2F80ED" strokeWidth={2} dot={false} isAnimationActive={!reduced}/><Line type="monotone" dataKey="cumulativeCustomers" name="Cumulative" stroke="#8B5CF6" strokeWidth={2} dot={false} isAnimationActive={!reduced}/><Line type="monotone" dataKey="activeCustomers" name="Active" stroke="#27AE60" strokeWidth={2} dot={false} isAnimationActive={!reduced}/><Line type="monotone" dataKey="returningCustomers" name="Returning" stroke="#E2A93B" strokeWidth={2} dot={false} isAnimationActive={!reduced}/></LineChart></ResponsiveContainer></div>
  </ChartCard>
}

export function TopCustomers({ data, baseCurrency }) {
  return <ChartCard title="Top customers" subtitle={`Ranked by successful volume in ${baseCurrency}`} className="xl:col-span-2">
    {!data.length ? <EmptyState title="No customer activity" message="No customer transactions match the current filters." /> : <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead><tr className="border-b border-line text-xs uppercase tracking-wide text-ink-muted"><th className="pb-3">Rank</th><th className="pb-3">Customer</th><th className="pb-3">Card</th><th className="pb-3 text-right">Transactions</th><th className="pb-3 text-right">Successful volume</th><th className="pb-3 text-right">Average</th><th className="pb-3 text-right">Success rate</th></tr></thead><tbody>{data.map((item, index) => <tr key={item.customerId} className="border-b border-line/70 last:border-0"><td className="py-3 font-bold text-primary-hover">#{index + 1}</td><td className="py-3 font-semibold text-ink">{item.customerName}</td><td className="py-3 font-mono text-xs text-ink-muted">{item.cardNumber || 'No card'}</td><td className="py-3 text-right">{item.transactionCount}</td><td className="py-3 text-right font-semibold">{formatMoney(item.successfulVolume, baseCurrency)}</td><td className="py-3 text-right">{formatMoney(item.averageTransactionValue, baseCurrency)}</td><td className="py-3 text-right">{Number(item.successRate).toFixed(1)}%</td></tr>)}</tbody></table></div>}
  </ChartCard>
}

export function ActivityHeatmap({ data }) {
  const max = Math.max(...data.map((item) => item.transactions), 1)
  const byCell = useMemo(() => new Map(data.map((item) => [`${item.dayOfWeek}:${item.hour}`, item])), [data])
  const days = [...new Map(data.map((item) => [item.dayOfWeek, item.day])).entries()].sort((a, b) => a[0] - b[0])
  return <ChartCard title="Transaction activity heatmap" subtitle="Activity by local day and hour">
    <div className="overflow-x-auto"><div className="min-w-[620px]"><div className="ml-14 grid grid-cols-[repeat(24,minmax(0,1fr))] gap-1 text-[9px] text-ink-muted">{Array.from({ length: 24 }, (_, hour) => <span key={hour} className="text-center">{hour}</span>)}</div>{days.map(([dayNumber, day]) => <div key={day} className="mt-1 grid grid-cols-[52px_repeat(24,minmax(0,1fr))] gap-1"><span className="truncate text-[10px] text-ink-muted">{day.slice(0, 3)}</span>{Array.from({ length: 24 }, (_, hour) => { const item = byCell.get(`${dayNumber}:${hour}`); const count = item?.transactions || 0; return <div key={hour} className="aspect-square rounded-sm border border-blue-100" title={`${day} ${hour}:00 — ${count} transactions`} style={{ backgroundColor: `rgba(47,128,237,${count ? .15 + .85 * count / max : .03})` }} /> })}</div>)}</div></div>
  </ChartCard>
}

export function FailureReasonsChart({ data }) {
  const reduced = useReducedMotion()
  if (!data.length) return null
  return <ChartCard title="Failure reason analysis" subtitle="Most frequent stored failure codes and descriptions">
    <div className="h-72"><ResponsiveContainer><BarChart data={data} layout="vertical" margin={{ left: 30 }}><CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#E8EEF5"/><XAxis type="number" allowDecimals={false} {...axisProps()}/><YAxis type="category" dataKey="reason" width={145} {...axisProps()} tickFormatter={(value) => value.length > 22 ? `${value.slice(0, 22)}…` : value}/><Tooltip contentStyle={tooltipStyle}/><Bar dataKey="count" name="Failures" fill="#D65A65" radius={[0,6,6,0]} isAnimationActive={!reduced}/></BarChart></ResponsiveContainer></div>
  </ChartCard>
}

export function ExchangeRateChart({ data, currencies, source, target, onPairChange, loading, error }) {
  const reduced = useReducedMotion()
  const action = <div className="flex gap-2"><select className="input h-9 w-24 text-xs" value={source} onChange={(e) => onPairChange(e.target.value, target)}>{currencies.map((item) => <option key={item}>{item}</option>)}</select><select className="input h-9 w-24 text-xs" value={target} onChange={(e) => onPairChange(source, e.target.value)}>{currencies.filter((item) => item !== source).map((item) => <option key={item}>{item}</option>)}</select></div>
  return <ChartCard title="Exchange-rate history" subtitle="Stored backend snapshots only" action={action} className="xl:col-span-2">
    {error ? <p className="rounded-lg bg-red-50 p-4 text-sm text-red-700">{error}</p> : loading ? <div className="h-72 animate-pulse rounded-xl bg-slate-100" /> : !data?.history?.length ? <EmptyState title="No stored exchange-rate history" message="Run the development analytics seed command or process converted payments to store snapshots." /> : <><div className="mb-4 grid gap-3 sm:grid-cols-4">{[['Current', data.currentRate], ['Highest', data.highestRate], ['Lowest', data.lowestRate], ['Change', data.percentageChange === null ? null : `${Number(data.percentageChange).toFixed(2)}%`]].map(([label, value]) => <div key={label} className="rounded-xl border border-line bg-canvas p-3"><p className="text-xs text-ink-muted">{label}</p><p className="mt-1 font-bold text-ink">{value ?? '—'}</p></div>)}</div><div className="h-72"><ResponsiveContainer><LineChart data={data.history}><CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E8EEF5"/><XAxis dataKey="fetchedAt" {...axisProps()} minTickGap={30} tickFormatter={(value) => new Date(value).toLocaleDateString()}/><YAxis {...axisProps()} domain={['auto','auto']}/><Tooltip contentStyle={tooltipStyle} labelFormatter={(value) => new Date(value).toLocaleString()}/><Line type="monotone" dataKey="rate" name={`${source}/${target}`} stroke="#8B5CF6" strokeWidth={2.5} dot={false} isAnimationActive={!reduced}/></LineChart></ResponsiveContainer></div></>}
  </ChartCard>
}
