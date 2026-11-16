package com.nova.bank.admin;

import com.nova.bank.auth.AuthDtos;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Delegate administrator-only contracts with a verified bearer, never a request role. */
@RestController @RequestMapping("/api/v1/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminController {
 private final AdminService service;
 /** Bind privileged service operations. */
 public AdminController(AdminService service){this.service=service;}
 /** Extract the bearer already validated by the security filter. */
 private String bearer(String authorization){return authorization.substring(7);}
 /** Return persistent safe audit events. */
 @GetMapping("/audit") public ResponseEntity<List<AdminDtos.Audit>> audit(@RequestHeader("Authorization") String authorization){return ResponseEntity.ok(service.audit(bearer(authorization)));}
 /** Return the exact notice only after successful atomic restoration. */
 @PostMapping("/reset") public ResponseEntity<AdminDtos.Notice> reset(@RequestHeader("Authorization") String authorization){return ResponseEntity.ok(service.reset(bearer(authorization)));}
 /** Return a customer login response and relinquish administrator session authority. */
 @PostMapping("/persona") public ResponseEntity<AuthDtos.LoginResponse> persona(@RequestHeader("Authorization") String authorization,@Valid @RequestBody AdminDtos.Persona request){return ResponseEntity.ok(service.persona(bearer(authorization),request));}
 /** Return the issuing session's persisted simulation setting. */
 @PostMapping("/simulation") public ResponseEntity<AdminDtos.SimulationState> simulation(@RequestHeader("Authorization") String authorization,@Valid @RequestBody AdminDtos.Simulation request){return ResponseEntity.ok(service.simulation(bearer(authorization),request));}
}
