import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const Payments = lazy(() => import('./pages/Payments'))
const PaymentDetails = lazy(() => import('./pages/PaymentDetails'))

export default function App() {
  return (
    <Suspense fallback={<div className="grid min-h-screen place-items-center bg-canvas text-sm text-ink-muted">Loading workspace…</div>}><Routes>
      <Route element={<AppLayout />}>
        <Route index element={<Dashboard />} />
        <Route path="payments" element={<Payments />} />
        <Route path="payments/:id" element={<PaymentDetails />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes></Suspense>
  )
}
