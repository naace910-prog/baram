import { useEffect, useState } from 'react'

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
 * 상단 고정 얇은 progress bar. axios 요청 중일 때만 표시.
 */
export default function LoadingBar() {
  const n = useLoadingCount()
  const active = n > 0
  return (
    <>
      <style>{`
        @keyframes wg-loading-slide {
          0% { transform: translateX(-100%); }
          100% { transform: translateX(400%); }
        }
      `}</style>
      <div
        style={{
          position: 'fixed', top: 0, left: 0, right: 0, height: 3,
          background: active ? 'rgba(124, 58, 237, 0.15)' : 'transparent',
          zIndex: 9999, pointerEvents: 'none',
          transition: 'background 200ms',
        }}
      >
        {active && (
          <div
            style={{
              width: '25%', height: '100%',
              background: '#7c3aed',
              boxShadow: '0 0 8px rgba(124,58,237,0.6)',
              animation: 'wg-loading-slide 1.2s cubic-bezier(0.4, 0, 0.2, 1) infinite',
            }}
          />
        )}
      </div>
    </>
  )
}
