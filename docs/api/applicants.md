# 지원자 API 응답 명세 초안 v0.1 (C-4 → D, C-5)

> 작성 C(seoyeon) · 2026-11-28 · 상태: **초안, 월요일(11/30) D와 합의**
> 계산 근거: [docs/matching/scoring-rules.md](../matching/scoring-rules.md) · 팀 전체 현황은 [balance.md](balance.md)
> 목 데이터: [mock/applicants-sample.json](mock/applicants-sample.json), [mock/applicant-detail-sample.json](mock/applicant-detail-sample.json)

## 0. 화면과 API 나누기 (PR #50 D 코멘트 반영)

| 화면 | 보여주는 것 | API |
|---|---|---|
| 지원자 목록 (지원자 탭의 카드 한 줄씩) | 매칭 점수, 기여도 숫자, 추천 이유, 배지 | `GET /api/projects/{projectId}/applicants` |
| 지원자 상세 (카드 클릭) | 위 내용 + 동기, 점수 내역, 커버리지 전/후, 기술별 3색 역량 막대 | `GET /api/projects/{projectId}/applicants/{applicationId}` |

목록에는 그래프를 넣지 않음. 커버리지 전/후는 기여도와 같은 정보라 상세에서만 풀어서 보여줌.

공통: 인증 필요(JWT), **팀장 전용**. 숫자는 화면 표시용 정수(HALF_UP), 정렬은 서버가 반올림 전 값으로 끝내서 내려줌.

## 1. 지원자 목록

`GET /api/projects/{projectId}/applicants?sort=contribution&status=PENDING`

| 파라미터 | 값 | 기본 |
|---|---|---|
| `sort` | `contribution` \| `match` \| `date` | `contribution` |
| `status` | `PENDING` \| `APPROVED` \| `REJECTED` | `PENDING` |

정렬 규칙 (계산 규칙 결정 6):
- `contribution`: 기여도 desc → 매칭 점수 desc → 지원일 asc. **팀 커버리지 100이면 기여도가 없으니 `match`와 같은 순서**
- `match`: 매칭 점수 desc → 지원일 asc
- `date`: 지원일 asc (먼저 지원한 사람 위)

### 응답 200

| 필드 | 타입 | 설명 |
|---|---|---|
| `projectId` | number | |
| `sort` · `status` | string | 실제 적용된 값 |
| `teamCoverage.score` | number (0~100) | 현재 팀 커버리지 |
| `teamCoverage.fullyCovered` | boolean | true면 카드에 "요구 기술을 모두 충족한 팀" 안내 |
| `applicants[]` | array | 서버가 정렬해서 내려줌. 지원자가 없으면 빈 배열 |
| `applicants[].applicationId` | number | 상세 조회·승인·거절에 사용 |
| `applicants[].applicant` | object | `userId`, `nickname` |
| `applicants[].role` | object | `roleId`, `name` (지원한 역할) |
| `applicants[].status` | `PENDING` \| `APPROVED` \| `REJECTED` | |
| `applicants[].appliedAt` | string (ISO-8601, +09:00) | |
| `applicants[].matchScore` | number (0~100) | |
| `applicants[].contribution` | number (0~100) \| **null** | 팀 커버리지 100이면 null |
| `applicants[].reasons[]` | string[] | 추천 이유, 최대 2개 (상세는 전부) |
| `applicants[].badges[]` | string[] | 아래 배지 표 |

### 배지

| 값 | 뜻 | 화면 예시 |
|---|---|---|
| `MISSING_REQUIRED` | 지원자가 필수 기술을 안 가졌고 팀도 아직 부족 | "필수 기술 미보유" |
| `COVERED_BY_TEAM` | 지원자가 필수 기술을 안 가졌지만 팀이 이미 충족 | "팀이 충족 중" |
| `ROLE_FULL` | 지원한 역할 정원이 이미 참 (점수는 계산함) | "정원 초과" |

## 2. 지원자 상세

`GET /api/projects/{projectId}/applicants/{applicationId}`

목록 한 줄의 필드에 아래가 추가됨.

| 필드 | 타입 | 설명 |
|---|---|---|
| `role.remaining` | number | 그 역할 남은 자리 |
| `motivation` | string | 지원 동기 (500자 이내) |
| `scores` | object | `matchScore`, `skill`, `interest`, `availability` (모두 0~100) |
| `coverage.before` · `coverage.after` | number (0~100) | 지금 팀 커버리지 → 이 사람이 합류하면. 팀 100이면 둘 다 100 |
| `skills[]` | array | 프로젝트 요구 기술별 비교. 정렬은 balance와 같음(필수 먼저 → 부족한 것 먼저 → 이름) |
| `skills[].skillId` · `name` · `importance` · `requiredLevel` | | balance와 같은 뜻 |
| `skills[].applicantLevel` | number (0~4) | 지원자 레벨, 없으면 0 |
| `skills[].teamMaxLevel` | number (0~4) | 지금 팀 최고 레벨 |
| `skills[].effect` | `FILLS` \| `ALREADY_COVERED` \| `NO_GAIN` | **3색 역량 막대 색**. 아래 규칙 |
| `extraSkills[]` | array | 프로젝트가 요구하지 않은 지원자 보유 기술 (`name`, `level`). 표시만, 점수 미반영 |
| `reasons[]` | string[] | 추천 이유 전부 |

`effect` 규칙 (서버가 계산):
1. 팀이 이미 요구 레벨 이상 (`teamMaxLevel ≥ requiredLevel`) → `ALREADY_COVERED`
2. 아니고, 지원자가 팀보다 높음 (`applicantLevel > teamMaxLevel`) → `FILLS` (팀 레벨이 올라감)
3. 그 외 → `NO_GAIN` (팀도 부족한데 이 사람도 못 채움)

예: 요구 2일 때 팀 3·지원자 4 → ALREADY_COVERED, 팀 0·지원자 2 → FILLS, 팀 1·지원자 1 → NO_GAIN

## 3. 에러

| 상태 | code | 언제 |
|---|---|---|
| 400 | `INVALID_SORT` · `INVALID_STATUS` | 쿼리 값이 목록에 없음 |
| 401 | `UNAUTHORIZED` | 토큰 없음·만료 |
| 403 | `NOT_PROJECT_LEADER` | 팀장이 아님 |
| 404 | `PROJECT_NOT_FOUND` · `APPLICATION_NOT_FOUND` | 없음, 또는 지원서가 그 프로젝트 것이 아님 |

에러 본문 형식은 A의 공통 에러 응답(`{ "code", "message" }` 예정)을 따름.

## 4. 예시 (계산 규칙 예시표와 같은 숫자)

팀장(Java 3, Spring Boot 3), 요구 Java·Spring Boot·React 필수 r2, Docker 우대 r2 → 팀 커버리지 60.

| 지원자 | 매칭 | 기여도 | 커버리지 전 → 후 | 배지 |
|---|---|---|---|---|
| B (React 3, Java 1, Docker 2) | 47 | 100 | 60 → 100 | 팀이 충족 중 |
| A (Java 4, Spring Boot 4, Docker 1) | 79 | 13 | 60 → 65 | 필수 기술 미보유 |

기여도순이면 B가 위, 매칭순이면 A가 위.

## 5. D에게 확인받을 것 ❓

1. 목록 카드 추천 이유를 최대 2개로 자를지, 개수 제한 없이 주고 화면에서 자를지
2. 지원자가 많아질 때 페이지네이션이 필요한지 (MVP는 프로젝트당 지원자가 적어서 없이 시작 제안)
3. 상세 화면에 협업 온도(B)도 같이 보여줄지. 보여주면 `applicant.temperature` 추가 (매칭 점수엔 안 넣음)

## 6. 다음 단계 (Sprint 2, P1) — D 인터뷰 의견

- 기술 체크박스 필터: `GET /api/projects/{projectId}/applicants?skillIds=3,7`
    - `applicants[].passesFilter`: boolean. 체크한 기술을 전부 요구 레벨 이상 가지면 true (점수 없이 통과/미달만)
    - 정렬: 통과한 사람 먼저(기존 `sort` 그대로) → 미달은 맨 아래, 화면은 회색
    - `skillIds`가 없으면 `passesFilter` 생략
- 미달자 일괄 거절: MVP 제외 (거절 API를 여러 명용으로 새로 만들어야 함)
- 비슷한 기술 추천(요구 안 한 기술 활용): AI 임베딩 매칭 도입 때 검토