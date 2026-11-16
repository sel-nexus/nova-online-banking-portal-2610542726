package com.nova.bank.statements;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Deliver owner/admin-only UTF-8 text attachments without private identifiers. */
@RestController
@RequestMapping("/api/v1/accounts")
public class StatementController {
    private final StatementService service;
    /** Bind the ownership-enforcing export service. */
    public StatementController(StatementService service) { this.service = service; }
    /** Derive privileges from the bearer and prohibit caching financial text. */
    @GetMapping("/{id}/statement/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        var result = service.download(id, (UUID) auth.getPrincipal(), admin);
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.content().getBytes(StandardCharsets.UTF_8));
    }
}
