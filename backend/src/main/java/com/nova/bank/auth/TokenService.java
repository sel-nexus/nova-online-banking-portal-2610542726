package com.nova.bank.auth;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Sign bearer claims and consult durable revocations on every protected request. */
@Service
public class TokenService {
 private final SecretKey key; private final long seconds; private final JdbcTemplate jdbc; private final UserRepository users;
 /** Bind the signing key and reject unsafe keys outside explicit local demonstration mode. */
 public TokenService(@Value("${nova.jwt-secret}") String secret,@Value("${nova.jwt-seconds}") long seconds,@Value("${nova.deployment}") String deployment,JdbcTemplate jdbc,UserRepository users) {
  if(secret.getBytes(StandardCharsets.UTF_8).length<32 || (!deployment.equals("local") && secret.contains("replace-before-deploy"))) throw new IllegalStateException("JWT_SECRET must be a random signing key of at least 32 bytes");
  if(seconds<1) throw new IllegalStateException("JWT_SECONDS must be positive");
  key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.seconds=seconds; this.jdbc=jdbc; this.users=users;
 }
 /** Return the lifetime used by both issuing and response contracts. */ public long lifetime(){return seconds;}
 /** Issue a signed unique bearer for an authoritative identity. */
 @Transactional public String issue(User user){
  UUID jti=UUID.randomUUID(); Instant expiry=Instant.now().plusSeconds(seconds);
  jdbc.update("INSERT INTO active_sessions(jti,user_id,expires_at) VALUES (?,?,?)",jti,user.getId(),java.sql.Timestamp.from(expiry));
  return Jwts.builder().subject(user.getId().toString()).id(jti.toString()).issuedAt(new Date()).expiration(Date.from(expiry)).signWith(key,Jwts.SIG.HS256).compact();
 }
 /** Verify signature, required claims, expiry, revocation and current identity; database errors propagate fail-closed. */
 @Transactional(readOnly=true) public User verify(String token){
  Claims claims=claims(token); UUID jti=UUID.fromString(claims.getId()); UUID id=UUID.fromString(claims.getSubject());
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM token_revocations WHERE jti=?)",Boolean.class,jti))) throw new IllegalArgumentException("Revoked bearer");
  User user=users.findById(id).orElseThrow(()->new IllegalArgumentException("Unknown bearer"));
  if(user.isLocked()) throw new IllegalArgumentException("Locked identity");
  if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM active_sessions WHERE jti=? AND user_id=? AND expires_at>now())",Boolean.class,jti,id))) throw new IllegalArgumentException("Unknown bearer");
  return user;
 }
 /** Validate required claims without accepting unsigned or alternate algorithm tokens. */
 private Claims claims(String token){
  var parsed=Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
  if(!"HS256".equals(parsed.getHeader().getAlgorithm())) throw new IllegalArgumentException("Invalid algorithm");
  Claims c=parsed.getPayload(); if(c.getExpiration()==null || c.getId()==null || c.getSubject()==null) throw new IllegalArgumentException("Missing claims");
  UUID.fromString(c.getId()); UUID.fromString(c.getSubject()); return c;
 }
 /** Persist logout idempotently, retaining the bearer expiry for later cleanup. */
 @Transactional public void revoke(String token){Claims c=claims(token); jdbc.update("INSERT INTO token_revocations(jti,user_id,expires_at) VALUES (?,?,?) ON CONFLICT(jti) DO NOTHING",UUID.fromString(c.getId()),UUID.fromString(c.getSubject()),java.sql.Timestamp.from(c.getExpiration().toInstant()));jdbc.update("DELETE FROM active_sessions WHERE jti=?",UUID.fromString(c.getId()));}

 /** Read simulation only after authenticating the bearer and its persisted session. */
 @Transactional(readOnly=true) public boolean serviceError(String token){verify(token);return Boolean.TRUE.equals(jdbc.queryForObject("SELECT service_error FROM active_sessions WHERE jti=?",Boolean.class,UUID.fromString(claims(token).getId())));}
 /** Change only the verified administrator's issuing session; never accept a request identity. */
 @Transactional public void setServiceError(String token,boolean enabled){
  if(!verify(token).getRole().equals("ROLE_ADMIN"))throw new org.springframework.security.access.AccessDeniedException("Access denied.");
  if(jdbc.update("UPDATE active_sessions SET service_error=? WHERE jti=?",enabled,UUID.fromString(claims(token).getId()))!=1)throw new IllegalArgumentException("Unknown bearer");
 }
 /** Revoke every customer session transactionally, retaining live revocations and safely cleaning expired rows. */
 @Transactional public void resetCustomerSessions(){
  jdbc.update("INSERT INTO token_revocations(jti,user_id,expires_at) SELECT s.jti,s.user_id,s.expires_at FROM active_sessions s JOIN users u ON u.id=s.user_id WHERE u.role='ROLE_CUSTOMER' AND s.expires_at>now() ON CONFLICT(jti) DO NOTHING");
  jdbc.update("DELETE FROM active_sessions WHERE user_id IN (SELECT id FROM users WHERE role='ROLE_CUSTOMER') OR expires_at<=now()");
  jdbc.update("DELETE FROM token_revocations WHERE expires_at<=now()");
  jdbc.update("UPDATE active_sessions SET service_error=false");
 }
}
