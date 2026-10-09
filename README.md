# 어반스테이 얼리체크인 데모 — 백엔드

어반스테이 얼리체크인 데모 과제의 API 서버입니다. 가상 시계, 샘플 데이터, 규칙 엔진(정오 안내, 얼리체크인 제안, 비밀번호 발송·보류, 운영자 알림)을 맡습니다. 화면은 프론트엔드 저장소에 있고, 기획은 [docs/planning.md](docs/planning.md)에 있습니다.

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| 서버 | Java 25, Spring Boot 4.1, Gradle, Spring Web MVC, Spring Data JPA, Validation, H2(메모리), Lombok |
| CI | GitHub Actions (빌드·테스트) |

## 요구 사항

- JDK 25 — 없으면 Gradle toolchain(foojay resolver)이 첫 빌드 때 자동으로 내려받습니다.

## 실행

```bash
./gradlew bootRun    # http://localhost:8080
```

시작할 때 `src/main/resources/sample-data.json`을 적재하고 가상 시계를 2026-10-10 09:30에 맞춥니다.

H2 콘솔: http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:mem:urbanstay`, 사용자 `sa`, 비밀번호 없음)

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/dashboard` | 운영 대시보드: 요약, 운영자 알림, 오늘 체크인 예약 |
| GET | `/api/clock` | 가상 시계 현재 시각 |
| POST | `/api/clock/advance` | 가상 시계 이동. 본문 `{"to": "2026-10-10T12:00:00"}`, 앞으로만·하루 이내 |
| POST | `/api/scenario/reset` | 샘플 데이터와 가상 시계를 처음 상태로 되돌림 |
| GET | `/api/reservations/{id}` | 예약 상세: 예약 정보, 이 예약의 운영자 알림, 추천 객실(요청 항목별 일치)과 제외된 객실(제외 이유) |
| POST | `/api/reservations/{id}/assignment` | 추천 객실로 배정. 본문 `{"roomNumber": "1203"}`, 정오 안내 발송 후에는 409 |
| DELETE | `/api/reservations/{id}/assignment` | 배정 취소 (배정 대기로 되돌림), 정오 안내 발송 후에는 409 |
| GET | `/api/rooms/{number}` | 객실 운영 상세: 오늘 퇴실·투숙·입실 예약, 규칙 엔진 판단 근거(조건별 충족 여부), 상태 타임라인, 발송 로그 |
| POST | `/api/rooms/{number}/checkout` | 이전 투숙객 퇴실 (퇴실 감지 목업) |
| POST | `/api/rooms/{number}/cleaning/start` | 청소 시작 (조기 퇴실 알림 해제) |
| POST | `/api/rooms/{number}/cleaning/finish` | 청소 완료 → 준비 완료. 조건이 맞으면 제안·비밀번호가 바로 나간다 |
| POST | `/api/rooms/{number}/issue` | 준비 완료 후 문제 발견: 다시 청소 중, 얼리 확정 건은 취소·환불 (화면 버튼 없음) |
| GET | `/api/check-in/{token}` | 고객 화면: 단계, 호수(정오 안내 후), 입실 시각 선택지와 요금, 비밀번호(발송 후) |
| POST | `/api/check-in/{token}/payment` | 얼리체크인 결제(목업). 본문 `{"checkInAt": "…" 또는 null(지금 바로), "expectedFee": 20000, "idempotencyKey": "…"}` |
| POST | `/api/check-in/{token}/decline` | 얼리체크인 거절 |
| POST | `/api/check-in/{token}/door` | 도어락 첫 사용(목업)으로 입실 |

고객 링크 토큰은 시작·초기화 때마다 새로 만들어지며, 정오 안내 문자 본문과 예약 상세 응답(`checkInToken`)에 들어 있습니다. 체크아웃 시각이 지나면 만료됩니다.

시계를 옮기면 그 사이의 규칙 시각(12:00, 13:30, 14:30, 15:00)을 차례로 거치며 규칙을 평가하므로, 한 번에 크게 건너뛰어도 발송 기록은 원래 시각으로 남습니다. 정책 시각은 `application.yml`의 `urbanstay.policy`에서 바꿉니다.

## 빌드·검사

```bash
./gradlew build    # 컴파일 + 테스트
```

CI(`.github/workflows/ci.yml`)는 모든 push와 pull request에서 같은 명령을 실행합니다.
