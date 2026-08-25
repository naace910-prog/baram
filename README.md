# 바람클래식-개화 문파 웹

문파 전용 웹앱. React(모바일 반응형) + Spring Boot + H2 + Discord 봇/웹훅/OAuth.

## 기능

- **로그인**: Discord OAuth (권장) · 계정+비번(초기 세팅용)
- **레이드**: 등록·목록·상세, 예정/완료/취소 상태, 참가 투표(YES/NO/MAYBE)
- **득템·분배**: 아이템 등록, 판매금액 입력, N분의 1 자동 계산, 개별 정산 체크
- **문파원 관리**: 문주가 추가(계정+역할+Discord ID), 비번 초기화, 활성/비활성
- **대상 관리**: 해골왕/흑룡/감룡/묵룡/진룡 시드, 자유 추가
- **Discord 알림**: 레이드 등록 시 즉시 + 30분 전 자동 알림 (임베드 카드)
- **Discord 슬래시 커맨드**: `/레이드등록 대상 시간` · `/레이드목록`
- **Discord 버튼 투표**: 알림 카드의 [참가]/[불참]/[미정] 버튼 → 사이트 안 열고 바로 투표

## 로컬 개발 실행

### 필요한 것
- JDK 21+, Maven 3.9+
- Node.js 20+

### 백엔드
```bash
cd backend
mvn spring-boot:run
```
- http://localhost:8080 에서 API 서버 구동
- H2 콘솔: http://localhost:8080/h2-console  (JDBC URL: `jdbc:h2:file:./data/guild;AUTO_SERVER=TRUE;MODE=MySQL`)

### 프론트엔드
```bash
cd frontend
npm install
npm run dev
```
- http://localhost:5173 에서 접속
- 초기 문주 계정: **master / 1234** (로그인 후 즉시 변경 권장)

---

## Discord 연동 설정

Discord 알림·봇·OAuth 모두 켜려면 환경변수 5개 필요합니다.

### 1) Discord Application 생성

1. https://discord.com/developers/applications 접속
2. **New Application** → 이름 입력 (예: 바람클래식개화)
3. 왼쪽 메뉴 **Bot** → Add Bot → 봇 이름 지정
   - **Privileged Gateway Intents** 에서 **MESSAGE CONTENT INTENT** ON
   - **Reset Token** 눌러 **봇 토큰** 복사 → `DISCORD_BOT_TOKEN`
4. 왼쪽 메뉴 **OAuth2** → General
   - **CLIENT ID** 복사 → `DISCORD_CLIENT_ID`
   - **CLIENT SECRET** 복사 → `DISCORD_CLIENT_SECRET`
   - **Redirects** 추가: `http://localhost:8080/api/auth/discord/callback` (배포 시 도메인으로 변경)

### 2) 봇을 문파 서버에 초대

OAuth2 → URL Generator:
- **SCOPES**: `bot` + `applications.commands`
- **BOT PERMISSIONS**: `Send Messages`, `Embed Links`, `Read Message History`, `Use Slash Commands`
- 생성된 URL로 봇을 문파 서버에 초대

### 3) 알림 채널 ID 확인

- Discord 설정 → 고급 → 개발자 모드 ON
- 알림 받을 채널 우클릭 → **채널 ID 복사** → `DISCORD_NOTIFY_CHANNEL_ID`
- 서버(길드) 우클릭 → **서버 ID 복사** → `DISCORD_GUILD_ID`

### 4) 환경변수 설정 후 재실행

Windows PowerShell:
```powershell
$env:DISCORD_ENABLED="true"
$env:DISCORD_BOT_TOKEN="봇토큰"
$env:DISCORD_GUILD_ID="서버ID"
$env:DISCORD_NOTIFY_CHANNEL_ID="채널ID"
$env:DISCORD_CLIENT_ID="클라이언트ID"
$env:DISCORD_CLIENT_SECRET="클라이언트시크릿"
cd backend; mvn spring-boot:run
```

### 5) 문파원 Discord 연결

- 각 문파원은 Discord에서 자기 프로필 우클릭 → **사용자 ID 복사**
- 문주가 문파원 관리에서 그 ID를 등록 → 이후 그 문파원은 "Discord로 로그인" 가능

---

## 배포 옵션

### A. 로컬 상시 실행 (제일 간단)
- 문파원 중 한 명 PC에 클론
- `mvn package` → `java -jar backend/target/guild-backend-*.jar`
- 프론트는 `npm run build` → `frontend/dist` 를 Spring Boot 정적 리소스에 복사
- 공유기 포트포워딩(8080) + DDNS 필요

### B. 클라우드 무료 티어 (권장)

**Fly.io** (신용카드 등록 필요, 무료 티어 넉넉함):
```bash
fly launch  # 자동으로 Dockerfile 감지, region 선택
fly volumes create data --size 1  # H2 파일 저장용
fly secrets set DISCORD_ENABLED=true DISCORD_BOT_TOKEN=... (나머지 시크릿)
fly deploy
```

**Render.com** (신용카드 불필요, 잠자기 있음):
- New Web Service → GitHub 저장소 연결
- Build command: `cd backend && mvn -DskipTests package`
- Start command: `java -jar backend/target/guild-backend-0.0.1-SNAPSHOT.jar`
- 환경변수 6개 등록

**Railway.app**: 마찬가지로 GitHub 연결, `nixpacks` 자동 빌드

### C. 오라클 클라우드 프리 티어 VM
- Ubuntu VM 생성 → JDK 21 설치 → 위 A와 동일하게 실행 → 방화벽 8080 열기

### D. GitHub Actions로 CI/CD
- 원하시면 `.github/workflows/deploy.yml` 추가해서 push 시 자동 배포 가능

---

## 프로젝트 구조

```
wind-guild/
├── backend/                  Spring Boot 3.3 + Java 21
│   ├── pom.xml
│   ├── src/main/java/com/wind/guild/
│   │   ├── GuildApplication.java
│   │   ├── config/           Security · Discord · DataInit
│   │   ├── domain/           JPA 엔티티
│   │   ├── repository/       Spring Data
│   │   ├── service/          비즈니스 + Discord 봇/알림/OAuth
│   │   └── web/              REST 컨트롤러 + DTO
│   └── src/main/resources/
│       ├── application.yml
│       └── data.sql          (레이드 대상 5마리 시드)
└── frontend/                 React 19 + Vite + Ant Design 5
    ├── package.json
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── api/client.ts     axios (세션 쿠키)
        ├── store/authStore.ts  zustand
        ├── components/       AppLayout · ProtectedRoute
        └── pages/            8개 화면
```

## 주요 API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/auth/login` | 계정+비번 로그인 |
| GET | `/api/auth/me` | 현재 로그인 정보 |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/auth/discord/authorize-url` | Discord OAuth URL |
| GET | `/api/auth/discord/callback?code=` | OAuth 콜백 |
| GET | `/api/members` | 문파원 목록 |
| POST | `/api/members` | 문파원 추가 |
| GET | `/api/targets` | 레이드 대상 목록 |
| GET/POST | `/api/raids` | 레이드 목록/등록 |
| POST | `/api/raids/{id}/votes` | 투표 |
| POST | `/api/raids/{raidId}/loots` | 득템 등록 |
| POST | `/api/raids/{raidId}/loots/{lootId}/distribute` | N분의 1 분배 |
