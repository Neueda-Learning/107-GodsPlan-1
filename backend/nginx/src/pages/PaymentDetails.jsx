import { ArrowLeft, ArrowRight, CalendarClock, Landmark, ReceiptText, RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { paymentApi, apiMessage } from '../api/client'
import ErrorBanner from '../components/ErrorBanner'
import LoadingSkeleton from '../components/LoadingSkeleton'
import PaymentTimeline from '../components/PaymentTimeline'
import StatusBadge from '../components/StatusBadge'
import { formatDate, formatMoney } from '../utils/format'

function Detail({ label, children }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{label}</dt><dd className="mt-1.5 text-sm font-semibold text-ink">{children ?? '—'}</dd></div>
}

export default function PaymentDetails() {
  const { id } = useParams()
  const [payment, setPayment] = useState(null)
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([paymentApi.get(id), paymentApi.history(id)]).then(([item, audit]) => {
      if (active) { setPayment(item); setHistory(audit.history) }
    }).catch((err) => active && setError(apiMessage(err))).finally(() => active && setLoading(false))
    return () => { active = false }
  }, [id, reload])

  if (loading) return <div className="mx-auto max-w-5xl"><div className="card"><LoadingSkeleton rows={8} /></div></div>
  if (error) return <div className="mx-auto max-w-3xl space-y-4"><Link className="inline-flex items-center gap-2 text-sm font-semibold text-primary-hover" to="/payments"><ArrowLeft className="size-4" />Back to payments</Link><ErrorBanner title="Payment details unavailable" description={error} /><button className="btn-secondary" onClick={() => { setLoading(true); setError(''); setReload((v) => v + 1) }}><RefreshCw className="size-4" />Try again</button></div>

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <Link className="inline-flex items-center gap-2 text-sm font-semibold text-primary-hover hover:text-primary" to="/payments"><ArrowLeft className="size-4" />Back to payments</Link>
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start"><div><p className="text-sm font-medium text-primary-hover">Payment record</p><h2 className="mt-1 text-2xl font-bold tracking-tight text-ink">Payment #{payment.id}</h2><p className="mt-1 text-sm text-ink-muted">{payment.reference || 'No reference provided'}</p></div><StatusBadge status={payment.status} /></div>
      {payment.status === 'FAILED' && <ErrorBanner code={payment.errorCode} description={payment.errorDescription} />}
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.5fr)_minmax(300px,0.75fr)]">
        <div className="space-y-6">
          <section className="card p-5 sm:p-6"><div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-xl bg-primary-light text-primary-hover"><ReceiptText className="size-5" /></span><div><h3 className="font-bold text-ink">Payment details</h3><p className="text-sm text-ink-muted">Core transaction information</p></div></div>
            <dl className="mt-6 grid gap-x-6 gap-y-5 sm:grid-cols-2 xl:grid-cols-3"><Detail label="Original amount">{formatMoney(payment.amount, payment.currency)}</Detail><Detail label="Currency">{payment.currency}</Detail><Detail label="Reference">{payment.reference || '—'}</Detail><Detail label="Created">{formatDate(payment.createdAt)}</Detail><Detail label="Last updated">{formatDate(payment.updatedAt)}</Detail><Detail label="Payment ID">#{payment.id}</Detail></dl>
          </section>
          <section className="card p-5 sm:p-6"><div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-xl bg-primary-light text-primary-hover"><Landmark className="size-5" /></span><div><h3 className="font-bold text-ink">Transfer route</h3><p className="text-sm text-ink-muted">Source and destination accounts</p></div></div>
            <div className="mt-6 grid items-center gap-3 sm:grid-cols-[1fr_auto_1fr]"><div className="rounded-xl border border-line bg-canvas p-4"><p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Source account</p><p className="mt-2 text-lg font-bold text-ink">Account #{payment.sourceAccountId}</p><p className="mt-1 text-sm text-ink-muted">Sending in {payment.currency}</p></div><ArrowRight className="mx-auto size-5 rotate-90 text-primary sm:rotate-0" /><div className="rounded-xl border border-line bg-canvas p-4"><p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Destination account</p><p className="mt-2 text-lg font-bold text-ink">Account #{payment.destinationAccountId}</p><p className="mt-1 text-sm text-ink-muted">Receiving account</p></div></div>
          </section>
          {payment.destinationAmount !== null && <section className="card border-primary/30 bg-primary-light/40 p-5 sm:p-6"><h3 className="font-bold text-ink">Currency conversion</h3><dl className="mt-5 grid gap-5 sm:grid-cols-2"><Detail label="Converted amount">{formatMoney(payment.destinationAmount, payment.destinationCurrency)}</Detail><Detail label="Exchange rate">1 {payment.currency} = {payment.exchangeRate} {payment.destinationCurrency}</Detail><Detail label="Rate source">{payment.exchangeRateSource}</Detail><Detail label="Rate fetched">{formatDate(payment.exchangeRateFetchedAt)}</Detail></dl></section>}
        </div>
        <aside className="card h-fit p-5 sm:p-6"><div className="flex items-center gap-3"><span className="grid size-10 place-items-center rounded-xl bg-primary-light text-primary-hover"><CalendarClock className="size-5" /></span><div><h3 className="font-bold text-ink">Status history</h3><p className="text-sm text-ink-muted">Complete audit trail</p></div></div><div className="mt-6">{history.length ? <PaymentTimeline history={history} /> : <p className="text-sm text-ink-muted">No history is available.</p>}</div></aside>
      </div>
    </div>
  )
}
