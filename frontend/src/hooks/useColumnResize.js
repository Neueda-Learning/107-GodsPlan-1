import { useEffect, useRef } from 'react'

export function useColumnResize(storageKey) {
  const rootRef = useRef(null)

  useEffect(() => {
    const root = rootRef.current
    if (!root) return undefined
    let widths = {}
    try { widths = JSON.parse(localStorage.getItem(`table-native-widths:${storageKey}`)) || {} } catch { widths = {} }

    const columnKey = (header, index) => header.textContent.trim() || `column-${index}`
    const apply = () => root.querySelectorAll('th').forEach((header, index) => {
      const key = columnKey(header, index)
      if (widths[key]) header.style.width = `${widths[key]}px`
    })
    apply()
    const observer = new MutationObserver(apply)
    observer.observe(root, { childList: true, subtree: true })

    const start = (event) => {
      const handle = event.target.closest('.resize-x')
      if (!handle || !root.contains(handle)) return
      event.preventDefault()
      const header = handle.closest('th')
      const headers = [...header.parentElement.children]
      const index = headers.indexOf(header)
      const startX = event.clientX
      const startWidth = header.getBoundingClientRect().width
      const move = (moveEvent) => { header.style.width = `${Math.max(84, startWidth + moveEvent.clientX - startX)}px` }
      const stop = () => {
        window.removeEventListener('pointermove', move)
        window.removeEventListener('pointerup', stop)
        widths[columnKey(header, index)] = Math.round(header.getBoundingClientRect().width)
        localStorage.setItem(`table-native-widths:${storageKey}`, JSON.stringify(widths))
      }
      window.addEventListener('pointermove', move)
      window.addEventListener('pointerup', stop, { once: true })
    }
    root.addEventListener('pointerdown', start)
    return () => { observer.disconnect(); root.removeEventListener('pointerdown', start) }
  }, [storageKey])

  return rootRef
}
