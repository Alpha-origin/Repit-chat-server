package com.person.repit.interview.exception;

import com.person.repit.common.exception.BusinessException;

public class InterviewNotFinishedException extends BusinessException {
    public InterviewNotFinishedException() {
        super("아직 처리할 질문이 남아 있어 면접을 완료할 수 없습니다.");
    }
}
