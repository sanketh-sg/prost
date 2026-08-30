package de.unibamberg.dsam.group6.prost.util.annotation;

import de.unibamberg.dsam.group6.prost.util.validator.FieldsMatchValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Asserts two properties of the annotated class hold equal values.
 *
 * <p>Class-level by necessity: a field-level constraint cannot see a sibling field.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FieldsMatchValidator.class)
@Documented
public @interface FieldsMatch {
    String message() default "Passwords didn''t match!";

    String first();

    String second();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
