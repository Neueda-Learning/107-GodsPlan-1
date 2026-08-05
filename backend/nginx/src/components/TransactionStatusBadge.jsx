import { CheckCircle2, Clock3, XCircle } from 'lucide-react'

const styles = {
  SUCCESSFUL: { label: 'Successful', icon: CheckCircle2, className: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  FAILED: { label: 'Failed', icon: XCircle, className: 'bg-red-50 text-red-700 ring-red-200' },
  PENDING: { label: 'Pending', icon: Clock3, className: 'bg-amber-50 text-amber-700 ring-amber-200' },
}

export default function TransactionStatusBadge({ outcome }) {
  const status = styles[outcome] || styles.PENDING
  const Icon = status.icon
  return <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${status.className}`}>
    <Icon className="size-3.5" />{status.label}
  </span>
}

