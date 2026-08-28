import http from 'k6/http';
import ws from 'k6/ws';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || BASE_URL.replace(/^http/, 'ws');
const SOCKET_TIMEOUT_MS = Number(__ENV.WS_TIMEOUT_MS || 20000);
const ANSWER_DELAY_MS = Number(__ENV.ANSWER_DELAY_MS || 0);

const websocketMessages = new Counter('repit_ws_messages');
const websocketFailures = new Rate('repit_ws_failures');
const answerDuration = new Trend('repit_ws_answer_duration', true);
const completedInterviews = new Counter('repit_interviews_completed');
const prepareFailures = new Counter('repit_prepare_failures');
const handshakeFailures = new Counter('repit_ws_handshake_failures');
const socketTimeouts = new Counter('repit_ws_socket_timeouts');
const socketErrors = new Counter('repit_ws_errors');
const serverErrors = new Counter('repit_ws_server_errors');

export const thresholds = {
    checks: ['rate>0.99'],
    repit_ws_failures: ['rate<0.01'],
    repit_ws_answer_duration: ['p(95)<10000'],
    ws_connecting: ['p(95)<2000'],
};

export function runInterview() {
    const sessionId = `k6-${__VU}-${__ITER}-${Date.now()}`;
    const prepared = prepareInterview(sessionId);

    if (!prepared) {
        prepareFailures.add(1);
        websocketFailures.add(true);
        return;
    }

    let completed = false;
    let outcomeRecorded = false;
    let answerStartedAt = 0;
    let sentAnswers = 0;

    function recordFailure() {
        if (!outcomeRecorded) {
            websocketFailures.add(true);
            outcomeRecorded = true;
        }
    }

    function recordSuccess() {
        if (!outcomeRecorded) {
            websocketFailures.add(false);
            outcomeRecorded = true;
        }
    }

    const response = ws.connect(
        `${WS_URL}/ws/chat/interviews?sessionId=${encodeURIComponent(sessionId)}`,
        { tags: { name: 'ws_chat_interviews' } },
        (socket) => {
            socket.on('message', (rawMessage) => {
                let message;

                try {
                    message = JSON.parse(rawMessage);
                } catch (error) {
                    check(false, { 'WebSocket response is valid JSON': () => false });
                    recordFailure();
                    socket.close();
                    return;
                }

                websocketMessages.add(1, { message_type: message.type || 'UNKNOWN' });

                if (answerStartedAt > 0) {
                    answerDuration.add(Date.now() - answerStartedAt);
                    answerStartedAt = 0;
                }

                if (message.type === 'ERROR') {
                    serverErrors.add(1);
                    check(false, { 'server returned no WebSocket error': () => false });
                    recordFailure();
                    socket.close();
                    return;
                }

                if (message.type === 'QUESTION') {
                    const hasQuestionId = check(message, {
                        'question response contains questionId': (value) =>
                            value.question && value.question.questionId !== null,
                    });

                    if (!hasQuestionId) {
                        recordFailure();
                        socket.close();
                        return;
                    }

                    const sendAnswer = () => {
                        sentAnswers += 1;
                        answerStartedAt = Date.now();
                        socket.send(JSON.stringify({
                            type: 'ANSWER',
                            questionId: message.question.questionId,
                            responseTime: Math.max(1, Math.round(ANSWER_DELAY_MS / 1000)),
                            content: `k6 부하 테스트 답변 ${sentAnswers}`,
                        }));
                    };

                    if (ANSWER_DELAY_MS > 0) {
                        socket.setTimeout(sendAnswer, ANSWER_DELAY_MS);
                    } else {
                        sendAnswer();
                    }
                    return;
                }

                if (message.type === 'END') {
                    completed = true;
                    completedInterviews.add(1);
                    check(sentAnswers, {
                        'all prepared questions were answered': (count) => count === 2,
                    });
                    recordSuccess();
                    socket.close();
                    return;
                }

                check(false, { 'server returned a supported message type': () => false });
                recordFailure();
                socket.close();
            });

            socket.on('error', () => {
                socketErrors.add(1);
                recordFailure();
            });

            socket.on('close', () => {
                if (!completed) {
                    recordFailure();
                }
            });

            socket.setTimeout(() => {
                if (!completed) {
                    socketTimeouts.add(1);
                    check(false, { 'interview completed before socket timeout': () => false });
                    recordFailure();
                    socket.close();
                }
            }, SOCKET_TIMEOUT_MS);
        },
    );

    const connected = check(response, {
        'WebSocket handshake status is 101': (result) => result && result.status === 101,
    });

    if (!connected) {
        handshakeFailures.add(1);
        recordFailure();
    }
}

function prepareInterview(sessionId) {
    const payload = JSON.stringify({
        sessionId,
        interviewId: __VU * 1000000 + __ITER,
        userId: __VU,
        status: 'IN_PROGRESS',
        questions: [
            {
                id: 1,
                category: 'java',
                question: 'JVM 메모리 구조를 설명해주세요.',
                expectedAnswer: 'JVM의 주요 메모리 영역과 역할을 설명한다.',
                basedOn: [],
                personaId: 1,
            },
            {
                id: 2,
                category: 'spring',
                question: 'Spring Bean 생명주기를 설명해주세요.',
                expectedAnswer: 'Bean 생성부터 소멸까지의 단계를 설명한다.',
                basedOn: [],
                personaId: 1,
            },
        ],
    });

    const response = http.post(`${BASE_URL}/chat/interviews`, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'prepare_interview' },
    });

    return check(response, {
        'prepare interview status is 200': (result) => result.status === 200,
        'prepare interview returns the same sessionId': (result) => {
            try {
                return result.json('sessionId') === sessionId;
            } catch (error) {
                return false;
            }
        },
    });
}
