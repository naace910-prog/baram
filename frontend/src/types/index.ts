export type MemberRole = 'MASTER' | 'VICE' | 'MEMBER'
export type RaidStatus = 'PLANNED' | 'DONE' | 'CANCELLED'
export type VoteType = 'YES' | 'NO' | 'MAYBE'

export interface AuthUser {
  memberId: number
  account: string
  nickname: string
  role: MemberRole
}

export interface Member {
  id: number
  account: string
  nickname: string
  role: MemberRole
  discordUserId?: string | null
  active: boolean
  joinedAt: string
}

export interface RaidTarget {
  id: number
  name: string
  dropItemName: string
  memo?: string | null
}

export interface RaidListItem {
  id: number
  targetId: number
  targetName: string
  dropItemName: string
  scheduledAt: string
  status: RaidStatus
  memo?: string | null
  yesCount: number
  noCount: number
  maybeCount: number
}

export interface RaidVote {
  memberId: number
  nickname: string
  vote: VoteType
  votedAt: string
}

export interface RaidDetail {
  id: number
  targetId: number
  targetName: string
  dropItemName: string
  scheduledAt: string
  status: RaidStatus
  memo?: string | null
  votes: RaidVote[]
  attendees: number[]
}

export interface LootShare {
  id: number
  memberId: number
  nickname: string
  share: number
  paid: boolean
  paidAt?: string | null
}

export interface Loot {
  id: number
  raidId: number
  itemName: string
  dropped: boolean
  soldPrice?: number | null
  soldAt?: string | null
  memo?: string | null
  shares: LootShare[]
}
