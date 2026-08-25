import { Button, Card, Table, Tag, Space, Modal, Form, Input, Select, App as AntApp, Switch } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import dayjs from 'dayjs'
import { memberApi } from '@/api/client'
import type { Member, MemberRole } from '@/types'
import { PlusOutlined } from '@ant-design/icons'

const roleColor: Record<MemberRole, string> = { MASTER: 'red', VICE: 'orange', MEMBER: 'default' }

export default function MemberManagePage() {
  const qc = useQueryClient()
  const { message, modal } = AntApp.useApp()
  const [includeInactive, setIncludeInactive] = useState(false)
  const { data: members = [] } = useQuery({
    queryKey: ['members', includeInactive],
    queryFn: () => memberApi.list(includeInactive),
  })
  const [editing, setEditing] = useState<Member | null>(null)
  const [creating, setCreating] = useState(false)

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>문파원 관리</h2>
        <Space>
          <span>비활성 포함</span>
          <Switch checked={includeInactive} onChange={setIncludeInactive} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>추가</Button>
        </Space>
      </div>

      <Card>
        <Table
          rowKey="id"
          dataSource={members}
          size="small"
          scroll={{ x: 720 }}
          pagination={{ pageSize: 20 }}
          columns={[
            { title: '계정', dataIndex: 'account', width: 100 },
            { title: '닉네임', dataIndex: 'nickname', width: 140, render: (n: string, m: Member) => (
              <span>{m.starred && <span style={{ color: '#faad14' }}>⭐ </span>}{n}</span>
            ) },
            { title: '역할', dataIndex: 'role', width: 90, render: (r: MemberRole) => <Tag color={roleColor[r]}>{r}</Tag> },
            {
              title: '⭐ 중요', dataIndex: 'starred', width: 80,
              render: (starred: boolean, m: Member) => (
                <Switch
                  size="small"
                  checked={starred}
                  disabled={m.role !== 'MASTER'}
                  onChange={async (c) => {
                    try {
                      await memberApi.setStarred(m.id, c)
                      qc.invalidateQueries({ queryKey: ['members'] })
                    } catch (e: any) {
                      message.error(e?.response?.data?.error ?? '변경 실패')
                    }
                  }}
                />
              )
            },
            { title: '디스코드ID', dataIndex: 'discordUserId', width: 150 },
            { title: '활성', dataIndex: 'active', width: 60, render: (a: boolean) => a ? '✅' : '❌' },
            { title: '가입일', dataIndex: 'joinedAt', width: 120, render: (v: string) => dayjs(v).format('YYYY-MM-DD') },
            {
              title: '작업', width: 180, fixed: 'right',
              render: (_, m: Member) => (
                <Space size={4} wrap>
                  <Button size="small" onClick={() => setEditing(m)}>편집</Button>
                  <Button size="small" onClick={() => {
                    modal.confirm({
                      title: `${m.nickname} 비밀번호 초기화`,
                      content: '새 비밀번호를 "1234" 로 재설정합니다',
                      onOk: async () => { await memberApi.resetPassword(m.id, '1234'); message.success('초기화 완료') }
                    })
                  }}>비번초기화</Button>
                </Space>
              )
            }
          ]}
        />
      </Card>

      <CreateMemberModal open={creating} onClose={() => setCreating(false)} onSaved={() => qc.invalidateQueries({ queryKey: ['members'] })} />
      <EditMemberModal member={editing} onClose={() => setEditing(null)} onSaved={() => qc.invalidateQueries({ queryKey: ['members'] })} />
    </>
  )
}

function CreateMemberModal({ open, onClose, onSaved }: { open: boolean; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm()
  const { message } = AntApp.useApp()
  return (
    <Modal
      open={open} onCancel={onClose} title="문파원 추가"
      onOk={async () => {
        const v = await form.validateFields()
        await memberApi.create(v)
        message.success('추가 완료')
        form.resetFields(); onSaved(); onClose()
      }}
    >
      <Form form={form} layout="vertical" initialValues={{ role: 'MEMBER', password: '1234' }}>
        <Form.Item name="account" label="계정" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="password" label="초기 비밀번호" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="nickname" label="닉네임" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="role" label="역할" rules={[{ required: true }]}>
          <Select options={[{ value: 'MASTER', label: '문주' }, { value: 'VICE', label: '부문주' }, { value: 'MEMBER', label: '일반' }]} />
        </Form.Item>
        <Form.Item name="discordUserId" label="디스코드 유저ID (선택)"><Input placeholder="예: 123456789012345678" /></Form.Item>
      </Form>
    </Modal>
  )
}

function EditMemberModal({ member, onClose, onSaved }: { member: Member | null; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm()
  const { message } = AntApp.useApp()
  useEffect(() => {
    if (member) form.setFieldsValue({
      nickname: member.nickname, role: member.role,
      discordUserId: member.discordUserId ?? '', active: member.active,
    })
  }, [member, form])
  return (
    <Modal
      open={!!member} onCancel={onClose} title={`문파원 편집 · ${member?.nickname ?? ''}`}
      destroyOnClose
      onOk={async () => {
        const v = await form.validateFields()
        await memberApi.update(member!.id, v)
        message.success('저장 완료')
        onSaved(); onClose()
      }}
    >
      <Form form={form} layout="vertical">
        <Form.Item name="nickname" label="닉네임" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="role" label="역할" rules={[{ required: true }]}>
          <Select options={[{ value: 'MASTER', label: '문주' }, { value: 'VICE', label: '부문주' }, { value: 'MEMBER', label: '일반' }]} />
        </Form.Item>
        <Form.Item name="discordUserId" label="디스코드 유저ID"><Input /></Form.Item>
        <Form.Item name="active" label="활성" valuePropName="checked"><Switch /></Form.Item>
      </Form>
    </Modal>
  )
}
