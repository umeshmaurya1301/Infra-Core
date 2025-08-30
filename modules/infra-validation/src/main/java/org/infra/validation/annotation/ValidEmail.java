package org.infra.validation.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import org.infra.validation.validator.EmailValidator;

import java.lang.annotation.*;

/**
 * Custom email validation annotation.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailValidator.class)
@Documented
public @interface ValidEmail {

    String message() default "Invalid email format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
