package com.shortlink.urlservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must be an absolute http:// or https:// URL that parses and has
 * a host. Stricter than a "starts with http" regex, which accepts things
 * like "http://" followed by spaces or a bare path. Null passes; pair it
 * with @NotBlank where the field is required.
 */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

    String message() default "must be an absolute http(s) URL with a valid host";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
