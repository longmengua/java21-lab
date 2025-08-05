package com.example.demo.interfaces.web.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class MobileValidator implements ConstraintValidator<Mobile, String> {

    private static final Pattern MOBILE_PATTERN = Pattern.compile("^09\\d{8}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || MOBILE_PATTERN.matcher(value).matches();
    }
}
