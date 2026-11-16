package com.nova.bank.chat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;

/** Define bounded plain-text queries and safe scripted actions. */
public final class ChatDtos {
 /** Prevent utility construction. */
 private ChatDtos() {}
 /** Reject empty, oversized and coerced non-string prompts. */
 public record Request(@JsonDeserialize(using=StrictText.class) @NotBlank @Size(min=1,max=500) String message) {}
 /** Return plain text and an optional allowlisted route or close control. */
 public record Reply(String message,String actionLabel,String actionRoute) {}
 /** Disable scalar-to-string coercion only for assistant requests. */
 public static class StrictText extends JsonDeserializer<String> {
  /** Accept JSON text; bean validation handles null and whitespace. */
  @Override public String deserialize(JsonParser parser,DeserializationContext context) throws IOException {
   if(parser.currentToken()!=JsonToken.VALUE_STRING)return (String)context.handleUnexpectedToken(String.class,parser);
   return parser.getText();
  }
 }
}
