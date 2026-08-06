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

export function maskIdentifier(value) {
  if (value === null || value === undefined || value === '') return '—'
  const compact = String(value).replace(/[\s-]/g, '')
  return `XXXX ${compact.slice(-4).padStart(4, 'X')}`
}

export const statusMeta = {
  COMPLETED: { label: 'Completed', className: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-950 dark:text-emerald-300 dark:ring-emerald-800' },
  FAILED: { label: 'Failed', className: 'bg-red-50 text-red-700 ring-red-200 dark:bg-red-950 dark:text-red-300 dark:ring-red-800' },
  SENT: { label: 'Sent', className: 'bg-blue-50 text-blue-700 ring-blue-200 dark:bg-blue-950 dark:text-blue-300 dark:ring-blue-800' },
  VALIDATED: { label: 'Validated', className: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950 dark:text-amber-300 dark:ring-amber-800' },
  CREATED: { label: 'Created', className: 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700' },
}
