import { Button, Card, Tag, Empty, Segmented, Space, Avatar } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import dayjs from 'dayjs'
import { raidApi, memberApi } from '@/api/client'
import { useAuth, isMaster } from '@/store/authStore'
import { PlusOutlined, ClockCircleOutlined, ArrowRightOutlined } from '@ant-design/icons'
import type { RaidCategory, RaidStatus, RaidListItem } from '@/types'
import { CATEGORY_LABEL } from '@/types'

const statusColor: Record<RaidStatus, string> = {
  PLANNED: 'processing', DONE: 'success', CANCELLED: 'default',
}
const statusLabel: Record<RaidStatus, string> = {
  PLANNED: '예정', DONE: '완료', CANCELLED: '취소',
}
const categoryIcon: Record<RaidCategory, string> = { SKULL_KING: '💀', FANG: '🐲' }

function relativeTime(target: dayjs.Dayjs): string {
  const now = dayjs()
  const diffMin = target.diff(now, 'minute')
  if (diffMin < 0) return `${-diffMin}분 지남`
  if (diffMin < 60) return `${diffMin}분 뒤`
  const diffHour = target.diff(now, 'hour')
  if (diffHour < 24) return `${diffHour}시간 뒤`
  const diffDay = target.diff(now, 'day')
  return `${diffDay}일 뒤`
}

export default function RaidListPage() {
  const nav = useNavigate()
  const { user } = useAuth()
  const { data: raids = [] } = useQuery({ queryKey: ['raids'], queryFn: raidApi.list })
  const { data: members = [] } = useQuery({ queryKey: ['members', false], queryFn: () => memberApi.list(false) })
  const [filter, setFilter] = useState<'ALL' | RaidStatus>('ALL')

  const nickById = new Map<number, string>()
  members.forEach(m => nickById.set(m.id, m.nickname))

  const filtered = raids.filter((r) => filter === 'ALL' || r.status === filter)

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>레이드 목록</h2>
        {isMaster(user) && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => nav('/raids/new')}>
            등록
          </Button>
        )}
      </div>

      <Segmented
        value={filter}
        onChange={(v) => setFilter(v as any)}
        options={[
          { label: `전체 ${raids.length}`, value: 'ALL' },
          { label: `예정 ${raids.filter(r => r.status === 'PLANNED').length}`, value: 'PLANNED' },
          { label: `완료 ${raids.filter(r => r.status === 'DONE').length}`, value: 'DONE' },
          { label: `취소 ${raids.filter(r => r.status === 'CANCELLED').length}`, value: 'CANCELLED' },
        ]}
        style={{ marginBottom: 12 }}
      />

      {filtered.length === 0 ? (
        <Card><Empty description="레이드가 없습니다" /></Card>
      ) : (
        <div style={{ display: 'grid', gap: 12 }}>
          {filtered.map((r) => (
            <RaidCard key={r.id} raid={r} nickById={nickById} onOpen={() => nav(`/raids/${r.id}`)} />
          ))}
        </div>
      )}
    </>
  )
}

function RaidCard({ raid: r, nickById, onOpen }: {
  raid: RaidListItem; nickById: Map<number, string>; onOpen: () => void
}) {
  const hasTime = !!r.scheduledAt
  const scheduled = hasTime ? dayjs(r.scheduledAt) : dayjs()
  const isPast = hasTime && scheduled.isBefore(dayjs())
  const dayLabel = hasTime ? scheduled.format('MM/DD(dd)') : '미정'
  const timeLabel = hasTime ? scheduled.format('HH:mm') : '⏳'

  const yesVoters = r.votes.filter(v => v.vote === 'YES')
  const noVoters = r.votes.filter(v => v.vote === 'NO')
  const maybeVoters = r.votes.filter(v => v.vote === 'MAYBE')
  const attendeeNames = r.attendees.map(id => nickById.get(id) ?? `#${id}`)
  const showAttendees = attendeeNames.length > 0

  const catIcon = r.category ? categoryIcon[r.category] : (r.targetIcon ?? '🎯')
  const catLabel = r.category ? CATEGORY_LABEL[r.category] : ''
  const subLabel = r.targetName ?? (r.category === 'FANG' ? '다종 (드랍 시 지정)' : '')

  return (
    <Card
      hoverable
      onClick={onOpen}
      styles={{ body: { padding: 16 } }}
      style={{
        borderLeft: `4px solid ${r.status === 'PLANNED' ? '#1677ff' : r.status === 'DONE' ? '#52c41a' : '#d9d9d9'}`,
      }}
    >
      {/* Header: 시간 · 카테고리 · 상태 */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 10, flexWrap: 'wrap' }}>
        <div style={{
          background: r.status === 'PLANNED' ? '#e6f4ff' : r.status === 'DONE' ? '#f6ffed' : '#fafafa',
          padding: '8px 12px', borderRadius: 8, textAlign: 'center', minWidth: 76,
        }}>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>{dayLabel}</div>
          <div style={{ fontSize: 20, fontWeight: 700, color: '#262626', lineHeight: 1.1 }}>{timeLabel}</div>
        </div>

        <div style={{ flex: 1, minWidth: 200 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 18, fontWeight: 700 }}>
              <span style={{ fontSize: 22, marginRight: 4 }}>{catIcon}</span>
              {catLabel}
            </span>
            {subLabel && <span style={{ color: '#595959', fontSize: 14 }}>· {subLabel}</span>}
            <Tag color={statusColor[r.status]} style={{ marginLeft: 4 }}>{statusLabel[r.status]}</Tag>
            {r.status === 'PLANNED' && !isPast && (
              <span style={{ color: '#8c8c8c', fontSize: 12 }}>
                <ClockCircleOutlined /> {relativeTime(scheduled)}
              </span>
            )}
          </div>
          {r.memo && (
            <div style={{ marginTop: 4, color: '#595959', fontSize: 13 }}>💬 {r.memo}</div>
          )}
        </div>

        <ArrowRightOutlined style={{ color: '#8c8c8c', fontSize: 18 }} />
      </div>

      {/* Vote counts summary */}
      <Space size={4} style={{ marginBottom: 8 }}>
        <Tag color="green" style={{ margin: 0 }}>✅ {r.yesCount}</Tag>
        <Tag color="red" style={{ margin: 0 }}>❌ {r.noCount}</Tag>
        <Tag style={{ margin: 0 }}>❓ {r.maybeCount}</Tag>
      </Space>

      {/* Attendees or YES voters */}
      {showAttendees ? (
        <MemberChipRow
          label="참가확정"
          color="#7c3aed"
          names={attendeeNames}
        />
      ) : yesVoters.length > 0 && (
        <MemberChipRow
          label="참가 예정"
          color="#52c41a"
          names={yesVoters.map(v => v.nickname)}
        />
      )}

      {noVoters.length > 0 && (
        <MemberChipRow
          label="불참"
          color="#ff4d4f"
          names={noVoters.map(v => v.nickname)}
          compact
        />
      )}
      {maybeVoters.length > 0 && (
        <MemberChipRow
          label="미정"
          color="#8c8c8c"
          names={maybeVoters.map(v => v.nickname)}
          compact
        />
      )}
    </Card>
  )
}

function MemberChipRow({ label, color, names, compact }: {
  label: string; color: string; names: string[]; compact?: boolean
}) {
  return (
    <div style={{ display: 'flex', gap: 6, marginTop: 6, alignItems: 'flex-start' }}>
      <span style={{
        fontSize: 11, color: '#fff', background: color,
        padding: '1px 8px', borderRadius: 8, flexShrink: 0,
        minWidth: 62, textAlign: 'center',
      }}>
        {label} {names.length}
      </span>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
        {names.map((name, i) => (
          <span
            key={i}
            style={{
              fontSize: compact ? 11 : 12,
              padding: compact ? '0 6px' : '2px 8px',
              background: '#f5f5f5', borderRadius: 10,
              color: '#262626',
            }}
          >
            <Avatar size={compact ? 14 : 16} style={{ background: color, marginRight: 4, fontSize: 9, verticalAlign: -2 }}>
              {name[0]}
            </Avatar>
            {name}
          </span>
        ))}
      </div>
    </div>
  )
}
