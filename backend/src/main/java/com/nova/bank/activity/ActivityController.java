package com.nova.bank.activity;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Delegate reads using only the verified bearer identity and authority. */
@RestController
@RequestMapping("/api/v1/accounts")
public class ActivityController {
    private final ActivityService service;
    /** Bind the owner-enforcing ledger service. */
    public ActivityController(ActivityService service) { this.service = service; }
    /** Return filtered activity without accepting an owner override. */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<ActivityDtos.Activity> activity(@PathVariable UUID id, Authentication auth,
            @RequestParam(required = false) String search, @RequestParam(required = false) String category,
            @RequestParam(required = false) String direction, @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(service.activity(id, (UUID) auth.getPrincipal(), admin,
                search, category, direction, startDate, endDate, page, size));
    }
}
