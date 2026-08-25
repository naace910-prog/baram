import { Card, Col, Row, Statistic, Table, Tag, Empty } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { statsApi } from '@/api/client'
import type { MemberStat, MonthlyBucket, TargetStat } from '@/types'
import {
  TeamOutlined, ThunderboltOutlined, TrophyOutlined,
  DollarOutlined, WarningOutlined,
} from '@ant-design/icons'

const fmt = (n: number) => n.toLocaleString('ko-KR')

function MonthlyChart({ data }: { data: MonthlyBucket[] }) {
  const w = 720, h = 200, pad = { l: 48, r: 32, t: 12, b: 32 }
  const iw = w - pad.l - pad.r
  const ih = h - pad.t - pad.b
  const maxKill = Math.max(1, ...data.map(d => d.killCount))
  const maxRev = Math.max(1, ...data.map(d => d.revenue))
  const barW = iw / data.length * 0.6
  const barGap = iw / data.length * 0.4

  return (
    <div style={{ overflowX: 'auto' }}>
      <svg viewBox={`0 0 ${w} ${h}`} style={{ width: '100%', minWidth: 600, height: h }}>
        {/* 판매금액 (선) */}
        <polyline
          fill="none" stroke="#52c41a" strokeWidth="2"
          points={data.map((d, i) => {
            const x = pad.l + (i + 0.5) * (iw / data.length)
            const y = pad.t + ih - (d.revenue / maxRev) * ih
            return `${x},${y}`
          }).join(' ')}
        />
        {data.map((d, i) => {
          const x = pad.l + (i + 0.5) * (iw / data.length)
          const y = pad.t + ih - (d.revenue / maxRev) * ih
          return <circle key={i} cx={x} cy={y} r={3} fill="#52c41a" />
        })}
        {/* 킬수 (막대) */}
        {data.map((d, i) => {
          const x = pad.l + i * (iw / data.length) + barGap / 2
          const hbar = (d.killCount / maxKill) * ih
          return (
            <rect key={i}
              x={x} y={pad.t + ih - hbar}
              width={barW} height={hbar}
              fill="#7c3aed" opacity="0.6" rx={2}
            />
          )
        })}
        {/* x축 라벨 */}
        {data.map((d, i) => {
          const x = pad.l + (i + 0.5) * (iw / data.length)
          const [, m] = d.yearMonth.split('-')
          return <text key={i} x={x} y={h - 10} textAnchor="middle" fontSize="10" fill="#8c8c8c">{m}월</text>
        })}
        {/* y축 라벨 */}
        <text x={4} y={pad.t + 4} fontSize="10" fill="#7c3aed">킬 최대 {maxKill}</text>
        <text x={4} y={pad.t + 18} fontSize="10" fill="#52c41a">판매 최대 {fmt(maxRev)}전</text>
      </svg>
      <div style={{ textAlign: 'center', fontSize: 11, color: '#8c8c8c', marginTop: 4 }}>
        <span style={{ display: 'inline-block', width: 10, height: 10, background: '#7c3aed', opacity: 0.6, marginRight: 4 }}></span>
        킬 수 (막대)
        <span style={{ marginLeft: 16, display: 'inline-block', width: 10, height: 3, background: '#52c41a', marginRight: 4, verticalAlign: 'middle' }}></span>
        판매금액 (선)
      </div>
    </div>
  )
}

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

      <Card title="최근 12개월 추이" style={{ marginTop: 12 }} loading={isLoading}>
        {(!data?.monthly || data.monthly.length === 0) ? (
          <Empty description="데이터 없음" />
        ) : (
          <MonthlyChart data={data.monthly} />
        )}
      </Card>

      <Card title="레이드 대상별 실적 (총 판매금액 순)" style={{ marginTop: 12 }} loading={isLoading}>
        {targets.length === 0 ? <Empty description="레이드 이력이 없습니다" /> : (
          <Table<TargetStat>
            rowKey="targetId" size="small" pagination={false}
            dataSource={targets} scroll={{ x: 620 }}
            columns={[
              {
                title: '대상', dataIndex: 'name', width: 120,
                render: (name: string, r) => <Tag color="purple" style={{ margin: 0 }}>{r.icon ?? '🎯'} {name}</Tag>,
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
