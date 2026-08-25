import { Button, Card, Table, Space, Modal, Form, Input, InputNumber, Switch, App as AntApp, Tag } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { partyRoleApi } from '@/api/client'
import type { PartyRole } from '@/types'
import { PlusOutlined } from '@ant-design/icons'

export default function PartyRolesPage() {
  const qc = useQueryClient()
  const { message, modal } = AntApp.useApp()
  const [includeInactive, setIncludeInactive] = useState(true)
  const { data: roles = [] } = useQuery({
    queryKey: ['party-roles', includeInactive],
    queryFn: () => partyRoleApi.list(includeInactive),
  })
  const [editing, setEditing] = useState<PartyRole | null>(null)
  const [creating, setCreating] = useState(false)

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>파티 역할 관리</h2>
        <Space>
          <span>비활성 포함</span>
          <Switch checked={includeInactive} onChange={setIncludeInactive} />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>추가</Button>
        </Space>
      </div>

      <Card>
        <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 8 }}>
          레이드 파티 편성 시 사용할 역할입니다. 예: 격수 · 태성 · 진선. 순서는 표시 순서를 결정합니다.
        </div>
        <Table
          rowKey="id" size="small" pagination={false}
          dataSource={roles}
          columns={[
            { title: '순서', dataIndex: 'displayOrder', width: 80, align: 'center' },
            { title: '아이콘', dataIndex: 'icon', width: 80, align: 'center' },
            { title: '이름', dataIndex: 'name', width: 200 },
            { title: '활성', dataIndex: 'active', width: 80, align: 'center',
              render: (a: boolean) => a ? <Tag color="green">활성</Tag> : <Tag>비활성</Tag> },
            { title: '작업', width: 160,
              render: (_, r: PartyRole) => (
                <Space size={4}>
                  <Button size="small" onClick={() => setEditing(r)}>편집</Button>
                  <Button size="small" danger onClick={() => modal.confirm({
                    title: `${r.name} 삭제`,
                    content: '삭제 시 이 역할을 사용 중인 파티 멤버 데이터는 그대로 남습니다',
                    onOk: async () => {
                      await partyRoleApi.delete(r.id); message.success('삭제됨')
                      qc.invalidateQueries({ queryKey: ['party-roles'] })
                    }
                  })}>삭제</Button>
                </Space>
              )
            }
          ]}
        />
      </Card>

      <RoleEditModal
        open={creating || !!editing}
        role={editing}
        onClose={() => { setCreating(false); setEditing(null) }}
        onSaved={() => qc.invalidateQueries({ queryKey: ['party-roles'] })}
      />
    </>
  )
}

function RoleEditModal({ open, role, onClose, onSaved }: {
  open: boolean; role: PartyRole | null; onClose: () => void; onSaved: () => void
}) {
  const [form] = Form.useForm()
  const { message } = AntApp.useApp()
  useEffect(() => {
    if (!open) return
    if (role) form.setFieldsValue({ name: role.name, icon: role.icon, displayOrder: role.displayOrder, active: role.active })
    else form.resetFields()
  }, [open, role, form])

  return (
    <Modal
      open={open} onCancel={onClose}
      title={role ? `역할 편집 · ${role.name}` : '새 역할 추가'}
      destroyOnClose
      onOk={async () => {
        const v = await form.validateFields()
        if (role) await partyRoleApi.update(role.id, v)
        else await partyRoleApi.create(v)
        message.success('저장 완료')
        onSaved(); onClose()
      }}
    >
      <Form form={form} layout="vertical" initialValues={{ active: true, displayOrder: 99 }}>
        <Form.Item name="name" label="이름 (예: 스킬셋)" rules={[{ required: true }]}>
          <Input maxLength={40} />
        </Form.Item>
        <Form.Item name="icon" label="아이콘 (이모지 1~2자, 선택)">
          <Input maxLength={8} placeholder="예: ⚔️ ✨ 🗡️" />
        </Form.Item>
        <Form.Item name="displayOrder" label="표시 순서 (작을수록 먼저)" rules={[{ required: true }]}>
          <InputNumber min={1} max={999} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="active" label="활성" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  )
}
