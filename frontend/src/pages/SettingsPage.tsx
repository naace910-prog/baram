import { Alert, Button, Card, Space, Tag } from 'antd'
import { BellOutlined, BellFilled } from '@ant-design/icons'
import { usePush } from '@/hooks/usePush'

export default function SettingsPage() {
  const { ready, status, enable, disable } = usePush()

  return (
    <>
      <div className="page-header">
        <h2 style={{ margin: 0 }}>설정</h2>
      </div>

      <Card title="푸시 알림" style={{ marginBottom: 12 }}>
        <div style={{ color: '#8c8c8c', fontSize: 13, marginBottom: 12 }}>
          레이드 등록, 30분 전 리마인더, 정산 완료 시 폰/PC 알림 팝업을 받을 수 있습니다.
          Android/PC 는 브라우저에서 바로 되고, iPhone 은 **홈 화면에 추가** 로 앱처럼 설치한 뒤 가능합니다 (iOS 16.4+).
        </div>

        {!ready && <div>로딩...</div>}

        {status === 'unsupported' && (
          <Alert type="warning" showIcon message="이 브라우저는 웹 푸시를 지원하지 않습니다"
                 description="Chrome / Edge / Firefox / Android · iOS 는 홈 화면에 추가 후 Safari 16.4+ 에서 가능합니다" />
        )}
        {status === 'server-disabled' && (
          <Alert type="info" showIcon message="서버에 푸시 설정이 아직 완료되지 않았습니다"
                 description="관리자가 VAPID 키를 등록하면 활성화됩니다" />
        )}
        {status === 'permission-denied' && (
          <Alert type="error" showIcon message="알림 권한이 거부됨"
                 description="브라우저 설정에서 이 사이트의 알림 권한을 '허용' 으로 바꾼 뒤 다시 시도하세요" />
        )}
        {status === 'permission-default' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Tag color="orange">비활성 상태</Tag>
            <Button type="primary" icon={<BellOutlined />} onClick={enable}>알림 허용하기</Button>
          </Space>
        )}
        {status === 'not-subscribed' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Tag color="orange">허용됐지만 구독 안 됨</Tag>
            <Button type="primary" icon={<BellOutlined />} onClick={enable}>알림 구독하기</Button>
          </Space>
        )}
        {status === 'subscribed' && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Tag color="green" icon={<BellFilled />}>활성 · 이 기기에서 알림 수신 중</Tag>
            <Button onClick={disable}>이 기기 알림 끄기</Button>
          </Space>
        )}
      </Card>

      <Card title="설치 · 홈 화면에 추가">
        <div style={{ color: '#8c8c8c', fontSize: 13 }}>
          모바일 브라우저 메뉴에서 <b>홈 화면에 추가</b> 를 선택하면 앱처럼 실행됩니다.
          <ul style={{ marginTop: 8 }}>
            <li>Android Chrome: 우상단 ⋮ → 홈 화면에 추가</li>
            <li>iOS Safari: 하단 공유 버튼 → 홈 화면에 추가</li>
            <li>설치 후에는 알림 아이콘 · 앱처럼 전체 화면</li>
          </ul>
        </div>
      </Card>
    </>
  )
}
