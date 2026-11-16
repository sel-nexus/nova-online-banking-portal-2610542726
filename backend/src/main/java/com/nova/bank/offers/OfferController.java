package com.nova.bank.offers;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** Delegate discovery and simulation using only the signed principal. */
@RestController @RequestMapping("/api/v1")
public class OfferController {
 private final OfferService service;
 /** Bind the targeting boundary. */ public OfferController(OfferService service){this.service=service;}
 /** Return bounded product recommendations. */ @GetMapping("/products")
 public ResponseEntity<List<OfferDtos.Product>> products(Authentication auth){return ResponseEntity.ok(service.products((UUID)auth.getPrincipal()));}
 /** Ignore untrusted segment overrides and list the verified customer's campaigns. */ @GetMapping("/promotions")
 public ResponseEntity<List<OfferDtos.Offer>> list(Authentication auth){return ResponseEntity.ok(service.list((UUID)auth.getPrincipal()));}
 /** Return permitted full detail with inclusive validity. */ @GetMapping("/promotions/{id}")
 public ResponseEntity<OfferDtos.Offer> detail(@PathVariable String id,Authentication auth){return ResponseEntity.ok(service.detail(parseId(id),(UUID)auth.getPrincipal()));}
 /** Confirm a persisted simulation, not a real application. */ @PostMapping("/promotions/{id}/express-interest")
 public ResponseEntity<OfferDtos.Interest> interest(@PathVariable String id,Authentication auth){return ResponseEntity.ok(service.expressInterest(parseId(id),(UUID)auth.getPrincipal()));}
 /** Reject abbreviated or noncanonical UUID inputs before repository lookup. */
 private UUID parseId(String id){if(!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException("Invalid UUID");return UUID.fromString(id);}
}
