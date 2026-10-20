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
이번 변경에서는 `./gradlew --offline --no-daemon test`로 전체 263개 테스트가 통과했고 실패·건너뜀은 없었다.
수정 전에는 식별자 충돌 거절 4개 사례가 실패하는 것도 확인했다.
S3 업로드 순서와 API 분석 요청·콜백·Web 중복 업로드 방지는 해당 서버의 통합 검증 대상이다.
질문별 영상 시간축과 실시간 행동 분석은 이번 Chat 계약에 포함하지 않는다.
