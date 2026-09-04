import { Alert, Button, Card, Input, Space, Tag, App as AntApp, Form } from 'antd'
import { BellOutlined, BellFilled, UserOutlined, DeleteOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { usePush } from '@/hooks/usePush'
import { useAuth, isMaster } from '@/store/authStore'
import { authApi, adminApi } from '@/api/client'

export default function SettingsPage() {
  const { ready, status, enable, disable } = usePush()
  const { user, fetchMe } = useAuth()
  const { message, modal } = AntApp.useApp()
  const qc = useQueryClient()
  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [saving, setSaving] = useState(false)
  const [resetting, setResetting] = useState(false)
  const [diagLoading, setDiagLoading] = useState(false)
  const [diag, setDiag] = useState<Awaited<ReturnType<typeof adminApi.discordTest>> | null>(null)
  const [logs, setLogs] = useState<Awaited<ReturnType<typeof adminApi.discordLogs>> | null>(null)

  const runDiscordDiag = async () => {
    setDiagLoading(true)
    try {
      const r = await adminApi.discordTest()
      setDiag(r)
      if (r.cooldownRemainingSec > 0) message.warning(`429 cooldown 중 · ${r.cooldownRemainingSec}초 남음`)
      else if (r.botReady && r.notifyChannelReachable) message.success('봇 정상 · 테스트 메시지 발송됨')
      else message.warning(`봇 미준비 (JDA: ${r.jdaStatus})`)
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '진단 실패')
    } finally {
      setDiagLoading(false)
    }
  }

  const reconnectBot = async () => {
    try {
      const r = await adminApi.discordReconnect()
      message.info(`재연결: ${r.result} (JDA: ${r.jdaStatus})`)
      setTimeout(runDiscordDiag, 3000)
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '재연결 실패')
    }
  }

  const clearCooldown = async () => {
    try {
      const r = await adminApi.discordClearCooldown()
      message.success(`cooldown 해제 (${r.clearedFromSec}s → 0s)`)
      setDiag(d => d ? { ...d, cooldownRemainingSec: 0 } : d)
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '해제 실패')
    }
  }

  const loadLogs = async () => {
    try {
      setLogs(await adminApi.discordLogs())
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '로그 조회 실패')
    }
  }

  const resetLoots = () => {
    modal.confirm({
      title: '⚠️ 모든 득템·분배 데이터 삭제',
      content: (
        <div>
          <div>DB 의 <b>모든 raid_loots · loot_shares</b> 를 삭제합니다.</div>
          <div style={{ color: '#ff4d4f', marginTop: 8 }}>
            판매금액·분배·정산 기록 전부 사라짐 · 통계 · orphan 데이터도 모두 초기화.
            <br />복구 불가.
          </div>
        </div>
      ),
      okType: 'danger',
      okText: '전부 삭제',
      cancelText: '취소',
      onOk: async () => {
        setResetting(true)
        try {
          const r = await adminApi.resetLoots()
          message.success(`삭제 완료: 득템 ${r.deletedLoots}개 · 분배 ${r.deletedShares}건`)
          qc.invalidateQueries()
        } catch (e: any) {
          message.error(e?.response?.data?.error ?? '삭제 실패')
        } finally {
          setResetting(false)
        }
      }
    })
  }

  const saveNickname = async () => {
    const trimmed = nickname.trim()
    if (!trimmed) { message.warning('닉네임 입력'); return }
    if (trimmed === user?.nickname) return
    setSaving(true)
    try {
      await authApi.changeNickname(trimmed)
      await fetchMe()
      message.success('닉네임 변경 완료')
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? '변경 실패')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>설정</h2>
      </div>

      <Card title={<><UserOutlined /> 내 정보</>} style={{ marginBottom: 12 }}>
        <Form layout="vertical" style={{ maxWidth: 400 }}>
          <Form.Item label="계정" style={{ marginBottom: 12 }}>
            <Input value={user?.account ?? ''} disabled />
          </Form.Item>
          <Form.Item label="역할" style={{ marginBottom: 12 }}>
            <Tag color={user?.role === 'MASTER' ? 'red' : user?.role === 'VICE' ? 'orange' : 'default'}>
              {user?.role === 'MASTER' ? '문주' : user?.role === 'VICE' ? '부문주' : '일반'}
            </Tag>
          </Form.Item>
          <Form.Item label="닉네임" style={{ marginBottom: 8 }}>
            <Space.Compact style={{ width: '100%' }}>
              <Input
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                maxLength={40}
                placeholder="채팅·투표·정산에 표시되는 이름"
              />
              <Button
                type="primary"
                onClick={saveNickname}
                loading={saving}
                disabled={!nickname.trim() || nickname.trim() === user?.nickname}
              >
                변경
              </Button>
            </Space.Compact>
          </Form.Item>
        </Form>
      </Card>

      <Card title={<><BellOutlined /> 푸시 알림</>} style={{ marginBottom: 12 }}>
        <div style={{ color: '#8c8c8c', fontSize: 13, marginBottom: 12 }}>
          레이드 등록, 30분 전 리마인더, 정산 완료 시 폰/PC 알림 팝업을 받을 수 있습니다.
          Android/PC 는 브라우저에서 바로 되고, iPhone 은 <b>홈 화면에 추가</b> 로 앱처럼 설치한 뒤 가능합니다 (iOS 16.4+).
        </div>

        {!ready && <div>로딩...</div>}

        {status === 'unsupported' && (
          <Alert type="warning" showIcon message="이 브라우저는 웹 푸시를 지원하지 않습니다"
                 description="Chrome / Edge / Firefox / Android · iOS 는 홈 화면에 추가 후 Safari 16.4+ 에서 가능합니다" />
        )}
        {status === 'server-disabled' && (
          <Alert type="info" showIcon message="서버에 푸시 설정이 아직 완료되지 않았습니다"
                 description="관리자가 VAPID 키를 등록하면 활성화됩니다" />
        )}
        {status === 'permission-denied' && (
          <Alert type="error" showIcon message="알림 권한이 거부됨"
                 description="브라우저 설정에서 이 사이트의 알림 권한을 '허용' 으로 바꾼 뒤 다시 시도하세요" />
        )}
        {status === 'permission-default' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Tag color="orange">비활성 상태</Tag>
            <Button type="primary" icon={<BellOutlined />} onClick={enable}>알림 허용하기</Button>
          </Space>
        )}
        {status === 'not-subscribed' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Tag color="orange">허용됐지만 구독 안 됨</Tag>
            <Button type="primary" icon={<BellOutlined />} onClick={enable}>알림 구독하기</Button>
          </Space>
        )}
        {status === 'subscribed' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Tag color="green" icon={<BellFilled />}>활성 · 이 기기에서 알림 수신 중</Tag>
            <Button onClick={disable}>이 기기 알림 끄기</Button>
          </Space>
        )}
      </Card>

      {isMaster(user) && (
        <Card title="🩺 Discord 봇 진단" style={{ marginBottom: 12 }}>
          <div style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 8 }}>
            봇 연결 상태 확인 + 알림 채널에 테스트 메시지 발송 (cooldown 중이면 스킵)
          </div>
          <Space wrap>
            <Button loading={diagLoading} onClick={runDiscordDiag}>Discord 진단 실행</Button>
            {diag && !diag.botReady && (
              <Button type="primary" onClick={reconnectBot}>봇 재연결</Button>
            )}
            {diag && diag.cooldownRemainingSec > 0 && user?.role === 'MASTER' && (
              <Button danger onClick={clearCooldown}>Cooldown 해제 ({diag.cooldownRemainingSec}s)</Button>
            )}
            <Button onClick={loadLogs}>최근 로그</Button>
          </Space>
          {diag && (
            <div style={{ marginTop: 8, fontSize: 12 }}>
              <div>JDA Status: <b>{diag.jdaStatus}</b> · Gateway Ping: <b>{diag.gatewayPingMs}ms</b></div>
              <div>Bot Ready: <b style={{ color: diag.botReady ? '#52c41a' : '#ff4d4f' }}>{String(diag.botReady)}</b></div>
              <div>Notify Channel: <b style={{ color: diag.notifyChannelReachable ? '#52c41a' : '#ff4d4f' }}>{String(diag.notifyChannelReachable)}</b> (id 설정: {String(diag.notifyChannelIdSet)})</div>
              <div>Cooldown 남음: <b style={{ color: diag.cooldownRemainingSec > 0 ? '#ff4d4f' : '#52c41a' }}>{diag.cooldownRemainingSec}s</b></div>
              <div>테스트 메시지 발송 시도: <b>{String(diag.testMessageAttempted)}</b></div>
              <div style={{ marginTop: 6, paddingTop: 6, borderTop: '1px solid #f0f0f0' }}>
                연결 시도 <b>{diag.connectAttempts}</b>회 · 재시도 루프 {diag.connectLoopRunning ? '동작중' : '정지'}
                {diag.lastConnectAttemptAt && <> · 마지막 {diag.lastConnectAttemptAt.substring(11, 19)}</>}
              </div>
              {diag.lastConnectError && (
                <Alert
                  type="error" showIcon style={{ marginTop: 6 }}
                  message="봇 연결 실패 원인"
                  description={<span style={{ fontSize: 11, wordBreak: 'break-all' }}>{diag.lastConnectError}</span>}
                />
              )}
            </div>
          )}
          {logs && (
            <div style={{ marginTop: 12, fontSize: 12 }}>
              <div>최근 1시간 총 {logs.total1h}건 · 실패 <b style={{ color: logs.fail1h > 0 ? '#ff4d4f' : '#52c41a' }}>{logs.fail1h}</b>건</div>
              <div style={{ maxHeight: 300, overflow: 'auto', marginTop: 6, border: '1px solid #f0f0f0', borderRadius: 4, padding: 4 }}>
                {logs.recent100.map(l => (
                  <div key={l.id} style={{ padding: '2px 4px', borderBottom: '1px dashed #f5f5f5', color: l.success ? '#333' : '#ff4d4f' }}>
                    #{l.id} {l.createdAt.substring(11, 19)} {l.op}/{l.kind} ref={l.refId ?? '-'} {l.trigger ?? ''} {l.success ? '✓' : '✗'} {l.latencyMs ?? '-'}ms {l.error ? '· ' + l.error.substring(0, 80) : ''}
                  </div>
                ))}
              </div>
            </div>
          )}
        </Card>
      )}

      {isMaster(user) && user?.role === 'MASTER' && (
        <Card
          title={<><DeleteOutlined style={{ color: '#ff4d4f' }} /> 위험 · 데이터 초기화</>}
          style={{ marginBottom: 12, borderColor: '#ffccc7' }}
        >
          <Alert
            type="warning" showIcon
            style={{ marginBottom: 12 }}
            message="모든 득템·분배 데이터를 삭제합니다"
            description="테스트 후 잔여 데이터·orphan 정리용. 판매금액·정산 기록 전부 사라짐. 복구 불가."
          />
          <Button danger loading={resetting} onClick={resetLoots} icon={<DeleteOutlined />}>
            모든 득템·분배 삭제
          </Button>
        </Card>
      )}

      <Card title="설치 · 홈 화면에 추가">
        <div style={{ color: '#8c8c8c', fontSize: 13 }}>
          모바일 브라우저 메뉴에서 <b>홈 화면에 추가</b> 를 선택하면 앱처럼 실행됩니다.
          <ul style={{ marginTop: 8 }}>
            <li>Android Chrome: 우상단 ⋮ → 홈 화면에 추가</li>
            <li>iOS Safari: 하단 공유 버튼 → 홈 화면에 추가</li>
            <li>설치 후에는 알림 아이콘 · 앱처럼 전체 화면</li>
          </ul>
        </div>
      </Card>
    </>
  )
}
