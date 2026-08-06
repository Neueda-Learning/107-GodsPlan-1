export default function LoadingSkeleton({ rows = 4 }) {
  return (
    <div className="space-y-4 p-5" aria-label="Loading" role="status">
      {Array.from({ length: rows }, (_, index) => <div key={index} className={`skeleton-shimmer rounded-xl ${index === 0 ? 'h-8 w-2/5' : 'h-14 w-full'}`} />)}
      <span className="sr-only">Loading content</span>
    </div>
  )
}
