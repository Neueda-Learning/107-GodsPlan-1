export default function LoadingSkeleton({ rows = 4 }) {
  return (
    <div className="animate-pulse space-y-4 p-5" aria-label="Loading">
      {Array.from({ length: rows }, (_, index) => <div key={index} className="h-14 rounded-lg bg-slate-100" />)}
    </div>
  )
}

