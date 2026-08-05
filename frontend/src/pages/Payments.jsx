import { CreditCard } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { paymentApi, apiMessage } from '../api/client'
import EmptyState from '../components/EmptyState'
import FilterDropdown from '../components/FilterDropdown'
import LoadingSkeleton from '../components/LoadingSkeleton'
import Pagination from '../components/Pagination'
import PaymentTable from '../components/PaymentTable'
import SearchBar from '../components/SearchBar'

const pageSize = 10

export default function Payments() {
  const { refreshToken } = useOutletContext()
  const [payments, setPayments] = useState([])
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    paymentApi.list({ size: 1000, sort: 'createdAt,desc' }).then((data) => active && setPayments(data.content))
      .catch((err) => active && setError(apiMessage(err))).finally(() => active && setLoading(false))
    return () => { active = false }
  }, [refreshToken])

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase().replace(/^#/, '')
    return payments.filter((payment) => (!status || payment.status === status)
      && (!term || String(payment.id).includes(term) || payment.reference?.toLowerCase().includes(term)))
  }, [payments, search, status])
  const pages = Math.max(Math.ceil(filtered.length / pageSize), 1)
  const currentPage = Math.min(page, pages)
  const visible = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize)
  const changeSearch = (value) => { setSearch(value); setPage(1) }
  const changeStatus = (value) => { setStatus(value); setPage(1) }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div><p className="text-sm font-medium text-primary-hover">Transaction records</p><h2 className="mt-1 text-2xl font-bold tracking-tight text-ink">All payments</h2><p className="mt-1 text-sm text-ink-muted">Search, filter, and inspect the complete payment lifecycle.</p></div>
      <section className="card overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-line p-4 sm:flex-row"><SearchBar value={search} onChange={changeSearch} placeholder="Search by ID or reference" /><FilterDropdown value={status} onChange={changeStatus} /></div>
        {error && <div className="m-4 rounded-lg bg-red-50 p-4 text-sm text-red-700" role="alert">{error}</div>}
        {loading ? <LoadingSkeleton rows={7} /> : visible.length ? <><PaymentTable payments={visible} /><Pagination page={currentPage} pages={pages} onChange={setPage} total={filtered.length} /></> : <EmptyState />}
      </section>
      {!loading && <p className="flex items-center gap-2 text-xs text-ink-muted"><CreditCard className="size-3.5" />Showing records currently available from the payment service.</p>}
    </div>
  )
}
