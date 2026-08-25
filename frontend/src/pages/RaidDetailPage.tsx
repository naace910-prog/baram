import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Card, Button, Tag, Space, App as AntApp, Descriptions, Divider, Segmented,
  Table, InputNumber, Input, Switch, Modal, Checkbox
} from 'antd'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import { raidApi, lootApi, memberApi, targetApi } from '@/api/client'
import { useAuth, isMaster } from '@/store/authStore'
import type { VoteType, Loot, RaidStatus, Member, RaidTarget, BulkDropEntry } from '@/types'
import { CATEGORY_LABEL } from '@/types'

const statusColor: Record<RaidStatus, string> = {
  PLANNED: 'blue', DONE: 'green', CANCELLED: 'default',
}

export default function RaidDetailPage() {
  const { id } = useParams()
  const raidId = Number(id)
  const nav = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = AntApp.useApp()
  const { user } = useAuth()

  const { data: raid } = useQuery({ queryKey: ['raid', raidId], queryFn: () => raidApi.get(raidId) })
  const { data: loots = [] } = useQuery({ queryKey: ['loots', raidId], queryFn: () => lootApi.list(raidId) })
  const { data: members = [] } = useQuery({ queryKey: ['members'], queryFn: () => memberApi.list(false) })
  const { data: targets = [] } = useQuery({ queryKey: ['targets'], queryFn: targetApi.list })

  const [lootModal, setLootModal] = useState<{ open: boolean; loot?: Loot | null }>({ open: false })
  const [distModal, setDistModal] = useState<{ open: boolean; loot?: Loot | null; picked: number[] }>({
    open: false, picked: [],
  })
  const [bulkModal, setBulkModal] = useState(false)

  if (!raid) return null

  const myVote = raid.votes.find((v) => v.memberId === user?.memberId)?.vote

  const vote = async (v: VoteType) => {
    await raidApi.vote(raidId, v)
    message.success(`투표: ${v}`)
    qc.invalidateQueries({ queryKey: ['raid', raidId] })
    qc.invalidateQueries({ queryKey: ['raids'] })
  }

  const changeStatus = async (s: RaidStatus) => {
    await raidApi.update(raidId, {
      category: raid.category ?? undefined,
      targetId: raid.targetId ?? undefined,
      scheduledAt: raid.scheduledAt,
      status: s,
      memo: raid.memo ?? undefined,
    })
    qc.invalidateQueries({ queryKey: ['raid', raidId] })
    qc.invalidateQueries({ queryKey: ['raids'] })
  }

  const removeRaid = () => {
    modal.confirm({
      title: '레이드를 삭제하시겠습니까?',
      onOk: async () => {
        await raidApi.delete(raidId)
        qc.invalidateQueries({ queryKey: ['raids'] })
        nav('/raids', { replace: true })
      }
    })
  }

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>레이드 상세</h2>
        <Space>
          <Button onClick={() => nav(`/raids/${raidId}/parties`)}>파티 편성</Button>
          {isMaster(user) && <Button danger onClick={removeRaid}>삭제</Button>}
        </Space>
      </div>

      <Card>
        <Descriptions column={{ xs: 1, md: 2 }} bordered size="small">
          <Descriptions.Item label="분류">
            <Tag color={raid.category === 'SKULL_KING' ? 'red' : 'purple'}>
              {raid.category === 'SKULL_KING' ? '💀 해골왕' : '🐲 어금니'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="대상">
            {raid.targetName ? <Tag color="purple">{raid.targetIcon ?? '🎯'} {raid.targetName}</Tag>
              : <span style={{ color: '#8c8c8c' }}>다종 (득템별 개별 몹)</span>}
          </Descriptions.Item>
          <Descriptions.Item label="드랍">{raid.dropItemName ?? (raid.category === 'FANG' ? '흑/묵/감/진룡 어금니 · 드랍 시 등록' : '-')}</Descriptions.Item>
          <Descriptions.Item label="시간">{dayjs(raid.scheduledAt).format('YYYY-MM-DD(dd) HH:mm')}</Descriptions.Item>
          <Descriptions.Item label="상태">
            {isMaster(user) ? (
              <Segmented
                value={raid.status}
                onChange={(v) => changeStatus(v as RaidStatus)}
                options={[
                  { label: '예정', value: 'PLANNED' },
                  { label: '완료', value: 'DONE' },
                  { label: '취소', value: 'CANCELLED' },
                ]}
              />
            ) : <Tag color={statusColor[raid.status]}>{raid.status}</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="메모" span={2}>{raid.memo || '-'}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="내 투표" style={{ marginTop: 12 }}>
        <Space wrap>
          <Button type={myVote === 'YES' ? 'primary' : 'default'} onClick={() => vote('YES')}>✅ 참가</Button>
          <Button type={myVote === 'NO' ? 'primary' : 'default'} danger={myVote === 'NO'} onClick={() => vote('NO')}>❌ 불참</Button>
          <Button type={myVote === 'MAYBE' ? 'primary' : 'default'} onClick={() => vote('MAYBE')}>❓ 미정</Button>
        </Space>
      </Card>

      <Card title={`투표 현황 (${raid.votes.length}표)`} style={{ marginTop: 12 }}>
        {raid.votes.length === 0 ? (
          <div style={{ color: '#999' }}>아직 투표가 없습니다</div>
        ) : (
          <Space size={[8, 8]} wrap>
            {raid.votes.map((v) => (
              <Tag
                key={v.memberId}
                color={v.vote === 'YES' ? 'green' : v.vote === 'NO' ? 'red' : 'default'}
              >
                {v.nickname} · {v.vote}
              </Tag>
            ))}
          </Space>
        )}
      </Card>

      {isMaster(user) && (
        <Card
          title="득템·판매·분배"
          style={{ marginTop: 12 }}
          extra={
            <Space>
              {raid.category === 'FANG' && (
                <Button onClick={() => setBulkModal(true)}>드랍 대량 입력</Button>
              )}
              <Button
                type="primary"
                onClick={() => setLootModal({ open: true, loot: { id: 0, raidId, itemName: raid.dropItemName ?? '', dropped: true, soldPrice: null, memo: null, shares: [], targetId: raid.targetId } as any })}
              >
                득템 추가
              </Button>
            </Space>
          }
        >
          {loots.length === 0 ? (
            <div style={{ color: '#999' }}>등록된 득템이 없습니다</div>
          ) : (
            loots.map((l) => (
              <Card key={l.id} type="inner" style={{ marginBottom: 8 }} title={
                <Space>
                  {l.targetIcon && <span>{l.targetIcon}</span>}
                  <span>{l.itemName}</span>
                  {l.dropped ? <Tag color="green">드랍</Tag> : <Tag>노드랍</Tag>}
                  {l.soldPrice != null && <Tag color="gold">{l.soldPrice.toLocaleString()}전</Tag>}
                </Space>
              } extra={
                <Space>
                  <Button size="small" onClick={() => setLootModal({ open: true, loot: l })}>편집</Button>
                  <Button size="small" danger onClick={async () => {
                    await lootApi.delete(raidId, l.id)
                    qc.invalidateQueries({ queryKey: ['loots', raidId] })
                  }}>삭제</Button>
                  <Button
                    size="small" type="primary"
                    disabled={l.soldPrice == null || l.soldPrice <= 0}
                    onClick={() => setDistModal({
                      open: true, loot: l,
                      picked: l.shares.length > 0 ? l.shares.map(s => s.memberId) : raid.votes.filter(v => v.vote === 'YES').map(v => v.memberId),
                    })}
                  >
                    분배
                  </Button>
                </Space>
              }>
                {l.memo && <div style={{ color: '#999', marginBottom: 8 }}>{l.memo}</div>}
                {l.shares.length === 0 ? (
                  <div style={{ color: '#999' }}>아직 분배 안 됨</div>
                ) : (
                  <Table
                    size="small" pagination={false} rowKey="id"
                    dataSource={l.shares}
                    columns={[
                      { title: '문파원', dataIndex: 'nickname' },
                      { title: '분배액', dataIndex: 'share', render: (v: number) => `${v.toLocaleString()}전` },
                      {
                        title: '정산', dataIndex: 'paid',
                        render: (paid: boolean, s) => (
                          <Switch
                            checked={paid}
                            onChange={async (c) => {
                              await lootApi.markPaid(raidId, l.id, s.id, c)
                              qc.invalidateQueries({ queryKey: ['loots', raidId] })
                            }}
                          />
                        )
                      },
                    ]}
                  />
                )}
              </Card>
            ))
          )}
        </Card>
      )}

      <LootEditModal
        open={lootModal.open}
        loot={lootModal.loot ?? null}
        onClose={() => setLootModal({ open: false })}
        onSaved={() => qc.invalidateQueries({ queryKey: ['loots', raidId] })}
        raidId={raidId}
      />

      <DistributeModal
        open={distModal.open}
        loot={distModal.loot ?? null}
        members={members}
        picked={distModal.picked}
        onPickedChange={(picked) => setDistModal((s) => ({ ...s, picked }))}
        onClose={() => setDistModal({ open: false, picked: [] })}
        onSaved={() => qc.invalidateQueries({ queryKey: ['loots', raidId] })}
        raidId={raidId}
      />

      <BulkDropModal
        open={bulkModal}
        onClose={() => setBulkModal(false)}
        targets={targets.filter(t => t.category === 'FANG')}
        raidId={raidId}
        onSaved={() => qc.invalidateQueries({ queryKey: ['loots', raidId] })}
      />
    </>
  )
}

function BulkDropModal({ open, onClose, targets, raidId, onSaved }: {
  open: boolean; onClose: () => void; targets: RaidTarget[]; raidId: number
  onSaved: () => void
}) {
  const { message } = AntApp.useApp()
  const [qty, setQty] = useState<Record<number, number>>({})

  useEffect(() => { if (open) setQty({}) }, [open])

  const save = async () => {
    const drops: BulkDropEntry[] = Object.entries(qty)
      .map(([tid, q]) => ({ targetId: Number(tid), quantity: q }))
      .filter(d => d.quantity > 0)
    if (drops.length === 0) {
      message.warning('수량을 하나 이상 입력해주세요')
      return
    }
    await lootApi.bulkAdd(raidId, drops)
    const total = drops.reduce((s, d) => s + d.quantity, 0)
    message.success(`${total}개 득템 일괄 등록 완료`)
    onSaved(); onClose()
  }

  return (
    <Modal open={open} onCancel={onClose} onOk={save} title="드랍 대량 입력" okText="등록" destroyOnClose>
      <div style={{ marginBottom: 12, color: '#8c8c8c', fontSize: 13 }}>
        각 몹에서 몇 개씩 드랍했는지 입력. 개별 판매/분배는 이후 각 득템 카드에서.
      </div>
      <div style={{ display: 'grid', gap: 10 }}>
        {targets.map(t => (
          <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ fontSize: 24, width: 32, textAlign: 'center' }}>{t.icon ?? '🎯'}</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600 }}>{t.name}</div>
              <div style={{ fontSize: 12, color: '#8c8c8c' }}>{t.dropItemName}</div>
            </div>
            <InputNumber
              min={0} max={99}
              value={qty[t.id] ?? 0}
              onChange={(v) => setQty(prev => ({ ...prev, [t.id]: Number(v) || 0 }))}
              style={{ width: 90 }}
              addonAfter="개"
            />
          </div>
        ))}
      </div>
    </Modal>
  )
}

function LootEditModal({
  open, loot, raidId, onClose, onSaved,
}: {
  open: boolean; loot: Loot | null; raidId: number
  onClose: () => void; onSaved: () => void
}) {
  const { message } = AntApp.useApp()
  const [itemName, setItemName] = useState('')
  const [dropped, setDropped] = useState(true)
  const [soldPrice, setSoldPrice] = useState<number | null>(null)
  const [memo, setMemo] = useState('')

  const isNew = !loot || loot.id === 0

  useEffect(() => {
    if (!open) return
    setItemName(loot?.itemName ?? '')
    setDropped(loot?.dropped ?? true)
    setSoldPrice(loot?.soldPrice ?? null)
    setMemo(loot?.memo ?? '')
  }, [open, loot])

  const save = async () => {
    if (!itemName.trim()) { message.warning('아이템명 입력'); return }
    if (isNew) {
      await lootApi.create(raidId, { itemName, dropped, soldPrice: soldPrice ?? undefined, memo: memo || undefined })
    } else {
      await lootApi.update(raidId, loot!.id, { itemName, dropped, soldPrice: soldPrice ?? undefined, memo: memo || undefined })
    }
    onSaved(); onClose()
  }

  return (
    <Modal open={open} onCancel={onClose} onOk={save} title={isNew ? '득템 추가' : '득템 편집'} okText="저장">
      <div style={{ display: 'grid', gap: 12 }}>
        <div>
          <div style={{ marginBottom: 4 }}>아이템명</div>
          <Input value={itemName} onChange={(e) => setItemName(e.target.value)} />
        </div>
        <div>
          <div style={{ marginBottom: 4 }}>드랍여부</div>
          <Switch checked={dropped} onChange={setDropped} />
        </div>
        <div>
          <div style={{ marginBottom: 4 }}>판매금액 (전)</div>
          <InputNumber
            value={soldPrice ?? undefined}
            onChange={(v) => setSoldPrice(v == null ? null : Number(v))}
            style={{ width: '100%' }} min={0} step={100000}
            formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
          />
        </div>
        <div>
          <div style={{ marginBottom: 4 }}>메모</div>
          <Input.TextArea value={memo} onChange={(e) => setMemo(e.target.value)} rows={2} maxLength={300} />
        </div>
      </div>
    </Modal>
  )
}

function DistributeModal({
  open, loot, members, picked, onPickedChange, raidId, onClose, onSaved,
}: {
  open: boolean; loot: Loot | null; members: Member[]; picked: number[]
  onPickedChange: (v: number[]) => void; raidId: number
  onClose: () => void; onSaved: () => void
}) {
  const { message } = AntApp.useApp()
  if (!loot) return null
  const perShare = picked.length > 0 && loot.soldPrice ? Math.floor(loot.soldPrice / picked.length) : 0

  const save = async () => {
    if (picked.length === 0) { message.warning('분배 대상을 선택하세요'); return }
    await lootApi.distribute(raidId, loot.id, picked)
    message.success(`${picked.length}인 분배 완료`)
    onSaved(); onClose()
  }

  return (
    <Modal open={open} onCancel={onClose} onOk={save} title={`${loot.itemName} · 분배`} okText="분배 확정" width={520}>
      <div style={{ marginBottom: 8 }}>
        판매금액: <b>{loot.soldPrice?.toLocaleString() ?? 0}</b> 전 ÷ {picked.length}명 = <b>{perShare.toLocaleString()}</b> 전/인
      </div>
      <Divider style={{ margin: '8px 0' }} />
      <div style={{ marginBottom: 8 }}>
        <Button size="small" onClick={() => onPickedChange(members.map(m => m.id))}>전체 선택</Button>
        <Button size="small" onClick={() => onPickedChange([])} style={{ marginLeft: 4 }}>전체 해제</Button>
        <span style={{ marginLeft: 8, color: '#8c8c8c', fontSize: 12 }}>선택: {picked.length}명</span>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', rowGap: 8, columnGap: 12 }}>
        {members.map((m) => {
          const checked = picked.includes(m.id)
          return (
            <label key={m.id} style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', userSelect: 'none' }}>
              <input
                type="checkbox"
                checked={checked}
                onChange={(e) => {
                  const next = e.target.checked
                    ? [...picked, m.id]
                    : picked.filter(id => id !== m.id)
                  onPickedChange(next)
                }}
              />
              <span>{m.nickname}</span>
            </label>
          )
        })}
      </div>
    </Modal>
  )
}
