package de.unibamberg.dsam.group6.prost.util.validator;

import de.unibamberg.dsam.group6.prost.util.annotation.FieldsMatch;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Objects;
import org.springframework.beans.BeanWrapperImpl;

public class FieldsMatchValidator implements ConstraintValidator<FieldsMatch, Object> {
    private String first;
    private String second;
    private String message;

    @Override
    public void initialize(FieldsMatch constraint) {
        this.first = constraint.first();
        this.second = constraint.second();
        this.message = constraint.message();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        var wrapper = new BeanWrapperImpl(value);
        var a = wrapper.getPropertyValue(this.first);
        var b = wrapper.getPropertyValue(this.second);

        if (Objects.equals(a, b)) {
            return true;
        }

        // Attach the violation to the second field so the form can render it inline.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(this.message)
                .addPropertyNode(this.second)
                .addConstraintViolation();
        return false;
    }
}
