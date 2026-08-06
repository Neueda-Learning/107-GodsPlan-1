import { Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { apiMessage, customerApi } from '../api/client'
import CustomerCard from '../components/CustomerCard'
import EmptyState from '../components/EmptyState'
import LoadingSkeleton from '../components/LoadingSkeleton'
import Pagination from '../components/Pagination'

export default function CustomerDetails() {
  const [customers, setCustomers] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    customerApi.list({ page: 0, size: 8 }).then((data) => active && setCustomers(data))
      .catch((err) => active && setError(apiMessage(err))).finally(() => active && setLoading(false))
    return () => { active = false }
  }, [])

  const changePage = async (page) => {
    setLoading(true); setError('')
    try { setCustomers(await customerApi.list({ page: page - 1, size: 8 })) }
    catch (err) { setError(apiMessage(err)) }
    finally { setLoading(false) }
  }

  return <div className="mx-auto max-w-7xl space-y-6">
    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><p className="text-sm font-medium text-primary-hover">Customer management</p><h2 className="mt-1 text-2xl font-bold tracking-tight text-ink">Customer details</h2><p className="mt-1 text-sm text-ink-muted">Review customer accounts and payment activity.</p></div></div>
    {error && <div className="rounded-card border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert"><p>{error}</p><button className="mt-2 font-semibold underline" onClick={() => changePage((customers?.page || 0) + 1)}>Try again</button></div>}
    {loading ? <div className="grid gap-5 lg:grid-cols-2"><div className="card"><LoadingSkeleton rows={5} /></div><div className="card"><LoadingSkeleton rows={5} /></div></div>
      : customers?.content.length ? <><section className="grid gap-5 lg:grid-cols-2" aria-label="Registered customers">{customers.content.map((customer) => <CustomerCard key={customer.id} customer={customer} />)}</section>
        <div className="card"><Pagination page={customers.page + 1} pages={customers.totalPages} total={customers.totalElements} onChange={changePage} label="customers" /></div></>
        : <div className="card"><EmptyState title="No customers found" message="There are no registered customers available." /></div>}
    {!loading && <p className="flex items-center gap-2 text-xs text-ink-muted"><Users className="size-4" />Staff accounts are excluded from this list.</p>}
  </div>
}
