# ElevenLabs 질문 음성 수신 한도

## 문제와 변경 범위

ElevenLabs 음성 응답은 `ElevenLabsTtsClient`에서 `byte[]`로 모은 뒤 기존 저장 경로로 넘긴다.
전용 WebClient에 별도 수신 한도가 없어 256KiB를 넘는 응답은
`DataBufferLimitException`으로 실패했다. 로컬 HTTP 서버의 320KiB 응답으로 재현한다.

전용 WebClient의 수신 한도를 기본 8MiB로 설정한다. 다른 WebClient에는 적용하지 않는다.
기존 `byte[]` 합성·저장 인터페이스, 타임아웃, 큐 동시성, 오디오 응답 계약은 유지한다.
TTS가 켜져 있으면 0 이하 또는 2147483647바이트를 넘는 설정은 기동 시 거부한다.
TTS가 꺼져 있으면 버퍼 설정 대신 안전한 8MiB 기본값을 사용해 전체 서버의 기동을 막지 않는다.
형식이 잘못된 값(예: `invalid`)은 설정 바인딩 단계에서 거부한다.
한도를 초과하면 재시도 불가능한 `TtsClientException`으로 변환하여
동일한 음성 합성을 반복 요청하지 않는다. 오디오 상태는 FAILED이고,
저장·조회 응답의 `errorCode`는 `TTS_AUDIO_TOO_LARGE`다. 다른 합성 실패는 기존
`TTS_GENERATION_FAILED`, 업로드 실패는 `AUDIO_STORAGE_FAILED`를 유지한다.

## 설정과 배포

| 항목 | 값 |
| --- | --- |
| 설정 키 | `tts.elevenlabs.max-in-memory-size` |
| 환경변수 | `ELEVENLABS_MAX_IN_MEMORY_SIZE` |
| 기본값 | `8388608` 바이트 (8MiB) |
| 허용값 | `8388608`, `8MB`, `512KB` 등 DataSize 형식 |
| 활성화 시 범위 | 1~2147483647바이트 |

Docker Compose도 이 환경변수를 Chat 컨테이너에 전달한다.
설정을 바꾼 뒤 컨테이너를 재생성해야 적용된다.
기본값은 `TtsProperties.ElevenLabs.DEFAULT_MAX_IN_MEMORY_SIZE`, `application.yml`,
`docker-compose.yml` 세 곳에 있으므로 기본값 변경 시 함께 수정한다.
합성 전역 동시성 기본값 4에서 오디오 바이트 배열만 최대 약 32MiB를 사용하며,
수신 버퍼·업로드·런타임 메모리는 별도로 필요하다. 동시성과 한도를 함께 고려한다.
배포 뒤 새로 준비한 면접의 긴 질문에서 오디오 READY와 재생을 확인한다.
이미 FAILED로 저장된 오디오는 이 변경만으로 자동 재생성되지 않는다.

## 클라이언트 수명과 공통 설정

Boot가 제공한 `WebClient.Builder`를 복제하므로 공통 커스터마이저를 유지하면서
ElevenLabs 전용 URL·코덱·커넥터를 설정한다. 원본 빌더와 다른 클라이언트는 바꾸지 않는다.
`elevenLabsConnectionProvider`는 Spring 빈으로 관리하고 컨텍스트 종료 시 dispose한다.
HTTP 테스트도 사용한 풀을 명시적으로 정리한다.

녹음 201 이후 면접 진행 문제는 [답변 제출 진단](interview-answer-troubleshooting.md)을 참고한다.

## 검증

`./gradlew test`로 기본 한도 초과였던 오디오의 원본 바이트 수신,
설정 한도 경계·초과, 단위 바인딩, TTS 활성화별 설정 검증, 풀 종료,
원본 빌더 격리, 오류 코드 전달, 기존 HTTP·빈 오디오 실패 처리를 검증한다.
실제 ElevenLabs 호출·유료 사용·운영 배포 없이 로컬 HTTP 서버를 사용한다.
