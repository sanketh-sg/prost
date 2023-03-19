package de.unibamberg.dsam.group6.prost.util.validator;

import de.unibamberg.dsam.group6.prost.util.annotation.IsAfter;
import java.time.LocalDate;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class DateValidator implements ConstraintValidator<IsAfter, LocalDate> {
    private int year;
    private int month;
    private int day;

    @Override
    public void initialize(IsAfter constraintAnnotation) {
        this.year = constraintAnnotation.year();
        this.month = constraintAnnotation.month();
        this.day = constraintAnnotation.day();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return value.isAfter(LocalDate.of(this.year, this.month, this.day));
    }
}
