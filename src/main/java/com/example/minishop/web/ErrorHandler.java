package com.example.minishop.web;

import com.example.minishop.config.TraceIdFilter;
import com.example.minishop.service.EmailTaken;
import com.example.minishop.service.Forbidden;
import com.example.minishop.service.ProductNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Product " + e.getId() + " does not exist");
        pd.setTitle("Product not found");
        pd.setType(URI.create(BASE + "not-found"));
        pd.setProperty(TraceIdFilter.TRACE_ID, MDC.get(TraceIdFilter.TRACE_ID));
        return pd;
    }

    @ExceptionHandler(EmailTaken.class)
    public ProblemDetail onEmailTaken(EmailTaken e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Email already registered");
        pd.setTitle("Email conflict");
        pd.setType(URI.create(BASE + "conflict"));
        pd.setProperty("field", "email");
        pd.setProperty(TraceIdFilter.TRACE_ID, MDC.get(TraceIdFilter.TRACE_ID));
        return pd;
    }

    @ExceptionHandler(Forbidden.class)
    public ProblemDetail onForbidden(Forbidden e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You are not allowed to do this");
        pd.setTitle("Forbidden");
        pd.setType(URI.create(BASE + "forbidden"));
        pd.setProperty(TraceIdFilter.TRACE_ID, MDC.get(TraceIdFilter.TRACE_ID));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields are invalid");
        pd.setTitle("Validation failed");
        pd.setType(URI.create(BASE + "validation"));
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", f.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        pd.setProperty(TraceIdFilter.TRACE_ID, MDC.get(TraceIdFilter.TRACE_ID));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onAnyException(Exception e) {
        log.error("unhandled exception", e);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong");
        pd.setTitle("Internal server error");
        pd.setType(URI.create(BASE + "server"));
        pd.setProperty(TraceIdFilter.TRACE_ID, MDC.get(TraceIdFilter.TRACE_ID));
        return pd;
    }
}