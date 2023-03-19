package de.unibamberg.dsam.group6.prost.util.annotation;

import de.unibamberg.dsam.group6.prost.util.validator.DateValidator;
import java.lang.annotation.*;
import javax.validation.Constraint;
import javax.validation.Payload;

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
