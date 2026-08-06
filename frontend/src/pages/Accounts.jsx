import { ArrowDown, ArrowUp, Landmark, MoreHorizontal, Plus } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { apiMessage, customerApi } from '../api/client'
import EmptyState from '../components/EmptyState'
import LoadingSkeleton from '../components/LoadingSkeleton'
import Pagination from '../components/Pagination'
import TableToolbar from '../components/TableToolbar'
import { useColumnResize } from '../hooks/useColumnResize'
import { formatMoney, maskIdentifier } from '../utils/format'
import { sortRows, storedColumns } from '../utils/table'

const allColumnKeys = ['name', 'number', 'currency', 'availableBalance', 'status', 'owner', 'country', 'actions']

export default function Accounts() {
  const { openCreate } = useOutletContext()
  const tableRoot = useColumnResize('accounts')
  const [accounts, setAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [currencies, setCurrencies] = useState([]); const [owners, setOwners] = useState([])
  const [types, setTypes] = useState([])
  const [minBalance, setMinBalance] = useState(''); const [maxBalance, setMaxBalance] = useState('')
  const [sort, setSort] = useState({ key: 'owner', direction: 'asc' })
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState(new Set())
  const [visibleColumns, setVisibleColumns] = useState(() => storedColumns('accounts', allColumnKeys))
  const [frozen, setFrozen] = useState(true)

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const customers = await customerApi.paymentOptions()
      const groups = await Promise.all(customers.map(async (customer) => ({ customer, accounts: await customerApi.accounts(customer.id) })))
      setAccounts(groups.flatMap(({ customer, accounts: customerAccounts }) => customerAccounts.map((account) => ({
        ...account,
        ownerId: customer.id,
        owner: customer.fullName,
        country: customer.country,
        number: maskIdentifier(account.accountNumber || account.maskedAccountNumber),
        status: 'AVAILABLE',
      }))))
    } catch (requestError) { setError(apiMessage(requestError)) }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { const timer = setTimeout(load, 0); return () => clearTimeout(timer) }, [load])

  const columns = useMemo(() => [
    { key: 'name', label: 'Account name', sortValue: (row) => row.accountType, exportValue: (row) => row.accountType },
    { key: 'number', label: 'Account number' },
    { key: 'currency', label: 'Currency' },
    { key: 'availableBalance', label: 'Available balance', sortValue: (row) => Number(row.availableBalance), exportValue: (row) => row.availableBalance },
    { key: 'status', label: 'Status', exportValue: () => 'Available' },
    { key: 'owner', label: 'Owner' },
    { key: 'country', label: 'Country' },
    { key: 'actions', label: 'Actions', exportValue: () => '' },
  ], [])
  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    return accounts.filter((account) => (!term || `${account.accountType} ${account.number} ${account.owner}`.toLowerCase().includes(term))
      && (!currencies.length || currencies.includes(account.currency))
      && (!owners.length || owners.includes(String(account.ownerId)))
      && (!types.length || types.includes(account.accountType))
      && (minBalance === '' || Number(account.availableBalance) >= Number(minBalance))
      && (maxBalance === '' || Number(account.availableBalance) <= Number(maxBalance)))
  }, [accounts, search, currencies, owners, types, minBalance, maxBalance])
  const sorted = useMemo(() => sortRows(filtered, sort, columns), [filtered, sort, columns])
  const pages = Math.max(Math.ceil(sorted.length / 12), 1); const currentPage = Math.min(page, pages)
  const visibleRows = sorted.slice((currentPage - 1) * 12, currentPage * 12)
  const exportRows = selected.size ? sorted.filter((row) => selected.has(row.id)) : sorted
  const availableCurrencies = [...new Set(accounts.map((account) => account.currency))].sort()
  const availableOwners = [...new Map(accounts.map((account) => [account.ownerId, account.owner])).entries()]
  const availableTypes = [...new Set(accounts.map((account) => account.accountType))].sort()
  const hasFilters = Boolean(search || currencies.length || owners.length || types.length || minBalance || maxBalance)
  const clear = () => { setSearch(''); setCurrencies([]); setOwners([]); setTypes([]); setMinBalance(''); setMaxBalance(''); setPage(1) }
  const changeSort = (key) => setSort((current) => ({ key, direction: current.key === key && current.direction === 'asc' ? 'desc' : 'asc' }))
  const toggleAll = () => setSelected(visibleRows.every((row) => selected.has(row.id)) ? new Set() : new Set([...selected, ...visibleRows.map((row) => row.id)]))
  const toggleRow = (id) => setSelected((current) => { const next = new Set(current); next.has(id) ? next.delete(id) : next.add(id); return next })
  const activeColumns = visibleColumns.map((key) => columns.find((column) => column.key === key)).filter(Boolean)

  return <div className="mx-auto max-w-[1600px] space-y-4 animate-enter">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="flex items-center gap-2 text-xl font-semibold tracking-tight text-ink"><Landmark className="size-5 text-primary" />Accounts</h2><p className="mt-0.5 text-xs text-ink-muted">Transfer-enabled customer accounts and current database balances.</p></div><button className="btn-primary h-9 px-3" onClick={openCreate}><Plus className="size-3.5" />Send payment</button></div>
    {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-xs text-red-700" role="alert">{error}</div>}
    <section ref={tableRoot} className="card overflow-hidden">
      <TableToolbar id="accounts" search={search} onSearch={(value) => { setSearch(value); setPage(1) }} filters={[
        { key: 'currency', label: 'Currency', value: currencies, onChange: (value) => { setCurrencies(value); setPage(1) }, options: availableCurrencies.map((value) => ({ value, label: value })) },
        { key: 'owner', label: 'Owner', value: owners, onChange: (value) => { setOwners(value); setPage(1) }, options: availableOwners.map(([value, label]) => ({ value: String(value), label })) },
        { key: 'type', label: 'Account type', value: types, onChange: (value) => { setTypes(value); setPage(1) }, options: availableTypes.map((value) => ({ value, label: value })) },
      ]} advanced={<div className="flex flex-wrap items-end gap-3"><label className="text-[10px] font-semibold text-ink-muted">Minimum balance<input className="input mt-1 h-8 w-40 text-xs" type="number" min="0" value={minBalance} onChange={(event) => setMinBalance(event.target.value)} /></label><label className="text-[10px] font-semibold text-ink-muted">Maximum balance<input className="input mt-1 h-8 w-40 text-xs" type="number" min="0" value={maxBalance} onChange={(event) => setMaxBalance(event.target.value)} /></label></div>} hasFilters={hasFilters} onClear={clear} onRefresh={load} busy={loading} rows={exportRows} columns={columns} visibleColumns={visibleColumns} onColumns={setVisibleColumns} frozen={frozen} onFrozen={setFrozen} selectedCount={selected.size} />
      {loading ? <LoadingSkeleton rows={8} /> : visibleRows.length ? <div className="max-h-[650px] overflow-auto"><table className="w-full min-w-[980px] table-fixed text-left text-xs"><thead className="sticky top-0 z-20 bg-canvas"><tr className="border-b border-line text-[9px] font-bold uppercase tracking-wider text-ink-muted"><th className="w-10 px-3 py-2.5"><input type="checkbox" checked={visibleRows.every((row) => selected.has(row.id))} onChange={toggleAll} aria-label="Select all visible accounts" /></th>{activeColumns.map((column, index) => <th key={column.key} className={`px-3 py-2.5 ${frozen && index === 0 ? 'sticky left-10 z-20 bg-canvas' : ''}`}><button className="flex items-center gap-1" onClick={() => column.key !== 'actions' && changeSort(column.key)}>{column.label}{sort.key === column.key && (sort.direction === 'asc' ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />)}</button><span className="block h-0.5 resize-x overflow-auto" /></th>)}</tr></thead><tbody>{visibleRows.map((account) => <tr key={`${account.ownerId}-${account.id}`} className="group border-b border-line/80 hover:bg-primary-light/40"><td className="px-3 py-2.5"><input type="checkbox" checked={selected.has(account.id)} onChange={() => toggleRow(account.id)} aria-label={`Select ${account.accountType}`} /></td>{activeColumns.map((column, index) => <td key={column.key} className={`truncate px-3 py-2.5 ${frozen && index === 0 ? 'sticky left-10 z-10 bg-surface group-hover:bg-primary-light' : ''}`}>{column.key === 'name' ? <div><strong className="block text-xs text-ink">{account.accountType}</strong><small className="text-[9px] text-ink-muted">Account #{account.id}</small></div> : column.key === 'number' ? <span className="font-mono font-semibold tracking-wide text-ink">{account.number}</span> : column.key === 'currency' ? <span className="font-semibold text-ink">{account.currency}</span> : column.key === 'availableBalance' ? <span className="font-semibold tabular-nums text-ink">{formatMoney(account.availableBalance, account.currency)}</span> : column.key === 'status' ? <span className="rounded-full bg-emerald-50 px-2 py-1 text-[9px] font-bold text-emerald-700">Available</span> : column.key === 'owner' ? account.owner : column.key === 'country' ? account.country || '—' : <button className="text-primary" onClick={openCreate} aria-label={`Create payment with ${account.accountType}`}><MoreHorizontal className="size-4" /></button>}</td>)}</tr>)}</tbody></table></div> : <EmptyState title="No accounts found" message="No accounts match the selected filters." />}
      {!loading && <Pagination page={currentPage} pages={pages} total={sorted.length} onChange={setPage} label="accounts" />}
    </section>
  </div>
}
