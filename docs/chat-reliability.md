# Chat 기록 전송 안정화 (N-1 · N-2 · T-3)

기준: `8c1f71f`, `fix/#37-chat-reliability`. 면접 생성 규칙을 유지하며, 종료 상태 전달 보강은 아래 절을 따른다.

## 내부 인증

`docker-compose.yml`이 `INTERNAL_AUTH_TOKEN`을 Chat 컨테이너에 전달한다.
기존 `repit.api-server.internal-token` 설정과 `X-Internal-Token` 헤더를 그대로 사용한다.
실제 토큰은 배포 환경에서 주입하며 레포나 로그에 기록하지 않는다.
API 요청은 10초 안에 종료하며 2xx만 성공으로 취급한다.

## 마지막 답변과 전송 대기

- `ChatInterviewServiceImpl`의 완료·중단·마지막 답변 자동 종료가 같은 경로를 사용한다.
- 답변을 AI 판단 전에 저장한다. 최종 세션은 TTL 없이 보존한 뒤 전송 대기를 등록한다.
- Redis `chat:interview:result:<sessionId>`에 최초 확정 JSON 문자열과 기존 interviewId를 저장한다.
  sessionId를 전송 작업 키로 사용하며 같은 세션의 중복 종료는 최초 상태를 반환한다.
- 최종 세션 저장이 확인되면 TTS를 즉시 중단한다. 전송 성공 또는 일시 실패의 대기 상태 저장이 확인된 뒤 세션을 정리한다.
- 대기 등록·상태 저장 실패는 기존 오류 응답으로 전달하고 최종 세션을 유지한다.
- 프로세스가 최종 세션 저장과 대기 등록 사이에 종료되면 `FinalizedInterviewRecovery`가 복구한다.
- 성공 시 본문을 제거하고 SENT 접수 기록을 유지한다. 중복 요청은 답변이나 최종 기록을 재생성하지 않는다.
- PENDING/BLOCKED 기록과 접수 기록에는 자동 만료가 없다. Redis 용량을 관찰하고,
  접수 기록 삭제는 해당 sessionId가 다시 사용되지 않는다는 운영 보장 후 수행해야 한다.
- 요청과 워커는 같은 Redis 잠금을 사용한다. 2분 임대와 소유자 확인 해제로 경합을 제어하고,
  작업 제한시간 45초로 임대 만료 전에 작업을 취소한다. 만료된 잠금은 이후 요청이 재획득할 수 있다.

## 재시도와 운영 확인

`INTERVIEW_RESULT_RETRY_ENABLED`의 기본값은 `false`이다.
이 상태에서도 최초 전송과 영속 대기 등록은 수행한다. 사용자 중복 요청은 저장된 접수 결과만 반환한다.
API N-3가 준비된 뒤 `true`로 배포하면 5초 주기로 미접수 최종 세션과 전송 대기를 복구한다.

| 오류 | 처리 |
| --- | --- |
| 연결 오류·타임아웃·408·429·5xx | PENDING 유지 및 자동 재시도 |
| 429 Retry-After | 초 또는 HTTP 날짜를 해석하고 그 시각보다 일찍 재시도하지 않음 |
| 401·403·다른 4xx·예상하지 못한 오류 | BLOCKED 보존 및 동기 요청 오류 반환 |

백오프는 5초·30초·2분·10분·30분이며 ±20% 지터를 적용한다.
최종 간격은 30분을 상한으로 제한한다. 유효한 Retry-After는 이 상한보다 길어도 준수한다.
실패 횟수·최초 실패 시각·다음 시도·오류 유형을 보존하며 횟수 초과로 삭제하지 않는다.
10회 연속 실패 또는 1시간 경과와 BLOCKED 전환 시 오류 로그 및
`repit.interview.result.attention` 카운터를 남긴다. 기존 모니터링에서 이 지표에 알림을 연결한다.
답변 본문·토큰·API 응답 본문은 전송 실패 로그에 기록하지 않는다.

BLOCKED 작업은 토큰 또는 payload 계약의 원인을 해결한 뒤 기존 Redis 운영 도구로 재개한다.
재전송 워커와 해당 세션 요청을 잠시 중지한 상태에서 해당 레코드의 `state`만 `PENDING`으로,
`nextAttemptAt`을 현재 이후 ISO-8601 시각으로 변경한다. 식별자·payload·실패 이력은 유지한다.
새 관리 UI나 별도 DB 스키마는 추가하지 않는다.

## Redis 영속성 및 배포

compose의 Redis는 named volume과 AOF, `appendfsync always`, `noeviction`을 사용한다.
기존 컨테이너에 볼륨 없이 저장된 데이터를 사용 중이라면 교체 전에 백업·이관해야 한다.
`REDIS_HOST`가 외부 Redis를 가리키면 compose의 설정이 적용되지 않으므로 외부 Redis에도
동등한 디스크 영속성·퇴거 방지 설정과 백업이 필요하다.

1. API 담당자가 N-3의 같은 interviewId 재전송 시 200 반환 및 질문 PK 유지 여부를 확인한다.
2. Chat과 API N-3를 같은 날 배포한다. N-3 확인 전에는 자동 재전송을 활성화하지 않는다.
3. `INTERVIEW_RESULT_RETRY_ENABLED=true`로 전환하고 대기 기록이 처리되는지 확인한다.
4. Chat과 Analysis 모두 토큰 전송 배포를 마친 뒤 API `INTERNAL_AUTH_REQUIRED=true`를 켠다.

API N-3 실제 서버와의 연동 및 운영 알림 라우팅은 별도 배포 확인 대상이다.

## 오디오 상태

`AudioResponseStatus`와 `InterviewAudioResponse`에서 외부 상태를 변환한다.
QUEUED·GENERATING·PENDING은 PENDING, URL이 있는 READY는 READY, FAILED는 FAILED이다.
READY인데 URL이 없거나 공백이면 PENDING을 반환한다. 다른 필드와 HTTP 오류 규칙은 유지한다.
매핑은 default 없는 switch로 작성하여 내부 enum 추가 시 누락을 컴파일 단계에서 검출한다.

## 검증

`./gradlew test`로 정상·중단·자동 종료, 일시/비일시 실패, 대기 등록 실패,
중복 종료, 응답 유실 후 같은 본문 재전송, Retry-After, 타임아웃, 오디오 매핑을 검증한다.
`ApiServerTransportTest`는 테스트 HTTP 서버로 실제 헤더와 JSON 본문을 확인한다.
`InterviewResultRedisTest`는 로컬 `redis-server`를 임시 포트·데이터 폴더로 실행해
AOF 강제 종료 복원, 중복 등록, 워커 경합, 최종 세션 복구를 검증한다.
redis-server가 없는 환경에서는 이 통합 테스트만 건너뛴다.
재시도 간격만큼 기다리는 테스트는 작성하지 않는다.
compose 검증은 `docker compose config --quiet`로 수행하며 실제 비밀값을 출력하지 않는다.


## 중도 종료와 정상 완료 구분

면접 진행 중 `POST /chat/interviews/{sessionId}/complete`는 처리할 질문이 남아 있으면
HTTP 409, code `INTERVIEW_NOT_FINISHED`를 반환한다. 거절 시 상태·진행 인덱스·최종 기록을 바꾸지 않는다.
WebSocket COMPLETE도 같은 검사를 사용하며, ERROR를 보낸 뒤 연결을 유지해 답변 또는 QUIT를 받을 수 있다.
서버가 마지막 답변을 처리해 다음 질문이 없으면 기존처럼 COMPLETED로 자동 종료한다.

QUIT는 ABANDONED를 확정한다. 이미 확정된 COMPLETED/ABANDONED는 뒤이은 반대 종료나 답변으로 변경하지 않는다.
요청·복구 워커는 같은 Redis operation lock과 최초 outbox 결과를 사용한다.
동시 요청이 잠금을 얻지 못하면 기존 409 규칙을 따르며, 다시 요청하면 먼저 확정된 결과를 반환한다.
단순 WebSocket 종료는 정상 완료나 중단으로 변환하지 않는다.

WebSocket END에는 실제 최종 상태를 추가한다.

```json
{"type":"END","status":"ABANDONED","question":null,"message":"면접이 중단되었습니다."}
```

정상 종료는 status=COMPLETED다. QUIT 요청이어도 이미 완료됐다면 COMPLETED를 반환하고,
COMPLETE 요청이어도 먼저 중단됐다면 ABANDONED를 반환한다. 메시지 문자열이나 요청 종류로 상태를 추측하지 않는다.
일반 progress 응답도 서비스의 실제 status를 복사한다. 초기 QUESTION·ERROR에는 상태를 임의로 부여하지 않는다.

최종 세션을 Redis에 보존한 뒤, API 전송·outbox 등록을 기다리기 전에 TTS를 정지한다.
대기 등록이나 전송이 실패하면 세션은 남기고 복구한다. 복구 워커도 잠금 안에서 최종 상태를 다시 확인하고
대기 등록 전 TTS를 정지한다. IN_PROGRESS로 바뀐 세션에는 종료 복구를 수행하지 않는다.

ABANDONED 기록도 기존 API result 경로로 계속 전달한다. 질문·접수 답변·부모 관계·면접관·기업 정보는 보존한다.
Chat은 피드백을 직접 요청하지 않는다. **피드백 생성을 실제로 차단하려면 API에도 COMPLETED 전용 생성·재시도·콜백 정책이 적용돼야 한다.**
Analysis 접수 검증과 Web의 종료 상태 분기 역시 각 서버 명세에 따라 별도로 배포한다.

검증에는 조기 COMPLETE의 REST/WS 거절, 종료 상태 직렬화, 중단 기록 보존, 대기 등록 전 TTS 중단,
Redis 강제 종료 후 정상/중단 복원, 실제 잠금 아래 마지막 답변/QUIT 경합과 늦은 답변 무시를 포함한다.
