package org.infra.validation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.infra.validation.annotation.ValidEmail;

import java.util.regex.Pattern;

/**
 * Custom email validator implementation.
 */
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    @Override
    public void initialize(ValidEmail constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.trim().isEmpty()) {
            return true; // Let @NotNull/@NotBlank handle null/empty validation
        }
        
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
