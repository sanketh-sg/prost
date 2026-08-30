package de.unibamberg.dsam.group6.prost.util;

import de.unibamberg.dsam.group6.prost.util.annotation.FieldsMatch;
import de.unibamberg.dsam.group6.prost.util.annotation.IsAfter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Registration input, validated before anything is hashed or persisted.
 *
 * <p>The entity was previously validated instead — after the password had been encoded, so
 * {@code @NotEmpty} was checking a bcrypt string that is never empty and an empty password was
 * accepted.
 */
@Getter
@Setter
@FieldsMatch(first = "password", second = "passwordCheck")
public class RegistrationForm {
    @NotEmpty(message = "Username is required.")
    private String username;

    @NotEmpty(message = "Password is required.")
    private String password;

    private String passwordCheck;

    @NotNull(message = "Birthday is required.")
    @Past(message = "Birthday must be in the past.")
    @IsAfter(year = 1900, message = "Birthday must be after 1900.")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;
}
