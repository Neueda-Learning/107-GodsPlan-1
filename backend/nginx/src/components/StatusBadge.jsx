import { CheckCircle2, CircleDot, Send, ShieldCheck, XCircle } from 'lucide-react'
import { statusMeta } from '../utils/format'

const icons = { COMPLETED: CheckCircle2, FAILED: XCircle, SENT: Send, VALIDATED: ShieldCheck, CREATED: CircleDot }

export default function StatusBadge({ status }) {
  const meta = statusMeta[status] || statusMeta.CREATED
  const Icon = icons[status] || CircleDot
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${meta.className}`}>
      <Icon className="size-3.5" aria-hidden="true" />{meta.label}
    </span>
  )
}

