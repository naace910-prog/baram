import { Navigate } from 'react-router-dom'
import { useAuth } from '@/store/authStore'
import type { ReactNode } from 'react'

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const user = useAuth((s) => s.user)
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}
