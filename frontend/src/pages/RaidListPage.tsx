import { Button, Card, Tag, Empty, Segmented } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import dayjs from 'dayjs'
import { raidApi } from '@/api/client'
import { useAuth, isMaster } from '@/store/authStore'
import { PlusOutlined } from '@ant-design/icons'
import type { RaidStatus } from '@/types'

const statusColor: Record<RaidStatus, string> = {
  PLANNED: 'blue', DONE: 'green', CANCELLED: 'default',
}

export default function RaidListPage() {
  const nav = useNavigate()
  const { user } = useAuth()
  const { data: raids = [] } = useQuery({ queryKey: ['raids'], queryFn: raidApi.list })
  const [filter, setFilter] = useState<'ALL' | RaidStatus>('ALL')

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
          { label: '전체', value: 'ALL' },
          { label: '예정', value: 'PLANNED' },
          { label: '완료', value: 'DONE' },
          { label: '취소', value: 'CANCELLED' },
        ]}
        style={{ marginBottom: 12 }}
      />

      {filtered.length === 0 ? (
        <Card><Empty description="레이드가 없습니다" /></Card>
      ) : (
        filtered.map((r) => (
          <Card
            key={r.id}
            className="card"
            hoverable
            onClick={() => nav(`/raids/${r.id}`)}
            style={{ marginBottom: 8 }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
              <div>
                <div style={{ fontSize: 16, fontWeight: 600 }}>
                  <Tag color="purple">{r.targetName}</Tag>
                  <Tag color={statusColor[r.status]}>{r.status}</Tag>
                  {dayjs(r.scheduledAt).format('MM/DD(dd) HH:mm')}
                </div>
                <div style={{ color: '#666', marginTop: 4 }}>드랍: {r.dropItemName}</div>
                {r.memo && <div style={{ color: '#999', marginTop: 4 }}>{r.memo}</div>}
              </div>
              <div style={{ textAlign: 'right' }}>
                <Tag color="green">✅ {r.yesCount}</Tag>
                <Tag color="red">❌ {r.noCount}</Tag>
                <Tag>❓ {r.maybeCount}</Tag>
              </div>
            </div>
          </Card>
        ))
      )}
    </>
  )
}
