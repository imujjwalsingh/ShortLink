package com.shortlink.analyticsservice.exception;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.UncheckedIOException;

/** RFC 9457 problem+json for every error, same shape as the other services. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Elasticsearch unreachable or erroring: a dependency problem, not a bug. */
    @ExceptionHandler({UncheckedIOException.class, ElasticsearchException.class})
    public ProblemDetail handleStoreUnavailable(Exception ex) {
        log.warn("Analytics store unavailable: {}", ex.toString());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Analytics are temporarily unavailable; try again shortly");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
