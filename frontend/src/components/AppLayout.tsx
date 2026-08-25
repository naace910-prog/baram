import { Layout, Menu, Drawer, Button, Grid, Dropdown, Avatar } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import {
  MenuOutlined, DashboardOutlined, ThunderboltOutlined, UserOutlined, AimOutlined, LogoutOutlined, BarChartOutlined, TeamOutlined
} from '@ant-design/icons'
import { useAuth, isMaster } from '@/store/authStore'

const { Header, Content, Sider } = Layout
const { useBreakpoint } = Grid

export default function AppLayout() {
  const screens = useBreakpoint()
  const nav = useNavigate()
  const loc = useLocation()
  const { user, logout } = useAuth()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const items = [
    { key: '/', icon: <DashboardOutlined />, label: '대시보드' },
    { key: '/raids', icon: <ThunderboltOutlined />, label: '레이드' },
    { key: '/stats', icon: <BarChartOutlined />, label: '통계' },
    ...(isMaster(user)
      ? [
          { key: '/members', icon: <UserOutlined />, label: '문파원' },
          { key: '/targets', icon: <AimOutlined />, label: '대상' },
          { key: '/party-roles', icon: <TeamOutlined />, label: '파티 역할' },
        ]
      : []),
  ]

  const goto = (key: string) => {
    setDrawerOpen(false)
    nav(key)
  }

  const userMenu = {
    items: [
      { key: 'logout', icon: <LogoutOutlined />, label: '로그아웃', onClick: async () => { await logout(); nav('/login') } },
    ],
  }

  const currentKey = '/' + (loc.pathname.split('/')[1] || '')

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 12px',
          background: '#141414',
          position: 'sticky',
          top: 0,
          zIndex: 10,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {!screens.md && (
            <Button
              type="text"
              icon={<MenuOutlined style={{ color: '#fff', fontSize: 18 }} />}
              onClick={() => setDrawerOpen(true)}
            />
          )}
          <div style={{ color: '#fff', fontWeight: 700, fontSize: 16 }}>
            바람클래식-개화
          </div>
        </div>
        <Dropdown menu={userMenu} placement="bottomRight">
          <div style={{ color: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
            <Avatar size="small" style={{ background: '#7c3aed' }}>{user?.nickname?.[0] ?? '?'}</Avatar>
            <span className="desktop-only">{user?.nickname} ({user?.role})</span>
          </div>
        </Dropdown>
      </Header>
      <Layout>
        {screens.md ? (
          <Sider width={200} theme="light" breakpoint="md" collapsedWidth={0}>
            <Menu
              mode="inline"
              items={items}
              selectedKeys={[currentKey === '/' ? '/' : currentKey]}
              onClick={(e) => goto(e.key)}
              style={{ borderRight: 0, paddingTop: 12 }}
            />
          </Sider>
        ) : (
          <Drawer
            title="메뉴"
            placement="left"
            open={drawerOpen}
            onClose={() => setDrawerOpen(false)}
            width={240}
            styles={{ body: { padding: 0 } }}
          >
            <Menu
              mode="inline"
              items={items}
              selectedKeys={[currentKey === '/' ? '/' : currentKey]}
              onClick={(e) => goto(e.key)}
            />
          </Drawer>
        )}
        <Content>
          <div className="page">
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}
