package com.nova.bank.portfolio;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Delegate authenticated portfolio requests without accepting customer overrides. */
@RestController
@RequestMapping("/api/v1")
public class PortfolioController {
    private final PortfolioService service;
    /** Bind the owner-enforcing business service. */
    public PortfolioController(PortfolioService service) { this.service = service; }
    /** Return only the bearer subject's relationship balance. */
    @GetMapping("/portfolio/summary")
    public ResponseEntity<PortfolioDtos.Summary> summary(Authentication authentication) {
        return ResponseEntity.ok(service.summary((UUID) authentication.getPrincipal()));
    }
    /** Return only the bearer subject's safe account summaries. */
    @GetMapping("/accounts")
    public ResponseEntity<List<PortfolioDtos.AccountSummary>> accounts(Authentication authentication) {
        return ResponseEntity.ok(service.listAccounts((UUID) authentication.getPrincipal()));
    }
}
