import { ChevronDown, ChevronUp, CreditCard, Landmark, Loader2, Mail, ReceiptText } from 'lucide-react'
import { useState } from 'react'
import { apiMessage, customerApi } from '../api/client'
import { formatDate, formatMoney } from '../utils/format'
import EmptyState from './EmptyState'
import TransactionStatusBadge from './TransactionStatusBadge'

export default function CustomerCard({ customer }) {
  const [expanded, setExpanded] = useState(false)
  const [transactions, setTransactions] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const loadTransactions = async (page = 0) => {
    setLoading(true); setError('')
    try { setTransactions(await customerApi.transactions(customer.id, { page, size: 5 })) }
    catch (err) { setError(apiMessage(err)) }
    finally { setLoading(false) }
  }
  const toggle = () => {
    const next = !expanded
    setExpanded(next)
    if (next && !transactions && !loading) loadTransactions()
  }

  return <article className="card overflow-hidden">
    <div className="p-5 sm:p-6">
      <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-start">
        <div className="min-w-0">
          <div className="flex items-center gap-3"><span className="grid size-11 shrink-0 place-items-center rounded-xl bg-primary-light text-lg font-bold text-primary-hover">{customer.fullName.charAt(0)}</span>
            <div className="min-w-0"><h3 className="truncate text-lg font-bold text-ink">{customer.fullName}</h3>
              <p className="mt-0.5 flex items-center gap-2 font-mono text-sm font-semibold tracking-wide text-ink-muted"><CreditCard className="size-4" />{customer.maskedCardNumber || 'No card on file'}</p></div></div>
          <p className="mt-4 flex items-center gap-2 text-sm text-ink-muted"><Mail className="size-4" />{customer.email}</p>
        </div>
        {customer.cardBrand && <span className="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-ink-muted">{customer.cardBrand}</span>}
      </div>
      <div className="mt-5 border-t border-line pt-5"><div className="mb-3 flex items-center gap-2"><Landmark className="size-4 text-primary-hover" /><h4 className="text-sm font-bold text-ink">Accounts</h4></div>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{customer.accounts.map((account) => <div key={account.id} className="rounded-xl border border-line bg-canvas p-3"><div className="flex items-center justify-between"><p className="text-sm font-semibold text-ink">{account.maskedAccountNumber}</p><span className={`size-2 rounded-full ${account.active ? 'bg-success' : 'bg-slate-300'}`} /></div><p className="mt-1 text-xs text-ink-muted">{account.accountType} · {account.currency}</p></div>)}</div>
      </div>
    </div>
    <button onClick={toggle} className="flex w-full items-center justify-between border-t border-line bg-slate-50/70 px-5 py-3.5 text-sm font-semibold text-ink transition hover:bg-primary-light/60 sm:px-6">
      <span className="flex items-center gap-2"><ReceiptText className="size-4 text-primary-hover" />Payment history</span>{expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
    </button>
    {expanded && <div className="border-t border-line">
      {loading ? <div className="grid h-36 place-items-center text-primary"><Loader2 className="size-6 animate-spin" /><span className="sr-only">Loading transactions</span></div>
        : error ? <div className="m-4 rounded-lg bg-red-50 p-4 text-sm text-red-700"><p>{error}</p><button className="mt-2 font-semibold underline" onClick={() => loadTransactions(transactions?.page || 0)}>Try again</button></div>
          : transactions?.content.length ? <>
            <div className="overflow-x-auto"><table className="w-full min-w-[720px] text-left"><thead><tr className="border-b border-line bg-white text-xs font-semibold uppercase tracking-wide text-ink-muted"><th className="px-5 py-3">Transaction</th><th className="px-5 py-3">Amount</th><th className="px-5 py-3">Date & time</th><th className="px-5 py-3">Method</th><th className="px-5 py-3">Status</th></tr></thead>
              <tbody>{transactions.content.map((transaction) => <tr key={transaction.transactionId} className="border-b border-line/80 text-sm last:border-0"><td className="px-5 py-3.5 font-semibold text-ink">#{transaction.transactionId}</td><td className="px-5 py-3.5 font-semibold text-ink">{formatMoney(transaction.amount, transaction.currency)}</td><td className="px-5 py-3.5 text-ink-muted">{formatDate(transaction.paymentDate)}</td><td className="px-5 py-3.5 text-ink-muted">{transaction.paymentMethod}</td><td className="px-5 py-3.5"><TransactionStatusBadge outcome={transaction.outcome} /></td></tr>)}</tbody></table></div>
            {transactions.totalPages > 1 && <div className="flex items-center justify-end gap-3 border-t border-line px-5 py-3"><button className="btn-secondary h-9" disabled={transactions.page === 0} onClick={() => loadTransactions(transactions.page - 1)}>Previous</button><span className="text-xs text-ink-muted">Page {transactions.page + 1} of {transactions.totalPages}</span><button className="btn-secondary h-9" disabled={transactions.page + 1 >= transactions.totalPages} onClick={() => loadTransactions(transactions.page + 1)}>Next</button></div>}
          </> : <EmptyState title="No payment history" message="This customer has no previous transactions." />}
    </div>}
  </article>
}
