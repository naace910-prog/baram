import { useEffect, useState } from 'react'
import { Spin } from 'antd'
import { LoadingOutlined } from '@ant-design/icons'

let counter = 0
const listeners = new Set<() => void>()

export function beginLoad() {
  counter++
  listeners.forEach(l => l())
}

export function endLoad() {
  counter = Math.max(0, counter - 1)
  listeners.forEach(l => l())
}

function useLoadingCount(): number {
  const [n, set] = useState(counter)
  useEffect(() => {
    const l = () => set(counter)
    listeners.add(l)
    return () => { listeners.delete(l) }
  }, [])
  return n
}

/**
 * 화면 중앙 빙글빙글 스피너. axios 요청 중일 때만 표시.
 * 짧은 요청(150ms 이내)엔 표시하지 않아 깜빡임 방지.
 */
export default function LoadingBar() {
  const n = useLoadingCount()
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (n > 0) {
      const t = setTimeout(() => setVisible(true), 150)
      return () => clearTimeout(t)
    } else {
      setVisible(false)
    }
  }, [n])

  if (!visible) return null

  return (
    <div
      style={{
        position: 'fixed', inset: 0,
        display: 'flex', justifyContent: 'center', alignItems: 'center',
        zIndex: 9999, pointerEvents: 'none',
        background: 'rgba(0, 0, 0, 0.08)',
      }}
    >
      <div
        style={{
          background: '#fff',
          borderRadius: 12,
          padding: '16px 20px',
          boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
          display: 'flex', alignItems: 'center', gap: 12,
        }}
      >
        <Spin indicator={<LoadingOutlined style={{ fontSize: 28, color: '#7c3aed' }} spin />} />
        <span style={{ color: '#595959', fontSize: 13 }}>불러오는 중…</span>
      </div>
    </div>
  )
}
