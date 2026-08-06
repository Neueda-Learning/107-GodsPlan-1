import { Bookmark, Check, ChevronDown, ChevronLeft, ChevronRight, Clipboard, Columns3, Download, FileSpreadsheet, RefreshCw, Save, Search, SlidersHorizontal, X } from 'lucide-react'
import { useState } from 'react'
import { copyTable, exportCsv, exportExcel, saveColumns } from '../utils/table'

function MultiFilter({ filter }) {
  const values = filter.value || []
  return <details className="group relative">
    <summary className="flex h-9 cursor-pointer list-none items-center gap-2 rounded-lg border border-line bg-surface px-3 text-xs font-medium text-ink-muted transition hover:bg-canvas hover:text-ink"><span>{filter.label}</span>{values.length > 0 && <span className="rounded-full bg-primary px-1.5 py-0.5 text-[9px] font-bold text-white">{values.length}</span>}<ChevronDown className="size-3 transition group-open:rotate-180" /></summary>
    <div className="absolute left-0 top-11 z-40 min-w-48 rounded-xl border border-line bg-elevated p-2 shadow-soft">{filter.options.map((option) => {
      const selected = values.includes(option.value)
      return <label key={option.value} className="flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-2 text-xs text-ink transition hover:bg-canvas"><input className="sr-only" type="checkbox" checked={selected} onChange={() => filter.onChange(selected ? values.filter((value) => value !== option.value) : [...values, option.value])} /><span className={`grid size-4 place-items-center rounded border ${selected ? 'border-primary bg-primary text-white' : 'border-line'}`}>{selected && <Check className="size-3" />}</span>{option.label}</label>
    })}</div>
  </details>
}

export default function TableToolbar({
  id, search, onSearch, filters = [], advanced, hasFilters, onClear, onRefresh, busy,
  rows = [], columns = [], visibleColumns = [], onColumns, frozen, onFrozen, selectedCount = 0,
}) {
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const [presetAvailable, setPresetAvailable] = useState(() => Boolean(localStorage.getItem(`table-preset:${id}`)))
  const selectedColumns = visibleColumns.map((key) => columns.find((column) => column.key === key)).filter(Boolean)
  const move = (key, delta) => {
    const next = [...visibleColumns]; const index = next.indexOf(key); const target = index + delta
    if (index < 0 || target < 0 || target >= next.length) return
    ;[next[index], next[target]] = [next[target], next[index]]; onColumns(next)
  }
  const toggle = (key) => onColumns(visibleColumns.includes(key)
    ? visibleColumns.filter((item) => item !== key)
    : [...visibleColumns, key])
  const announce = (message) => { setNotice(message); window.setTimeout(() => setNotice(''), 1800) }
  const save = () => { saveColumns(id, visibleColumns); announce('Layout saved') }
  const copy = async () => { await copyTable(rows, selectedColumns); announce('Rows copied') }
  const savePreset = () => {
    localStorage.setItem(`table-preset:${id}`, JSON.stringify({ search, filters: Object.fromEntries(filters.map((filter) => [filter.key, filter.value])) }))
    setPresetAvailable(true); announce('Filter preset saved')
  }
  const loadPreset = () => {
    try {
      const preset = JSON.parse(localStorage.getItem(`table-preset:${id}`))
      onSearch(preset.search || '')
      filters.forEach((filter) => filter.onChange(preset.filters?.[filter.key] || []))
      announce('Filter preset loaded')
    } catch { announce('Saved preset is unavailable') }
  }

  return <div className="border-b border-line bg-surface">
    <div className="flex flex-wrap items-center gap-2 p-3">
      <label className="relative min-w-48 flex-1 lg:max-w-sm"><span className="sr-only">Search table</span><Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-ink-muted" /><input className="input h-9 rounded-lg pl-9 text-xs" value={search} onChange={(event) => onSearch(event.target.value)} placeholder="Search records…" />{search && <button className="absolute right-2 top-2 rounded p-0.5 text-ink-muted hover:text-ink" onClick={() => onSearch('')} aria-label="Clear search"><X className="size-3.5" /></button>}</label>
      {filters.map((filter) => <MultiFilter key={filter.key} filter={filter} />)}
      {advanced && <button className={`flex h-9 items-center gap-2 rounded-lg border px-3 text-xs font-medium transition ${advancedOpen ? 'border-primary bg-primary-light text-primary' : 'border-line bg-surface text-ink-muted hover:bg-canvas hover:text-ink'}`} onClick={() => setAdvancedOpen((value) => !value)}><SlidersHorizontal className="size-3.5" />Advanced</button>}
      {hasFilters && <button className="h-9 px-2 text-xs font-semibold text-primary hover:text-primary-hover" onClick={onClear}>Clear all</button>}
      <div className="ml-auto flex items-center gap-1.5">
        {selectedCount > 0 && <span className="mr-1 rounded-lg bg-primary-light px-2.5 py-1.5 text-[10px] font-bold text-primary">{selectedCount} selected</span>}
        <button className="icon-button size-9 rounded-lg" onClick={onRefresh} disabled={busy} title="Refresh" aria-label="Refresh table"><RefreshCw className={`size-3.5 ${busy ? 'animate-spin' : ''}`} /></button>
        {hasFilters && <button className="icon-button size-9 rounded-lg" onClick={savePreset} title="Save filter preset" aria-label="Save filter preset"><Bookmark className="size-3.5" /></button>}
        {presetAvailable && <button className="hidden h-9 rounded-lg border border-line px-2 text-[10px] font-semibold text-ink-muted hover:bg-canvas sm:block" onClick={loadPreset}>Load view</button>}
        <details className="group relative"><summary className="icon-button size-9 cursor-pointer list-none rounded-lg" title="Export" aria-label="Export table"><Download className="size-3.5" /></summary><div className="absolute right-0 top-11 z-40 w-44 rounded-xl border border-line bg-elevated p-1.5 shadow-soft"><button className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs text-ink hover:bg-canvas" onClick={() => exportCsv(rows, selectedColumns, id)}><Download className="size-3.5" />Export CSV</button><button className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs text-ink hover:bg-canvas" onClick={() => exportExcel(rows, selectedColumns, id)}><FileSpreadsheet className="size-3.5" />Export Excel</button><button className="flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs text-ink hover:bg-canvas" onClick={copy}><Clipboard className="size-3.5" />Copy rows</button></div></details>
        <details className="group relative"><summary className="icon-button size-9 cursor-pointer list-none rounded-lg" title="Column settings" aria-label="Column settings"><Columns3 className="size-3.5" /></summary><div className="absolute right-0 top-11 z-40 w-72 rounded-xl border border-line bg-elevated p-2 shadow-soft"><div className="flex items-center justify-between px-2 py-1"><p className="text-xs font-semibold text-ink">Columns</p><button className="flex items-center gap-1 text-[10px] font-semibold text-primary" onClick={save}><Save className="size-3" />Save layout</button></div><div className="mt-1 max-h-72 space-y-0.5 overflow-auto">{columns.map((column) => { const visible = visibleColumns.includes(column.key); return <div key={column.key} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-canvas"><button onClick={() => toggle(column.key)} className={`grid size-4 place-items-center rounded border ${visible ? 'border-primary bg-primary text-white' : 'border-line'}`} aria-label={`${visible ? 'Hide' : 'Show'} ${column.label}`}>{visible && <Check className="size-3" />}</button><span className="flex-1 truncate text-xs text-ink">{column.label}</span><button className="p-1 text-ink-muted disabled:opacity-25" disabled={!visible || visibleColumns.indexOf(column.key) === 0} onClick={() => move(column.key, -1)} aria-label={`Move ${column.label} left`}><ChevronLeft className="size-3.5" /></button><button className="p-1 text-ink-muted disabled:opacity-25" disabled={!visible || visibleColumns.indexOf(column.key) === visibleColumns.length - 1} onClick={() => move(column.key, 1)} aria-label={`Move ${column.label} right`}><ChevronRight className="size-3.5" /></button></div>})}</div><label className="mt-2 flex items-center gap-2 border-t border-line px-2 pt-2 text-xs text-ink"><input type="checkbox" checked={frozen} onChange={(event) => onFrozen(event.target.checked)} />Freeze first column</label></div></details>
      </div>
    </div>
    {hasFilters && <div className="flex flex-wrap items-center gap-1.5 border-t border-line px-3 py-2">{search && <button className="flex items-center gap-1 rounded-full bg-primary-light px-2 py-1 text-[9px] font-semibold text-primary" onClick={() => onSearch('')}>Search: {search}<X className="size-2.5" /></button>}{filters.flatMap((filter) => (filter.value || []).map((value) => { const label = filter.options.find((option) => option.value === value)?.label || value; return <button key={`${filter.key}-${value}`} className="flex items-center gap-1 rounded-full bg-canvas px-2 py-1 text-[9px] font-semibold text-ink-muted" onClick={() => filter.onChange(filter.value.filter((item) => item !== value))}>{filter.label}: {label}<X className="size-2.5" /></button> }))}</div>}
    {advancedOpen && advanced && <div className="border-t border-line bg-canvas/60 p-3">{advanced}</div>}
    {notice && <div className="absolute right-6 z-50 rounded-lg bg-slate-950 px-3 py-2 text-xs font-semibold text-white shadow-soft" role="status">{notice}</div>}
  </div>
}
