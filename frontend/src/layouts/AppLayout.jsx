import { useCallback, useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import CreatePaymentModal from '../components/CreatePaymentModal'
import Sidebar from '../components/Sidebar'
import Topbar from '../components/Topbar'

export default function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [refreshToken, setRefreshToken] = useState(0)
  const navigate = useNavigate()
  const closeModal = useCallback(() => setModalOpen(false), [])
  const created = (payment) => {
    setModalOpen(false)
    setRefreshToken((value) => value + 1)
    navigate(`/payments/${payment.id}`)
  }
  return (
    <div className="min-h-screen bg-canvas">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <div className="min-h-screen md:pl-20 lg:pl-64">
        <Topbar onMenu={() => setMenuOpen(true)} onCreate={() => setModalOpen(true)} />
        <main className="p-4 sm:p-6 lg:p-8"><Outlet context={{ refreshToken, openCreate: () => setModalOpen(true) }} /></main>
      </div>
      {modalOpen && <CreatePaymentModal onClose={closeModal} onCreated={created} />}
    </div>
  )
}
