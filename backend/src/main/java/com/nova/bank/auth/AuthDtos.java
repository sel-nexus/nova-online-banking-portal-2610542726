package com.nova.bank.auth;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
/** Define the safe authentication wire contract. */
public final class AuthDtos {
 private AuthDtos() {}
 /** Accept credentials without ever rendering them in a response. */
 public record LoginRequest(@NotBlank(message="User ID is required") @Size(max=50,message="User ID must be at most 50 characters") String username, @NotBlank(message="Password is required.") @Size(max=72,message="Password must be at most 72 characters") String password) { @Override public String toString(){return "LoginRequest[redacted]";} }
 /** Expose only approved profile fields and the previous login. */
 public record SafeUser(UUID id,String username,String fullName,String role,Instant lastLogin) {}
 /** Return a server-issued bearer with its configured lifetime. */
 public record LoginResponse(String token,String tokenType,long expiresIn,SafeUser user) {}
}
