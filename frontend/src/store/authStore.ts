import { create } from 'zustand'
import type { AuthUser } from '@/types'
import { authApi } from '@/api/client'

interface State {
  user: AuthUser | null
  loading: boolean
  fetchMe: () => Promise<void>
  login: (account: string, password: string) => Promise<AuthUser>
  logout: () => Promise<void>
  setUser: (u: AuthUser | null) => void
}

export const useAuth = create<State>((set) => ({
  user: null,
  loading: true,
  fetchMe: async () => {
    try {
      const u = await authApi.me()
      set({ user: u, loading: false })
    } catch {
      set({ user: null, loading: false })
    }
  },
  login: async (account, password) => {
    const u = await authApi.login(account, password)
    set({ user: u })
    return u
  },
  logout: async () => {
    await authApi.logout()
    set({ user: null })
  },
  setUser: (u) => set({ user: u })
}))

export const isMaster = (u: AuthUser | null) => u?.role === 'MASTER' || u?.role === 'VICE'
