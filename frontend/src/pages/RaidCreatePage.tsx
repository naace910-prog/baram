import { Button, Card, Form, Input, App as AntApp, DatePicker, Radio, Alert } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import dayjs, { Dayjs } from 'dayjs'
import { useState } from 'react'
import { raidApi } from '@/api/client'
import type { RaidCategory } from '@/types'

export default function RaidCreatePage() {
  const nav = useNavigate()
  const qc = useQueryClient()
  const { message } = AntApp.useApp()
  const [form] = Form.useForm()
  const [category, setCategory] = useState<RaidCategory>('FANG')

  const onFinish = async (v: { scheduledAt: Dayjs; memo?: string }) => {
    try {
      const r = await raidApi.create({
        category,
        scheduledAt: v.scheduledAt.format('YYYY-MM-DDTHH:mm:ss'),
        memo: v.memo,
      })
      message.success('레이드 등록 완료')
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
          <Form.Item label="레이드 종류" required>
            <Radio.Group
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              buttonStyle="solid"
              size="large"
            >
              <Radio.Button value="SKULL_KING">💀 해골왕</Radio.Button>
              <Radio.Button value="FANG">🐲 어금니 (룡 잡기)</Radio.Button>
            </Radio.Group>
            <div style={{ marginTop: 8, fontSize: 12, color: '#8c8c8c' }}>
              {category === 'SKULL_KING'
                ? '해골왕 1마리 · 해골왕의 뼈 드랍'
                : '흑룡·묵룡·감룡·진룡 자유 조합 · 각각의 어금니 드랍 (수량은 레이드 후 등록)'}
            </div>
          </Form.Item>

          <Form.Item name="scheduledAt" label="시간" rules={[{ required: true, message: '선택' }]}>
            <DatePicker
              showTime={{ format: 'HH:mm', minuteStep: 1 }}
              format="YYYY-MM-DD HH:mm"
              size="large"
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item name="memo" label="메모 (선택)">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>

          <Alert
            type="info" showIcon
            message="드랍 아이템은 레이드 완료 후 상세 화면에서 등록합니다"
            description={category === 'FANG' ? '예: 흑룡 어금니 2개 · 감룡 어금니 1개 → 각각 개별 판매·분배 가능' : '해골왕의 뼈 1개'}
            style={{ marginBottom: 16 }}
          />

          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="large" onClick={() => nav(-1)}>취소</Button>
            <Button size="large" type="primary" htmlType="submit" style={{ flex: 1 }}>등록</Button>
          </div>
        </Form>
      </Card>
    </>
  )
}
