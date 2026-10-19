package com.person.repit.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class CodePointLengthValidator implements ConstraintValidator<CodePointLength, String> {
    private int max;

    @Override
    public void initialize(CodePointLength constraint) {
        max = constraint.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.codePointCount(0, value.length()) <= max;
    }
}
