import { ChevronRight } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { formatDate, formatMoney } from '../utils/format'
import StatusBadge from './StatusBadge'

export default function PaymentTable({ payments }) {
  const navigate = useNavigate()
  const open = (id) => navigate(`/payments/${id}`)
  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-left">
          <thead><tr className="border-b border-line bg-slate-50/60 text-xs font-semibold uppercase tracking-wide text-ink-muted">
            <th className="px-5 py-3.5">Payment ID</th><th className="px-5 py-3.5">Amount</th><th className="px-5 py-3.5">Currency</th><th className="px-5 py-3.5">Status</th><th className="px-5 py-3.5">Created at</th><th className="w-12"><span className="sr-only">Open</span></th>
          </tr></thead>
          <tbody>{payments.map((payment) => <tr key={payment.id} tabIndex="0" onClick={() => open(payment.id)} onKeyDown={(e) => e.key === 'Enter' && open(payment.id)} className="cursor-pointer border-b border-line/80 text-sm transition last:border-0 hover:bg-primary-light/50 focus:bg-primary-light/50 focus:outline-none">
            <td className="px-5 py-4 font-semibold text-ink">#{payment.id}</td>
            <td className="px-5 py-4 font-medium text-ink">{formatMoney(payment.amount, payment.currency)}</td>
            <td className="px-5 py-4 text-ink-muted">{payment.currency}</td>
            <td className="px-5 py-4"><StatusBadge status={payment.status} /></td>
            <td className="px-5 py-4 text-ink-muted">{formatDate(payment.createdAt)}</td>
            <td className="pr-4 text-ink-muted"><ChevronRight className="size-4" /></td>
          </tr>)}</tbody>
        </table>
      </div>
      <div className="divide-y divide-line md:hidden">
        {payments.map((payment) => <button key={payment.id} onClick={() => open(payment.id)} className="block w-full p-4 text-left transition hover:bg-primary-light/50">
          <div className="flex items-start justify-between gap-3"><div><p className="font-semibold text-ink">Payment #{payment.id}</p><p className="mt-1 text-sm text-ink-muted">{payment.reference || 'No reference'}</p></div><StatusBadge status={payment.status} /></div>
          <div className="mt-4 flex items-end justify-between"><p className="font-semibold text-ink">{formatMoney(payment.amount, payment.currency)}</p><p className="text-xs text-ink-muted">{formatDate(payment.createdAt)}</p></div>
        </button>)}
      </div>
    </>
  )
}

