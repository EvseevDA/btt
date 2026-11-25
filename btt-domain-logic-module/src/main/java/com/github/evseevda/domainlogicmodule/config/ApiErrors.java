package com.github.evseevda.domainlogicmodule.config;

import com.github.evseevda.domainlogic.model.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException error) {
        var status = HttpStatus.resolve(error.getStatusCode().value());
        String code = status == null ? "HTTP_" + error.getStatusCode().value() : status.name();
        String message = error.getReason() == null ? code : error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(new ApiError(code, message));
    }
}
