import { useCallback, useLayoutEffect, useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import CreatePaymentModal from '../components/CreatePaymentModal'
import Sidebar from '../components/Sidebar'
import Topbar from '../components/Topbar'

function initialTheme() {
  const saved = localStorage.getItem('payments-theme')
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export default function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => localStorage.getItem('payments-sidebar') === 'collapsed')
  const [theme, setTheme] = useState(initialTheme)
  const [modalOpen, setModalOpen] = useState(false)
  const [refreshToken, setRefreshToken] = useState(0)
  const navigate = useNavigate()
  useLayoutEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    localStorage.setItem('payments-theme', theme)
  }, [theme])

  const closeModal = useCallback(() => setModalOpen(false), [])
  const toggleSidebar = () => {
    setSidebarCollapsed((value) => {
      localStorage.setItem('payments-sidebar', value ? 'expanded' : 'collapsed')
      return !value
    })
  }
  const created = (payment) => {
    setModalOpen(false)
    setRefreshToken((value) => value + 1)
    navigate(`/payments/${payment.id}`)
  }
  return (
    <div className="min-h-screen bg-canvas text-ink transition-colors duration-300">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} collapsed={sidebarCollapsed} onCollapse={toggleSidebar} />
      <div className={`min-h-screen transition-[padding] duration-300 ${sidebarCollapsed ? 'md:pl-[88px]' : 'md:pl-[272px]'}`}>
        <Topbar onMenu={() => setMenuOpen(true)} onCreate={() => setModalOpen(true)} theme={theme} onTheme={setTheme} />
        <main className="p-3 sm:p-4 lg:p-5 xl:p-6"><Outlet context={{ refreshToken, openCreate: () => setModalOpen(true) }} /></main>
      </div>
      {modalOpen && <CreatePaymentModal onClose={closeModal} onCreated={created} />}
    </div>
  )
}
