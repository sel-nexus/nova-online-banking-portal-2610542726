package com.nova.bank.chat;

import com.nova.bank.common.ApiException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Delegate authenticated queries without trusting body identity or retaining messages. */
@RestController @RequestMapping("/api/v1/chat")
public class ChatController {
 private final ChatService service;
 /** Bind the scripted assistant boundary. */
 public ChatController(ChatService service){this.service=service;}
 /** Use only the security filter's verified UUID principal. */
 @PostMapping("/query") public ResponseEntity<ChatDtos.Reply> query(Authentication auth,@Valid @RequestBody ChatDtos.Request request){
  if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof UUID id))throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required.");
  return ResponseEntity.ok(service.query(id,request));
 }
}
