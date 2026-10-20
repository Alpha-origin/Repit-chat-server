package com.person.repit.interview.type;

public enum InterviewMajor {
    FRONTEND("프론트엔드"),
    BACKEND("백엔드"),
    WEB_PUBLISHER("웹 퍼블리셔"),
    DATA_ANALYST("데이터 분석가"),
    DBA_INFRA("DBA·인프라 엔지니어"),
    QA_ENGINEER("QA 엔지니어"),
    SECURITY("정보보안"),
    PRODUCT_MANAGER("서비스 기획자 (PM·PO)"),
    IT_CONSULTANT("IT 컨설턴트·기술영업"),
    GEN_STRATEGY("경영기획·전략"),
    GEN_HR_ADMIN("인사·총무"),
    GEN_FINANCE("재무·회계"),
    GEN_MARKETING("마케팅·홍보"),
    GEN_SALES("영업·고객관리"),
    GEN_DESIGN("디자인");

    private final String label;

    InterviewMajor(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isGeneral() {
        return switch (this) {
            case GEN_STRATEGY, GEN_HR_ADMIN, GEN_FINANCE, GEN_MARKETING, GEN_SALES, GEN_DESIGN -> true;
            default -> false;
        };
    }

    public boolean isDevelopment() {
        return this == FRONTEND || this == BACKEND || this == WEB_PUBLISHER;
    }
}
