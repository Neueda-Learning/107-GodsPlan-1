import { ArrowDown, ArrowUp, ChevronRight, MoreHorizontal, UserRoundCheck, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { apiMessage, customerApi } from '../api/client'
import EmptyState from '../components/EmptyState'
import LoadingSkeleton from '../components/LoadingSkeleton'
import Pagination from '../components/Pagination'
import TableToolbar from '../components/TableToolbar'
import TransactionStatusBadge from '../components/TransactionStatusBadge'
import { useColumnResize } from '../hooks/useColumnResize'
import { formatDate, formatMoney, maskIdentifier } from '../utils/format'
import { sortRows, storedColumns } from '../utils/table'

const allColumnKeys = ['name', 'country', 'currency', 'accounts', 'lastPayment', 'status', 'actions']

function BeneficiaryDrawer({ customer, onClose, onPayment }) {
  useEffect(() => {
    const close = (event) => event.key === 'Escape' && onClose()
    document.addEventListener('keydown', close)
    return () => document.removeEventListener('keydown', close)
  }, [onClose])
  if (!customer) return null
  const preferredCurrency = [...new Set(customer.accounts.map((account) => account.currency))].join(', ')
  const info = [
    ['Email', customer.email], ['Country', customer.country], ['Preferred currency', preferredCurrency],
    ['Card', customer.cardNumber ? maskIdentifier(customer.cardNumber) : null], ['Card brand', customer.cardBrand],
    ['Total payments', customer.transactionPage?.totalElements],
    ['Last activity', customer.lastPayment ? formatDate(customer.lastPayment) : null],
  ].filter(([, value]) => value !== null && value !== undefined && value !== '')
  return <div className="fixed inset-0 z-[70] bg-slate-950/40 backdrop-blur-sm" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <aside className="ml-auto flex h-full w-full max-w-3xl animate-enter flex-col border-l border-line bg-surface shadow-2xl" role="dialog" aria-modal="true" aria-labelledby="beneficiary-title">
      <header className="flex items-center justify-between border-b border-line px-5 py-4"><div><p className="eyebrow">Beneficiary details</p><h2 id="beneficiary-title" className="mt-1 text-lg font-semibold text-ink">{customer.fullName}</h2></div><button className="icon-button size-9" onClick={onClose} aria-label="Close beneficiary details"><X className="size-4" /></button></header>
      <div className="flex-1 overflow-y-auto p-5">
        <section><h3 className="text-xs font-bold uppercase tracking-wider text-ink-muted">Beneficiary information</h3><dl className="mt-3 grid border-l border-t border-line sm:grid-cols-2">{info.map(([label, value]) => <div key={label} className="border-b border-r border-line px-3 py-2.5"><dt className="text-[10px] font-medium text-ink-muted">{label}</dt><dd className="mt-1 text-xs font-semibold text-ink">{value}</dd></div>)}</dl></section>
        {customer.accounts.length > 0 && <section className="mt-6"><h3 className="text-xs font-bold uppercase tracking-wider text-ink-muted">Accounts</h3><div className="mt-3 overflow-x-auto rounded-xl border border-line"><table className="w-full min-w-[620px] text-left text-xs"><thead className="bg-canvas text-[9px] uppercase tracking-wider text-ink-muted"><tr><th className="px-3 py-2.5">Account number</th><th className="px-3 py-2.5">Currency</th><th className="px-3 py-2.5">Account type</th><th className="px-3 py-2.5">Status</th></tr></thead><tbody>{customer.accounts.map((account) => <tr key={account.id} className="border-t border-line"><td className="px-3 py-2.5 font-mono font-semibold text-ink">{maskIdentifier(account.accountNumber || account.maskedAccountNumber)}</td><td className="px-3 py-2.5 text-ink-muted">{account.currency}</td><td className="px-3 py-2.5 text-ink-muted">{account.accountType}</td><td className="px-3 py-2.5"><span className={`inline-flex rounded-full px-2 py-0.5 text-[9px] font-bold ${account.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{account.active ? 'Active' : 'Inactive'}</span></td></tr>)}</tbody></table></div></section>}
        <div className="mt-3 flex justify-end"><button className="btn-primary h-9 px-3" onClick={onPayment}>Send payment</button></div>
        {customer.transactionPage?.content?.length > 0 && <section className="mt-6"><h3 className="text-xs font-bold uppercase tracking-wider text-ink-muted">Recent payments</h3><div className="mt-3 overflow-x-auto rounded-xl border border-line"><table className="w-full min-w-[680px] text-left text-xs"><thead className="bg-canvas text-[9px] uppercase tracking-wider text-ink-muted"><tr><th className="px-3 py-2.5">Transaction</th><th className="px-3 py-2.5">Amount</th><th className="px-3 py-2.5">Method</th><th className="px-3 py-2.5">Date</th><th className="px-3 py-2.5">Status</th></tr></thead><tbody>{customer.transactionPage.content.map((transaction) => <tr key={transaction.transactionId} className="border-t border-line"><td className="px-3 py-2.5 font-semibold text-ink">#{transaction.transactionId}</td><td className="px-3 py-2.5 font-semibold text-ink">{formatMoney(transaction.amount, transaction.currency)}</td><td className="px-3 py-2.5 text-ink-muted">{transaction.paymentMethod}</td><td className="px-3 py-2.5 text-ink-muted">{formatDate(transaction.paymentDate)}</td><td className="px-3 py-2.5"><TransactionStatusBadge outcome={transaction.outcome} /></td></tr>)}</tbody></table></div></section>}
      </div>
    </aside>
  </div>
}

export default function CustomerDetails() {
  const { openCreate } = useOutletContext()
  const tableRoot = useColumnResize('beneficiaries')
  const [customers, setCustomers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [countries, setCountries] = useState([])
  const [currencies, setCurrencies] = useState([])
  const [statuses, setStatuses] = useState([])
  const [dateFrom, setDateFrom] = useState(''); const [dateTo, setDateTo] = useState('')
  const [sort, setSort] = useState({ key: 'name', direction: 'asc' })
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState(new Set())
  const [drawer, setDrawer] = useState(null)
  const [visibleColumns, setVisibleColumns] = useState(() => storedColumns('beneficiaries', allColumnKeys))
  const [frozen, setFrozen] = useState(true)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [response, options] = await Promise.all([customerApi.list({ page: 0, size: 100 }), customerApi.paymentOptions()])
      const countryById = new Map(options.map((customer) => [customer.id, customer.country]))
      const enriched = await Promise.all(response.content.map(async (customer) => {
        const transactionPage = await customerApi.transactions(customer.id, { page: 0, size: 5 }).catch(() => null)
        return { ...customer, country: countryById.get(customer.id), transactionPage, lastPayment: transactionPage?.content?.[0]?.paymentDate || null }
      }))
      setCustomers(enriched)
    } catch (requestError) { setError(apiMessage(requestError)) }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { const timer = setTimeout(load, 0); return () => clearTimeout(timer) }, [load])

  const columns = useMemo(() => [
    { key: 'name', label: 'Beneficiary name', sortValue: (row) => row.fullName, exportValue: (row) => row.fullName },
    { key: 'country', label: 'Country', exportValue: (row) => row.country },
    { key: 'currency', label: 'Currency', sortValue: (row) => row.accounts[0]?.currency || '', exportValue: (row) => [...new Set(row.accounts.map((account) => account.currency))].join(', ') },
    { key: 'accounts', label: 'Accounts', sortValue: (row) => row.accounts.length, exportValue: (row) => row.accounts.length },
    { key: 'lastPayment', label: 'Last payment', sortValue: (row) => row.lastPayment ? new Date(row.lastPayment).getTime() : 0, exportValue: (row) => row.lastPayment || '' },
    { key: 'status', label: 'Status', sortValue: (row) => row.accounts.some((account) => account.active) ? 'Active' : 'Inactive', exportValue: (row) => row.accounts.some((account) => account.active) ? 'Active' : 'Inactive' },
    { key: 'actions', label: 'Actions', exportValue: () => '' },
  ], [])

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return customers.filter((customer) => {
      const customerCurrencies = customer.accounts.map((account) => account.currency)
      const state = customer.accounts.some((account) => account.active) ? 'ACTIVE' : 'INACTIVE'
      const paymentTime = customer.lastPayment ? new Date(customer.lastPayment).getTime() : null
      return (!term || `${customer.fullName} ${customer.email} ${customer.country || ''}`.toLowerCase().includes(term))
        && (!countries.length || countries.includes(customer.country))
        && (!currencies.length || currencies.some((currency) => customerCurrencies.includes(currency)))
        && (!statuses.length || statuses.includes(state))
        && (!dateFrom || paymentTime && paymentTime >= new Date(dateFrom).getTime())
        && (!dateTo || paymentTime && paymentTime <= new Date(`${dateTo}T23:59:59`).getTime())
    })
  }, [customers, search, countries, currencies, statuses, dateFrom, dateTo])
  const sorted = useMemo(() => sortRows(filtered, sort, columns), [filtered, sort, columns])
  const pages = Math.max(Math.ceil(sorted.length / 10), 1); const currentPage = Math.min(page, pages)
  const visibleRows = sorted.slice((currentPage - 1) * 10, currentPage * 10)
  const exportRows = selected.size ? sorted.filter((row) => selected.has(row.id)) : sorted
  const availableCountries = [...new Set(customers.map((customer) => customer.country).filter(Boolean))].sort()
  const availableCurrencies = [...new Set(customers.flatMap((customer) => customer.accounts.map((account) => account.currency)))].sort()
  const hasFilters = Boolean(search || countries.length || currencies.length || statuses.length || dateFrom || dateTo)
  const clear = () => { setSearch(''); setCountries([]); setCurrencies([]); setStatuses([]); setDateFrom(''); setDateTo(''); setPage(1) }
  const changeSort = (key) => setSort((current) => ({ key, direction: current.key === key && current.direction === 'asc' ? 'desc' : 'asc' }))
  const toggleAll = () => setSelected(visibleRows.every((row) => selected.has(row.id)) ? new Set() : new Set([...selected, ...visibleRows.map((row) => row.id)]))
  const toggleRow = (id) => setSelected((current) => { const next = new Set(current); next.has(id) ? next.delete(id) : next.add(id); return next })
  const activeColumns = visibleColumns.map((key) => columns.find((column) => column.key === key)).filter(Boolean)

  return <div className="mx-auto max-w-[1600px] space-y-4 animate-enter">
    <div><h2 className="text-xl font-semibold tracking-tight text-ink">Beneficiaries</h2><p className="mt-0.5 text-xs text-ink-muted">Search, filter, and inspect registered payment beneficiaries.</p></div>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs text-red-700" role="alert">{error}</div>}
    <section ref={tableRoot} className="card overflow-hidden">
      <TableToolbar id="beneficiaries" search={search} onSearch={(value) => { setSearch(value); setPage(1) }} filters={[
        { key: 'country', label: 'Country', value: countries, onChange: (value) => { setCountries(value); setPage(1) }, options: availableCountries.map((value) => ({ value, label: value })) },
        { key: 'currency', label: 'Currency', value: currencies, onChange: (value) => { setCurrencies(value); setPage(1) }, options: availableCurrencies.map((value) => ({ value, label: value })) },
        { key: 'status', label: 'Status', value: statuses, onChange: (value) => { setStatuses(value); setPage(1) }, options: [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }] },
      ]} advanced={<div className="flex flex-wrap items-end gap-3"><label className="text-[10px] font-semibold text-ink-muted">Last payment from<input type="date" className="input mt-1 h-8 w-40 text-xs" value={dateFrom} onChange={(event) => setDateFrom(event.target.value)} /></label><label className="text-[10px] font-semibold text-ink-muted">Last payment to<input type="date" className="input mt-1 h-8 w-40 text-xs" value={dateTo} onChange={(event) => setDateTo(event.target.value)} /></label></div>} hasFilters={hasFilters} onClear={clear} onRefresh={load} busy={loading} rows={exportRows} columns={columns} visibleColumns={visibleColumns} onColumns={setVisibleColumns} frozen={frozen} onFrozen={setFrozen} selectedCount={selected.size} />
      {loading ? <LoadingSkeleton rows={7} /> : visibleRows.length ? <div className="max-h-[620px] overflow-auto"><table className="w-full min-w-[900px] table-fixed text-left text-xs"><thead className="sticky top-0 z-20 bg-canvas"><tr className="border-b border-line text-[9px] font-bold uppercase tracking-wider text-ink-muted"><th className="w-10 px-3 py-2.5"><input type="checkbox" checked={visibleRows.every((row) => selected.has(row.id))} onChange={toggleAll} aria-label="Select all visible beneficiaries" /></th>{activeColumns.map((column, index) => <th key={column.key} className={`px-3 py-2.5 ${frozen && index === 0 ? 'sticky left-10 z-20 bg-canvas' : ''}`}><button className="flex w-full items-center gap-1 text-left" onClick={() => column.key !== 'actions' && changeSort(column.key)}>{column.label}{sort.key === column.key && (sort.direction === 'asc' ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />)}</button><span className="block h-0.5 resize-x overflow-auto" /></th>)}</tr></thead><tbody>{visibleRows.map((customer) => <tr key={customer.id} onDoubleClick={() => setDrawer(customer)} className="group border-b border-line/80 hover:bg-primary-light/40"><td className="px-3 py-2.5"><input type="checkbox" checked={selected.has(customer.id)} onChange={() => toggleRow(customer.id)} aria-label={`Select ${customer.fullName}`} /></td>{activeColumns.map((column, index) => <td key={column.key} className={`truncate px-3 py-2.5 ${frozen && index === 0 ? 'sticky left-10 z-10 bg-surface group-hover:bg-primary-light' : ''}`}>{column.key === 'name' ? <button className="flex items-center gap-2 text-left" onClick={() => setDrawer(customer)}><span className="grid size-7 place-items-center rounded-lg bg-primary-light text-[10px] font-bold text-primary">{customer.fullName[0]}</span><span><strong className="block text-xs text-ink">{customer.fullName}</strong><small className="block max-w-44 truncate text-[9px] text-ink-muted">{customer.email}</small></span></button> : column.key === 'country' ? customer.country || '—' : column.key === 'currency' ? <span className="font-semibold text-ink">{[...new Set(customer.accounts.map((account) => account.currency))].join(', ') || '—'}</span> : column.key === 'accounts' ? customer.accounts.length : column.key === 'lastPayment' ? customer.lastPayment ? formatDate(customer.lastPayment) : '—' : column.key === 'status' ? <span className={`rounded-full px-2 py-1 text-[9px] font-bold ${customer.accounts.some((account) => account.active) ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{customer.accounts.some((account) => account.active) ? 'Active' : 'Inactive'}</span> : <button className="flex items-center gap-1 text-primary" onClick={() => setDrawer(customer)}><MoreHorizontal className="size-4" /><ChevronRight className="size-3" /></button>}</td>)}</tr>)}</tbody></table></div> : <EmptyState title="No beneficiaries found" message="No beneficiaries match the selected filters." />}
      {!loading && <Pagination page={currentPage} pages={pages} total={sorted.length} onChange={setPage} label="beneficiaries" />}
    </section>
    <p className="flex items-center gap-2 text-[10px] text-ink-muted"><UserRoundCheck className="size-3.5" />Staff accounts remain excluded. Double-click a row to open details.</p>
    {drawer && <BeneficiaryDrawer customer={drawer} onClose={() => setDrawer(null)} onPayment={() => { setDrawer(null); openCreate() }} />}
  </div>
}
