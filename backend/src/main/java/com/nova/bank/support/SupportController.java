package com.nova.bank.support;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
/** Delegate protected support operations using the signed UUID principal, never a body owner. */
@RestController @RequestMapping("/api/v1/support")
public class SupportController {
 private final SupportService service;
 /** Bind the support boundary. */ public SupportController(SupportService service){this.service=service;}
 /** Return persisted reference content. */ @GetMapping("/content")
 public ResponseEntity<SupportDtos.Content> content(Authentication auth){return ResponseEntity.ok(service.content((UUID)auth.getPrincipal()));}
 /** Return the exact created simulation receipt. */ @PostMapping("/inquiries")
 public ResponseEntity<SupportDtos.Receipt> submit(Authentication auth,@Valid @RequestBody SupportDtos.Request request){return ResponseEntity.status(201).body(service.submit((UUID)auth.getPrincipal(),request));}
}
