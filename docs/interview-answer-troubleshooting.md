# 녹음 저장 후 다음 질문으로 넘어가지 않을 때

## 저장 응답과 면접 진행

`POST /api/interviews/{id}/recordings`의 201은 녹음 파일 저장 결과다.
Chat의 답변 제출과는 별도이므로 이 응답만으로 다음 질문이 반환되었다고 판단하지 않는다.
Chat은 답변 제출이 접수되어야 세션에 텍스트를 저장하고 다음 질문을 반환한다.
빈 답변을 성공 처리하거나 질문을 자동으로 건너뛰지는 않는다.

## 확인 순서

1. 브라우저 Network에서 `POST /chat/interviews/{sessionId}/answers`의 요청 유무를 확인한다.
2. 요청이 있다면 상태 코드와 응답 본문의 다음 질문 또는 오류를 확인한다.
3. Chat의 `[ANSWER ACCEPTED]` 로그에서 세션·면접·질문 식별자를 확인한다.
4. 요청 자체가 없다면 녹음 종료 후 음성 인식 결과와 화면 오류를 확인한다.
5. 동일 질문에 녹음만 반복 저장되었다면 각 시도의 답변 제출 여부를 따로 확인한다.

`answer: null`인 최종 결과만으로 네트워크·브라우저·음성 인식 중 어느 원인인지 확정하지 않는다.
답변 내용이나 인증 토큰을 진단 로그에 추가하지 않는다.

## Web 동작을 확인할 때

2026-10-19 조사 시 Web `dev`의 `useInterviewSession.ts`는 음성 인식 텍스트가
빈 문자열이면 인식 실패를 표시하고 Chat 제출을 중단했다.
이는 Chat 저장소가 보장하는 계약이 아니라 다른 저장소의 특정 시점 관찰이다.
Web 변경 또는 배포 버전 차이가 있으면 아래 코드와 실제 브라우저 동작을 다시 대조한다.

- [Web 답변 제출 흐름](https://github.com/Alpha-origin/Repit-web/blob/dev/src/features/interview-page/interview/model/useInterviewSession.ts)
- [Web 음성 인식 처리](https://github.com/Alpha-origin/Repit-web/blob/dev/src/features/interview-page/interview/model/useVoiceAnswer.ts)

질문 음성의 수신 오류는 [TTS 수신 한도](tts-audio-buffer.md)를 참고한다.
TTS 오류 수정이 브라우저 음성 인식 실패를 해결하는 것은 아니다.
