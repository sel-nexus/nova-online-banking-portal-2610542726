package com.nova.bank.auth;
import com.nova.bank.TestSupport;
import com.nova.bank.common.ApiException;
import com.nova.bank.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Exercise service transitions and the real PostgreSQL-backed HTTP security boundary. */
public class AuthTests extends TestSupport {
 @Autowired UserRepository users; @Autowired TokenService tokens;
 @Value("${nova.jwt-secret}") String secret;
 /** Verify safe previous-login response, BCrypt fixtures and successful reset of consecutive failures. */
 @Test void successfulServiceLoginCommitsAndReturnsPreviousLogin(){
  assertThatThrownBy(()->auth.login(new AuthDtos.LoginRequest("miguel","wrong"))).isInstanceOf(ApiException.class);
  var result=auth.login(new AuthDtos.LoginRequest("miguel","demo-only"));
  assertThat(result.user().lastLogin()).isEqualTo(Instant.parse("2026-10-14T08:10:00Z"));
  assertThat(result.user().fullName()).isEqualTo("Miguel De La Cruz");assertThat(result.tokenType()).isEqualTo("Bearer");assertThat(result.expiresIn()).isEqualTo(86400);
  User persisted=users.findByUsername("miguel").orElseThrow();assertThat(persisted.getFailedAttempts()).isZero();assertThat(persisted.getLastLogin()).isAfter(result.user().lastLogin());
  assertThat(persisted.getPasswordHash()).startsWith("$2a$");assertThat(tokens.verify(result.token()).getId()).isEqualTo(persisted.getId());
 }
 /** Commit failure counts even though the direct service call throws. */
 @Test void serviceFailuresLockAndStayLocked(){for(int i=1;i<=3;i++){int attempt=i;assertThatThrownBy(()->auth.login(new AuthDtos.LoginRequest("rosa","wrong"))).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(attempt==3?HttpStatus.LOCKED:HttpStatus.UNAUTHORIZED);assertThat(e.getMessage()).isEqualTo(attempt==3?AuthService.LOCKED:AuthService.INVALID);});assertThat(users.findByUsername("rosa").orElseThrow().getFailedAttempts()).isEqualTo(i);}
  auth.seedUsers();assertThatThrownBy(()->auth.login(new AuthDtos.LoginRequest("rosa","demo-only"))).hasMessage(AuthService.LOCKED);assertThat(users.findByUsername("rosa").orElseThrow().isLocked()).isTrue();
 }
 /** Ensure unknown and injection-like usernames remain generic and do not mutate fixtures. */
 @Test void serviceRejectsUnknownAndSecurityStrings(){for(String name:List.of("missing","' OR 1=1 --","<script>alert(1)</script>"))assertThatThrownBy(()->auth.login(new AuthDtos.LoginRequest(name,"demo-only"))).hasMessage(AuthService.INVALID);assertThat(jdbc.queryForObject("SELECT sum(failed_attempts) FROM users",Integer.class)).isZero();}
 /** Serialize three simultaneous failed attempts rather than losing increments. */
 @Test void concurrentFailuresAreSerialized() throws Exception {try(var executor=Executors.newFixedThreadPool(3)){CountDownLatch start=new CountDownLatch(1);List<Future<Integer>> futures=new ArrayList<>();for(int i=0;i<3;i++)futures.add(executor.submit(()->{start.await();try{auth.login(new AuthDtos.LoginRequest("miguel","wrong"));return 200;}catch(ApiException e){return e.status().value();}}));start.countDown();List<Integer> statuses=new ArrayList<>();for(var f:futures)statuses.add(f.get(20,TimeUnit.SECONDS));assertThat(statuses).containsExactlyInAnyOrder(401,401,423);}
  var u=users.findByUsername("miguel").orElseThrow();assertThat(u.getFailedAttempts()).isEqualTo(3);assertThat(u.isLocked()).isTrue();}
 /** Verify HTTP success fields never include private hash or counters. */
 @Test void httpLoginHasSafeResponseForEveryPersona() throws Exception {for(String name:List.of("miguel","rosa","admin")){var r=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username",name,"password","demo-only")))).andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.user.username").value(name)).andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andExpect(jsonPath("$.user.failedAttempts").doesNotExist()).andReturn();assertThat(r.getResponse().getContentAsString()).doesNotContain("demo-only","password_hash");}}
 /** Check explicit HTTP rejection bodies and durable lock state after each response. */
 @Test void httpLockoutStatusBodyAndPersistence() throws Exception {for(int i=1;i<=3;i++){mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"username\":\"miguel\",\"password\":\"wrong\"}")).andExpect(status().is(i==3?423:401)).andExpect(jsonPath("$.status").value(i==3?423:401)).andExpect(jsonPath("$.message").value(i==3?AuthService.LOCKED:AuthService.INVALID)).andExpect(jsonPath("$.errors").isMap()).andExpect(jsonPath("$.timestamp").exists());assertThat(users.findByUsername("miguel").orElseThrow().getFailedAttempts()).isEqualTo(i);}mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"username\":\"miguel\",\"password\":\"demo-only\"}")).andExpect(status().isLocked()).andExpect(jsonPath("$.message").value(AuthService.LOCKED));}
 /** Confirm successful HTTP and direct logout persist revocation, with replay denied. */
 @Test void logoutRevokesAndReplaysAreUnauthorized() throws Exception {String token=login("miguel");mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logged out successfully."));assertThat(jdbc.queryForObject("SELECT count(*) FROM token_revocations",Integer.class)).isEqualTo(1);assertThatThrownBy(()->tokens.verify(token)).hasMessage("Revoked bearer");mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));String second=login("rosa");auth.logout(second);auth.logout(second);assertThat(jdbc.queryForObject("SELECT count(*) FROM token_revocations",Integer.class)).isEqualTo(2);}
 /** Reject missing, malformed, tampered, expired, unsigned and incomplete bearers. */
 @Test void tokenFailuresReturnSafe401() throws Exception {
  String valid=login("miguel");var key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  String expired=Jwts.builder().subject(users.findByUsername("miguel").orElseThrow().getId().toString()).id(UUID.randomUUID().toString()).expiration(Date.from(Instant.now().minusSeconds(10))).signWith(key,Jwts.SIG.HS256).compact();
  String incomplete=Jwts.builder().subject(users.findByUsername("miguel").orElseThrow().getId().toString()).expiration(Date.from(Instant.now().plusSeconds(30))).signWith(key,Jwts.SIG.HS256).compact();
  String[] headers={"","Basic abc","Bearer nonsense","Bearer "+valid.substring(0,valid.lastIndexOf('.')+1)+"bad-signature","Bearer "+expired,"Bearer "+incomplete,"Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9."};
  for(String header:headers)mvc.perform(post("/api/v1/auth/logout").header("Authorization",header)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.message").value("Authentication required."));
  mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors").isMap());
 }
 /** Enforce required fields, bounded lengths, unknown JSON fields and safe malformed input. */
 @Test void validationRejectsInvalidRequestsWithoutMutation() throws Exception {
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.username").value("User ID is required")).andExpect(jsonPath("$.errors.password").value("Password is required."));
  for(String body:List.of("{",mapper.writeValueAsString(Map.of("username","m".repeat(51),"password","demo-only")),mapper.writeValueAsString(Map.of("username","miguel","password","x".repeat(73))),"{\"username\":\"miguel\",\"password\":\"demo-only\",\"role\":\"ROLE_ADMIN\"}"))mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
  assertThat(jdbc.queryForObject("SELECT sum(failed_attempts) FROM users",Integer.class)).isZero();
 }
 /** Keep only health/login public and deny customers administrator paths before routing. */
 @Test void securityMatrixIsFailClosed() throws Exception {mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));for(String path:List.of("/api/v1/portfolio/summary","/api/v1/accounts","/api/v1/support/content","/api/v1/admin/audit"))mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));mvc.perform(post("/api/v1/admin/reset").header("Authorization","Bearer "+login("rosa"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Access denied."));}
 /** Reject locked or missing identities even with an otherwise valid signature. */
 @Test void currentIdentityIsRequired(){String token=auth.login(new AuthDtos.LoginRequest("miguel","demo-only")).token();jdbc.update("UPDATE users SET locked=true WHERE username='miguel'");assertThatThrownBy(()->tokens.verify(token)).hasMessage("Locked identity");var key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));String absent=Jwts.builder().subject(UUID.randomUUID().toString()).id(UUID.randomUUID().toString()).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key,Jwts.SIG.HS256).compact();assertThatThrownBy(()->tokens.verify(absent)).hasMessage("Unknown bearer");}
 /** Prove maintenance provisioning selects the named real database and rejects unsafe identifiers. */
 @Test void databaseProvisioningIsIdempotentAndSafe() throws Exception {var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://localhost:5432/postgres").withProperty("spring.datasource.username","postgres").withProperty("spring.datasource.password","postgres").withProperty("nova.db-name","nova_provision_test");try(var pool=(com.zaxxer.hikari.HikariDataSource)new DatabaseConfig().dataSource(env);var c=pool.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT current_database()")){r.next();assertThat(r.getString(1)).isEqualTo("nova_provision_test");}env.setProperty("nova.db-name","bad;DROP DATABASE postgres");assertThatThrownBy(()->new DatabaseConfig().dataSource(env)).isInstanceOf(IllegalArgumentException.class);}
 /** Prove revocation-store failures deny requests rather than accepting a verified signature alone. */
 @Test void revocationDatabaseFailureIsFailClosed() throws Exception {String token=login("miguel");jdbc.execute("ALTER TABLE token_revocations RENAME TO token_revocations_unavailable");try{mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));}finally{jdbc.execute("ALTER TABLE token_revocations_unavailable RENAME TO token_revocations");}assertThat(tokens.verify(token).getUsername()).isEqualTo("miguel");}
}
