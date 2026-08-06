import EmptyState from './EmptyState'
import LoadingSkeleton from './LoadingSkeleton'
import Pagination from './Pagination'
import { formatDate, formatMoney } from '../utils/format'

const statusStyles = {
  SUCCESSFUL: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  FAILED: 'bg-red-50 text-red-700 ring-red-200',
  PENDING: 'bg-amber-50 text-amber-700 ring-amber-200',
  REFUNDED: 'bg-violet-50 text-violet-700 ring-violet-200',
}

export default function AnalyticsRecentTransactions({ data, loading, error, onPage }) {
  return <section className="card overflow-hidden xl:col-span-2"><div className="border-b border-line p-5"><h3 className="font-bold text-ink">Recent transactions</h3><p className="mt-1 text-xs text-ink-muted">Paginated directly by the backend with sensitive fields removed.</p></div>
    {error ? <p className="m-4 rounded-lg bg-red-50 p-4 text-sm text-red-700">{error}</p> : loading ? <LoadingSkeleton rows={7} /> : !data?.content?.length ? <EmptyState title="No recent transactions" message="No transactions match the selected filters." /> : <><div className="overflow-x-auto"><table className="w-full min-w-[980px] text-left text-sm"><thead><tr className="border-b border-line bg-slate-50/70 text-xs uppercase tracking-wide text-ink-muted"><th className="px-5 py-3">Customer</th><th className="px-5 py-3">Card</th><th className="px-5 py-3">Transaction</th><th className="px-5 py-3">Amount</th><th className="px-5 py-3">Method</th><th className="px-5 py-3">Status</th><th className="px-5 py-3">Date & time</th><th className="px-5 py-3">Failure reason</th></tr></thead><tbody>{data.content.map((item) => <tr key={item.transactionId} className="border-b border-line/70 last:border-0"><td className="px-5 py-3.5 font-semibold text-ink">{item.customerName}</td><td className="px-5 py-3.5 font-mono text-xs text-ink-muted">{item.cardNumber || 'No card'}</td><td className="px-5 py-3.5 font-semibold">#{item.transactionId}</td><td className="px-5 py-3.5 font-semibold">{formatMoney(item.amount, item.currency)}</td><td className="px-5 py-3.5 text-ink-muted">{item.paymentMethod}</td><td className="px-5 py-3.5"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusStyles[item.paymentStatus] || statusStyles.PENDING}`}>{item.paymentStatus[0] + item.paymentStatus.slice(1).toLowerCase()}</span></td><td className="px-5 py-3.5 text-ink-muted">{formatDate(item.paymentDate)}</td><td className="max-w-52 px-5 py-3.5 text-xs text-red-700">{item.failureReason || '—'}</td></tr>)}</tbody></table></div><Pagination page={data.page + 1} pages={data.totalPages} total={data.totalElements} label="transactions" onChange={(page) => onPage(page - 1)} /></>}
  </section>
}
