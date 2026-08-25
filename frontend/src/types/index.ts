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
  icon?: string | null
  memo?: string | null
}

export interface RaidListItem {
  id: number
  targetId: number
  targetName: string
  targetIcon?: string | null
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
  targetIcon?: string | null
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

export interface StatsOverview {
  totalMembers: number
  plannedRaids: number
  doneRaids: number
  totalRevenue: number
  unpaidTotal: number
}

export interface MemberStat {
  memberId: number
  nickname: string
  attendCount: number
  totalShare: number
  unpaidShare: number
  unpaidCount: number
}

export interface TargetStat {
  targetId: number
  name: string
  icon?: string | null
  dropItemName: string
  killCount: number
  totalSoldPrice: number
  avgSoldPrice: number
}

export interface MonthlyBucket {
  yearMonth: string   // "2026-08"
  killCount: number
  revenue: number
}

export interface StatsResult {
  overview: StatsOverview
  members: MemberStat[]
  targets: TargetStat[]
  monthly: MonthlyBucket[]
}

export type ChannelType = 'MAIN' | 'INVADE'

export interface PartyRole {
  id: number
  name: string
  icon?: string | null
  displayOrder: number
  active: boolean
}

export interface PartyMemberView {
  id: number
  role: string
  memberId?: number | null
  freeName?: string | null
  nickname: string
  displayOrder: number
}

export interface PartyView {
  id: number
  raidId: number
  channelType: ChannelType
  channelNumber?: number | null
  memo?: string | null
  mikeMemberId?: number | null
  mikeFreeName?: string | null
  mikeNickname?: string | null
  displayOrder: number
  members: PartyMemberView[]
}

export interface PartyMemberEntry {
  role: string
  memberId?: number | null
  freeName?: string | null
}
