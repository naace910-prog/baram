import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, Tag, Space, App as AntApp, Modal, Form, Select, InputNumber, Input, Empty, Alert } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import {
  DndContext, DragEndEvent, DragOverlay, DragStartEvent, PointerSensor, TouchSensor, useDroppable, useDraggable, useSensor, useSensors,
} from '@dnd-kit/core'
import { PlusOutlined, DeleteOutlined, CloseOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { raidApi, memberApi, partyApi, partyRoleApi } from '@/api/client'
import type { PartyView, PartyMemberEntry, PartyRole, Member, ChannelType } from '@/types'
import { useAuth, isMaster } from '@/store/authStore'

type Draft = Record<number, PartyMemberEntry[]>  // partyId → entries

const channelLabel = (t: ChannelType) => t === 'MAIN' ? '본대' : '침략'

export default function RaidPartyPage() {
  const { id } = useParams()
  const raidId = Number(id)
  const nav = useNavigate()
  const qc = useQueryClient()
  const { message, modal } = AntApp.useApp()
  const { user } = useAuth()
  const master = isMaster(user)

  const { data: raid } = useQuery({ queryKey: ['raid', raidId], queryFn: () => raidApi.get(raidId) })
  const { data: parties = [] } = useQuery({ queryKey: ['parties', raidId], queryFn: () => partyApi.list(raidId) })
  const { data: roles = [] } = useQuery({ queryKey: ['party-roles', false], queryFn: () => partyRoleApi.list(false) })
  const { data: members = [] } = useQuery({ queryKey: ['members', false], queryFn: () => memberApi.list(false) })

  const [draft, setDraft] = useState<Draft>({})
  const [dirty, setDirty] = useState<Set<number>>(new Set())
  const [activeDrag, setActiveDrag] = useState<{ memberId?: number; freeName?: string; nickname: string } | null>(null)
  const [creating, setCreating] = useState(false)
  const [autoFill, setAutoFill] = useState<{ open: boolean; partyId?: number; role?: string }>({ open: false })

  useEffect(() => {
    const d: Draft = {}
    for (const p of parties) {
      d[p.id] = p.members.map(m => ({
        role: m.role, memberId: m.memberId ?? undefined, freeName: m.freeName ?? undefined,
      }))
    }
    setDraft(d)
    setDirty(new Set())
  }, [parties])

  const nickById = useMemo(() => {
    const m = new Map<number, string>()
    members.forEach(x => m.set(x.id, x.nickname))
    return m
  }, [members])

  // 중복 카운트: 같은 memberId 가 몇 개 슬롯에 있는지 (전체 파티 통틀어)
  const memberOccurrence = useMemo(() => {
    const c = new Map<number, number>()
    Object.values(draft).forEach(list => list.forEach(e => {
      if (e.memberId != null) c.set(e.memberId, (c.get(e.memberId) ?? 0) + 1)
    }))
    return c
  }, [draft])

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 5 } }),
  )

  if (!raid) return null

  const markDirty = (partyId: number) => setDirty(prev => new Set(prev).add(partyId))

  const onDragStart = (e: DragStartEvent) => {
    const data = e.active.data.current as { memberId?: number; freeName?: string; nickname: string }
    setActiveDrag(data)
  }

  const onDragEnd = (e: DragEndEvent) => {
    setActiveDrag(null)
    if (!e.over) return
    const overId = String(e.over.id)  // format: "party:{partyId}:role:{roleName}"
    const parts = overId.split(':')
    if (parts.length !== 4 || parts[0] !== 'party') return
    const partyId = Number(parts[1])
    const role = parts[3]

    const data = e.active.data.current as { memberId?: number; freeName?: string; source?: { partyId: number; index: number } }
    // 소스가 다른 파티/슬롯이면 그쪽에서 제거
    if (data.source) {
      const src = data.source
      setDraft(prev => {
        const next = { ...prev }
        next[src.partyId] = [...(next[src.partyId] ?? [])]
        next[src.partyId].splice(src.index, 1)
        return next
      })
      markDirty(src.partyId)
    }
    // 타겟 파티/슬롯에 추가
    setDraft(prev => {
      const next = { ...prev }
      next[partyId] = [...(next[partyId] ?? []), {
        role, memberId: data.memberId, freeName: data.freeName,
      }]
      return next
    })
    markDirty(partyId)
  }

  const removeFromParty = (partyId: number, index: number) => {
    setDraft(prev => {
      const next = { ...prev }
      next[partyId] = [...(next[partyId] ?? [])]
      next[partyId].splice(index, 1)
      return next
    })
    markDirty(partyId)
  }

  const saveParty = async (partyId: number) => {
    try {
      await partyApi.replaceMembers(partyId, draft[partyId] ?? [])
      message.success('파티 저장 완료')
      qc.invalidateQueries({ queryKey: ['parties', raidId] })
    } catch {}
  }

  const saveAll = async () => {
    for (const pid of dirty) await partyApi.replaceMembers(pid, draft[pid] ?? [])
    message.success(`${dirty.size}개 파티 저장`)
    qc.invalidateQueries({ queryKey: ['parties', raidId] })
  }

  const addFreeName = (partyId: number, role: string) => {
    let name = ''
    modal.confirm({
      title: '외부 인원 (자유 입력)',
      content: (
        <Input placeholder="닉네임" onChange={(e) => { name = e.target.value }} />
      ),
      onOk: () => {
        if (!name.trim()) return
        setDraft(prev => {
          const next = { ...prev }
          next[partyId] = [...(next[partyId] ?? []), { role, memberId: undefined, freeName: name.trim() }]
          return next
        })
        markDirty(partyId)
      },
    })
  }

  return (
    <>
      <div className="page-header">
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => nav(`/raids/${raidId}`)}>레이드로</Button>
          <h2 style={{ margin: 0 }}>파티 편성 · {raid.targetIcon ?? '🎯'} {raid.targetName} · {dayjs(raid.scheduledAt).format('MM/DD HH:mm')}</h2>
        </Space>
        {master && (
          <Space>
            {parties.length > 0 && (
              <Button onClick={() => setAutoFill({ open: true, partyId: parties[0].id, role: roles[0]?.name })}>
                YES 자동 배치
              </Button>
            )}
            {dirty.size > 0 && <Button type="primary" onClick={saveAll}>{dirty.size}개 파티 저장</Button>}
            <Button icon={<PlusOutlined />} onClick={() => setCreating(true)}>새 파티</Button>
          </Space>
        )}
      </div>

      {!master && (
        <Alert type="info" showIcon message="파티 편성은 문주/부문주만 수정할 수 있습니다" style={{ marginBottom: 12 }} />
      )}

      <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd}>
        <div style={{ display: 'grid', gap: 12, gridTemplateColumns: 'minmax(200px, 260px) 1fr' }}>
          {/* 사이드바: 문파원 목록 */}
          <div>
            <Card size="small" title={`문파원 (${members.length})`} style={{ position: 'sticky', top: 76 }}
                  styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto', padding: 8 } }}>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {members.map(m => (
                  <MemberChip
                    key={m.id}
                    id={`member:${m.id}`}
                    label={m.nickname}
                    memberId={m.id}
                    count={memberOccurrence.get(m.id) ?? 0}
                    disabled={!master}
                  />
                ))}
              </div>
            </Card>
          </div>

          {/* 파티 카드들 */}
          <div style={{ display: 'grid', gap: 12 }}>
            {parties.length === 0 ? (
              <Card><Empty description="아직 파티가 없습니다. 새 파티 를 추가하세요" /></Card>
            ) : parties.map(p => (
              <PartyCard
                key={p.id}
                party={p}
                roles={roles}
                entries={draft[p.id] ?? []}
                dirty={dirty.has(p.id)}
                master={master}
                nickById={nickById}
                memberOccurrence={memberOccurrence}
                onRemove={(idx) => removeFromParty(p.id, idx)}
                onSave={() => saveParty(p.id)}
                onDelete={() => modal.confirm({
                  title: `${channelLabel(p.channelType)} · 채널 ${p.channelNumber ?? '-'} 파티 삭제`,
                  onOk: async () => {
                    await partyApi.delete(p.id); message.success('삭제됨')
                    qc.invalidateQueries({ queryKey: ['parties', raidId] })
                  },
                })}
                onAddFree={(role) => addFreeName(p.id, role)}
                onEditParty={() => { /* handled via inline modal below */ }}
              />
            ))}
          </div>
        </div>

        <DragOverlay>
          {activeDrag ? (
            <Tag color="purple" style={{ fontSize: 13, padding: '4px 10px', cursor: 'grabbing' }}>
              {activeDrag.nickname}
            </Tag>
          ) : null}
        </DragOverlay>
      </DndContext>

      <PartyCreateModal
        open={creating}
        onClose={() => setCreating(false)}
        members={members}
        onCreate={async (v) => {
          await partyApi.create(raidId, v)
          qc.invalidateQueries({ queryKey: ['parties', raidId] })
          message.success('파티 생성됨')
        }}
      />

      <Modal
        open={autoFill.open}
        title="참가 투표 YES 자동 배치"
        onCancel={() => setAutoFill({ open: false })}
        okText="배치"
        onOk={() => {
          const partyId = autoFill.partyId!
          const role = autoFill.role!
          const placedIds = new Set<number>()
          Object.values(draft).forEach(list => list.forEach(e => {
            if (e.memberId != null) placedIds.add(e.memberId)
          }))
          const yesUnplaced = raid.votes.filter(v => v.vote === 'YES' && !placedIds.has(v.memberId))
          if (yesUnplaced.length === 0) {
            message.info('배치할 YES 인원이 없습니다 (전원 이미 배치됨)')
            setAutoFill({ open: false })
            return
          }
          setDraft(prev => {
            const next = { ...prev }
            next[partyId] = [...(next[partyId] ?? []), ...yesUnplaced.map(v => ({
              role, memberId: v.memberId, freeName: undefined,
            }))]
            return next
          })
          markDirty(partyId)
          message.success(`${yesUnplaced.length}명 배치 (미저장, 상단 저장 버튼 눌러야 반영)`)
          setAutoFill({ open: false })
        }}
      >
        <div style={{ marginBottom: 8 }}>
          아직 어느 파티에도 배치되지 않은 <b>참가(YES)</b> 투표자를 선택한 파티/역할에 한꺼번에 넣습니다.
        </div>
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>대상 파티</div>
          <Select
            style={{ width: '100%' }}
            value={autoFill.partyId}
            onChange={(v) => setAutoFill(prev => ({ ...prev, partyId: v }))}
            options={parties.map(p => ({
              value: p.id,
              label: `${channelLabel(p.channelType)} · 채널 ${p.channelNumber ?? '-'} ${p.memo ? '· ' + p.memo : ''}`,
            }))}
          />
        </div>
        <div>
          <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 4 }}>배치할 역할</div>
          <Select
            style={{ width: '100%' }}
            value={autoFill.role}
            onChange={(v) => setAutoFill(prev => ({ ...prev, role: v }))}
            options={roles.map(r => ({ value: r.name, label: `${r.icon ?? ''} ${r.name}` }))}
          />
        </div>
      </Modal>
    </>
  )
}

function MemberChip({ id, label, memberId, freeName, count, disabled, source }: {
  id: string; label: string; memberId?: number; freeName?: string; count?: number
  disabled?: boolean; source?: { partyId: number; index: number }
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id, data: { memberId, freeName, nickname: label, source }, disabled,
  })
  const showCount = (count ?? 0) > 1
  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={{
        opacity: isDragging ? 0.4 : 1,
        cursor: disabled ? 'default' : 'grab',
        display: 'inline-flex', alignItems: 'center', touchAction: 'none',
      }}
    >
      <Tag
        color={freeName ? 'orange' : (showCount ? 'gold' : 'purple')}
        style={{ margin: 0, fontSize: 13, padding: '2px 8px', userSelect: 'none' }}
      >
        {label}{showCount ? ` (${count})` : ''}
      </Tag>
    </div>
  )
}

function RoleDropSlot({
  partyId, role, entries, nickById, memberOccurrence, master, onRemove, onAddFree,
}: {
  partyId: number; role: PartyRole
  entries: (PartyMemberEntry & { originalIndex: number })[]
  nickById: Map<number, string>
  memberOccurrence: Map<number, number>
  master: boolean
  onRemove: (originalIndex: number) => void
  onAddFree: () => void
}) {
  const dropId = `party:${partyId}:role:${role.name}`
  const { setNodeRef, isOver } = useDroppable({ id: dropId })
  return (
    <div>
      <div style={{ marginBottom: 4, fontSize: 13, color: '#595959' }}>
        <span>{role.icon} {role.name}</span>
        <span style={{ color: '#8c8c8c', marginLeft: 6 }}>({entries.length})</span>
      </div>
      <div
        ref={setNodeRef}
        style={{
          minHeight: 44,
          padding: 6,
          borderRadius: 6,
          border: `1px dashed ${isOver ? '#7c3aed' : '#d9d9d9'}`,
          background: isOver ? '#f9f0ff' : '#fafafa',
          display: 'flex', flexWrap: 'wrap', gap: 4, alignItems: 'flex-start',
        }}
      >
        {entries.map((e) => {
          const label = e.memberId != null ? (nickById.get(e.memberId) ?? `#${e.memberId}`) : (e.freeName ?? '?')
          const key = `${e.role}:${e.memberId ?? e.freeName}:${e.originalIndex}`
          const showCount = e.memberId != null && (memberOccurrence.get(e.memberId) ?? 0) > 1
          return (
            <Tag
              key={key}
              closable={master}
              onClose={(ev) => { ev.preventDefault(); onRemove(e.originalIndex) }}
              color={e.freeName ? 'orange' : (showCount ? 'gold' : 'purple')}
              style={{ margin: 0, fontSize: 13, padding: '2px 8px' }}
            >
              {label}{showCount ? ` (${memberOccurrence.get(e.memberId!) })` : ''}
            </Tag>
          )
        })}
        {master && (
          <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={onAddFree}>외부</Button>
        )}
      </div>
    </div>
  )
}

function PartyCard({
  party, roles, entries, dirty, master, nickById, memberOccurrence,
  onRemove, onSave, onDelete, onAddFree,
}: {
  party: PartyView; roles: PartyRole[]
  entries: PartyMemberEntry[]
  dirty: boolean; master: boolean
  nickById: Map<number, string>
  memberOccurrence: Map<number, number>
  onRemove: (index: number) => void
  onSave: () => void
  onDelete: () => void
  onAddFree: (role: string) => void
  onEditParty: () => void
}) {
  const entriesWithIdx = entries.map((e, i) => ({ ...e, originalIndex: i }))
  const byRole = new Map<string, typeof entriesWithIdx>()
  for (const e of entriesWithIdx) {
    if (!byRole.has(e.role)) byRole.set(e.role, [])
    byRole.get(e.role)!.push(e)
  }
  const total = entries.length

  return (
    <Card
      size="small"
      title={
        <Space wrap>
          <Tag color={party.channelType === 'MAIN' ? 'blue' : 'red'} style={{ margin: 0 }}>
            {party.channelType === 'MAIN' ? '본대' : '침략'}
          </Tag>
          {party.channelNumber != null && <span>채널 {party.channelNumber}</span>}
          {party.memo && <span style={{ color: '#8c8c8c' }}>· {party.memo}</span>}
        </Space>
      }
      extra={
        <Space>
          {master && dirty && <Button size="small" type="primary" onClick={onSave}>저장</Button>}
          {master && <Button size="small" icon={<DeleteOutlined />} danger onClick={onDelete} />}
        </Space>
      }
    >
      <div style={{ marginBottom: 8, color: '#595959', fontSize: 13 }}>
        🎤 마이크: {party.mikeNickname ?? <span style={{ color: '#bfbfbf' }}>미배정</span>}
      </div>
      <div style={{ display: 'grid', gap: 8 }}>
        {roles.map(r => (
          <RoleDropSlot
            key={r.id}
            partyId={party.id}
            role={r}
            entries={byRole.get(r.name) ?? []}
            nickById={nickById}
            memberOccurrence={memberOccurrence}
            master={master}
            onRemove={onRemove}
            onAddFree={() => onAddFree(r.name)}
          />
        ))}
      </div>
      <div style={{ marginTop: 8, textAlign: 'right', color: '#8c8c8c', fontSize: 12 }}>
        총원 {total}명
      </div>
    </Card>
  )
}

function PartyCreateModal({ open, onClose, members, onCreate }: {
  open: boolean; onClose: () => void; members: Member[]
  onCreate: (body: { channelType: ChannelType; channelNumber?: number; memo?: string; mikeMemberId?: number; mikeFreeName?: string }) => Promise<void>
}) {
  const [form] = Form.useForm()
  useEffect(() => { if (open) form.resetFields() }, [open, form])
  return (
    <Modal
      open={open} onCancel={onClose} title="새 파티" destroyOnClose
      onOk={async () => {
        const v = await form.validateFields()
        await onCreate(v)
        onClose()
      }}
    >
      <Form form={form} layout="vertical" initialValues={{ channelType: 'MAIN' }}>
        <Form.Item name="channelType" label="채널 타입" rules={[{ required: true }]}>
          <Select options={[{ value: 'MAIN', label: '본대' }, { value: 'INVADE', label: '침략' }]} />
        </Form.Item>
        <Form.Item name="channelNumber" label="채널 번호 (선택)">
          <InputNumber style={{ width: '100%' }} min={1} max={99999} />
        </Form.Item>
        <Form.Item name="memo" label="메모 (예: 빛채널)"><Input maxLength={200} /></Form.Item>
        <Form.Item name="mikeMemberId" label="마이크 (문파원 선택 · 선택)">
          <Select
            allowClear showSearch
            options={members.map(m => ({ value: m.id, label: m.nickname }))}
            filterOption={(input, o) => (o?.label as string ?? '').includes(input)}
            placeholder="문파원 선택"
          />
        </Form.Item>
        <Form.Item name="mikeFreeName" label="마이크 (자유 입력 · 문파원 선택 안 할 때)">
          <Input maxLength={40} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
