import { Button, Card, Table, Space, Modal, Form, Input, App as AntApp, Select, Tag } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { targetApi } from '@/api/client'
import type { RaidTarget, RaidCategory } from '@/types'
import { CATEGORY_LABEL } from '@/types'

const categoryColor: Record<RaidCategory, string> = { SKULL_KING: 'red', FANG: 'purple' }
import { PlusOutlined } from '@ant-design/icons'

export default function TargetManagePage() {
  const qc = useQueryClient()
  const { message, modal } = AntApp.useApp()
  const { data: targets = [] } = useQuery({ queryKey: ['targets'], queryFn: targetApi.list })
  const [editing, setEditing] = useState<RaidTarget | null>(null)
  const [creating, setCreating] = useState(false)

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>레이드 대상 관리</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>추가</Button>
      </div>
      <Card>
        <Table
          rowKey="id" size="small" pagination={false}
          dataSource={targets}
          columns={[
            { title: '아이콘', dataIndex: 'icon', width: 80, align: 'center', render: (v: string) => v || '-' },
            { title: '이름', dataIndex: 'name', width: 140 },
            { title: '분류', dataIndex: 'category', width: 110,
              render: (c: RaidCategory) => c ? <Tag color={categoryColor[c]}>{CATEGORY_LABEL[c]}</Tag> : '-' },
            { title: '드랍 아이템', dataIndex: 'dropItemName', width: 200 },
            { title: '메모', dataIndex: 'memo' },
            {
              title: '작업', width: 160,
              render: (_, t: RaidTarget) => (
                <Space size={4}>
                  <Button size="small" onClick={() => setEditing(t)}>편집</Button>
                  <Button size="small" danger onClick={() => modal.confirm({
                    title: `${t.name} 삭제`,
                    onOk: async () => {
                      await targetApi.delete(t.id); message.success('삭제됨')
                      qc.invalidateQueries({ queryKey: ['targets'] })
                    }
                  })}>삭제</Button>
                </Space>
              )
            }
          ]}
        />
      </Card>

      <TargetEditModal
        open={creating || !!editing}
        target={editing}
        onClose={() => { setCreating(false); setEditing(null) }}
        onSaved={() => qc.invalidateQueries({ queryKey: ['targets'] })}
      />
    </>
  )
}

function TargetEditModal({
  open, target, onClose, onSaved,
}: { open: boolean; target: RaidTarget | null; onClose: () => void; onSaved: () => void }) {
  const [form] = Form.useForm()
  const { message } = AntApp.useApp()
  useEffect(() => {
    if (!open) return
    if (target) form.setFieldsValue({
      name: target.name, dropItemName: target.dropItemName, icon: target.icon,
      category: target.category, memo: target.memo,
    })
    else form.resetFields()
  }, [open, target, form])

  return (
    <Modal
      open={open} onCancel={onClose} title={target ? `대상 편집 · ${target.name}` : '대상 추가'}
      destroyOnClose
      onOk={async () => {
        const v = await form.validateFields()
        if (target) await targetApi.update(target.id, v)
        else await targetApi.create(v)
        message.success('저장 완료'); form.resetFields()
        onSaved(); onClose()
      }}
    >
      <Form form={form} layout="vertical" initialValues={{ category: 'FANG' }}>
        <Form.Item name="name" label="이름" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="icon" label="아이콘 (이모지 1~2자, 선택)">
          <Input maxLength={8} placeholder="예: 💀 🐲 🦖" />
        </Form.Item>
        <Form.Item name="category" label="분류" rules={[{ required: true }]}>
          <Select options={[
            { value: 'SKULL_KING', label: '해골왕 (단독 킬 · 뼈 드랍)' },
            { value: 'FANG', label: '어금니 (룡 종류 · 어금니 드랍)' },
          ]} />
        </Form.Item>
        <Form.Item name="dropItemName" label="드랍 아이템" rules={[{ required: true }]}><Input /></Form.Item>
        <Form.Item name="memo" label="메모"><Input.TextArea rows={2} maxLength={400} /></Form.Item>
      </Form>
    </Modal>
  )
}
