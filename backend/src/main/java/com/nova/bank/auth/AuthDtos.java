package com.nova.bank.auth;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
/** Define the safe authentication wire contract. */
public final class AuthDtos {
 private AuthDtos() {}
 /** Accept credentials without ever rendering them in a response. */
 public record LoginRequest(@JsonDeserialize(using=StrictText.class) @NotBlank(message="User ID is required") @Size(max=50,message="User ID must be at most 50 characters") String username, @JsonDeserialize(using=StrictText.class) @NotBlank(message="Password is required.") @Size(max=72,message="Password must be at most 72 characters") String password) { @Override public String toString(){return "LoginRequest[redacted]";} }
 /** Reject scalar coercion at the credential boundary, matching other strict request DTOs. */
 public static class StrictText extends JsonDeserializer<String> {
  /** Accept JSON strings only; bean validation retains the existing null and blank guidance. */
  @Override public String deserialize(JsonParser parser,DeserializationContext context) throws IOException {
   if(parser.currentToken()!=JsonToken.VALUE_STRING)return (String)context.handleUnexpectedToken(String.class,parser);
   return parser.getText();
  }
 }
 /** Expose only approved profile fields and the previous login. */
 public record SafeUser(UUID id,String username,String fullName,String role,Instant lastLogin) {}
 /** Return a server-issued bearer with its configured lifetime. */
 public record LoginResponse(String token,String tokenType,long expiresIn,SafeUser user) {}
}
