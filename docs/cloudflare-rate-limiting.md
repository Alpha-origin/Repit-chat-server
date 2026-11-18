# Cloudflare 연결과 요청 제한

## 현재 코드의 동작

HTTP 제한은 `RequestRateLimitFilter`, WebSocket 메시지 제한은
`ReactiveChatInterviewWebSocketHandler`가 담당한다. 카운터는 Redis에서 공유한다.
HTTP 제한은 인증된 `Principal`의 이름을 사용하고, 인증 정보가 없으면 접속 IP를 사용한다.
요청 본문의 `userId`, `X-User-Id`, `CF-Connecting-IP`, `X-Forwarded-For`를
필터가 직접 신뢰하지 않는다. 사용자 인증 자체를 추가하는 기능은 아니다.

| 요청 | 같은 호출자의 한도 |
| --- | --- |
| `POST /chat/interviews` | 60초에 10회 |
| 면접·현재 질문 조회 | 두 경로 합산 10초에 30회 |
| 답변 제출·완료·중단 | 세 경로 합산 10초에 10회 |
| 오디오 조회 | 10초에 30회 |
| WebSocket 연결 요청 | 10초에 10회 |
| WebSocket 내부 메시지 | 같은 면접 세션에서 10초에 10회 |

HTTP 카운터는 세션 ID를 바꿔도 같은 호출자와 정책이면 공유한다.
첫 요청부터 지정한 기간 동안 계산하는 고정 구간 방식이며, 구간 경계에서는 짧은 시간에
두 구간의 요청이 몰릴 수 있다. 수치는 정상 사용량과 공용 IP 사용량을 관찰해 조정한다.
WebSocket 연결 요청 횟수 제한은 활성 연결 수 제한과 별개다.

초과한 HTTP 요청에는 `429`, `RATE_LIMITED`, `Retry-After`를 반환한다.
남은 시간을 별도로 조회하지 않으므로 `Retry-After`에는 해당 정책의 전체 기간을 사용한다.
Redis 검사 실패 시에는 요청을 실행하지 않고 `503`, `RATE_LIMIT_UNAVAILABLE`,
`Retry-After: 1`을 반환한다. 서비스에서 발생한 오류는 기존 오류 처리로 전달한다.
조회 경로의 `HEAD`도 `GET`과 같은 카운터로 제한한다.
`OPTIONS`, Actuator, 그 외 대상이 아닌 경로는 제한하지 않는다.
CORS 필터는 요청 제한보다 먼저 실행하며 `Retry-After`를 브라우저에 노출한다.

## Cloudflare 연결 시 적용할 설정

1. 프론트뿐 아니라 채팅/API 도메인의 DNS 레코드에 Cloudflare 프록시를 적용한다.
2. 원본 HTTPS 인증서를 구성하고 Cloudflare SSL/TLS를 Full (strict)로 설정한다.
3. 원본 서버의 방화벽에서 공개 웹 포트로 들어오는 연결을 Cloudflare IP 범위로 제한한다.
   Cloudflare Tunnel을 사용한다면 공개 인바운드 포트 없이 연결하는 방식을 사용할 수 있다.
4. 호스트 Nginx가 앞에 있는 경우 현재 Compose의 `127.0.0.1:8080:8080`을 유지한다.
   Nginx나 cloudflared가 Docker 컨테이너라면 같은 Docker 네트워크의 `chat:8080`으로
   연결하고 호스트 포트 공개가 필요한지 별도로 확인한다.
5. Cloudflare에서 HTTP 경로와 WebSocket 최초 연결에 대한 Rate Limiting 규칙을 추가한다.
   사용 가능한 규칙 수와 조건은 요금제에 따라 다르다. WebSocket 내부 메시지 제한은
   서버 코드가 계속 담당한다.

### 프록시 뒤에서 실제 사용자 IP 사용

인증이 없는 상태에서 프록시를 연결하면 Spring이 프록시의 IP를 접속 IP로 볼 수 있다.
이 경우 여러 사용자가 같은 카운터를 사용하므로 실제 사용자 IP 전달 설정이 필요하다.

Nginx는 신뢰할 Cloudflare IP 범위에 대해서만 `CF-Connecting-IP`를 복원하고,
외부에서 받은 전달 헤더를 검증된 주소로 덮어써 Spring에 전달해야 한다.
Spring에 `server.forward-headers-strategy=framework`를 적용하려면 먼저 Spring 접근을
신뢰할 프록시로만 제한하고 프록시의 헤더 정리를 완료한다. 이 속성 자체가 프록시를
검증해주는 것은 아니다. 인증된 사용자 정보가 전달되는 구조라면 사용자별 카운터가 우선한다.

Cloudflare 연결·프록시 IP 신뢰 설정·사용자 인증 검증은 현재 저장소 변경만으로
운영 서버에 적용되지 않는다. 이 문서는 연결 전의 구현과 연결 시 필요한 작업을 구분한다.

## 프론트 확인

오디오 API는 `?wait=20`으로 응답을 기다린 뒤 PENDING일 때 다음 요청을 보낸다.
READY 또는 FAILED면 중단하고 화면을 떠나면 진행 중인 요청을 취소한다.
429 또는 503 응답을 받으면 `Retry-After`만큼 기다린다.
프론트에서 요청을 0.5초마다 중첩 실행하는 코드는 별도 프론트 저장소에서 확인해야 한다.

## 참고

- [Cloudflare Rate Limiting](https://developers.cloudflare.com/waf/rate-limiting-rules/)
- [Cloudflare WebSocket 지원과 WAF 범위](https://developers.cloudflare.com/network/websockets/)
- [원본 서버 보호](https://developers.cloudflare.com/fundamentals/security/protect-your-origin-server/)
