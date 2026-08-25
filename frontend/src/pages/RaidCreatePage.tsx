import { Button, Card, Form, Input, Select, DatePicker, App as AntApp } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import dayjs, { Dayjs } from 'dayjs'
import { raidApi, targetApi } from '@/api/client'

export default function RaidCreatePage() {
  const nav = useNavigate()
  const qc = useQueryClient()
  const { message } = AntApp.useApp()
  const { data: targets = [] } = useQuery({ queryKey: ['targets'], queryFn: targetApi.list })
  const [form] = Form.useForm()

  const onFinish = async (v: { targetId: number; scheduledAt: Dayjs; memo?: string }) => {
    try {
      const r = await raidApi.create({
        targetId: v.targetId,
        scheduledAt: v.scheduledAt.format('YYYY-MM-DDTHH:mm:ss'),
        memo: v.memo,
      })
      message.success('레이드 등록 완료 (디스코드 알림 발송)')
      qc.invalidateQueries({ queryKey: ['raids'] })
      nav(`/raids/${r.id}`)
    } catch {}
  }

  return (
    <>
      <div className="page-header"><h2 style={{ margin: 0 }}>레이드 등록</h2></div>
      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ scheduledAt: dayjs().add(1, 'hour').startOf('hour') }}
        >
          <Form.Item name="targetId" label="대상" rules={[{ required: true, message: '선택' }]}>
            <Select
              placeholder="레이드 대상 선택"
              size="large"
              options={targets.map((t) => ({ label: `${t.name} (${t.dropItemName})`, value: t.id }))}
            />
          </Form.Item>
          <Form.Item name="scheduledAt" label="시간" rules={[{ required: true, message: '선택' }]}>
            <DatePicker
              showTime={{ format: 'HH:mm', minuteStep: 5 }}
              format="YYYY-MM-DD HH:mm"
              size="large"
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item name="memo" label="메모 (선택)">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="large" onClick={() => nav(-1)}>취소</Button>
            <Button size="large" type="primary" htmlType="submit" style={{ flex: 1 }}>등록</Button>
          </div>
        </Form>
      </Card>
    </>
  )
}
