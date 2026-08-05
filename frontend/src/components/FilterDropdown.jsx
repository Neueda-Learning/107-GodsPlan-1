import { Filter } from 'lucide-react'

export default function FilterDropdown({ value, onChange }) {
  return (
    <label className="relative block sm:w-48">
      <span className="sr-only">Filter by status</span>
      <Filter className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
      <select className="input appearance-none pl-10" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">All statuses</option>
        <option value="COMPLETED">Completed</option>
        <option value="FAILED">Failed</option>
        <option value="SENT">Sent</option>
        <option value="VALIDATED">Validated</option>
        <option value="CREATED">Created</option>
      </select>
    </label>
  )
}

