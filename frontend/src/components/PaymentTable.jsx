import { ArrowDown, ArrowUp, ArrowUpRight, ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { formatDate, formatMoney } from '../utils/format'
import { beginColumnResize, storedWidths } from '../utils/table'
import StatusBadge from './StatusBadge'

const masked = (value) => value ? `XXXX ${String(value).replace(/\s/g, '').slice(-4)}` : '—'

// This shared column schema intentionally lives beside the table renderer.
// eslint-disable-next-line react-refresh/only-export-components
export const paymentTableColumns = [
  { key: 'id', label: 'Payment', sortValue: (row) => Number(row.id), exportValue: (row) => row.id },
  { key: 'reference', label: 'Reference', exportValue: (row) => row.reference || '' },
  { key: 'source', label: 'Source account', sortValue: (row) => row.sourceAccountNumber || '', exportValue: (row) => masked(row.sourceAccountNumber) },
  { key: 'destination', label: 'Destination', sortValue: (row) => row.destinationAccountNumber || '', exportValue: (row) => masked(row.destinationAccountNumber) },
  { key: 'amount', label: 'Amount', sortValue: (row) => Number(row.amount), exportValue: (row) => `${row.currency} ${row.amount}` },
  { key: 'converted', label: 'Converted', sortValue: (row) => Number(row.destinationAmount || row.amount), exportValue: (row) => row.destinationAmount ? `${row.destinationCurrency} ${row.destinationAmount}` : '' },
  { key: 'status', label: 'Status' },
  { key: 'createdAt', label: 'Created', sortValue: (row) => new Date(row.createdAt).getTime(), exportValue: (row) => row.createdAt },
  { key: 'updatedAt', label: 'Last updated', sortValue: (row) => new Date(row.updatedAt).getTime(), exportValue: (row) => row.updatedAt },
  { key: 'exchangeRate', label: 'FX rate', sortValue: (row) => Number(row.exchangeRate || 1), exportValue: (row) => row.exchangeRate || 1 },
  { key: 'actions', label: 'Actions', exportValue: () => '' },
]

const defaultColumns = ['id', 'reference', 'amount', 'status', 'createdAt', 'actions']

export default function PaymentTable({ payments, compact = false, visibleColumns = defaultColumns, sort, onSort, selected, onSelect, onSelectAll, frozen = false, tableId = 'payment-table' }) {
  const navigate = useNavigate()
  const [columnWidths, setColumnWidths] = useState(() => storedWidths(tableId))
  const open = (id) => navigate(`/payments/${id}`)
  const selectable = selected instanceof Set && onSelect
  const columns = visibleColumns.map((key) => paymentTableColumns.find((column) => column.key === key)).filter(Boolean)
  const cell = (payment, key) => {
    if (key === 'id') return <span className="flex items-center gap-2"><span className="grid size-7 place-items-center rounded-lg bg-canvas text-primary transition group-hover:bg-primary group-hover:text-white"><ArrowUpRight className="size-3.5" /></span><strong className="text-xs text-ink">#{payment.id}</strong></span>
    if (key === 'reference') return <span className="block max-w-48 truncate text-ink-muted">{payment.reference || 'No reference'}</span>
    if (key === 'source') return <span className="font-mono text-[10px] text-ink">{masked(payment.sourceAccountNumber)}</span>
    if (key === 'destination') return <span className="font-mono text-[10px] text-ink">{masked(payment.destinationAccountNumber)}</span>
    if (key === 'amount') return <span><strong className="block tabular-nums text-ink">{formatMoney(payment.amount, payment.currency)}</strong><small className="text-[9px] font-bold text-ink-muted">{payment.currency}</small></span>
    if (key === 'converted') return payment.destinationAmount ? <span><strong className="block tabular-nums text-ink">{formatMoney(payment.destinationAmount, payment.destinationCurrency)}</strong><small className="text-[9px] text-ink-muted">at {payment.exchangeRate}</small></span> : '—'
    if (key === 'status') return <StatusBadge status={payment.status} />
    if (key === 'createdAt') return <span className="text-ink-muted">{formatDate(payment.createdAt)}</span>
    if (key === 'updatedAt') return <span className="text-ink-muted">{formatDate(payment.updatedAt)}</span>
    if (key === 'exchangeRate') return <span className="tabular-nums text-ink-muted">{payment.exchangeRate || '1.0000'}</span>
    return <button onClick={(event) => { event.stopPropagation(); open(payment.id) }} className="text-ink-muted hover:text-primary" aria-label={`Open payment ${payment.id}`}><ChevronRight className="size-4" /></button>
  }
  return <>
    <div className="hidden max-h-[650px] overflow-auto md:block"><table className="w-full min-w-[760px] table-fixed text-left text-xs"><thead className="sticky top-0 z-20 bg-canvas"><tr className="border-b border-line text-[9px] font-bold uppercase tracking-wider text-ink-muted">{selectable && <th className="w-10 px-3 py-2.5"><input type="checkbox" checked={payments.length > 0 && payments.every((row) => selected.has(row.id))} onChange={onSelectAll} aria-label="Select all visible payments" /></th>}{columns.map((column, index) => <th key={column.key} style={columnWidths[column.key] ? { width: columnWidths[column.key] } : undefined} className={`relative px-3 ${compact ? 'py-2' : 'py-2.5'} ${frozen && index === 0 ? `sticky ${selectable ? 'left-10' : 'left-0'} z-20 bg-canvas` : ''}`}><button className="flex items-center gap-1 text-left" onClick={() => column.key !== 'actions' && onSort?.(column.key)}>{column.label}{sort?.key === column.key && (sort.direction === 'asc' ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />)}</button><span className="absolute inset-y-1 right-0 w-1 cursor-col-resize rounded-full hover:bg-primary/40" onPointerDown={(event) => beginColumnResize(event, tableId, column.key, setColumnWidths)} aria-hidden="true" /></th>)}</tr></thead><tbody>{payments.map((payment) => <tr key={payment.id} tabIndex="0" onClick={() => open(payment.id)} onKeyDown={(event) => event.key === 'Enter' && open(payment.id)} className="group cursor-pointer border-b border-line/80 transition last:border-0 hover:bg-primary-light/40 focus:bg-primary-light/40 focus:outline-none">{selectable && <td className="px-3 py-2.5" onClick={(event) => event.stopPropagation()}><input type="checkbox" checked={selected.has(payment.id)} onChange={() => onSelect(payment.id)} aria-label={`Select payment ${payment.id}`} /></td>}{columns.map((column, index) => <td key={column.key} className={`truncate px-3 ${compact ? 'py-2' : 'py-2.5'} ${frozen && index === 0 ? `sticky ${selectable ? 'left-10' : 'left-0'} z-10 bg-surface group-hover:bg-primary-light` : ''}`}>{cell(payment, column.key)}</td>)}</tr>)}</tbody></table></div>
    <div className="divide-y divide-line md:hidden">{payments.map((payment) => <div key={payment.id} className="flex gap-3 p-3">{selectable && <input type="checkbox" checked={selected.has(payment.id)} onChange={() => onSelect(payment.id)} aria-label={`Select payment ${payment.id}`} />}<button onClick={() => open(payment.id)} className="min-w-0 flex-1 text-left"><div className="flex items-start justify-between gap-2"><div><p className="text-xs font-semibold text-ink">Payment #{payment.id}</p><p className="mt-0.5 max-w-48 truncate text-[10px] text-ink-muted">{payment.reference || 'No reference'}</p></div><StatusBadge status={payment.status} /></div><div className="mt-2 flex items-end justify-between"><p className="text-xs font-semibold text-ink">{formatMoney(payment.amount, payment.currency)}</p><p className="text-[9px] text-ink-muted">{formatDate(payment.createdAt)}</p></div></button></div>)}</div>
  </>
}
