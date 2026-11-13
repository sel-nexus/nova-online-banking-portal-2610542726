package com.nova.bank.common;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
/** Expose public process liveness without customer data. */
@RestController
public class HealthController {
 /** Confirm the HTTP process is responding. */
 @GetMapping("/api/health") public Map<String,String> health() { return Map.of("status","UP"); }
}
