package com.example.minishop.web;

import com.example.minishop.config.TraceIdFilter;
import com.example.minishop.service.AuthException;
import com.example.minishop.service.ConflictException;
import com.example.minishop.service.NotFoundException;
import com.example.minishop.service.ProductNotFound;
import com.example.minishop.service.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);
    private static final String BASE = "https://minishop.example/errors/";

    @ExceptionHandler(ProductNotFound.class)
    public ProblemDetail onProductNotFound(ProductNotFound e) {
        ProblemDetail pd = problem(HttpStatus.NOT_FOUND, "Product not found", e);
        pd.setProperty("productId", e.getId());
        return pd;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail onConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e);
    }

    @ExceptionHandler(AuthException.class)
    public ProblemDetail onAuth(AuthException e) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", e);
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail onValidationException(ValidationException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", e);
        pd.setProperty("errors", e.fieldErrors());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onBeanValidation(MethodArgumentNotValidException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", e);
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", f.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail onMissingHeader(MissingRequestHeaderException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Missing required header", e);
        pd.setProperty("header", e.getHeaderName());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onAny(Exception e) {
        log.error("unhandled exception", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", e);
    }

    private ProblemDetail problem(HttpStatus status, String title, Exception e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        pd.setTitle(title);
        pd.setType(URI.create(BASE + status.value()));
        pd.setProperty(TraceIdFilter.TRACE_ID, MDC.get(TraceIdFilter.TRACE_ID));
        return pd;
    }
}