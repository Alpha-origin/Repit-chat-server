package com.person.repit.common.type;

public enum MessageType {
    START,    // 면접 세션 시작
    QUESTION,    // 질문 송신
    ANSWER,      // 답변 수신
    END,         // 면접 종합 데이터 반환
    COMPLETE,    // 면접 세션 최종 종료
    QUIT,        // 중간 이탈
    ERROR        // 에러 발생
}
