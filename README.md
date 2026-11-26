# commitMate

## 실행 방법
### 준비물
- JDK 25, Node.js 26, Docker Desktop

### 1. DB 실행
`.env.example`을 복사해 `.env`를 만들고 비밀번호를 채운 뒤:
docker compose up -d

### 2. 백엔드
`backend/src/main/resources/application-local.yml.example`을 복사해
`application-local.yml`을 만들고 비밀번호를 채운 뒤, IntelliJ에서 backend 폴더를 열어 실행

### 3. 프론트엔드
cd frontend
npm install
npm run dev
# commitMate