import { CheckCircle2, X, XCircle } from 'lucide-react'
import { useCallback, useState } from 'react'
import { ToastContext } from '../hooks/useToast'

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const dismiss = useCallback((id) => setToasts((items) => items.filter((item) => item.id !== id)), [])
  const notify = useCallback((message, type = 'success') => {
    const id = crypto.randomUUID()
    setToasts((items) => [...items, { id, message, type }])
    window.setTimeout(() => dismiss(id), 4500)
  }, [dismiss])

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="fixed right-4 top-4 z-[70] flex w-[calc(100%-2rem)] max-w-sm flex-col gap-3" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className="flex items-start gap-3 rounded-card border border-line bg-white p-4 shadow-card">
            {toast.type === 'error'
              ? <XCircle className="mt-0.5 size-5 shrink-0 text-danger" aria-hidden="true" />
              : <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-success" aria-hidden="true" />}
            <p className="flex-1 text-sm font-medium text-ink">{toast.message}</p>
            <button onClick={() => dismiss(toast.id)} className="rounded p-0.5 text-ink-muted hover:text-ink" aria-label="Dismiss notification">
              <X className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

