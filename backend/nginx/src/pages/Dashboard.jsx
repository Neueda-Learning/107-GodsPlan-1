import { Activity, CheckCircle2, Clock3, CreditCard, Plus, XCircle } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { useOutletContext } from 'react-router-dom'
import { paymentApi, apiMessage } from '../api/client'
import DashboardCard from '../components/DashboardCard'
import EmptyState from '../components/EmptyState'
import LoadingSkeleton from '../components/LoadingSkeleton'
import PaymentTable from '../components/PaymentTable'

const colors = { COMPLETED: '#67B68A', FAILED: '#D77A7A', SENT: '#6FAEDB', VALIDATED: '#D8B24C', CREATED: '#94A3B8' }

export default function Dashboard() {
  const { refreshToken, openCreate } = useOutletContext()
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    paymentApi.list({ size: 1000, sort: 'createdAt,desc' })
      .then((data) => active && setPayments(data.content))
      .catch((err) => active && setError(apiMessage(err)))
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [refreshToken])

  const stats = useMemo(() => {
    const count = (status) => payments.filter((payment) => payment.status === status).length
    return { total: payments.length, completed: count('COMPLETED'), failed: count('FAILED'),
      progress: payments.filter((payment) => !['COMPLETED', 'FAILED'].includes(payment.status)).length }
  }, [payments])
  const chart = Object.keys(colors).map((status) => ({ name: status[0] + status.slice(1).toLowerCase(), status, value: payments.filter((p) => p.status === status).length })).filter((item) => item.value)

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><p className="text-sm font-medium text-primary-hover">Today&apos;s workspace</p><h2 className="mt-1 text-2xl font-bold tracking-tight text-ink">Payment overview</h2><p className="mt-1 text-sm text-ink-muted">Monitor processing health and recent activity.</p></div>
        <button className="btn-secondary sm:hidden" onClick={openCreate}><Plus className="size-4" />Create payment</button></div>
      {error && <div className="rounded-card border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">{error}</div>}
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="Payment summary">
        <DashboardCard label="Total payments" value={stats.total} icon={CreditCard} helper="All recorded payments" />
        <DashboardCard label="Completed" value={stats.completed} icon={CheckCircle2} tone="green" helper="Successfully processed" />
        <DashboardCard label="Failed" value={stats.failed} icon={XCircle} tone="red" helper="Needs attention" />
        <DashboardCard label="In progress" value={stats.progress} icon={Clock3} tone="amber" helper="Across active stages" />
      </section>
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(300px,0.8fr)]">
        <section className="card min-w-0 overflow-hidden"><div className="flex items-center justify-between border-b border-line px-5 py-4"><div><h3 className="font-bold text-ink">Recent payments</h3><p className="mt-0.5 text-sm text-ink-muted">Your five latest records</p></div><Activity className="size-5 text-primary" /></div>
          {loading ? <LoadingSkeleton rows={5} /> : payments.length ? <PaymentTable payments={payments.slice(0, 5)} /> : <EmptyState />}
        </section>
        <section className="card p-5"><div><h3 className="font-bold text-ink">Status breakdown</h3><p className="mt-0.5 text-sm text-ink-muted">All recorded payments</p></div>
          {loading ? <div className="mt-6 h-64 animate-pulse rounded-full bg-slate-100" /> : chart.length ? <>
            <div className="mt-3 h-56"><ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={chart} dataKey="value" nameKey="name" innerRadius={58} outerRadius={82} paddingAngle={3} stroke="none">{chart.map((item) => <Cell key={item.status} fill={colors[item.status]} />)}</Pie><Tooltip contentStyle={{ borderRadius: 10, borderColor: '#DDE7F1', fontSize: 12 }} /></PieChart></ResponsiveContainer></div>
            <div className="grid grid-cols-2 gap-3">{chart.map((item) => <div key={item.status} className="flex items-center gap-2 text-sm"><span className="size-2.5 rounded-full" style={{ backgroundColor: colors[item.status] }} /><span className="flex-1 text-ink-muted">{item.name}</span><strong className="text-ink">{item.value}</strong></div>)}</div>
          </> : <EmptyState title="No status data" message="Create your first payment to see the breakdown." />}
        </section>
      </div>
    </div>
  )
}
