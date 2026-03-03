package com.messagepulse.core.config;

import com.messagepulse.core.exception.AuthenticationException;
import com.messagepulse.core.exception.AuthorizationException;
import com.messagepulse.core.exception.DuplicateMessageException;
import com.messagepulse.core.exception.MessageNotFoundException;
import com.messagepulse.core.exception.MessagePulseException;
import com.messagepulse.core.exception.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MessageNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotFound(MessageNotFoundException ex) {
        return buildResponse(ex.getErrorCode().getCode(), ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateMessageException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateMessage(DuplicateMessageException ex) {
        return buildResponse(ex.getErrorCode().getCode(), ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return buildResponse(ex.getErrorCode().getCode(), ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return buildResponse(ex.getErrorCode().getCode(), ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorization(AuthorizationException ex) {
        return buildResponse(ex.getErrorCode().getCode(), ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MessagePulseException.class)
    public ResponseEntity<Map<String, Object>> handleMessagePulse(MessagePulseException ex) {
        return buildResponse(ex.getErrorCode().getCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return buildResponse(1000, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(int code, String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
