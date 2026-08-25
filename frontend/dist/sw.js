// 바람클래식-개화 문파 - Service Worker (Web Push 수신)
self.addEventListener('install', (e) => { self.skipWaiting() })
self.addEventListener('activate', (e) => { e.waitUntil(self.clients.claim()) })

self.addEventListener('push', (event) => {
  let data = { title: '알림', body: '', url: '/' }
  try {
    if (event.data) {
      const parsed = event.data.json()
      data = { ...data, ...parsed }
    }
  } catch (e) {
    data.body = event.data ? event.data.text() : ''
  }
  const opts = {
    body: data.body,
    icon: '/manifest-icon.png',
    badge: '/manifest-icon.png',
    data: { url: data.url },
    tag: data.tag || undefined,
    renotify: false,
    requireInteraction: false,
  }
  event.waitUntil(self.registration.showNotification(data.title, opts))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/'
  event.waitUntil((async () => {
    const clientsList = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const c of clientsList) {
      if ('focus' in c) {
        try {
          await c.focus()
          if ('navigate' in c) await c.navigate(url)
          return
        } catch (e) {}
      }
    }
    if (self.clients.openWindow) await self.clients.openWindow(url)
  })())
})
