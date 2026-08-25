import { Card, Col, Row, Statistic, Table, Tag, Empty } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { statsApi } from '@/api/client'
import type { MemberStat, TargetStat } from '@/types'
import {
  TeamOutlined, ThunderboltOutlined, TrophyOutlined,
  DollarOutlined, WarningOutlined,
} from '@ant-design/icons'

const fmt = (n: number) => n.toLocaleString('ko-KR')

export default function StatsPage() {
  const { data, isLoading } = useQuery({ queryKey: ['stats'], queryFn: statsApi.get })

  const overview = data?.overview
  const members = data?.members ?? []
  const targets = data?.targets ?? []

  return (
    <>
      <div className="page-header"><h2 style={{ margin: 0 }}>통계 · 정산 현황</h2></div>

      <Row gutter={[12, 12]}>
        <Col xs={12} md={8} lg={4}>
          <Card><Statistic title="문파원" value={overview?.totalMembers ?? 0} prefix={<TeamOutlined />} /></Card>
        </Col>
        <Col xs={12} md={8} lg={5}>
          <Card><Statistic title="예정 레이드" value={overview?.plannedRaids ?? 0} prefix={<ThunderboltOutlined />} /></Card>
        </Col>
        <Col xs={12} md={8} lg={5}>
          <Card><Statistic title="완료 레이드" value={overview?.doneRaids ?? 0} prefix={<TrophyOutlined />} /></Card>
        </Col>
        <Col xs={12} md={12} lg={5}>
          <Card>
            <Statistic
              title="총 판매금액"
              value={overview?.totalRevenue ?? 0}
              prefix={<DollarOutlined />}
              suffix="전"
              valueStyle={{ color: '#52c41a' }}
              formatter={(v) => fmt(Number(v))}
            />
          </Card>
        </Col>
        <Col xs={24} md={12} lg={5}>
          <Card>
            <Statistic
              title="미정산 총액"
              value={overview?.unpaidTotal ?? 0}
              prefix={<WarningOutlined />}
              suffix="전"
              valueStyle={{ color: (overview?.unpaidTotal ?? 0) > 0 ? '#faad14' : '#8c8c8c' }}
              formatter={(v) => fmt(Number(v))}
            />
          </Card>
        </Col>
      </Row>

      <Card title="문파원별 정산 현황 (분배액 많은 순)" style={{ marginTop: 12 }} loading={isLoading}>
        {members.length === 0 ? <Empty description="분배 이력이 없습니다" /> : (
          <Table<MemberStat>
            rowKey="memberId" size="small" pagination={false}
            dataSource={members} scroll={{ x: 620 }}
            columns={[
              { title: '문파원', dataIndex: 'nickname', width: 140 },
              { title: '참여 건수', dataIndex: 'attendCount', width: 90, align: 'right', render: fmt },
              {
                title: '총 분배액', dataIndex: 'totalShare', width: 140, align: 'right',
                render: (v: number) => <b>{fmt(v)}</b>,
              },
              {
                title: '미정산 금액', dataIndex: 'unpaidShare', width: 140, align: 'right',
                render: (v: number, r) => v > 0
                  ? <Tag color="orange" style={{ margin: 0 }}>{fmt(v)}전 ({r.unpaidCount}건)</Tag>
                  : <Tag color="green" style={{ margin: 0 }}>완납</Tag>,
              },
            ]}
          />
        )}
      </Card>

      <Card title="레이드 대상별 실적 (총 판매금액 순)" style={{ marginTop: 12 }} loading={isLoading}>
        {targets.length === 0 ? <Empty description="레이드 이력이 없습니다" /> : (
          <Table<TargetStat>
            rowKey="targetId" size="small" pagination={false}
            dataSource={targets} scroll={{ x: 620 }}
            columns={[
              {
                title: '대상', dataIndex: 'name', width: 100,
                render: (name: string) => <Tag color="purple" style={{ margin: 0 }}>{name}</Tag>,
              },
              { title: '드랍', dataIndex: 'dropItemName', width: 140 },
              { title: '킬 수', dataIndex: 'killCount', width: 80, align: 'right', render: fmt },
              { title: '총 판매금액', dataIndex: 'totalSoldPrice', width: 140, align: 'right', render: fmt },
              { title: '평균', dataIndex: 'avgSoldPrice', width: 120, align: 'right', render: fmt },
            ]}
          />
        )}
      </Card>
    </>
  )
}
