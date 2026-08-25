import { useEffect, useRef, useState, useCallback } from 'react'
import type { ChatMessage } from '@/types'
import { chatApi } from '@/api/client'

const WS_PATH = '/ws/chat'
const RECONNECT_DELAY_MS = 3000
const POLL_FALLBACK_MS = 8000

export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [connected, setConnected] = useState(false)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<number | null>(null)
  const pollTimer = useRef<number | null>(null)
  const lastIdRef = useRef<number>(0)
  const mountedRef = useRef(true)

  const upsert = useCallback((incoming: ChatMessage[]) => {
    if (incoming.length === 0) return
    setMessages(prev => {
      const seen = new Set(prev.map(m => m.id))
      const merged = [...prev]
      for (const m of incoming) {
        if (!seen.has(m.id)) {
          merged.push(m)
          seen.add(m.id)
          if (m.id > lastIdRef.current) lastIdRef.current = m.id
        }
      }
      merged.sort((a, b) => a.id - b.id)
      return merged
    })
  }, [])

  const startPollingFallback = useCallback(() => {
    if (pollTimer.current) return
    const tick = async () => {
      try {
        const fresh = await chatApi.since(lastIdRef.current)
        upsert(fresh)
      } catch { /* ignore */ }
      if (mountedRef.current) pollTimer.current = window.setTimeout(tick, POLL_FALLBACK_MS)
    }
    tick()
  }, [upsert])

  const stopPollingFallback = useCallback(() => {
    if (pollTimer.current) { window.clearTimeout(pollTimer.current); pollTimer.current = null }
  }, [])

  const connect = useCallback(() => {
    if (!mountedRef.current) return
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${proto}//${location.host}${WS_PATH}`
    let ws: WebSocket
    try { ws = new WebSocket(url) } catch { scheduleReconnect(); return }
    wsRef.current = ws

    ws.onopen = () => {
      setConnected(true)
      stopPollingFallback()
      // Since 로 놓친 메시지 동기화
      chatApi.since(lastIdRef.current).then(upsert).catch(() => {})
    }
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data) as ChatMessage
        upsert([msg])
      } catch {}
    }
    ws.onerror = () => { /* onclose 가 이어짐 */ }
    ws.onclose = () => {
      setConnected(false)
      wsRef.current = null
      startPollingFallback()
      scheduleReconnect()
    }
  }, [upsert, startPollingFallback, stopPollingFallback])

  function scheduleReconnect() {
    if (reconnectTimer.current) return
    reconnectTimer.current = window.setTimeout(() => {
      reconnectTimer.current = null
      connect()
    }, RECONNECT_DELAY_MS)
  }

  useEffect(() => {
    mountedRef.current = true
    // 초기 로드
    chatApi.recent(100).then(initial => {
      upsert(initial)
      connect()
    }).catch(() => connect())

    return () => {
      mountedRef.current = false
      if (reconnectTimer.current) { window.clearTimeout(reconnectTimer.current); reconnectTimer.current = null }
      stopPollingFallback()
      if (wsRef.current) { wsRef.current.close(); wsRef.current = null }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const send = useCallback(async (content: string) => {
    const trimmed = content.trim()
    if (!trimmed) return
    const msg = await chatApi.send(trimmed)
    upsert([msg])
    return msg
  }, [upsert])

  return { messages, connected, send }
}
