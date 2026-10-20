# 행동 분석 연동에 대한 Chat 계약

## 역할

전체 면접 영상은 Web → API → S3 경로로 업로드한다.
API가 영상과 면접 식별자를 연결하고 Analysis에 분석을 요청한다.
Chat은 기존 질문·답변 기록을 API에 전달한다.
영상 업로드와 행동 분석 완료를 기다리는 단계는 Chat에 추가하지 않는다.
텍스트 면접도 동일한 종료 경로를 사용한다.

## 식별자와 중복 준비

- 준비 요청의 `sessionId`, `interviewId`, `userId`를 세션과 준비 응답에 그대로 보존한다.
- SOLO와 MULTI 모두 종료 본문에 동일 식별자와 `mode`를 전달한다.
- 같은 세션과 같은 면접·사용자로 준비를 재시도하면 기존 진행 상태를 반환한다.
- 같은 `sessionId`에 다른 `interviewId`나 `userId`를 보내면 HTTP 409 `ALREADY_IN_USE`로 거절한다.
- 식별자가 충돌한 요청은 기존 세션을 덮어쓰거나 TTS를 다시 등록하지 않는다.

## 종료와 전송

자동 종료·명시적 완료·중단은 기존 최종 기록 보존 경로를 사용한다.
마지막 답변을 저장한 후 최종 본문을 전송 대기 저장소에 보존한다.
API 타임아웃은 기존 정책에 따라 대기 기록을 보존하고 종료 응답을 반환한다.
재전송은 최초 직렬화 본문을 사용하므로 식별자와 답변 시각이 바뀌지 않는다.
중복 답변·완료·중단 요청은 기존 종료 상태를 반환한다.
인증 오류 등 재시도 불가능한 API 실패는 기존 오류 정책을 유지한다.

종료 본문의 최상위 필드는 다음과 같다.
`sessionId`, `interviewId`, `userId`, `status`, `mode`, `interviewCreatedAt`, `qnaRequests`.
영상 URL·행동 지표·landmark·모델 버전을 Redis 세션이나 종료 본문·AI 프롬프트에 추가하지 않는다.

## 회귀 검증

`ChatInterviewIdentityTest`의 8개 사례가 준비 식별자 보존·동일 요청 재시도·식별자 충돌 거절을 검증한다.
`BehaviorInterviewFlowTest`의 8개 사례가 SOLO/MULTI의 자동 종료·명시적 완료·중단과 타임아웃 재전송을 검증한다.
후자는 실제 Redis 직렬화기·Outbox·Delivery 서비스를 연결하고 Redis I/O와 API 클라이언트를 대역으로 사용한다.
기존 `InterviewResultRedisTest`는 실제 로컬 Redis를 사용하여 장애 복구와 잠금 동작을 검증한다.

검증 명령은 Java 21에서 `./gradlew test`다.
기존 식별자 보강 당시에는 `./gradlew --offline --no-daemon test`로 전체 263개 테스트가 통과했고 실패·건너뜀은 없었다.
수정 전에는 식별자 충돌 거절 4개 사례가 실패하는 것도 확인했다.
S3 업로드 순서와 API 분석 요청·콜백·Web 중복 업로드 방지는 해당 서버의 통합 검증 대상이다.
질문별 영상 시간축과 실시간 행동 분석은 이번 Chat 계약에 포함하지 않는다.

## 2026-10-21 종료 계약 재검증

기준은 fetch 후 `dcaa01f312921dc59f2fb18b19fc4016a33f2aa3`이며,
작업 브랜치는 `feature/#59-action-analyze`다. 기존 main의 조기 COMPLETE 거절과 소켓 실제 status 전달은 유지했다.

확인한 결함은 구 outbox의 `interviewStatus`가 null/IN_PROGRESS여도 재요청 응답을 COMPLETED로 만드는 fallback이다.
신규 outbox는 COMPLETED/ABANDONED만 받는다. 구 기록의 상태가 유효하지 않으면 원문·식별자를 보존하고
기존 실패 기록/알림 경로로 BLOCKED 처리한다. API로 전송하거나 성공 END를 반환하지 않는다.
답변 수·요청 동사로 상태를 보충하지 않는다. 이미 BLOCKED인 항목은 실패 횟수를 다시 늘리지 않는다.

원인 조사 시 최종 Redis 세션·최초 payload·API의 첫 저장 상태를 대조한다.
422와 누락 상태는 무한 재시도하지 않으며 기존 BLOCKED 복구 절차를 따른다.
최초 상태를 입증할 수 없는 기록을 임의 수정하지 않는다. 운영 데이터 조사·보정은 이번 변경에서 수행하지 않았다.

Java 21에서 `./gradlew --offline --no-daemon test`로 전체 375건 통과, 실패/skip 0건을 확인했다.
실제 로컬 redis-server를 사용하는 11건도 모두 실행했다. 추가 검사는 다음을 확인한다.

- 종료 상태 누락/IN_PROGRESS의 신규 등록 거절과 구 기록의 BLOCKED 보존.
- Redis 강제 종료·AOF 복원 후에도 같은 원문과 상태를 보존하고 잘못된 기록을 전송하지 않음.
- AI 꼬리질문 판단 대기 중 마지막 ANSWER/QUIT/COMPLETE 경합에서 공유 잠금 유지.
- AI 종료 후 첫 COMPLETED 유지와 늦은 ANSWER/QUIT에서 새 AI/TTS 작업이 생기지 않음.
- ABANDONED의 API 2xx를 분석 실행 여부와 관계없이 SENT로 처리.

준비 식별자·SOLO/MULTI 종료·REST/소켓·타임아웃 동일 payload 재전송·실제 HTTP 인증 헤더 검사는 기존 회귀에 포함된다.
API 응답은 대역/로컬 HTTP 서버로 확인했으며 실제 배포 API·S3·토큰 일치·업로드 순서는 미검증이다.
행동 워커 호출·행동 완료 대기는 Chat에 없으며 기존 면접/TEXT 경로에 추가하지 않았다.
운영 배포·병합·실제 outbox 정리는 수행하지 않았다.
