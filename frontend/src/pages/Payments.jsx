import { ArrowLeftRight, CreditCard, Plus } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useOutletContext, useSearchParams } from 'react-router-dom'
import { apiMessage, paymentApi } from '../api/client'
import EmptyState from '../components/EmptyState'
import LoadingSkeleton from '../components/LoadingSkeleton'
import Pagination from '../components/Pagination'
import PaymentTable, { paymentTableColumns } from '../components/PaymentTable'
import TableToolbar from '../components/TableToolbar'
import { sortRows, storedColumns } from '../utils/table'

const defaultColumns = ['id', 'reference', 'source', 'destination', 'amount', 'converted', 'status', 'createdAt', 'actions']
const pageSize = 12

export default function Payments({ mode = 'payments' }) {
  const { refreshToken, openCreate } = useOutletContext()
  const [searchParams] = useSearchParams()
  const [payments, setPayments] = useState([])
  const [search, setSearch] = useState(() => searchParams.get('search') || '')
  const [statuses, setStatuses] = useState([]); const [currencies, setCurrencies] = useState([]); const [types, setTypes] = useState([])
  const [dateFrom, setDateFrom] = useState(''); const [dateTo, setDateTo] = useState('')
  const [minAmount, setMinAmount] = useState(''); const [maxAmount, setMaxAmount] = useState('')
  const [sort, setSort] = useState({ key: 'createdAt', direction: 'desc' })
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState(new Set())
  const [visibleColumns, setVisibleColumns] = useState(() => storedColumns(mode, defaultColumns))
  const [frozen, setFrozen] = useState(true)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { const data = await paymentApi.list({ size: 1000, sort: 'createdAt,desc' }); setPayments(data.content) }
    catch (requestError) { setError(apiMessage(requestError)) }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { const timer = setTimeout(load, 0); return () => clearTimeout(timer) }, [load, refreshToken])
  useEffect(() => { const timer = setTimeout(() => { setSearch(searchParams.get('search') || ''); setPage(1) }, 0); return () => clearTimeout(timer) }, [searchParams])

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase().replace(/^#/, '')
    return payments.filter((payment) => {
      const international = payment.destinationCurrency && payment.destinationCurrency !== payment.currency
      const created = new Date(payment.createdAt).getTime()
      return (!term || `${payment.id} ${payment.reference || ''} ${payment.sourceAccountNumber || ''} ${payment.destinationAccountNumber || ''}`.toLowerCase().includes(term))
        && (!statuses.length || statuses.includes(payment.status))
        && (!currencies.length || currencies.includes(payment.currency) || currencies.includes(payment.destinationCurrency))
        && (!types.length || types.includes(international ? 'INTERNATIONAL' : 'DOMESTIC'))
        && (!dateFrom || created >= new Date(dateFrom).getTime())
        && (!dateTo || created <= new Date(`${dateTo}T23:59:59`).getTime())
        && (minAmount === '' || Number(payment.amount) >= Number(minAmount))
        && (maxAmount === '' || Number(payment.amount) <= Number(maxAmount))
    })
  }, [payments, search, statuses, currencies, types, dateFrom, dateTo, minAmount, maxAmount])
  const sorted = useMemo(() => sortRows(filtered, sort, paymentTableColumns), [filtered, sort])
  const pages = Math.max(Math.ceil(sorted.length / pageSize), 1); const currentPage = Math.min(page, pages)
  const visible = sorted.slice((currentPage - 1) * pageSize, currentPage * pageSize)
  const exportRows = selected.size ? sorted.filter((row) => selected.has(row.id)) : sorted
  const availableCurrencies = [...new Set(payments.flatMap((payment) => [payment.currency, payment.destinationCurrency]).filter(Boolean))].sort()
  const hasFilters = Boolean(search || statuses.length || currencies.length || types.length || dateFrom || dateTo || minAmount || maxAmount)
  const clear = () => { setSearch(''); setStatuses([]); setCurrencies([]); setTypes([]); setDateFrom(''); setDateTo(''); setMinAmount(''); setMaxAmount(''); setPage(1) }
  const changeSort = (key) => setSort((current) => ({ key, direction: current.key === key && current.direction === 'asc' ? 'desc' : 'asc' }))
  const toggleAll = () => setSelected(visible.every((row) => selected.has(row.id)) ? new Set() : new Set([...selected, ...visible.map((row) => row.id)]))
  const toggleRow = (id) => setSelected((current) => { const next = new Set(current); next.has(id) ? next.delete(id) : next.add(id); return next })
  const Icon = mode === 'transactions' ? ArrowLeftRight : CreditCard
  const title = mode === 'transactions' ? 'Transaction history' : 'Payments'

  return <div className="mx-auto max-w-[1600px] space-y-4 animate-enter">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="flex items-center gap-2 text-xl font-semibold tracking-tight text-ink"><Icon className="size-5 text-primary" />{title}</h2><p className="mt-0.5 text-xs text-ink-muted">Search, filter, export, and inspect the complete payment lifecycle.</p></div>{mode === 'payments' && <button className="btn-primary h-9 px-3" onClick={openCreate}><Plus className="size-3.5" />Send payment</button>}</div>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs text-red-700" role="alert">{error}</div>}
    <section className="card overflow-hidden">
      <TableToolbar id={mode} search={search} onSearch={(value) => { setSearch(value); setPage(1) }} filters={[
        { key: 'status', label: 'Status', value: statuses, onChange: (value) => { setStatuses(value); setPage(1) }, options: ['COMPLETED', 'FAILED', 'SENT', 'VALIDATED', 'CREATED'].map((value) => ({ value, label: value[0] + value.slice(1).toLowerCase() })) },
        { key: 'currency', label: 'Currency', value: currencies, onChange: (value) => { setCurrencies(value); setPage(1) }, options: availableCurrencies.map((value) => ({ value, label: value })) },
        { key: 'type', label: 'Payment type', value: types, onChange: (value) => { setTypes(value); setPage(1) }, options: [{ value: 'DOMESTIC', label: 'Domestic' }, { value: 'INTERNATIONAL', label: 'International' }] },
      ]} advanced={<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><label className="text-[10px] font-semibold text-ink-muted">Created from<input type="date" className="input mt-1 h-8 text-xs" value={dateFrom} onChange={(event) => setDateFrom(event.target.value)} /></label><label className="text-[10px] font-semibold text-ink-muted">Created to<input type="date" className="input mt-1 h-8 text-xs" value={dateTo} onChange={(event) => setDateTo(event.target.value)} /></label><label className="text-[10px] font-semibold text-ink-muted">Minimum amount<input type="number" min="0" className="input mt-1 h-8 text-xs" value={minAmount} onChange={(event) => setMinAmount(event.target.value)} /></label><label className="text-[10px] font-semibold text-ink-muted">Maximum amount<input type="number" min="0" className="input mt-1 h-8 text-xs" value={maxAmount} onChange={(event) => setMaxAmount(event.target.value)} /></label></div>} hasFilters={hasFilters} onClear={clear} onRefresh={load} busy={loading} rows={exportRows} columns={paymentTableColumns} visibleColumns={visibleColumns} onColumns={setVisibleColumns} frozen={frozen} onFrozen={setFrozen} selectedCount={selected.size} />
      {loading ? <LoadingSkeleton rows={9} /> : visible.length ? <PaymentTable payments={visible} visibleColumns={visibleColumns} sort={sort} onSort={changeSort} selected={selected} onSelect={toggleRow} onSelectAll={toggleAll} frozen={frozen} tableId={mode} /> : <EmptyState title="No payments found" message="No payments match the selected filters." />}
      {!loading && <Pagination page={currentPage} pages={pages} onChange={setPage} total={sorted.length} />}
    </section>
    {!loading && <p className="flex items-center gap-2 text-[10px] text-ink-muted"><Icon className="size-3.5" />{sorted.length} records match the current view. Export and copy operate on selected rows when applicable.</p>}
  </div>
}
