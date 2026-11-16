package com.nova.bank.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Keep administrator input strict and audit output free of arbitrary details or personal data. */
public final class AdminDtos {
 /** Prevent construction of the contract namespace. */
 private AdminDtos() {}
 /** Accept only the two approved demonstration customers, with no scalar coercion. */
 public record Persona(JsonNode username) {
  /** Reject missing, non-textual and unapproved persona names. */
  public String target(){
   if(username==null||!username.isTextual()||!java.util.List.of("miguel","rosa").contains(username.textValue()))throw new IllegalArgumentException("Invalid persona");
   return username.textValue();
  }
 }
 /** Accept a JSON boolean, never a coercible number or string. */
 public record Simulation(JsonNode serviceError) {
  /** Reject missing or non-boolean simulation input. */
  public boolean enabled(){if(serviceError==null||!serviceError.isBoolean())throw new IllegalArgumentException("Invalid simulation");return serviceError.booleanValue();}
 }
 /** Return the persisted state of the issuing administrator session. */
 public record SimulationState(boolean serviceError) {}
 /** Return the exact reset notice. */
 public record Notice(String message) {}
 /** Expose event identity, actor identity and timestamp without arbitrary details. */
 public record Audit(UUID id,UUID userId,String action,Instant timestamp) {}
}
