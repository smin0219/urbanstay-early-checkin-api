# CLAUDE.md

어반스테이 얼리체크인 데모 과제의 백엔드. 이 파일의 규칙은 이후 모든 구현에서 지킨다.
기획은 `docs/planning.md`, 화면은 프론트엔드 저장소(Next.js)가 맡는다.

## 구조

- Spring Boot 4.1 / Java 25 / Gradle(Groovy DSL), 패키지 `com.urbanstay.earlycheckin`
- `.github/workflows/ci.yml` — push / pull_request 시 빌드·테스트 (배포·Docker 없음)

## 명령어

```bash
./gradlew build      # 컴파일 + 테스트
./gradlew bootRun    # 포트 8080
```

변경 후에는 CI와 같이 `./gradlew build` 통과를 확인한다.

## 시간

- 현재 시각은 **항상 주입받은 `Clock`** 으로 구한다: `LocalDateTime.now(clock)`.
  `LocalDateTime.now()`, `Instant.now()`, `System.currentTimeMillis()` 등 시스템 시계를 직접 쓰지 않는다.
- 가상 시계 시작 시각은 **2026-10-10 09:30 (Asia/Seoul)** 고정.

## 샘플 데이터

- `sample-data.json`을 `ApplicationRunner`가 애플리케이션 시작 시 적재한다.
- 시나리오 초기화 API도 **같은 로더**를 호출한다 (적재 로직을 중복 구현하지 않는다).

## 규칙 엔진

- 스프링·DB에 의존하지 않는 **순수 클래스**로 작성한다 (어노테이션, Repository, `Clock` 빈 직접 참조 없음 — 필요한 값은 인자로 받는다).
- 스케줄러(`@Scheduled` 등)를 쓰지 않는다. 다음 시점에 평가 함수를 호출한다.
  - 시각 이동 API
  - 이벤트: 퇴실, 청소 완료, 결제
- 엔진(`rule` 패키지)은 엔티티 대신 스냅샷(`RuleContext`)을 받아 할 일(`RuleAction`)을 돌려준다. 적용(상태 변경·발송 기록)은 `evaluation.RuleEvaluationService.evaluate()`가 하므로, 새 이벤트 API도 상태를 바꾼 뒤 이 메서드를 호출한다.
- 운영자 알림과 "다음 할 일"은 저장하지 않고 현재 상태에서 매번 계산한다 (조건이 풀리면 자동 해제).

## 패키지

| 패키지 | 역할 |
| --- | --- |
| `domain` | 엔티티·enum·Repository |
| `rule` | 순수 규칙 엔진 (정책값 `CheckInPolicy`는 `application.yml`의 `urbanstay.policy`) |
| `evaluation` | 엔진 실행·결과 반영 |
| `clock` | 가상 시계, 시각 이동 API |
| `scenario` | 샘플 데이터 로더, 시나리오 초기화 API |
| `dashboard` | 화면별 조회 API. 다른 화면도 화면 단위 패키지로 추가한다 |

## 도메인 규칙

| 규칙 | 내용 |
| --- | --- |
| 정오 안내 | 12:00에 호수·입실 방법·얼리체크인 안내 발송. **비밀번호는 포함하지 않음** |
| 비밀번호 발송 | `max(14:30, 객실 준비 완료 시각)`에 발송 |
| 얼리체크인 | **14:00까지 입실 가능할 때만** 제공 |
| 얼리체크인 요금 | `올림((15:00 − 입실 시각) / 1시간) × 10,000원` (예: 13:20 입실 → 1시간 40분 → 2시간 → 20,000원) |
| 청소 지연 경고 | 13:30까지 청소 미완료 시 운영자 경고 |
| 조기 퇴실 알림 | 예정 퇴실 시각보다 30분 이상 일찍 퇴실하면 운영자 알림 |

## API

- 프론트엔드는 상대 경로 `/api/...`로 호출하고 Next.js rewrites가 이 서버로 프록시한다. 따라서 API는 `/api` 하위에 둔다.
- 오류는 `ProblemDetail`로 돌려주고 화면은 `detail`을 그대로 보여준다: 잘못된 요청 400, 현재 상태에서 할 수 없는 동작 409.

## DB

- H2 메모리 모드 (`jdbc:h2:mem:urbanstay;DB_CLOSE_DELAY=-1`), `ddl-auto: create-drop`.
- H2 콘솔: http://localhost:8080/h2-console (user `sa`, 비밀번호 없음).

## 범위 밖

MySQL, Docker, OTA·문자·결제·도어락 실연동, 로그인
