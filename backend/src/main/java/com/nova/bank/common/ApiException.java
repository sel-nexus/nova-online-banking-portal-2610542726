package com.nova.bank.common;
import org.springframework.http.HttpStatus;
/** Carry a safe domain error whose status survives transaction handling. */
public class ApiException extends RuntimeException {
 private final HttpStatus status;
 /** Associate a safe public message with its HTTP status. */
 public ApiException(HttpStatus status, String message) { super(message); this.status = status; }
 /** Return the intended domain status. */
 public HttpStatus status() { return status; }
}
