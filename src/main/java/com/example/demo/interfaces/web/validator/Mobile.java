package com.example.demo.interfaces.web.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = MobileValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mobile {
    String message() default "手機格式錯誤";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

