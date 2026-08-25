import { Button, Card, Form, Input, App as AntApp, Divider, Alert, Tag, Space, Checkbox } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/store/authStore'
import { useEffect, useState } from 'react'
import { authApi } from '@/api/client'

const LS_ACCOUNT = 'wg.lastAccount'
const LS_PW = 'wg.lastPw'
const LS_REMEMBER_PW = 'wg.rememberPw'
const APP_VERSION = 'v1.0.10'

function readSaved() {
  try {
    const account = localStorage.getItem(LS_ACCOUNT) || ''
    const rememberPw = localStorage.getItem(LS_REMEMBER_PW) === '1'
    const password = rememberPw ? (localStorage.getItem(LS_PW) || '') : ''
    return { account, password, rememberPw }
  } catch { return { account: '', password: '', rememberPw: false } }
}

const DISCORD_COLOR = '#5865F2'
const DiscordIcon = () => (
  <svg width="18" height="18" viewBox="0 0 71 55" fill="currentColor" style={{ marginRight: 6, verticalAlign: -3 }}>
    <path d="M60.1 4.9A58.6 58.6 0 0 0 45.4.4a41 41 0 0 0-1.9 3.8 54.1 54.1 0 0 0-16 0A41 41 0 0 0 25.6.4 58.6 58.6 0 0 0 10.9 4.9C1.6 18.5-.9 31.7.4 44.8a58.9 58.9 0 0 0 18 9.1 42.5 42.5 0 0 0 3.9-6.3 38 38 0 0 1-6-2.9c.5-.4 1-.8 1.5-1.2a41.9 41.9 0 0 0 35.5 0c.5.4 1 .8 1.5 1.2a37.9 37.9 0 0 1-6 2.9 42.6 42.6 0 0 0 3.9 6.3 58.9 58.9 0 0 0 18-9.1c1.6-15.1-2.4-28.2-10.6-39.9ZM23.7 36.8c-3.5 0-6.4-3.2-6.4-7.2s2.8-7.2 6.4-7.2 6.4 3.2 6.4 7.2-2.9 7.2-6.4 7.2Zm23.6 0c-3.5 0-6.4-3.2-6.4-7.2s2.8-7.2 6.4-7.2 6.4 3.2 6.4 7.2-2.9 7.2-6.4 7.2Z" />
  </svg>
)

export default function LoginPage() {
  const { user, login } = useAuth()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const { message } = AntApp.useApp()
  const [discordEnabled, setDiscordEnabled] = useState(false)
  const [discordUrl, setDiscordUrl] = useState<string | undefined>()
  const saved = readSaved()
  const [showAccountForm, setShowAccountForm] = useState(!!saved.account)
  const [rememberPw, setRememberPw] = useState(saved.rememberPw)

  const discordError = params.get('discordError')
  const discordId = params.get('discordId')
  const discordName = params.get('discordName')
  const discordDetail = params.get('detail')

  useEffect(() => { if (user) nav('/', { replace: true }) }, [user, nav])

  useEffect(() => {
    authApi.discordAuthorizeUrl()
      .then((r) => { setDiscordEnabled(!!r.enabled); setDiscordUrl(r.url) })
      .catch(() => setDiscordEnabled(false))
  }, [])

  const onFinish = async (v: { account: string; password: string }) => {
    try {
      await login(v.account, v.password)
      try {
        localStorage.setItem(LS_ACCOUNT, v.account)
        if (rememberPw) {
          localStorage.setItem(LS_PW, v.password)
          localStorage.setItem(LS_REMEMBER_PW, '1')
        } else {
          localStorage.removeItem(LS_PW)
          localStorage.removeItem(LS_REMEMBER_PW)
        }
      } catch {}
      message.success('환영합니다')
      nav('/')
    } catch {}
  }

  return (
    <div
      style={{
        minHeight: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'center',
        padding: 16,
        background: 'radial-gradient(circle at 30% 20%, #2d1b5c 0%, #0d1128 60%, #050612 100%)',
      }}
    >
      <Card
        style={{ width: '100%', maxWidth: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.5)' }}
        styles={{ body: { padding: '32px 28px' } }}
      >
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ fontSize: 48, lineHeight: 1, marginBottom: 8 }}>🌸</div>
          <h1 style={{ margin: '0 0 4px', color: '#7c3aed', fontSize: 24, letterSpacing: '-0.5px' }}>
            바람클래식 · 개화
          </h1>
          <p style={{ margin: 0, color: '#8c8c8c', fontSize: 13 }}>문파 전용 관리 시스템</p>
        </div>

        {discordError === 'NOT_GUILD_MEMBER' && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="문파 디스코드 서버 멤버가 아닙니다"
            description={<div>
              <div><b>{discordName}</b> 로 로그인 시도했습니다.</div>
              <div style={{ marginTop: 6 }}>
                이 사이트는 <b>바람클래식-개화 문파 디스코드 서버</b> 멤버만 이용할 수 있습니다.
                먼저 문파 디스코드 서버에 참여한 뒤 다시 시도해주세요.
              </div>
            </div>}
          />
        )}
        {discordError === 'NOT_REGISTERED' && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="아직 등록되지 않은 디스코드 계정"
            description={<div>
              <div><b>{discordName}</b> 로 로그인 시도했습니다.</div>
              <div style={{ marginTop: 6 }}>문주에게 아래 <b>디스코드 ID</b>를 알려주고 문파원 등록 요청하세요:</div>
              <div style={{ marginTop: 6 }}>
                <Tag color="blue" style={{ fontSize: 14, padding: '4px 8px' }}>{discordId}</Tag>
              </div>
            </div>}
          />
        )}
        {discordError === 'INACTIVE' && (
          <Alert type="error" showIcon message="비활성 계정입니다. 문주에게 문의하세요." style={{ marginBottom: 12 }} />
        )}
        {discordError === 'GUILD_NOT_CONFIGURED' && (
          <Alert type="error" showIcon message="서버 설정 오류: DISCORD_GUILD_ID 미설정. 문주에게 문의하세요." style={{ marginBottom: 12 }} />
        )}
        {discordError && !['NOT_GUILD_MEMBER', 'NOT_REGISTERED', 'INACTIVE', 'GUILD_NOT_CONFIGURED'].includes(discordError) && (
          <Alert
            type="error"
            showIcon
            message={`디스코드 로그인 실패: ${discordError}`}
            description={discordDetail ? <div style={{ wordBreak: 'break-all' }}><b>상세:</b> {discordDetail}</div> : undefined}
            style={{ marginBottom: 12 }}
          />
        )}

        {discordEnabled && discordUrl ? (
          <Button
            block size="large" icon={<DiscordIcon />}
            href={discordUrl}
            style={{ background: DISCORD_COLOR, borderColor: DISCORD_COLOR, color: '#fff' }}
          >
            Discord로 로그인
          </Button>
        ) : (
          <Alert
            type="info" showIcon style={{ marginBottom: 12 }}
            message="Discord OAuth이 아직 설정되지 않았습니다"
            description="README 참고: DISCORD_CLIENT_ID / DISCORD_CLIENT_SECRET 환경변수 설정 필요"
          />
        )}

        <Divider style={{ margin: '16px 0' }}>또는</Divider>

        {!showAccountForm ? (
          <Button block type="link" onClick={() => setShowAccountForm(true)}>
            계정 · 비밀번호로 로그인 (문주 초기 세팅용)
          </Button>
        ) : (
          <Form
            layout="vertical"
            onFinish={onFinish}
            initialValues={{
              account: saved.account || '',
              password: saved.password || '',
            }}
            autoComplete="on"
          >
            <Form.Item name="account" label="계정" rules={[{ required: true, message: '계정 입력' }]}>
              <Input size="large" autoFocus autoComplete="username" name="account" />
            </Form.Item>
            <Form.Item name="password" label="비밀번호" rules={[{ required: true, message: '비밀번호 입력' }]}>
              <Input.Password size="large" autoComplete="current-password" name="password" />
            </Form.Item>
            <Form.Item style={{ marginBottom: 8 }}>
              <Checkbox checked={rememberPw} onChange={(e) => setRememberPw(e.target.checked)}>
                이 기기에 비밀번호도 저장 (자동 로그인 편의)
              </Checkbox>
            </Form.Item>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button type="primary" htmlType="submit" size="large" block>로그인</Button>
              <Button block type="link" onClick={() => setShowAccountForm(false)}>돌아가기</Button>
            </Space>
          </Form>
        )}

        <p style={{ marginTop: 12, fontSize: 12, color: '#999', textAlign: 'center' }}>
          문파원이 아닌 경우 문주에게 등록 요청하세요
          <br />
          <span style={{ fontSize: 10, color: '#bfbfbf' }}>{APP_VERSION}</span>
        </p>
      </Card>
    </div>
  )
}
