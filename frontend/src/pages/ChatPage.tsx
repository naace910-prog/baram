import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Alert, Avatar, Button, Card, Input, Space, Tag, App as AntApp } from 'antd'
import { SendOutlined, WifiOutlined, DisconnectOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { useChat } from '@/hooks/useChat'
import { raidApi } from '@/api/client'
import type { ChatMessage, VoteType } from '@/types'
import { useAuth } from '@/store/authStore'

const DISCORD_COLOR = '#5865F2'
const MY_BUBBLE = '#7c3aed'
const OTHER_BUBBLE = '#ffffff'
const DISCORD_BUBBLE = '#eef1ff'
const CHAT_BG = '#b2c7d9' // 카톡 하늘색 배경

export default function ChatPage() {
  const { messages, connected, send } = useChat()
  const { user } = useAuth()
  const { message: toast } = AntApp.useApp()
  const nav = useNavigate()
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const [atBottom, setAtBottom] = useState(true)

  useLayoutEffect(() => {
    if (atBottom) bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages, atBottom])

  useEffect(() => {
    const el = scrollRef.current
    if (!el) return
    const onScroll = () => {
      const gap = el.scrollHeight - el.scrollTop - el.clientHeight
      setAtBottom(gap < 40)
    }
    el.addEventListener('scroll', onScroll)
    return () => el.removeEventListener('scroll', onScroll)
  }, [])

  const handleSend = async () => {
    if (!input.trim() || sending) return
    setSending(true)
    try {
      await send(input)
      setInput('')
      setAtBottom(true)
    } catch (e: any) {
      toast.error(e?.response?.data?.error ?? '전송 실패')
    } finally {
      setSending(false)
    }
  }

  const copyText = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      toast.success('복사됨')
    } catch {
      toast.error('클립보드 접근 실패')
    }
  }

  const vote = async (raidId: number, v: VoteType) => {
    try {
      await raidApi.vote(raidId, v)
      toast.success(`투표: ${v === 'YES' ? '참가' : v === 'NO' ? '불참' : '미정'}`)
    } catch (e: any) {
      toast.error(e?.response?.data?.error ?? '투표 실패')
    }
  }

  // 이전 메시지와 같은 사람 & 같은 분(minute) 이면 이름/시간 생략
  const rendered = messages.map((m, i) => {
    const prev = messages[i - 1]
    const same = prev
      && prev.origin === m.origin
      && ((m.origin === 'DISCORD' ? prev.authorDiscordId === m.authorDiscordId
          : prev.authorMemberId === m.authorMemberId))
      && dayjs(prev.createdAt).isSame(m.createdAt, 'minute')
    return { m, hideHeader: !!same }
  })

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <div className="page-header">
        <Space>
          <h2 style={{ margin: 0 }}>문파 채팅</h2>
          {connected
            ? <Tag icon={<WifiOutlined />} color="green">실시간</Tag>
            : <Tag icon={<DisconnectOutlined />} color="orange">재연결 중</Tag>}
        </Space>
      </div>

      <Card
        styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', height: 'calc(100vh - 200px)', minHeight: 400, position: 'relative' } }}
      >
        <div
          ref={scrollRef}
          style={{
            flex: 1, overflowY: 'auto', padding: '12px 12px 16px',
            display: 'flex', flexDirection: 'column', gap: 6,
            background: CHAT_BG,
          }}
        >
          {messages.length === 0 && (
            <Alert type="info" showIcon message="아직 메시지가 없습니다." />
          )}
          {rendered.map(({ m, hideHeader }) => (
            m.origin === 'SYSTEM'
              ? <SystemMessage key={m.id} msg={m} onCopy={copyText} onVote={vote} onNav={(u) => nav(u)} />
              : <MessageBubble
                  key={m.id}
                  msg={m}
                  isMe={m.authorMemberId === user?.memberId}
                  hideHeader={hideHeader}
                  onCopy={copyText}
                />
          ))}
          <div ref={bottomRef} />
        </div>

        {!atBottom && (
          <div style={{ position: 'absolute', right: 16, bottom: 68, zIndex: 2 }}>
            <Button size="small" shape="round" onClick={() => { setAtBottom(true); bottomRef.current?.scrollIntoView({ block: 'end' }) }}>
              최신으로 ↓
            </Button>
          </div>
        )}

        <div style={{ borderTop: '1px solid #f0f0f0', padding: 8, display: 'flex', gap: 8, background: '#fff' }}>
          <Input.TextArea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) { e.preventDefault(); handleSend() }
            }}
            placeholder="메시지 (Enter 전송, Shift+Enter 줄바꿈)"
            autoSize={{ minRows: 1, maxRows: 4 }}
            maxLength={2000}
            disabled={sending}
          />
          <Button type="primary" icon={<SendOutlined />} onClick={handleSend} loading={sending}>
            전송
          </Button>
        </div>
      </Card>

      <div style={{ marginTop: 4, fontSize: 11, color: '#8a8d91', textAlign: 'center' }}>
        말풍선 클릭 = 복사 · Enter 전송 · Shift+Enter 줄바꿈
      </div>
    </div>
  )
}

function SystemMessage({ msg, onCopy, onVote, onNav }: {
  msg: ChatMessage
  onCopy: (t: string) => void
  onVote: (raidId: number, v: VoteType) => void
  onNav: (url: string) => void
}) {
  const raidId = msg.actionType === 'RAID_VOTE' ? msg.actionRefId ?? undefined : undefined
  return (
    <div style={{ display: 'flex', justifyContent: 'center', margin: '6px 0' }}>
      <div
        onClick={() => onCopy(msg.content)}
        style={{
          background: 'rgba(255,255,255,0.85)',
          border: 'none', borderRadius: 12,
          padding: '8px 14px', maxWidth: '85%',
          color: '#3a3a3a', fontSize: 13, cursor: 'pointer',
          whiteSpace: 'pre-wrap', wordBreak: 'break-word',
          boxShadow: '0 1px 2px rgba(0,0,0,0.08)',
        }}
        title="클릭하면 복사"
      >
        <div>{msg.content}</div>
        {raidId != null && (
          <div style={{ marginTop: 8, display: 'flex', gap: 4, flexWrap: 'wrap' }} onClick={(e) => e.stopPropagation()}>
            <Button size="small" type="primary" onClick={() => onVote(raidId, 'YES')}>✅ 참가</Button>
            <Button size="small" danger onClick={() => onVote(raidId, 'NO')}>❌ 불참</Button>
            <Button size="small" onClick={() => onVote(raidId, 'MAYBE')}>❓ 미정</Button>
            <Button size="small" type="link" onClick={() => onNav(`/raids/${raidId}`)}>상세 →</Button>
          </div>
        )}
        <div style={{ fontSize: 10, color: '#9a9a9a', marginTop: 4, textAlign: 'right' }}>
          {dayjs(msg.createdAt).format('HH:mm')}
        </div>
      </div>
    </div>
  )
}

function MessageBubble({ msg, isMe, hideHeader, onCopy }: {
  msg: ChatMessage; isMe: boolean; hideHeader: boolean; onCopy: (t: string) => void
}) {
  const fromDiscord = msg.origin === 'DISCORD'
  const time = dayjs(msg.createdAt).format('HH:mm')

  // 내 메시지: 오른쪽 · 아바타/이름 없음 · 보라 말풍선 · 왼쪽에 시간
  if (isMe) {
    return (
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 4, alignItems: 'flex-end' }}>
        <span style={{ fontSize: 10, color: '#5a6472', marginBottom: 2 }}>{time}</span>
        <div
          onClick={() => onCopy(msg.content)}
          style={{
            background: MY_BUBBLE, color: '#fff',
            padding: '9px 13px',
            borderRadius: 16, borderTopRightRadius: 4,
            maxWidth: '70%', minWidth: 32,
            fontSize: 15, lineHeight: 1.4,
            wordBreak: 'break-word', whiteSpace: 'pre-wrap',
            cursor: 'pointer',
            boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
          }}
          title="클릭하면 복사"
        >
          {msg.content}
        </div>
      </div>
    )
  }

  // 상대 메시지: 왼쪽 · 아바타 + 이름 · 흰 or 라이트블루 말풍선 · 오른쪽에 시간
  return (
    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
      {hideHeader ? (
        <div style={{ width: 32, flexShrink: 0 }} />
      ) : (
        <Avatar
          size={32}
          style={{ background: fromDiscord ? DISCORD_COLOR : '#7c3aed', flexShrink: 0 }}
        >
          {msg.authorNickname?.[0] ?? '?'}
        </Avatar>
      )}
      <div style={{ maxWidth: '70%', minWidth: 0 }}>
        {!hideHeader && (
          <div style={{ fontSize: 12, color: '#232323', marginBottom: 2, fontWeight: 500, marginLeft: 2 }}>
            {msg.authorStarred && <span style={{ color: '#faad14', marginRight: 2 }}>⭐</span>}
            {msg.authorNickname}
            {fromDiscord && (
              <span style={{
                marginLeft: 4, fontSize: 9, padding: '1px 5px', borderRadius: 4,
                background: DISCORD_COLOR + '20', color: DISCORD_COLOR,
              }}>Discord</span>
            )}
          </div>
        )}
        <div style={{ display: 'flex', gap: 4, alignItems: 'flex-end' }}>
          <div
            onClick={() => onCopy(msg.content)}
            style={{
              background: fromDiscord ? DISCORD_BUBBLE : OTHER_BUBBLE,
              color: '#191919',
              padding: '9px 13px',
              borderRadius: 16, borderTopLeftRadius: 4,
              fontSize: 15, lineHeight: 1.4,
              wordBreak: 'break-word', whiteSpace: 'pre-wrap',
              cursor: 'pointer', minWidth: 32,
              boxShadow: '0 1px 2px rgba(0,0,0,0.08)',
            }}
            title="클릭하면 복사"
          >
            {msg.content}
          </div>
          <span style={{ fontSize: 10, color: '#5a6472', marginBottom: 2, flexShrink: 0 }}>{time}</span>
        </div>
      </div>
    </div>
  )
}
