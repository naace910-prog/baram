import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Alert, Avatar, Badge, Button, Card, Input, Space, Tag, App as AntApp } from 'antd'
import { SendOutlined, WifiOutlined, DisconnectOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useChat } from '@/hooks/useChat'
import type { ChatMessage } from '@/types'
import { useAuth } from '@/store/authStore'

const DISCORD_COLOR = '#5865F2'
const SITE_COLOR = '#7c3aed'

export default function ChatPage() {
  const { messages, connected, send } = useChat()
  const { user } = useAuth()
  const { message: toast } = AntApp.useApp()
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

  return (
    <>
      <div className="page-header">
        <Space>
          <h2 style={{ margin: 0 }}>문파 채팅</h2>
          {connected
            ? <Tag icon={<WifiOutlined />} color="green">실시간 연결됨</Tag>
            : <Tag icon={<DisconnectOutlined />} color="orange">재연결 중 (폴링)</Tag>}
        </Space>
      </div>

      <Card
        styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column', height: 'calc(100vh - 200px)', minHeight: 400 } }}
      >
        <div
          ref={scrollRef}
          style={{
            flex: 1, overflowY: 'auto', padding: 12,
            display: 'flex', flexDirection: 'column', gap: 8,
            background: '#fafafa',
          }}
        >
          {messages.length === 0 && (
            <Alert type="info" showIcon message="아직 메시지가 없습니다. 첫 메시지를 남겨보세요." />
          )}
          {messages.map((m) => (
            <MessageBubble key={m.id} msg={m} isMe={m.authorMemberId === user?.memberId} />
          ))}
          <div ref={bottomRef} />
        </div>

        {!atBottom && (
          <div style={{ position: 'absolute', right: 24, bottom: 80 }}>
            <Button size="small" onClick={() => { setAtBottom(true); bottomRef.current?.scrollIntoView({ block: 'end' }) }}>
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
            placeholder="메시지 입력 (Enter 전송, Shift+Enter 줄바꿈)"
            autoSize={{ minRows: 1, maxRows: 4 }}
            maxLength={2000}
            disabled={sending}
          />
          <Button type="primary" icon={<SendOutlined />} onClick={handleSend} loading={sending}>
            전송
          </Button>
        </div>
      </Card>
    </>
  )
}

function MessageBubble({ msg, isMe }: { msg: ChatMessage; isMe: boolean }) {
  const fromDiscord = msg.origin === 'DISCORD'
  const badge = fromDiscord
    ? <Tag color={DISCORD_COLOR} style={{ margin: 0, fontSize: 10, padding: '0 4px' }}>Discord</Tag>
    : <Tag color={SITE_COLOR} style={{ margin: 0, fontSize: 10, padding: '0 4px' }}>사이트</Tag>
  const bg = isMe ? '#7c3aed' : (fromDiscord ? '#eef1ff' : '#fff')
  const color = isMe ? '#fff' : '#000'

  return (
    <div style={{ display: 'flex', flexDirection: isMe ? 'row-reverse' : 'row', gap: 8, alignItems: 'flex-start' }}>
      <Avatar size="small" style={{ background: fromDiscord ? DISCORD_COLOR : SITE_COLOR, flexShrink: 0 }}>
        {msg.authorNickname?.[0] ?? '?'}
      </Avatar>
      <div style={{ maxWidth: '75%', display: 'flex', flexDirection: 'column', alignItems: isMe ? 'flex-end' : 'flex-start' }}>
        <div style={{ fontSize: 11, color: '#8c8c8c', marginBottom: 2 }}>
          <span style={{ marginRight: 6, color: '#262626', fontWeight: 500 }}>{msg.authorNickname}</span>
          {badge}
          <span style={{ marginLeft: 6 }}>{dayjs(msg.createdAt).format('HH:mm')}</span>
        </div>
        <div
          style={{
            background: bg, color,
            padding: '6px 10px', borderRadius: 12,
            whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 14,
            border: !isMe ? '1px solid #f0f0f0' : 'none',
          }}
        >
          {msg.content}
        </div>
      </div>
    </div>
  )
}
