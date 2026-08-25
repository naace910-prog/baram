import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import { useAuth } from '@/store/authStore'
import AppLayout from '@/components/AppLayout'
import ProtectedRoute from '@/components/ProtectedRoute'
import LoginPage from '@/pages/LoginPage'
import DashboardPage from '@/pages/DashboardPage'
import RaidListPage from '@/pages/RaidListPage'
import RaidDetailPage from '@/pages/RaidDetailPage'
import RaidCreatePage from '@/pages/RaidCreatePage'
import MemberManagePage from '@/pages/MemberManagePage'
import TargetManagePage from '@/pages/TargetManagePage'
import StatsPage from '@/pages/StatsPage'
import PartyRolesPage from '@/pages/PartyRolesPage'
import RaidPartyPage from '@/pages/RaidPartyPage'
import ChatPage from '@/pages/ChatPage'
import SettingsPage from '@/pages/SettingsPage'

export default function App() {
  const { fetchMe, loading } = useAuth()
  useEffect(() => { fetchMe() }, [fetchMe])

  if (loading) {
    return (
      <div style={{ display: 'flex', height: '100vh', justifyContent: 'center', alignItems: 'center' }}>
        <Spin size="large" />
      </div>
    )
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/raids" element={<RaidListPage />} />
        <Route path="/raids/new" element={<RaidCreatePage />} />
        <Route path="/raids/:id" element={<RaidDetailPage />} />
        <Route path="/raids/:id/parties" element={<RaidPartyPage />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/stats" element={<StatsPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/members" element={<MemberManagePage />} />
        <Route path="/targets" element={<TargetManagePage />} />
        <Route path="/party-roles" element={<PartyRolesPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
