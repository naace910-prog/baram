import { useCallback, useEffect, useState } from 'react'
import { App as AntApp } from 'antd'
import { pushApi } from '@/api/client'

type Status =
  | 'unsupported'
  | 'server-disabled'
  | 'permission-denied'
  | 'permission-default'
  | 'not-subscribed'
  | 'subscribed'

function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = atob(base64)
  const output = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; ++i) output[i] = raw.charCodeAt(i)
  return output
}

function subToJson(sub: PushSubscription) {
  const key = sub.getKey ? sub.getKey('p256dh') : null
  const authKey = sub.getKey ? sub.getKey('auth') : null
  const b64 = (buf: ArrayBuffer | null) =>
    buf ? btoa(String.fromCharCode(...new Uint8Array(buf))) : ''
  return {
    endpoint: sub.endpoint,
    p256dh: b64(key),
    auth: b64(authKey),
  }
}

export function usePush() {
  const [ready, setReady] = useState(false)
  const [status, setStatus] = useState<Status>('permission-default')
  const [vapidKey, setVapidKey] = useState<string>('')
  const { message } = AntApp.useApp()

  const supported = typeof window !== 'undefined'
      && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window

  const refresh = useCallback(async () => {
    if (!supported) { setStatus('unsupported'); setReady(true); return }
    try {
      const vk = await pushApi.vapidKey()
      if (!vk.enabled || !vk.publicKey) { setStatus('server-disabled'); setReady(true); return }
      setVapidKey(vk.publicKey)
      const perm = Notification.permission
      if (perm === 'denied') { setStatus('permission-denied'); setReady(true); return }
      if (perm === 'default') { setStatus('permission-default'); setReady(true); return }
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      setStatus(sub ? 'subscribed' : 'not-subscribed')
    } catch {
      setStatus('server-disabled')
    } finally {
      setReady(true)
    }
  }, [supported])

  useEffect(() => {
    if (!supported) { setStatus('unsupported'); setReady(true); return }
    navigator.serviceWorker.register('/sw.js').then(() => refresh()).catch(() => setStatus('server-disabled'))
  }, [supported, refresh])

  const enable = useCallback(async () => {
    if (!supported) return
    try {
      const perm = await Notification.requestPermission()
      if (perm !== 'granted') { setStatus('permission-denied'); return }
      const reg = await navigator.serviceWorker.ready
      let sub = await reg.pushManager.getSubscription()
      if (!sub) {
        const key = urlBase64ToUint8Array(vapidKey)
        sub = await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: key.buffer.slice(key.byteOffset, key.byteOffset + key.byteLength) as ArrayBuffer,
        })
      }
      await pushApi.subscribe(subToJson(sub))
      setStatus('subscribed')
      message.success('알림 활성화 완료')
    } catch (e: any) {
      message.error('알림 활성화 실패: ' + (e?.message ?? e))
    }
  }, [supported, vapidKey, message])

  const disable = useCallback(async () => {
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (sub) {
        await pushApi.unsubscribe(sub.endpoint).catch(() => {})
        await sub.unsubscribe()
      }
      setStatus('not-subscribed')
      message.success('이 기기 알림 해제됨')
    } catch (e: any) {
      message.error('실패: ' + (e?.message ?? e))
    }
  }, [message])

  return { ready, status, enable, disable, refresh }
}
