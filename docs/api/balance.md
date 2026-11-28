# 팀 밸런스 API 응답 명세 초안 v0.1 (C-4 → D-4)

> 작성 C(seoyeon) · 2026-11-27 · 상태: **초안, 월요일(11/30) D와 합의**
> 계산 근거: [docs/matching/scoring-rules.md](../matching/scoring-rules.md) (Coverage(T), 팀 최고 레벨 기준)
> 목 데이터: [mock/balance-sample.json](mock/balance-sample.json) (D-4 화면은 이걸로 먼저 만들기)

## 1. 엔드포인트

`GET /api/projects/{projectId}/balance`

- 인증 필요 (JWT). **팀장 전용**
- 기준 팀원 T = 승인된 팀원(팀장 포함). 검토 중 지원자는 계산에 넣지 않음
- 승인·거절 후 화면은 이 API를 다시 호출해서 새로고침

## 2. 응답 200

| 필드 | 타입 | 설명 |
|---|---|---|
| `projectId` | number | |
| `coverage.score` | number (0~100 정수) | 팀 커버리지, 화면 표시용 HALF_UP 정수 |
| `coverage.fullyCovered` | boolean | 100이면 true. 지원자 탭의 "요구 기술을 모두 충족한 팀" 문구, 매칭 점수순 정렬 전환에 사용 |
| `skills[]` | array | 프로젝트 요구 기술(팀장이 등록한 것)별 충족 현황. **정렬: 필수 먼저 → 같은 중요도 안에서 부족한 것 먼저(MISSING → PARTIAL → FULFILLED) → 이름순**. 서버가 정렬해서 내려줌 |
| `skills[].skillId` | number | |
| `skills[].name` | string | |
| `skills[].importance` | `REQUIRED` \| `PREFERRED` | |
| `skills[].weight` | number | 필수 3, 우대 1 (설정값이라 내려줌) |
| `skills[].requiredLevel` | number (1~4) | |
| `skills[].teamMaxLevel` | number (0~4) | 팀원 중 최고 레벨, 없으면 0 |
| `skills[].fulfillment` | number (0~100 정수) | 100 × min(1, teamMaxLevel / requiredLevel). 정렬·부족 정도 판단용 |
| `skills[].status` | `FULFILLED` \| `PARTIAL` \| `MISSING` | 100 / 1~99 / 0. **역량 막대 색** (FULFILLED = 초록) |
> 역량 막대 그리는 법: 막대 길이 = `teamMaxLevel`(0~4), 요구 레벨 선 = `requiredLevel`. `status`가 FULFILLED면 초록색으로 칠하고 선은 생략. (11/27 D 의견 반영)
| `skills[].coveredBy[]` | array | 이 기술을 가진 팀원 (레벨 높은 순). `userId`, `nickname`, `level` |
| `roles[]` | array | 역할별 충원 현황 (등록 순) |
| `roles[].roleId` · `name` | number · string | |
| `roles[].capacity` · `filled` · `remaining` | number | remaining = capacity − filled (0 이상) |
| `roles[].status` | `OPEN` \| `FULL` | |
| `suggestions[]` | array | 부족 제안 문구, 최대 3개. 우선순위: 필수 기술 MISSING → 필수 PARTIAL → 남은 역할 → 우대 기술 |
| `suggestions[].type` | `SKILL_MISSING` \| `SKILL_PARTIAL` \| `ROLE_OPEN` \| `ALL_COVERED` | |
| `suggestions[].targetId` | number \| null | skillId 또는 roleId. ALL_COVERED면 null |
| `suggestions[].message` | string | 서버가 만든 한국어 문구, 화면은 그대로 표시 |
| `pendingApplicantCount` | number | 검토 중 지원자 수 ("지원자 3명 검토하기" 버튼용) |
| `calculatedAt` | string (ISO-8601, +09:00) | |

예시: 목 데이터 참고. 계산 규칙 예시표와 같은 상황이에요 (팀장 Java 3·Spring Boot 3, 요구 Java·Spring Boot·React 필수 r2, Docker 우대 r2 → 커버리지 60).

## 3. 에러

| 상태 | code | 언제 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰 없음·만료 |
| 403 | `NOT_PROJECT_LEADER` | 팀장이 아님 |
| 404 | `PROJECT_NOT_FOUND` | 프로젝트 없음 |

에러 본문 형식은 A의 공통 에러 응답(`{ "code", "message" }` 예정)을 따름.

## 4. 정한 것과 이유

- **숫자는 정수만 내려줌.** 계산 규칙상 반올림은 표시 직전에 한 번만 하고, 정렬은 서버가 원래 값으로 끝내기 때문에 프론트가 소수를 가질 이유가 없음
- **정렬·문구는 서버가 함.** 프론트와 백이 같은 규칙을 두 번 구현하지 않게
- **팀 최고 레벨 기준.** 레벨 1 세 명이 레벨 3 한 명이 되지 않음 (합산 아님)

## 5. D에게 확인받을 것 ❓

1. ~~막대 차트에 fulfillment 하나면 충분한지~~ → 결정: 역량 막대, 길이 teamMaxLevel + requiredLevel 선, 충족 시 초록 (D 코멘트)
2. ~~suggestions 문구를 서버가 만들지, 화면이 만들지~~ → 결정: 서버가 만듦. 프론트는 type으로 아이콘·색·배치를 정하고 message는 그대로 표시 (D 코멘트)
3. ~~지원자 카드 커버리지 미리보기~~ → 결정: 목록 카드는 기여도 숫자만, 지원자 상세 화면에서 커버리지 전/후와 기술별 3색(FILLS / ALREADY_COVERED / NO_GAIN) 표시. 지원자 API 명세(C-4)에서 다룸 (D 코멘트)
