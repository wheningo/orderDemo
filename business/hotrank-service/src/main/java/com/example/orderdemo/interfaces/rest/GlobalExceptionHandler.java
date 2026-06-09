package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.order.DuplicateCommandException;
import com.example.orderdemo.application.order.OrderNotFoundException;
import com.example.orderdemo.application.order.OversellRejectedException;
import com.example.orderdemo.domain.order.InvariantViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvariantViolationException.class)
    public ProblemDetail handleInvariantViolation(InvariantViolationException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invariant Violation");
        problem.setProperty("invariant", ex.invariant());
        if (ex.orderId() != null) {
            problem.setProperty("orderId", ex.orderId().value());
        }
        return problem;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Order Not Found");
        problem.setProperty("orderId", ex.orderId().value());
        return problem;
    }

    @ExceptionHandler(DuplicateCommandException.class)
    public ProblemDetail handleDuplicate(DuplicateCommandException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.OK, ex.getMessage());
        problem.setTitle("Duplicate Command");
        problem.setProperty("idempotencyKey", ex.idempotencyKey());
        return problem;
    }

    @ExceptionHandler(OversellRejectedException.class)
    public ProblemDetail handleOversellRejected(OversellRejectedException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Oversell Rejected");
        problem.setProperty("retryable", false);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}