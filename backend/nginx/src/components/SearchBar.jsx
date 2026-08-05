import { Search, X } from 'lucide-react'

export default function SearchBar({ value, onChange, placeholder = 'Search payments' }) {
  return (
    <div className="relative min-w-0 flex-1">
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" />
      <input className="input pl-10 pr-9" type="search" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} aria-label={placeholder} />
      {value && <button className="absolute right-3 top-1/2 -translate-y-1/2 rounded text-ink-muted hover:text-ink" onClick={() => onChange('')} aria-label="Clear search"><X className="size-4" /></button>}
    </div>
  )
}

