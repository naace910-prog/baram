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

### C. Oracle Cloud Free VM (권장 · 무료 상시)

Oracle Cloud Always Free 는 **Linux VM만 무료**입니다 (Windows Server는 유료).
Ubuntu 22.04 기준 절차 — 이대로 하면 SSL·자동재시작·리버스프록시까지 완성됩니다.

#### C-1. Oracle Cloud VM 만들기

1. https://cloud.oracle.com 접속 → 계정 만들기 (신용카드 검증 필요, 청구 없음)
2. Compute → Instances → **Create Instance**
   - Image: **Canonical Ubuntu 22.04**
   - Shape: **VM.Standard.A1.Flex** (Always Free · ARM · 4 OCPU / 24GB RAM)
     - 이게 안 뜨면 **VM.Standard.E2.1.Micro** (AMD · 1 OCPU / 1GB RAM)
   - Networking: Assign a public IPv4 address ✅
   - SSH keys: Generate a key pair for me → **private key 다운로드해서 안전한 곳에 저장**
3. 인스턴스 생성되면 **Public IP** 복사

#### C-2. Security List (방화벽) 열기

Networking → Virtual Cloud Networks → VCN 선택 → Security Lists → Default → **Add Ingress Rules**

| Source CIDR | Protocol | Port | 용도 |
|---|---|---|---|
| 0.0.0.0/0 | TCP | 80  | HTTP |
| 0.0.0.0/0 | TCP | 443 | HTTPS |

(8080은 열지 않음 · nginx가 내부에서 프록시)

#### C-3. VM 접속 & 필수 패키지 설치

로컬(내 PC)에서:
```bash
chmod 400 ~/Downloads/ssh-key-*.key
ssh -i ~/Downloads/ssh-key-*.key ubuntu@VM공인IP
```

VM 안에서:
```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-21-jdk maven git nginx certbot python3-certbot-nginx
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Ubuntu 자체 방화벽도 열기 (Oracle Security List와 별개)
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw --force enable

java -version   # openjdk 21 확인
node -v         # v20 확인
```

#### C-4. 코드 받기 · 빌드

```bash
cd /opt
sudo git clone https://github.com/naace910-prog/baram.git wind-guild
sudo chown -R ubuntu:ubuntu wind-guild
cd wind-guild

# 프론트 빌드 → Spring Boot static 폴더에 복사
cd frontend
npm ci
npm run build
mkdir -p ../backend/src/main/resources/static
cp -r dist/* ../backend/src/main/resources/static/

# 백엔드 fat jar 빌드
cd ../backend
mvn -DskipTests package
ls -lh target/*.jar   # guild-backend-0.0.1-SNAPSHOT.jar 확인
```

#### C-5. 환경변수 파일

```bash
sudo mkdir -p /etc/wind-guild
sudo tee /etc/wind-guild/env > /dev/null <<'EOF'
DISCORD_ENABLED=true
DISCORD_BOT_TOKEN=여기에봇토큰
DISCORD_GUILD_ID=여기에서버ID
DISCORD_NOTIFY_CHANNEL_ID=여기에채널ID
DISCORD_CLIENT_ID=여기에클라이언트ID
DISCORD_CLIENT_SECRET=여기에클라이언트시크릿
DISCORD_OAUTH_REDIRECT_URI=https://내도메인/api/auth/discord/callback
DISCORD_OAUTH_SUCCESS_REDIRECT=https://내도메인/
SITE_BASE_URL=https://내도메인
EOF
sudo chmod 600 /etc/wind-guild/env
```

⚠ Discord Developer Portal → OAuth2 → Redirects 에도 **정확히 같은 URL** 추가해야 합니다.

#### C-6. systemd 서비스로 등록 (자동 재시작·부팅시 시작)

```bash
sudo tee /etc/systemd/system/wind-guild.service > /dev/null <<'EOF'
[Unit]
Description=Wind Guild Web
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/wind-guild/backend
EnvironmentFile=/etc/wind-guild/env
ExecStart=/usr/bin/java -Xmx512m -jar /opt/wind-guild/backend/target/guild-backend-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=append:/var/log/wind-guild.log
StandardError=append:/var/log/wind-guild.log

[Install]
WantedBy=multi-user.target
EOF

sudo touch /var/log/wind-guild.log
sudo chown ubuntu:ubuntu /var/log/wind-guild.log

sudo systemctl daemon-reload
sudo systemctl enable wind-guild
sudo systemctl start wind-guild
sudo systemctl status wind-guild    # active (running) 확인
tail -f /var/log/wind-guild.log     # Ctrl+C 로 나가기
```

이제 `curl http://localhost:8080/api/targets` 로 로컬 확인 가능.

#### C-7. Nginx 리버스 프록시 (도메인 없이 IP만 쓸 때)

```bash
sudo tee /etc/nginx/sites-available/wind-guild > /dev/null <<'EOF'
server {
    listen 80 default_server;
    server_name _;

    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/wind-guild /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

이제 브라우저에서 `http://VM공인IP` 접속. ✅

#### C-8. 도메인 + SSL (선택, 하지만 Discord OAuth 쓰려면 사실상 필수)

무료 도메인: https://www.duckdns.org (예: `windguild.duckdns.org`) — 회원가입 후 subdomain 만들고 VM IP 등록.

또는 유료 도메인(가비아·후이즈 등)에서 A 레코드 → VM IP.

도메인 연결 후:
```bash
sudo sed -i 's/server_name _;/server_name windguild.duckdns.org;/' /etc/nginx/sites-available/wind-guild
sudo systemctl reload nginx

sudo certbot --nginx -d windguild.duckdns.org
# 이메일 입력, 약관 동의, HTTP → HTTPS 리다이렉트 예 선택
# 자동으로 nginx conf에 SSL 세팅됨, 90일마다 자동 갱신
```

이제 https://windguild.duckdns.org 접속 가능.

`/etc/wind-guild/env` 의 `SITE_BASE_URL`·`DISCORD_OAUTH_REDIRECT_URI`·`DISCORD_OAUTH_SUCCESS_REDIRECT` 를 https 도메인으로 바꾸고 재시작:
```bash
sudo systemctl restart wind-guild
```

Discord Developer Portal의 Redirects에도 도메인 URL 추가 필수.

#### C-9. 코드 업데이트 절차

로컬에서 push한 뒤 VM에서:
```bash
cd /opt/wind-guild
git pull
cd frontend && npm ci && npm run build && cp -r dist/* ../backend/src/main/resources/static/
cd ../backend && mvn -DskipTests package
sudo systemctl restart wind-guild
```

한 줄로 하고 싶으면 아래 스크립트를 `deploy.sh` 로 저장 후 `bash deploy.sh` 로 실행.

#### C-10. 로그 확인 · 문제 해결

```bash
tail -f /var/log/wind-guild.log         # 앱 로그
sudo journalctl -u wind-guild -f        # systemd 로그
sudo systemctl status wind-guild        # 상태
sudo nginx -t                           # nginx conf 검증
sudo tail -f /var/log/nginx/error.log   # nginx 로그
```

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
