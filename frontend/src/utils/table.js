const text = (value) => value === null || value === undefined ? '' : String(value)

export function storedColumns(key, fallback) {
  try {
    const saved = JSON.parse(localStorage.getItem(`table-columns:${key}`))
    if (Array.isArray(saved) && saved.some((column) => fallback.includes(column))) {
      return [...saved.filter((column) => fallback.includes(column)), ...fallback.filter((column) => !saved.includes(column))]
    }
  } catch { /* Ignore an invalid local preference. */ }
  return fallback
}

export function saveColumns(key, columns) {
  localStorage.setItem(`table-columns:${key}`, JSON.stringify(columns))
}

export function storedWidths(key) {
  try {
    const saved = JSON.parse(localStorage.getItem(`table-widths:${key}`))
    return saved && typeof saved === 'object' && !Array.isArray(saved) ? saved : {}
  } catch { return {} }
}

export function beginColumnResize(event, tableKey, columnKey, setWidths) {
  event.preventDefault()
  const header = event.currentTarget.closest('th')
  const startX = event.clientX
  const startWidth = header?.getBoundingClientRect().width || 120
  let latest = startWidth
  const move = (moveEvent) => {
    latest = Math.max(84, Math.round(startWidth + moveEvent.clientX - startX))
    setWidths((current) => ({ ...current, [columnKey]: latest }))
  }
  const stop = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', stop)
    setWidths((current) => {
      const next = { ...current, [columnKey]: latest }
      localStorage.setItem(`table-widths:${tableKey}`, JSON.stringify(next))
      return next
    })
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop, { once: true })
}

function values(rows, columns) {
  return rows.map((row) => columns.map((column) => text(column.exportValue ? column.exportValue(row) : row[column.key])))
}

function download(contents, filename, type) {
  const url = URL.createObjectURL(new Blob([contents], { type }))
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = filename; anchor.click()
  URL.revokeObjectURL(url)
}

export function exportCsv(rows, columns, filename) {
  const allRows = [columns.map((column) => column.label), ...values(rows, columns)]
  const csv = allRows.map((row) => row.map((value) => `"${value.replaceAll('"', '""')}"`).join(',')).join('\n')
  download(csv, `${filename}.csv`, 'text/csv;charset=utf-8')
}

export function exportExcel(rows, columns, filename) {
  const header = columns.map((column) => `<th>${column.label}</th>`).join('')
  const body = values(rows, columns).map((row) => `<tr>${row.map((value) => `<td>${value.replaceAll('&', '&amp;').replaceAll('<', '&lt;')}</td>`).join('')}</tr>`).join('')
  download(`<html><head><meta charset="utf-8"></head><body><table><thead><tr>${header}</tr></thead><tbody>${body}</tbody></table></body></html>`, `${filename}.xls`, 'application/vnd.ms-excel')
}

export async function copyTable(rows, columns) {
  const allRows = [columns.map((column) => column.label), ...values(rows, columns)]
  await navigator.clipboard.writeText(allRows.map((row) => row.join('\t')).join('\n'))
}

export function sortRows(rows, sort, columns) {
  if (!sort?.key) return rows
  const column = columns.find((item) => item.key === sort.key)
  if (!column) return rows
  return [...rows].sort((left, right) => {
    const a = column.sortValue ? column.sortValue(left) : left[column.key]
    const b = column.sortValue ? column.sortValue(right) : right[column.key]
    const compared = typeof a === 'number' && typeof b === 'number'
      ? a - b : String(a ?? '').localeCompare(String(b ?? ''), undefined, { numeric: true })
    return sort.direction === 'desc' ? -compared : compared
  })
}
