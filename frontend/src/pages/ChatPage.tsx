import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Alert, Avatar, Button, Card, Input, Space, Tag, App as AntApp, Tooltip } from 'antd'
import { SendOutlined, WifiOutlined, DisconnectOutlined, CopyOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useNavigate } from 'react-router-dom'
import { useChat } from '@/hooks/useChat'
import { raidApi } from '@/api/client'
import type { ChatMessage, VoteType } from '@/types'
import { useAuth } from '@/store/authStore'

const DISCORD_COLOR = '#5865F2'
const SITE_COLOR = '#7c3aed'

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
            flex: 1, overflowY: 'auto', padding: 12,
            display: 'flex', flexDirection: 'column', gap: 10,
            background: '#fafafa',
          }}
        >
          {messages.length === 0 && (
            <Alert type="info" showIcon message="아직 메시지가 없습니다." />
          )}
          {messages.map((m) => (
            m.origin === 'SYSTEM'
              ? <SystemMessage key={m.id} msg={m} onCopy={copyText} onVote={vote} onNav={(u) => nav(u)} />
              : <MessageBubble key={m.id} msg={m} isMe={m.authorMemberId === user?.memberId} onCopy={copyText} />
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
    <div style={{ display: 'flex', justifyContent: 'center', margin: '4px 0' }}>
      <div
        style={{
          background: '#fff', border: '1px dashed #d9d9d9', borderRadius: 8,
          padding: '8px 12px', maxWidth: '90%', color: '#595959', fontSize: 13,
          whiteSpace: 'pre-wrap', wordBreak: 'break-word',
        }}
      >
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>{msg.content}</div>
          <Tooltip title="복사">
            <Button type="text" size="small" icon={<CopyOutlined />} onClick={() => onCopy(msg.content)} />
          </Tooltip>
        </div>
        {raidId != null && (
          <Space wrap size={4} style={{ marginTop: 6 }}>
            <Button size="small" type="primary" onClick={() => onVote(raidId, 'YES')}>✅ 참가</Button>
            <Button size="small" danger onClick={() => onVote(raidId, 'NO')}>❌ 불참</Button>
            <Button size="small" onClick={() => onVote(raidId, 'MAYBE')}>❓ 미정</Button>
            <Button size="small" type="link" onClick={() => onNav(`/raids/${raidId}`)}>상세보기 →</Button>
          </Space>
        )}
        <div style={{ fontSize: 10, color: '#bfbfbf', marginTop: 4, textAlign: 'right' }}>
          {dayjs(msg.createdAt).format('HH:mm')}
        </div>
      </div>
    </div>
  )
}

function MessageBubble({ msg, isMe, onCopy }: { msg: ChatMessage; isMe: boolean; onCopy: (t: string) => void }) {
  const fromDiscord = msg.origin === 'DISCORD'
  const bg = isMe ? '#7c3aed' : (fromDiscord ? '#eef1ff' : '#fff')
  const color = isMe ? '#fff' : '#262626'
  const border = !isMe ? '1px solid #e4e6eb' : 'none'
  const originLabel = fromDiscord ? 'Discord' : '사이트'
  const originColor = fromDiscord ? DISCORD_COLOR : SITE_COLOR

  return (
    <div style={{ display: 'flex', flexDirection: isMe ? 'row-reverse' : 'row', gap: 8, alignItems: 'flex-end', width: '100%' }}>
      <Avatar size={32} style={{ background: originColor, flexShrink: 0 }}>
        {msg.authorNickname?.[0] ?? '?'}
      </Avatar>

      <div style={{
        display: 'flex', flexDirection: 'column',
        alignItems: isMe ? 'flex-end' : 'flex-start',
        maxWidth: 'calc(100% - 60px)', minWidth: 0,
      }}>
        {/* 이름 · 배지 (시간은 말풍선 옆으로 이동) */}
        <div style={{
          fontSize: 11, color: '#606770', marginBottom: 3,
          display: 'flex', gap: 5, alignItems: 'center',
          flexDirection: isMe ? 'row-reverse' : 'row',
        }}>
          <span style={{ color: '#050505', fontWeight: 600 }}>{msg.authorNickname}</span>
          <span style={{
            fontSize: 9, padding: '1px 5px', borderRadius: 4,
            background: originColor + '20', color: originColor,
          }}>{originLabel}</span>
        </div>

        {/* 말풍선 · 시간 · 복사 */}
        <div style={{
          display: 'flex', alignItems: 'flex-end', gap: 6,
          flexDirection: isMe ? 'row-reverse' : 'row',
        }}>
          <div style={{
            background: bg, color, border,
            padding: '8px 12px', borderRadius: 16,
            borderBottomRightRadius: isMe ? 4 : 16,
            borderBottomLeftRadius: !isMe ? 4 : 16,
            whiteSpace: 'pre-wrap', wordBreak: 'break-word', fontSize: 14,
            lineHeight: 1.4, minWidth: 32,
            boxShadow: isMe ? 'none' : '0 1px 2px rgba(0,0,0,0.05)',
          }}>
            {msg.content}
          </div>
          <div style={{
            display: 'flex', flexDirection: 'column',
            alignItems: isMe ? 'flex-end' : 'flex-start',
            gap: 2, fontSize: 10, color: '#8a8d91', flexShrink: 0,
          }}>
            <Tooltip title="복사">
              <Button
                type="text" size="small"
                icon={<CopyOutlined style={{ fontSize: 12, color: '#8a8d91' }} />}
                onClick={() => onCopy(msg.content)}
                style={{ padding: 2, height: 'auto', minWidth: 'auto' }}
              />
            </Tooltip>
            <span>{dayjs(msg.createdAt).format('HH:mm')}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
