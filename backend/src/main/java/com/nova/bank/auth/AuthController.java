package com.nova.bank.auth;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/** Expose only login and authenticated logout. */
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthService auth;
 /** Delegate authentication state transitions to the service. */ public AuthController(AuthService auth){this.auth=auth;}
 /** Validate credentials and return the safe signed-session contract. */
 @PostMapping("/login") public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request){return auth.login(request);}
 /** Revoke the bearer already validated by the security filter. */
 @PostMapping("/logout") public Map<String,String> logout(@RequestHeader("Authorization") String header){auth.logout(header.substring(7));return Map.of("message","Logged out successfully.");}
}
