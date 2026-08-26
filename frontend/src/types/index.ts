export type MemberRole = 'MASTER' | 'VICE' | 'MEMBER'
export type RaidStatus = 'PLANNED' | 'DONE' | 'CANCELLED'
export type VoteType = 'YES' | 'NO' | 'MAYBE'
export type RaidCategory = 'SKULL_KING' | 'FANG'

export const CATEGORY_LABEL: Record<RaidCategory, string> = {
  SKULL_KING: '해골왕',
  FANG: '어금니',
}

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
  starred: boolean
  joinedAt: string
}

export interface RaidTarget {
  id: number
  name: string
  dropItemName: string
  icon?: string | null
  category?: RaidCategory | null
  memo?: string | null
}

export interface RaidListItem {
  id: number
  category?: RaidCategory | null
  targetId?: number | null
  targetName?: string | null
  targetIcon?: string | null
  dropItemName?: string | null
  scheduledAt: string
  status: RaidStatus
  memo?: string | null
  yesCount: number
  noCount: number
  maybeCount: number
  votes: RaidVote[]
  attendees: number[]
}

export interface RaidVote {
  memberId: number
  nickname: string
  vote: VoteType
  votedAt: string
}

export interface RaidDetail {
  id: number
  category?: RaidCategory | null
  targetId?: number | null
  targetName?: string | null
  targetIcon?: string | null
  dropItemName?: string | null
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
  paidByNickname?: string | null
  received: boolean
  receivedAt?: string | null
}

export interface Loot {
  id: number
  raidId: number
  targetId?: number | null
  targetName?: string | null
  targetIcon?: string | null
  itemName: string
  dropped: boolean
  soldPrice?: number | null
  soldAt?: string | null
  memo?: string | null
  distributedByNickname?: string | null
  distributedAt?: string | null
  shares: LootShare[]
}

export interface BulkDropEntry {
  targetId: number
  quantity: number
  unitPrice?: number | null
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

export type ChatOrigin = 'SITE' | 'DISCORD' | 'SYSTEM'

export interface ChatMessage {
  id: number
  content: string
  authorMemberId?: number | null
  authorDiscordId?: string | null
  authorNickname: string
  authorStarred: boolean
  origin: ChatOrigin
  actionType?: string | null
  actionRefId?: number | null
  createdAt: string
}
