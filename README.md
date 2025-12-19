# 신규 API 및 DB 확장 가이드

본 문서는 통계 조회, Gemini 기반 추천/평가, 챗봇 기능을 위해 추가된 파일과 API, DB 스키마, 테스트 방법을 정리합니다. 이 문서만으로 새 기능을 이해하고 바로 호출/테스트할 수 있도록 상세하게 설명합니다.

## 1. 추가된 파일 및 주요 기능
- **주간 통계**: `domain/stats/*` 에 주간 식단/운동 합산 API(`WeeklyStatsController`, `WeeklyStatsService`)를 추가하여 월요일~일요일 데이터를 날짜별로 반환합니다.
- **Gemini 클라이언트**: `domain/ai/service/GeminiClient`가 `gemini-2.5-flash` 엔드포인트를 호출합니다.
- **AI 추천 식단**: `domain/ai/mealplan/*`에서 사용자의 건강 정보와 주간 통계를 기반으로 하루 식단을 생성/조회합니다.
- **AI 챗봇**: `domain/ai/chat/*`에서 건강 정보 + 주간 통계를 포함한 질문을 Gemini에 전달하고, 일자별 대화 기록을 저장/조회합니다.
- **주간 영양/운동 평가**: `domain/ai/review/*`에서 주간 식단/운동 데이터를 Gemini로 평가하고 결과를 저장/조회합니다.
- **DB 스키마 추가**: `db/20251219_ai_features.sql`에 AI 관련 테이블 5종이 정의되어 있습니다.
- **MyBatis 매퍼**: `src/main/resources/mapper/ai/*.xml`에 신규 테이블용 매퍼를 추가했습니다.

## 2. API 요약표
| 기능 | Function | API Path | Header | HTTP Method |
| --- | --- | --- | --- | --- |
| 주간 통계 조회 | `getWeeklyStats` | `/api/me/stats/week?date=YYYY-MM-DD` | Authorization: Bearer | GET |
| 추천 식단 생성 | `generatePlan` | `/api/me/ai-meal-plans/generate?date=YYYY-MM-DD` | Authorization: Bearer, JSON Body | POST |
| 추천 식단 조회 | `getExistingPlan` | `/api/me/ai-meal-plans/daily?date=YYYY-MM-DD` | Authorization: Bearer | GET |
| AI 챗봇 질문 | `chat` | `/api/me/ai-chat/send?date=YYYY-MM-DD` | Authorization: Bearer, JSON Body | POST |
| 일자별 대화 조회 | `getMessages` | `/api/me/ai-chat/history?date=YYYY-MM-DD` | Authorization: Bearer | GET |
| 주간 영양 평가 생성 | `evaluate` | `/api/me/ai-nutrition-evaluations/run?date=YYYY-MM-DD` | Authorization: Bearer | POST |
| 주간 영양 평가 조회 | `get` | `/api/me/ai-nutrition-evaluations/summary?date=YYYY-MM-DD` | Authorization: Bearer | GET |
| 주간 운동 평가 생성 | `evaluate` | `/api/me/ai-workout-evaluations/run?date=YYYY-MM-DD` | Authorization: Bearer | POST |
| 주간 운동 평가 조회 | `get` | `/api/me/ai-workout-evaluations/summary?date=YYYY-MM-DD` | Authorization: Bearer | GET |

## 3. Notion 스타일 상세 설명

### (A) 주간 통계 조회
1) **개요**: 특정 날짜가 속한 주(월~일)별 식단/운동 합계를 반환합니다. 데이터가 없으면 0으로 채워집니다.
2) **요청 헤더**: Authorization = `Bearer {accessToken}` (필수)
3) **Request Body**: 없음 (`date`는 **Query Param**)
4) **Response**
- 🟩 200 OK
```json
{
  "weekStartDate": "2025-12-15",
  "weekEndDate": "2025-12-21",
  "dietStats": [
    {
      "date": "2025-12-15",
      "dayOfWeekKr": "월요일",
      "totalCarbohydrate": 120.5,
      "totalProtein": 85.2,
      "totalFat": 40.0,
      "totalCalories": 1520.3
    }
  ],
  "exerciseStats": [
    {
      "date": "2025-12-15",
      "dayOfWeekKr": "월요일",
      "totalDurationMinutes": 90.0,
      "totalCalories": 430.0
    }
  ]
}
```

### (B) AI 추천 식단 생성 (하루 1회 캐시)
1) **개요**: 건강 정보와 주간 식단 합계를 활용해 아침/점심/저녁 메뉴·칼로리·한줄평을 생성하고 저장합니다. 동일 날짜 재호출 시 저장된 결과를 반환합니다.
2) **요청 헤더**: Authorization = `Bearer {accessToken}`
3) **Request Body**: 없음 (`date`는 **Query Param**, 미입력 시 KST 기준 오늘)
4) **Response**
- 🟩 200 OK
```json
{
  "planDate": "2025-12-19",
  "generated": true,
  "generatedAt": "2025-12-19T07:30:00",
  "meals": [
    {
      "mealTime": "BREAKFAST",
      "menuDescription": "현미밥+닭가슴살+샐러드",
      "calories": 420.0,
      "highlight": "고단백 저지방"
    }
  ],
  "rawText": "...Gemini raw response..."
}
```

### (C) AI 추천 식단 조회
1) **개요**: 특정 날짜에 저장된 추천 식단을 조회합니다. 없으면 `generated=false`와 빈 목록을 반환합니다.
2) **요청 헤더**: Authorization = `Bearer {accessToken}`
3) **Request Body**: 없음 (`date`는 **Query Param**, 기본 오늘)
4) **Response**: (B)와 동일 구조, 데이터 없을 때 `generated=false`.

### (D) AI 챗봇 질문/상담
1) **개요**: 건강 정보, 주간 식단/운동 요약, 오늘 날짜·시간을 포함해 Gemini에 질문하고, 당일 대화 로그를 저장합니다.
2) **요청 헤더**: Authorization = `Bearer {accessToken}`
3) **Request Body (POST /send)**
```json
{
  "message": "오늘 점심 뭐 먹으면 좋을까?"
}
```
`date`는 **Query Param**, 기본 오늘(KST)
4) **Response** (POST/GET 동일)
```json
{
  "messageDate": "2025-12-19",
  "messages": [
    {"sender": "USER", "content": "오늘 점심...", "createdAt": "2025-12-19T08:00:00"},
    {"sender": "AI", "content": "단백질...", "createdAt": "2025-12-19T08:00:01"}
  ]
}
```

### (E) 주간 영양 평가
1) **개요**: 해당 주(월 시작)의 식단 데이터를 기준일(오늘 또는 전달된 date, 미래면 오늘)까지만 모아 Gemini로 평가 후 저장/조회합니다.
2) **요청 헤더**: Authorization = `Bearer {accessToken}`
3) **Request Body**: 없음 (`date`는 **Query Param**, 기본 오늘)
4) **Response**
```json
{
  "weekStartDate": "2025-12-15",
  "evaluationDate": "2025-12-19",
  "summary": "이번 주 단백질이 다소 부족...",
  "carbohydrateStatus": "적당",
  "proteinStatus": "부족",
  "fatStatus": "적당",
  "calorieStatus": "적당",
  "createdAt": "2025-12-19T08:05:00"
}
```

### (F) 주간 운동 평가
1) **개요**: 기준일이 속한 주의 운동 데이터를 기준일까지만 모아 Gemini로 평가하고 추천 운동을 반환/저장합니다.
2) **요청 헤더**: Authorization = `Bearer {accessToken}`
3) **Request Body**: 없음 (`date`는 **Query Param**, 기본 오늘)
4) **Response**
```json
{
  "weekStartDate": "2025-12-15",
  "evaluationDate": "2025-12-19",
  "summary": "주간 운동량이 목표 대비 적당합니다.",
  "recommendation": "주말에는 40분 걷기와 가벼운 하체 근력 운동을 권장합니다.",
  "createdAt": "2025-12-19T08:06:00"
}
```

## 4. Postman 테스트 가이드 (모든 API 공통)
1. **환경 설정**: `Authorization` 탭에서 `Bearer Token`에 액세스 토큰 입력.
2. **Base URL**: 로컬은 `http://localhost:8080` (dev 프로필 기준).
3. **쿼리 파라미터**: 각 API의 `date`를 `YYYY-MM-DD` 형식으로 지정. 미지정 시 일부 API는 오늘(KST)로 자동 처리.
4. **Body**:
   - 챗봇 질문: `POST /api/me/ai-chat/send` → Body 탭 `raw` + `application/json`으로 `{ "message": "..." }` 입력.
   - 나머지 POST는 Body 없이 전송.
5. **요청 순서 예시**:
   - `GET /api/me/stats/week?date=2025-12-19` → 주간 합계 확인.
   - `POST /api/me/ai-meal-plans/generate?date=2025-12-19` → 식단 생성.
   - `POST /api/me/ai-nutrition-evaluations/run?date=2025-12-19` → 영양 평가.
   - `POST /api/me/ai-workout-evaluations/run?date=2025-12-19` → 운동 평가.
   - `POST /api/me/ai-chat/send?date=2025-12-19` + Body → 챗봇 질의 후 `GET /api/me/ai-chat/history?date=2025-12-19` 확인.

## 5. Gemini API 키 저장 위치
- `.env` 또는 환경 변수에 `GMS_KEY`를 설정하면 `gms.api.key`로 읽혀 `gemini-2.5-flash` 엔드포인트를 호출합니다.
- 필요 시 `.env`에 아래 예시를 추가하세요 (저장소에 커밋 금지):
  ```env
  gms.api.key=YOUR_GEMINI_KEY
  # 필요 시 커스텀 엔드포인트를 바꾸려면:
  # gms.api.url=https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
  ```
- 호출 예시는 `GeminiClient`가 요청 본문을 `contents.parts.text`로 구성하며, 모델은 항상 `gemini-2.5-flash`를 사용합니다.

## 6. 신규 테이블 설명 (db/20251219_ai_features.sql)

### ai_meal_plans
| 컬럼 | 타입 | 설명 | 제약 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 식단 계획 PK | PK, AUTO_INCREMENT |
| email | VARCHAR(255) | 사용자 이메일 | FK → accounts.email, NOT NULL |
| plan_date | DATE | 계획 대상 날짜 | UNIQUE(email, plan_date) |
| prompt_context | TEXT | Gemini에 전달한 프롬프트 | NULL 허용 |
| raw_response | LONGTEXT | Gemini 원문 응답 | NULL 허용 |
| created_at | DATETIME | 생성 시각 | DEFAULT CURRENT_TIMESTAMP |

### ai_meal_plan_items
| 컬럼 | 타입 | 설명 | 제약 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 항목 PK | PK, AUTO_INCREMENT |
| meal_plan_id | BIGINT UNSIGNED | 상위 계획 ID | FK → ai_meal_plans.id ON DELETE CASCADE |
| meal_time | VARCHAR(50) | BREAKFAST/LUNCH/DINNER 등 | NULL 허용 |
| menu_description | TEXT | 추천 메뉴 | NOT NULL |
| calories | DOUBLE | 예상 칼로리 | NULL 허용 |
| highlight | VARCHAR(255) | 한줄 평 | NULL 허용 |
| created_at | DATETIME | 생성 시각 | DEFAULT CURRENT_TIMESTAMP |

### ai_chat_messages
| 컬럼 | 타입 | 설명 | 제약 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 메시지 PK | PK, AUTO_INCREMENT |
| email | VARCHAR(255) | 사용자 이메일 | FK → accounts.email |
| message_date | DATE | 대화 일자 | NOT NULL, INDEX(email, message_date, created_at) |
| sender | VARCHAR(20) | `USER` 또는 `AI` | NOT NULL |
| content | TEXT | 메시지 본문 | NOT NULL |
| created_at | DATETIME | 생성 시각 | DEFAULT CURRENT_TIMESTAMP |

### ai_nutrition_reviews
| 컬럼 | 타입 | 설명 | 제약 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 평가 PK | PK, AUTO_INCREMENT |
| email | VARCHAR(255) | 사용자 이메일 | FK → accounts.email |
| week_start_date | DATE | 해당 주 월요일 | NOT NULL |
| evaluation_date | DATE | 평가 기준일 | UNIQUE(email, evaluation_date) |
| summary | TEXT | 평가 요약 | NOT NULL |
| carbohydrate_status | VARCHAR(50) | 탄수화물 상태 | NULL 허용 |
| protein_status | VARCHAR(50) | 단백질 상태 | NULL 허용 |
| fat_status | VARCHAR(50) | 지방 상태 | NULL 허용 |
| calorie_status | VARCHAR(50) | 칼로리 상태 | NULL 허용 |
| created_at | DATETIME | 생성 시각 | DEFAULT CURRENT_TIMESTAMP |

### ai_exercise_reviews
| 컬럼 | 타입 | 설명 | 제약 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | 평가 PK | PK, AUTO_INCREMENT |
| email | VARCHAR(255) | 사용자 이메일 | FK → accounts.email |
| week_start_date | DATE | 해당 주 월요일 | NOT NULL |
| evaluation_date | DATE | 평가 기준일 | UNIQUE(email, evaluation_date) |
| summary | TEXT | 운동량 평가 요약 | NOT NULL |
| recommendation | TEXT | 추가 운동 제안 | NULL 허용 |
| created_at | DATETIME | 생성 시각 | DEFAULT CURRENT_TIMESTAMP |

---

위 내용을 참고하여 API를 호출하면, 기존 코드에 손대지 않고 로그인 사용자 기준으로 모든 데이터가 저장·조회됩니다. 테이블 생성은 `db/20251219_ai_features.sql`을 실행하면 됩니다.
