# 일반 직군 Chat 구현

- 기준: 2026-10-20 fetch `origin/main@95f2cc0`.
- 명세: `Doc/general-job-impl-spec/01-overview.md`, `05-chat.md`, 비개발 `05-chat.md`.
- 선행 비개발 9코드와 QA 기준 원문을 먼저 구현·검사하고 일반 6코드를 추가한다. 병합은 auth → Analysis → Chat → API → Web 순서다.
- `CompanyFollowUpPrompt`와 TTS는 기존 구현을 유지한다.

## 구현 전 기술 가정

- [가정] enum에 공통 한글 `label`을 두고 일반 여부는 명시적인 enum 판별 메서드로 판단한다. 비개발 화면 이름은 비개발 01 §3.1 원문을 사용한다.
- [가정] 일반 문구 판단은 `FollowQuestionAiRequest.interviewMajor`를 사용한다. 해당 값이 없으면 일반 문구를 넣지 않으며, IT 프롬프트는 기존 면접관 전공으로 판단하여 비개발 기준 원문을 유지한다.
- [가정] 새 `of(...)` 인자는 세션 전공·직무명 순서로 기존 `major` 뒤에 추가한다. 모든 호출부·테스트를 함께 수정한다.
- 직무명 Chat 수신은 명세의 선택적 `@Size(max = 30)`을 따른다. 저장 업무 검증은 API가 담당한다.

## 검증

- Java 21 `./gradlew test`: 총 295개, 성공 291개, 실패 0개, 기존 Redis 통합 4개 생략. 이 호스트에 `redis-server` 실행 파일이 없어 기존 테스트가 abort한다. CI는 Redis를 설치하고 해당 4개가 생략 없이 실행되는지 별도로 검사한다.
- 개발·비개발 QA 기준 프롬프트 원문 통과. 일반 마케팅 원문, 일반 6개 관점, 뒷자리 역할·세션 전공, 직무명 미입력·길이 초과, 옛 Redis 세션 역직렬화와 새 세션 JSON 왕복 통과.
- 01-overview §3.1 대조: IT 9개 + 일반 6개 코드 및 일반 한글 이름 일치. 기업 프롬프트·TTS 구현과 기존 원문 fixture 무변경.
- 자체 리뷰: 일반 분기는 세션 `interviewMajor`로 제한하고 HR 등 뒷자리의 `major = null`에도 관점이 유지됨을 확인. 추가로 이전 세션 필드 부재와 새 세션 왕복 검사를 보완했다.
- 유료 모델 호출·배포·병합은 하지 않았다. 일반 직군 기능 노출 전 auth → Analysis → Chat → API → Web 병합 순서를 지킨다.
