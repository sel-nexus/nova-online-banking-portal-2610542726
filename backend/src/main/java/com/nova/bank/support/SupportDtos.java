package com.nova.bank.support;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.List;

/** Define protected reference content and the bounded simulation contract. */
public final class SupportDtos {
 private SupportDtos() {}
 /** Carry only strictly typed, bounded strings; whitespace-only input is invalid. */
 public record Request(
  @JsonDeserialize(using=StrictText.class) @NotBlank @Size(max=50) @Pattern(regexp="LOGIN|ACCOUNTS|TRANSACTIONS|CARDS|LOANS|OFFERS|GENERAL") String topic,
  @JsonDeserialize(using=StrictText.class) @NotBlank @Size(min=1,max=150) String subject,
  @JsonDeserialize(using=StrictText.class) @NotBlank @Size(min=1,max=5000) String message) {}
 /** Confirm only local persistence, never message delivery. */
 public record Receipt(String referenceNumber,String status,String estimatedResponse) {}
 /** Describe a demonstration contact channel. */
 public record Contact(String title,String detail,String hours,String description) {}
 /** Describe an exact static branch or ATM. */
 public record Branch(String name,String address,String description,String hours) {}
 /** Carry plain-text searchable help. */
 public record Faq(String id,String question,String answer) {}
 /** Keep directory and FAQ in one persisted reference document. */
 public record Content(List<Contact> contacts,List<Branch> branches,List<Faq> faqs,List<String> topics,String notice) {}
 /** Reject Jackson's numeric/boolean-to-string coercion at this feature boundary. */
 public static class StrictText extends JsonDeserializer<String> {
  /** Accept JSON strings only; normal bean validation handles null. */
  @Override public String deserialize(JsonParser parser,DeserializationContext context) throws IOException {
   if(parser.currentToken()!=JsonToken.VALUE_STRING)return (String)context.handleUnexpectedToken(String.class,parser);
   return parser.getText();
  }
 }
}
