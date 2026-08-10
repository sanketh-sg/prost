package de.unibamberg.dsam.group6.prost.util.annotation;

import de.unibamberg.dsam.group6.prost.util.validator.DateValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateValidator.class)
@Documented
public @interface IsAfter {
    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int year();

    int month() default 1;

    int day() default 1;
}
