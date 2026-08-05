export function formatMoney(amount, currency = 'USD') {
  if (amount === null || amount === undefined) return '—'
  try {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(Number(amount))
  } catch {
    return `${currency} ${Number(amount).toLocaleString()}`
  }
}

export function formatDate(value, options = {}) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', ...options,
  }).format(new Date(value))
}

export const statusMeta = {
  COMPLETED: { label: 'Completed', className: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  FAILED: { label: 'Failed', className: 'bg-red-50 text-red-700 ring-red-200' },
  SENT: { label: 'Sent', className: 'bg-blue-50 text-blue-700 ring-blue-200' },
  VALIDATED: { label: 'Validated', className: 'bg-amber-50 text-amber-700 ring-amber-200' },
  CREATED: { label: 'Created', className: 'bg-slate-100 text-slate-700 ring-slate-200' },
}
