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
  const [statusPending, setStatusPending] = useState(false)

  if (!raid) return null

  const myVote = raid.votes.find((v) => v.memberId === user?.memberId)?.vote

  const vote = async (v: VoteType) => {
    await raidApi.vote(raidId, v)
    message.success(`투표: ${v}`)
    qc.invalidateQueries({ queryKey: ['raid', raidId] })
    qc.invalidateQueries({ queryKey: ['raids'] })
  }

  const changeStatus = async (s: RaidStatus) => {
    if (s === raid.status || statusPending) return
    setStatusPending(true)
    try {
      const updated = await raidApi.update(raidId, {
        category: raid.category ?? undefined,
        targetId: raid.targetId ?? undefined,
        scheduledAt: raid.scheduledAt,
        status: s,
        memo: raid.memo ?? undefined,
      })
      // 즉시 캐시 반영 → Segmented 값이 곧바로 변경됨
      qc.setQueryData(['raid', raidId], updated)
      qc.invalidateQueries({ queryKey: ['raids'] })
      const label = s === 'DONE' ? '완료' : s === 'CANCELLED' ? '취소' : '예정'
      message.success(`상태 변경: ${label}`)
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '상태 변경 실패')
    } finally {
      setStatusPending(false)
    }
  }

  const removeRaid = () => {
    const lootCount = loots.length
    const totalSold = loots.reduce((s, l) => s + (l.soldPrice ?? 0), 0)
    const distCount = loots.reduce((s, l) => s + l.shares.length, 0)
    const unpaidTotal = loots.reduce((s, l) =>
      s + l.shares.filter(sh => !sh.paid).reduce((ss, sh) => ss + sh.share, 0), 0)
    const paidCount = loots.reduce((s, l) => s + l.shares.filter(sh => sh.paid).length, 0)
    const hasImpact = lootCount > 0 || distCount > 0

    modal.confirm({
      title: hasImpact ? '⚠️ 관련 데이터도 함께 삭제됩니다' : '레이드 삭제',
      width: 480,
      content: (
        <div style={{ whiteSpace: 'pre-line', lineHeight: 1.7 }}>
          {hasImpact ? (
            <>
              <div>이 레이드에 관련된 아래 데이터가 <b>모두 함께 삭제</b>됩니다:</div>
              <ul style={{ margin: '10px 0 10px 20px', padding: 0 }}>
                {lootCount > 0 && (
                  <li>득템 <b>{lootCount}</b>개 · 총 판매금액 <b>{totalSold.toLocaleString()}전</b></li>
                )}
                {distCount > 0 && (
                  <li>분배 <b>{distCount}</b>건 (정산 완료 {paidCount}건 · <b style={{color: '#faad14'}}>미정산 {unpaidTotal.toLocaleString()}전</b>)</li>
                )}
                <li>파티 편성 · 참가확정 · 투표 기록</li>
              </ul>
              <div style={{ color: '#ff4d4f', marginTop: 8 }}>정말 삭제하시겠습니까?</div>
            </>
          ) : (
            <div>이 레이드를 삭제하시겠습니까?</div>
          )}
        </div>
      ),
      okType: 'danger',
      okText: hasImpact ? `모두 삭제 (${lootCount + distCount}건)` : '삭제',
      cancelText: '취소',
      onOk: async () => {
        try {
          await raidApi.delete(raidId)
          message.success('레이드 삭제됨')
          qc.invalidateQueries({ queryKey: ['raids'] })
          qc.invalidateQueries({ queryKey: ['stats'] })
          nav('/raids', { replace: true })
        } catch (e: any) {
          message.error(e?.response?.data?.error ?? '삭제 실패')
        }
      }
    })
  }

  const sendPre30 = () => {
    modal.confirm({
      title: '수동 30분 리마인더 발송',
      content: (
        <div>
          <div>Discord 알림 채널에 <b>@here 멘션</b> 새 메시지 + 웹 푸시 + 채팅 시스템 메시지 발송.</div>
          <div style={{ marginTop: 8, color: '#ff4d4f' }}>⚠️ pre30Sent 마킹 → 자동 발송은 이후 중복되지 않음. 이미 자동 발송된 레이드는 재발송 불가.</div>
          <div style={{ marginTop: 8 }}>레이드 시간이 이미 지났어도 발송은 됩니다 (경과 시간은 "0분 뒤" 로 표기).</div>
        </div>
      ),
      okText: '지금 발송',
      cancelText: '취소',
      onOk: async () => {
        try {
          await raidApi.sendPre30Manual(raidId)
          message.success('리마인더 발송 완료')
          qc.invalidateQueries({ queryKey: ['raid', raidId] })
        } catch (e: any) {
          message.error(e?.response?.data?.error ?? '발송 실패')
        }
      },
    })
  }

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>레이드 상세</h2>
        <Space wrap>
          {isMaster(user) && raid.status === 'PLANNED' && (
            <Button onClick={sendPre30}>🔔 리마인더 발송</Button>
          )}
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
                disabled={statusPending}
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
                    onClick={() => {
                      // 항상 최신 YES 투표자 기준. 이미 분배된 경우도 기존 명단 + 새 YES 투표자 union.
                      const yesVoters = raid.votes.filter(v => v.vote === 'YES').map(v => v.memberId)
                      const existing = l.shares.map(s => s.memberId)
                      const picked = Array.from(new Set([...existing, ...yesVoters]))
                      setDistModal({ open: true, loot: l, picked })
                    }}
                  >
                    분배
                  </Button>
                </Space>
              }>
                {l.memo && <div style={{ color: '#999', marginBottom: 8 }}>{l.memo}</div>}
                {l.distributedByNickname && l.distributedAt && (
                  <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 8 }}>
                    ⚖️ 분배: <b>{l.distributedByNickname}</b> · {dayjs(l.distributedAt).format('MM/DD HH:mm')}
                  </div>
                )}
                {l.shares.length === 0 ? (
                  <div style={{ color: '#999' }}>아직 분배 안 됨</div>
                ) : (
                  <Table
                    size="small" pagination={false} rowKey="id"
                    dataSource={l.shares}
                    columns={[
                      { title: '문파원', dataIndex: 'nickname', width: 100 },
                      { title: '분배액', dataIndex: 'share', width: 160,
                        render: (v: number, s: any) => (
                          <ShareAmountEdit
                            amount={v}
                            onSave={async (newAmount) => {
                              await lootApi.updateShareAmount(raidId, l.id, s.id, newAmount)
                              qc.invalidateQueries({ queryKey: ['loots', raidId] })
                            }}
                          />
                        ) },
                      {
                        title: '지급', dataIndex: 'paid', width: 180,
                        render: (paid: boolean, s: any) => (
                          <Space direction="vertical" size={0}>
                            <Switch
                              checked={paid}
                              disabled={!isMaster(user)}
                              onChange={async (c) => {
                                await lootApi.markPaid(raidId, l.id, s.id, c)
                                qc.invalidateQueries({ queryKey: ['loots', raidId] })
                              }}
                            />
                            {paid && s.paidAt && (
                              <span style={{ fontSize: 11, color: '#8c8c8c' }}>
                                {dayjs(s.paidAt).format('MM/dd HH:mm')}
                                {s.paidByNickname ? ` · ${s.paidByNickname}` : ''}
                              </span>
                            )}
                          </Space>
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

function ShareAmountEdit({ amount, onSave }: { amount: number; onSave: (v: number) => Promise<any> }) {
  const [editing, setEditing] = useState(false)
  const [val, setVal] = useState<number | null>(amount)
  const [saving, setSaving] = useState(false)
  const { message } = AntApp.useApp()

  useEffect(() => { setVal(amount) }, [amount])

  const save = async () => {
    if (val == null || val < 0) { message.warning('0 이상 숫자 입력'); return }
    if (val === amount) { setEditing(false); return }
    setSaving(true)
    try {
      await onSave(val)
      message.success('분배액 수정')
      setEditing(false)
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '수정 실패')
    } finally { setSaving(false) }
  }

  if (!editing) {
    return (
      <span
        onClick={() => setEditing(true)}
        style={{ cursor: 'pointer', textDecoration: 'underline dotted #d9d9d9' }}
        title="클릭하여 수정"
      >
        {amount.toLocaleString()}전
      </span>
    )
  }

  return (
    <Space.Compact size="small" style={{ width: '100%' }}>
      <InputNumber
        value={val}
        onChange={(v) => setVal(v == null ? null : Number(v))}
        min={0} step={100000}
        formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
        style={{ width: 120 }}
        autoFocus
        onPressEnter={save}
      />
      <Button size="small" type="primary" onClick={save} loading={saving}>저장</Button>
      <Button size="small" onClick={() => { setEditing(false); setVal(amount) }}>취소</Button>
    </Space.Compact>
  )
}

function BulkDropModal({ open, onClose, targets, raidId, onSaved }: {
  open: boolean; onClose: () => void; targets: RaidTarget[]; raidId: number
  onSaved: () => void
}) {
  const { message } = AntApp.useApp()
  const [qty, setQty] = useState<Record<number, number>>({})
  const [price, setPrice] = useState<Record<number, number>>({})

  useEffect(() => { if (open) { setQty({}); setPrice({}) } }, [open])

  const rows = targets.map(t => {
    const q = qty[t.id] ?? 0
    const p = price[t.id] ?? 0
    return { t, q, p, sub: q * p }
  })
  const totalDrops = rows.reduce((s, r) => s + r.q, 0)
  const totalPrice = rows.reduce((s, r) => s + r.sub, 0)

  const save = async () => {
    const drops: BulkDropEntry[] = rows
      .filter(r => r.q > 0)
      .map(r => ({ targetId: r.t.id, quantity: r.q, unitPrice: r.p > 0 ? r.p : undefined }))
    if (drops.length === 0) {
      message.warning('수량을 하나 이상 입력해주세요')
      return
    }
    await lootApi.bulkAdd(raidId, drops)
    message.success(`${totalDrops}개 득템 일괄 등록${totalPrice > 0 ? ' · 총 ' + totalPrice.toLocaleString() + '전' : ''}`)
    onSaved(); onClose()
  }

  return (
    <Modal open={open} onCancel={onClose} onOk={save} title="드랍 대량 입력" okText="등록" destroyOnClose width={560}>
      <div style={{ marginBottom: 12, color: '#8c8c8c', fontSize: 13 }}>
        각 몹의 드랍 수량과 <b>1개당 가격</b> 입력. 개별 분배·정산은 이후 각 득템 카드에서.
      </div>
      <div style={{ display: 'grid', gap: 10 }}>
        {rows.map(({ t, q, p, sub }) => (
          <div key={t.id} style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: 8, borderRadius: 6,
            background: q > 0 ? '#f9f0ff' : '#fafafa',
          }}>
            <div style={{ fontSize: 22, width: 28, textAlign: 'center' }}>{t.icon ?? '🎯'}</div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600 }}>{t.name}</div>
              <div style={{ fontSize: 11, color: '#8c8c8c' }}>{t.dropItemName}</div>
            </div>
            <InputNumber
              min={0} max={99}
              value={q}
              onChange={(v) => setQty(prev => ({ ...prev, [t.id]: Number(v) || 0 }))}
              style={{ width: 78 }}
              addonAfter="개"
              size="small"
            />
            <InputNumber
              min={0}
              value={p}
              onChange={(v) => setPrice(prev => ({ ...prev, [t.id]: Number(v) || 0 }))}
              style={{ width: 140 }}
              addonAfter="전/개"
              step={100000}
              formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
              placeholder="0"
              size="small"
            />
            <div style={{ width: 100, textAlign: 'right', color: sub > 0 ? '#7c3aed' : '#bfbfbf', fontSize: 12, fontWeight: sub > 0 ? 600 : 400 }}>
              {sub > 0 ? sub.toLocaleString() + '전' : '-'}
            </div>
          </div>
        ))}
      </div>
      <Divider style={{ margin: '12px 0 8px' }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 8px' }}>
        <div style={{ fontSize: 14, color: '#595959' }}>
          총 <b style={{ color: '#7c3aed' }}>{totalDrops}</b> 개
        </div>
        <div style={{ fontSize: 18, fontWeight: 700, color: totalPrice > 0 ? '#52c41a' : '#bfbfbf' }}>
          TOTAL: {totalPrice.toLocaleString()} 전
        </div>
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
  const [divisor, setDivisor] = useState<number | null>(null)
  useEffect(() => { setDivisor(picked.length > 0 ? picked.length : null) }, [picked.length, open])
  if (!loot) return null
  const effectiveDivisor = divisor && divisor > 0 ? divisor : picked.length
  const perShare = effectiveDivisor > 0 && loot.soldPrice ? Math.floor(loot.soldPrice / effectiveDivisor) : 0
  const extraCount = Math.max(0, effectiveDivisor - picked.length)

  const save = async () => {
    if (picked.length === 0) { message.warning('분배 대상을 선택하세요'); return }
    if (effectiveDivisor < picked.length) { message.warning('분배 인원수는 선택된 문파원 수보다 크거나 같아야 합니다'); return }
    await lootApi.distribute(raidId, loot.id, picked, effectiveDivisor)
    message.success(`${picked.length}인 분배 완료 (÷${effectiveDivisor})`)
    onSaved(); onClose()
  }

  return (
    <Modal open={open} onCancel={onClose} onOk={save} title={`${loot.itemName} · 분배`} okText="분배 확정" width={560}>
      <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
        판매금액: <b>{loot.soldPrice?.toLocaleString() ?? 0}</b> 전 ÷
        <InputNumber
          size="small"
          value={effectiveDivisor}
          min={Math.max(1, picked.length)}
          max={99}
          onChange={(v) => setDivisor(v == null ? picked.length : Number(v))}
          style={{ width: 70 }}
          addonAfter="명"
        />
        = <b>{perShare.toLocaleString()}</b> 전/인
        {extraCount > 0 && (
          <span style={{ color: '#faad14', fontSize: 12 }}>· 미등록 {extraCount}명 포함</span>
        )}
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
