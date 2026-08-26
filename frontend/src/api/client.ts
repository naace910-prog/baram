import axios from 'axios'
import { message } from 'antd'
import type {
  AuthUser, Member, RaidTarget, RaidListItem, RaidDetail, Loot, VoteType, RaidStatus, MemberRole, StatsResult,
  PartyRole, PartyView, PartyMemberEntry, ChannelType, ChatMessage, RaidCategory, BulkDropEntry
} from '@/types'

export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

http.interceptors.response.use(
  (r) => r,
  (err) => {
    if (err.response?.status === 401) {
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else {
      const msg = err.response?.data?.error || err.message || '요청 실패'
      message.error(msg)
    }
    return Promise.reject(err)
  }
)

export const authApi = {
  login: (account: string, password: string) =>
    http.post<AuthUser>('/auth/login', { account, password }).then((r) => r.data),
  logout: () => http.post('/auth/logout').then(() => true),
  me: () => http.get<AuthUser>('/auth/me').then((r) => r.data),
  changePassword: (currentPassword: string, newPassword: string) =>
    http.post('/auth/change-password', { currentPassword, newPassword }).then(() => true),
  changeNickname: (nickname: string) =>
    http.post<{ result: string; nickname: string }>('/auth/change-nickname', { nickname }).then((r) => r.data),
  discordAuthorizeUrl: () =>
    http.get<{ enabled: boolean; url?: string }>('/auth/discord/authorize-url').then((r) => r.data),
}

export const memberApi = {
  list: (includeInactive = false) =>
    http.get<Member[]>('/members', { params: { includeInactive } }).then((r) => r.data),
  create: (body: { account: string; password: string; nickname: string; role: MemberRole; discordUserId?: string }) =>
    http.post<Member>('/members', body).then((r) => r.data),
  update: (id: number, body: { nickname: string; role: MemberRole; discordUserId?: string; active?: boolean }) =>
    http.put<Member>(`/members/${id}`, body).then((r) => r.data),
  resetPassword: (id: number, newPassword: string) =>
    http.post(`/members/${id}/reset-password`, { newPassword }).then(() => true),
  setStarred: (id: number, starred: boolean) =>
    http.post<Member>(`/members/${id}/starred`, { starred }).then((r) => r.data),
}

export const targetApi = {
  list: () => http.get<RaidTarget[]>('/targets').then((r) => r.data),
  create: (body: { name: string; dropItemName: string; icon?: string; category?: RaidCategory; memo?: string }) =>
    http.post<RaidTarget>('/targets', body).then((r) => r.data),
  update: (id: number, body: { name: string; dropItemName: string; icon?: string; category?: RaidCategory; memo?: string }) =>
    http.put<RaidTarget>(`/targets/${id}`, body).then((r) => r.data),
  delete: (id: number) => http.delete(`/targets/${id}`).then(() => true),
}

export const raidApi = {
  list: () => http.get<RaidListItem[]>('/raids').then((r) => r.data),
  get: (id: number) => http.get<RaidDetail>(`/raids/${id}`).then((r) => r.data),
  create: (body: { category: RaidCategory; targetId?: number; scheduledAt: string; memo?: string }) =>
    http.post<RaidDetail>('/raids', body).then((r) => r.data),
  update: (id: number, body: { category?: RaidCategory; targetId?: number; scheduledAt: string; status: RaidStatus; memo?: string }) =>
    http.put<RaidDetail>(`/raids/${id}`, body).then((r) => r.data),
  delete: (id: number) => http.delete(`/raids/${id}`).then(() => true),
  vote: (id: number, vote: VoteType) =>
    http.post<RaidDetail>(`/raids/${id}/votes`, { vote }).then((r) => r.data),
  setAttendees: (id: number, memberIds: number[]) =>
    http.put<RaidDetail>(`/raids/${id}/attendees`, { memberIds }).then((r) => r.data),
  sendPre30Manual: (id: number) =>
    http.post<RaidDetail>(`/raids/${id}/send-pre30`).then((r) => r.data),
}

export const statsApi = {
  get: () => http.get<StatsResult>('/stats').then((r) => r.data),
}

export const chatApi = {
  recent: (limit = 100) =>
    http.get<ChatMessage[]>('/chat/messages', { params: { limit } }).then((r) => r.data),
  since: (since: number) =>
    http.get<ChatMessage[]>('/chat/messages', { params: { since } }).then((r) => r.data),
  send: (content: string) =>
    http.post<ChatMessage>('/chat/messages', { content }).then((r) => r.data),
}

export const adminApi = {
  resetLoots: () =>
    http.post<{ result: string; deletedShares: number; deletedLoots: number }>('/admin/reset-loots')
        .then((r) => r.data),
}

export const pushApi = {
  vapidKey: () =>
    http.get<{ enabled: boolean; publicKey: string }>('/push/vapid-key').then((r) => r.data),
  subscribe: (body: { endpoint: string; p256dh: string; auth: string }) =>
    http.post('/push/subscribe', body).then(() => true),
  unsubscribe: (endpoint: string) =>
    http.post('/push/unsubscribe', { endpoint }).then(() => true),
}

export const partyRoleApi = {
  list: (includeInactive = false) =>
    http.get<PartyRole[]>('/party-roles', { params: { includeInactive } }).then((r) => r.data),
  create: (body: { name: string; icon?: string; displayOrder?: number; active?: boolean }) =>
    http.post<PartyRole>('/party-roles', body).then((r) => r.data),
  update: (id: number, body: { name: string; icon?: string; displayOrder?: number; active?: boolean }) =>
    http.put<PartyRole>(`/party-roles/${id}`, body).then((r) => r.data),
  delete: (id: number) => http.delete(`/party-roles/${id}`).then(() => true),
}

export const partyApi = {
  list: (raidId: number) =>
    http.get<PartyView[]>(`/raids/${raidId}/parties`).then((r) => r.data),
  create: (raidId: number, body: { channelType: ChannelType; channelNumber?: number; memo?: string; mikeMemberId?: number; mikeFreeName?: string }) =>
    http.post<PartyView>(`/raids/${raidId}/parties`, body).then((r) => r.data),
  update: (partyId: number, body: { channelType: ChannelType; channelNumber?: number; memo?: string; mikeMemberId?: number; mikeFreeName?: string; displayOrder?: number }) =>
    http.put<PartyView>(`/parties/${partyId}`, body).then((r) => r.data),
  delete: (partyId: number) => http.delete(`/parties/${partyId}`).then(() => true),
  replaceMembers: (partyId: number, members: PartyMemberEntry[]) =>
    http.put<PartyView>(`/parties/${partyId}/members`, { members }).then((r) => r.data),
  autoAssign: (raidId: number) =>
    http.post<{
      basis: string
      previousRaidId: number
      previousScheduledAt: string
      carriedParties: number
      assignedMembers: number
      newcomerCount: number
      droppedFromPrev: number
      parties: PartyView[]
    }>(`/raids/${raidId}/parties/auto-assign`).then((r) => r.data),
}

export const lootApi = {
  list: (raidId: number) => http.get<Loot[]>(`/raids/${raidId}/loots`).then((r) => r.data),
  create: (raidId: number, body: { targetId?: number; itemName: string; dropped: boolean; soldPrice?: number; memo?: string }) =>
    http.post<Loot[]>(`/raids/${raidId}/loots`, body).then((r) => r.data),
  bulkAdd: (raidId: number, drops: BulkDropEntry[]) =>
    http.post<Loot[]>(`/raids/${raidId}/loots/bulk`, { drops }).then((r) => r.data),
  update: (raidId: number, lootId: number, body: { targetId?: number; itemName: string; dropped: boolean; soldPrice?: number; memo?: string }) =>
    http.put<Loot[]>(`/raids/${raidId}/loots/${lootId}`, body).then((r) => r.data),
  delete: (raidId: number, lootId: number) =>
    http.delete<Loot[]>(`/raids/${raidId}/loots/${lootId}`).then((r) => r.data),
  distribute: (raidId: number, lootId: number, memberIds: number[]) =>
    http.post<Loot[]>(`/raids/${raidId}/loots/${lootId}/distribute`, { memberIds }).then((r) => r.data),
  markPaid: (raidId: number, lootId: number, shareId: number, paid: boolean) =>
    http.post<Loot[]>(`/raids/${raidId}/loots/${lootId}/shares/${shareId}/paid`, { shareId, paid }).then((r) => r.data),
  updateShareAmount: (raidId: number, lootId: number, shareId: number, amount: number) =>
    http.put<Loot[]>(`/raids/${raidId}/loots/${lootId}/shares/${shareId}`, { amount }).then((r) => r.data),
}
