import { Card, Row, Col, Statistic, List, Tag, Button, Empty } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { raidApi, statsApi } from '@/api/client'
import { useAuth, isMaster } from '@/store/authStore'
import { ThunderboltOutlined, PlusOutlined, WarningOutlined, DollarOutlined } from '@ant-design/icons'

const fmt = (n: number) => n.toLocaleString('ko-KR')

export default function DashboardPage() {
  const nav = useNavigate()
  const { user } = useAuth()
  const { data: raids = [] } = useQuery({ queryKey: ['raids'], queryFn: raidApi.list })
  const { data: stats } = useQuery({ queryKey: ['stats'], queryFn: statsApi.get })

  const upcoming = raids
    .filter((r) => r.status === 'PLANNED')
    .sort((a, b) => (dayjs(a.scheduledAt).isAfter(b.scheduledAt) ? 1 : -1))
  const past = raids.filter((r) => r.status === 'DONE')

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>대시보드</h2>
        {isMaster(user) && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => nav('/raids/new')}>
            레이드 등록
          </Button>
        )}
      </div>

      <Row gutter={[12, 12]}>
        <Col xs={12} md={6}>
          <Card><Statistic title="예정 레이드" value={upcoming.length} prefix={<ThunderboltOutlined />} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="완료 레이드" value={past.length} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card
            hoverable
            onClick={() => nav('/stats')}
            style={{ cursor: 'pointer' }}
          >
            <Statistic
              title="총 판매금액"
              value={stats?.overview?.totalRevenue ?? 0}
              suffix="전"
              prefix={<DollarOutlined />}
              valueStyle={{ color: '#52c41a' }}
              formatter={(v) => fmt(Number(v))}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card
            hoverable
            onClick={() => nav('/stats')}
            style={{ cursor: 'pointer' }}
          >
            <Statistic
              title="미정산 총액"
              value={stats?.overview?.unpaidTotal ?? 0}
              suffix="전"
              prefix={<WarningOutlined />}
              valueStyle={{ color: (stats?.overview?.unpaidTotal ?? 0) > 0 ? '#faad14' : '#8c8c8c' }}
              formatter={(v) => fmt(Number(v))}
            />
          </Card>
        </Col>
      </Row>

      <Card title="다가오는 레이드" style={{ marginTop: 12 }}>
        {upcoming.length === 0 ? (
          <Empty description="예정 레이드가 없습니다" />
        ) : (
          <List
            dataSource={upcoming}
            renderItem={(r) => (
              <List.Item
                actions={[<Button size="small" onClick={() => nav(`/raids/${r.id}`)}>상세</Button>]}
              >
                <List.Item.Meta
                  title={<>
                    <Tag color="purple">{r.targetIcon ?? '🎯'} {r.targetName}</Tag>
                    {dayjs(r.scheduledAt).format('MM/DD(dd) HH:mm')}
                  </>}
                  description={<>드랍: {r.dropItemName} · 참가 {r.yesCount} / 불참 {r.noCount} / 미정 {r.maybeCount}</>}
                />
              </List.Item>
            )}
          />
        )}
      </Card>
    </>
  )
}
