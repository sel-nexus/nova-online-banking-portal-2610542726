package com.nova.bank.common;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.*;
/** Map failures to safe consistent JSON without echoing submitted secrets. */
@RestControllerAdvice
public class ErrorHandler {
 /** Represent errors shared by security filters and controllers. */
 public record ErrorBody(int status, String message, Map<String,String> errors, Instant timestamp) {}
 /** Build a public error without internal exception details. */
 public static ErrorBody body(int status, String message) { return new ErrorBody(status, message, Map.of(), Instant.now()); }
 /** Preserve explicit domain failures. */
 @ExceptionHandler(ApiException.class) public ResponseEntity<ErrorBody> domain(ApiException e) { return ResponseEntity.status(e.status()).body(body(e.status().value(), e.getMessage())); }
 /** Expose field guidance but never rejected values. */
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
  Map<String,String> errors = new TreeMap<>(); e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
  return ResponseEntity.badRequest().body(new ErrorBody(400,"Please check the required fields.",errors,Instant.now()));
 }
 /** Reject malformed JSON without exposing parser details. */
 @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) public ResponseEntity<ErrorBody> malformed(Exception e) { return ResponseEntity.badRequest().body(body(400,"Invalid request.")); }
 /** Keep database constraints private. */
 @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<ErrorBody> conflict(Exception e) { return ResponseEntity.status(409).body(body(409,"Request conflicts with existing data.")); }
 /** Deny privileged operations with safe guidance. */
 @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<ErrorBody> denied(Exception e) { return ResponseEntity.status(403).body(body(403,"Access denied.")); }
}
