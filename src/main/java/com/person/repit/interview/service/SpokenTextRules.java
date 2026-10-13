package com.person.repit.interview.service;

public final class SpokenTextRules {

    public static final String PROMPT_BLOCK = """
            [발음 문장 작성]
            - 화면에 표시할 content의 의미와 문장 구조를 유지합니다.
            - 영문 단어와 알파벳 약어는 자연스러운 한국어 발음으로 적습니다.
            - 슬래시 연결어, 기호가 포함된 이름, 버전, 숫자와 단위, 코드 식별자도 소리 나는 대로 적습니다.
            - 설명이나 괄호 태그를 추가하지 말고 발음할 문장만 작성합니다.
            - 변환할 표현이 없으면 content와 같은 문장을 작성합니다.
            - 용어 사전:
            """;

    private SpokenTextRules() {
    }
}
