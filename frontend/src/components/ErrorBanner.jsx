import { AlertTriangle } from 'lucide-react'

export default function ErrorBanner({ code, description, title = 'This payment failed' }) {
  return (
    <div className="flex gap-3 rounded-card border border-red-200 bg-red-50 p-4" role="alert">
      <AlertTriangle className="mt-0.5 size-5 shrink-0 text-danger" />
      <div><h3 className="font-semibold text-red-800">{title}</h3>
        {code && <p className="mt-1 text-xs font-bold uppercase tracking-wide text-red-700">{code.replaceAll('_', ' ')}</p>}
        <p className="mt-1 text-sm text-red-700">{description || 'The payment could not be processed.'}</p></div>
    </div>
  )
}

