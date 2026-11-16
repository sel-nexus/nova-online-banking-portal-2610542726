package com.nova.bank.admin;

import com.nova.bank.TestSupport;
import com.nova.bank.auth.*;
import com.nova.bank.activity.ActivityService;
import com.nova.bank.portfolio.PortfolioService;
import com.nova.bank.offers.OfferService;
import com.nova.bank.support.*;
import com.nova.bank.chat.*;
import com.nova.bank.common.ApiException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise privileged transitions through services and MockMvc against real PostgreSQL. */
public class AdminTests extends TestSupport {
 @Autowired AdminService admin; @Autowired TokenService tokens; @Autowired UserRepository users;
 @Autowired PortfolioService portfolio; @Autowired ActivityService activity; @Autowired OfferService offers;
 @Autowired SupportService support; @Autowired ChatService chat; @Autowired PlatformTransactionManager transactions;
 @Value("${nova.jwt-secret}") String secret;
 private static final UUID MIGUEL=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
 private static final UUID SAVINGS=UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");
 private static final UUID PROMOTION=UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a20");
 /** Restore feature data without relying on prior test order or erasing audit history. */
 @Override protected void resetFeatureFixtures(){
  support.clearInquiries();offers.clearInterests();jdbc.update("DELETE FROM transactions");jdbc.update("DELETE FROM accounts");jdbc.update("DELETE FROM promotions");
  jdbc.update("DELETE FROM reference_content WHERE content_key IN ('support.directory','chat.assistant')");
  auth.restoreUsers();portfolio.seedAccounts();activity.seedTransactions();offers.seedPromotions();support.seedContent();chat.seedContent();
 }
 /** Construct input through the same strict JSON contract used by HTTP. */
 private AdminDtos.Persona persona(String name){return new AdminDtos.Persona(mapper.valueToTree(name));}
 /** Construct a true JSON boolean rather than a coercible scalar. */
 private AdminDtos.Simulation simulation(boolean enabled){return new AdminDtos.Simulation(mapper.valueToTree(enabled));}
 /** Read committed table counts for transition assertions. */
 private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
 /** Check every control for customer 403, anonymous 401 and administrator 200. */
 @Test void fullEndpointRoleMatrix() throws Exception {
  for(String endpoint:List.of("audit","reset","persona","simulation")){
   String body=endpoint.equals("persona")?"{\"username\":\"miguel\"}":endpoint.equals("simulation")?"{\"serviceError\":false}":"{}";
   var anonymous=endpoint.equals("audit")?get("/api/v1/admin/"+endpoint):post("/api/v1/admin/"+endpoint).contentType("application/json").content(body);
   mvc.perform(anonymous).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));
   for(String customer:List.of("miguel","rosa")){
    var denied=endpoint.equals("audit")?get("/api/v1/admin/"+endpoint):post("/api/v1/admin/"+endpoint).contentType("application/json").content(body);
    mvc.perform(denied.header("Authorization","Bearer "+login(customer))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Access denied."));
   }
   var allowed=endpoint.equals("audit")?get("/api/v1/admin/"+endpoint):post("/api/v1/admin/"+endpoint).contentType("application/json").content(body);
   mvc.perform(allowed.header("Authorization","Bearer "+login("admin"))).andExpect(status().isOk());
  }
 }
 /** Reject malformed, missing, wrong-type, unknown, oversized and hostile persona input without issuance. */
 @Test void personaValidationDoesNotIssueOrAudit() throws Exception {
  String token=login("admin");int sessions=count("active_sessions"),audit=count("audit_events");
  List<String> bodies=new ArrayList<>(List.of("{}","null","{","{\"username\":null}","{\"username\":123}","{\"username\":true}","{\"username\":[]}","{\"username\":{}}","{\"username\":\"miguel\",\"role\":\"ROLE_ADMIN\"}"));
  for(String name:List.of(""," ","admin","missing","MIGUEL","' OR 1=1 --","<script>alert(1)</script>","../../admin","x".repeat(5000)))bodies.add(mapper.writeValueAsString(Map.of("username",name)));
  for(String body:bodies)mvc.perform(post("/api/v1/admin/persona").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
  assertThat(count("active_sessions")).isEqualTo(sessions);assertThat(count("audit_events")).isEqualTo(audit);
 }
 /** Reject simulation coercion and unknown fields while preserving the session setting. */
 @Test void simulationValidationPreservesState() throws Exception {
  String token=login("admin");int before=count("audit_events");
  for(String body:List.of("{}","null","{","{\"serviceError\":null}","{\"serviceError\":\"true\"}","{\"serviceError\":1}","{\"serviceError\":[]}","{\"serviceError\":{}}","{\"serviceError\":false,\"userId\":\"admin\"}"))mvc.perform(post("/api/v1/admin/simulation").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request."));
  assertThat(tokens.serviceError(token)).isFalse();assertThat(count("audit_events")).isEqualTo(before);
 }
 /** Deny direct service calls for absent, expired and customer sessions, not merely controller annotations. */
 @Test void directServicesRequireAuthoritativeAdministrator() throws Exception {
  String customer=login("miguel");
  assertThatThrownBy(()->admin.audit(customer)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->admin.reset(customer)).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->admin.persona(customer,persona("rosa"))).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->admin.simulation(customer,simulation(true))).isInstanceOf(AccessDeniedException.class);
  assertThatThrownBy(()->admin.audit("bad")).isInstanceOf(ApiException.class);
  String expired=signed(Instant.now().minusSeconds(10));
  for(String path:List.of("audit","reset","persona","simulation")){
   var r=path.equals("audit")?get("/api/v1/admin/"+path):post("/api/v1/admin/"+path).contentType("application/json").content(path.equals("persona")?"{\"username\":\"rosa\"}":"{\"serviceError\":false}");
   mvc.perform(r.header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
  }
  String facilitator=login("admin");jdbc.update("UPDATE users SET role='ROLE_CUSTOMER' WHERE username='admin'");
  try{mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+facilitator)).andExpect(status().isForbidden());assertThatThrownBy(()->admin.audit(facilitator)).isInstanceOf(AccessDeniedException.class);}finally{jdbc.update("UPDATE users SET role='ROLE_ADMIN' WHERE username='admin'");}
 }
 /** Create a signature-valid hand-issued token for expiration and untracked-session checks. */
 private String signed(Instant expiry){return Jwts.builder().subject("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13").id(UUID.randomUUID().toString()).expiration(Date.from(expiry)).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)),Jwts.SIG.HS256).compact();}
 /** Verify persistent active-session authority, signature rejection and fail-closed store failure. */
 @Test void sessionsRequireAuthenticityAndPersistentAuthority() throws Exception {
  String token=login("admin");assertThat(tokens.verify(token).getUsername()).isEqualTo("admin");
  for(String invalid:List.of(signed(Instant.now().plusSeconds(60)),token.substring(0,token.lastIndexOf('.')+1)+"bad","eyJhbGciOiJub25lIn0.e30."))mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+invalid)).andExpect(status().isUnauthorized());
  jdbc.execute("ALTER TABLE active_sessions RENAME TO active_sessions_unavailable");
  try{mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());}finally{jdbc.execute("ALTER TABLE active_sessions_unavailable RENAME TO active_sessions");}
  assertThat(tokens.verify(token).getUsername()).isEqualTo("admin");
 }
 /** Mutate financial, identity, campaign and help fixtures and create runtime interest/enquiry activity. */
 private void mutate(){
  jdbc.update("UPDATE accounts SET available_balance=1,current_balance=2,status='CLOSED' WHERE id=?",SAVINGS);
  jdbc.update("UPDATE users SET locked=true,failed_attempts=3,password_hash='broken',full_name='Changed',last_login=now() WHERE username='miguel'");
  jdbc.update("UPDATE transactions SET amount=7,description='Changed'");jdbc.update("UPDATE promotions SET title='Changed',active=false");
  jdbc.update("INSERT INTO user_offer_interests(user_id,promotion_id) VALUES (?,?)",MIGUEL,PROMOTION);
  support.submit(MIGUEL,new SupportDtos.Request("CARDS","Reset fixture","Simulation message"));
  jdbc.update("UPDATE reference_content SET content='{}'::jsonb WHERE content_key IN ('support.directory','chat.assistant')");
 }
 /** Verify exact reset notice, restored fixtures, unlock, historical audit, revocation and retained admin session. */
 @Test void resetRestoresMutationsAndRevokesCustomersRetainingAudit() throws Exception {
  String customer=login("miguel"),rosa=login("rosa"),facilitator=login("admin");
  var historical=admin.audit(facilitator);int migrationCount=count("flyway_schema_history");
  admin.simulation(facilitator,simulation(true));mutate();
  mvc.perform(post("/api/v1/admin/reset").header("Authorization","Bearer "+facilitator)).andExpect(status().isOk()).andExpect(content().json("{\"message\":\""+AdminService.NOTICE+"\"}"));
  assertThat(portfolio.summary(MIGUEL).relationshipBalance()).isEqualTo("142860.25");assertThat(count("accounts")).isEqualTo(6);assertThat(count("transactions")).isEqualTo(6);assertThat(count("promotions")).isEqualTo(10);
  assertThat(jdbc.queryForObject("SELECT description FROM transactions WHERE reference_code='REF-TX-882191'",String.class)).isEqualTo("Salary credit");
  assertThat(jdbc.queryForObject("SELECT active FROM promotions WHERE offer_key='new-year-cashback-expired'",Boolean.class)).isFalse();
  assertThat(jdbc.queryForObject("SELECT status FROM accounts WHERE id=?",String.class,SAVINGS)).isEqualTo("ACTIVE");
  assertThat(count("customer_inquiries")).isZero();assertThat(count("user_offer_interests")).isZero();
  assertThat(users.findByUsername("miguel").orElseThrow().getFullName()).isEqualTo("Miguel De La Cruz");
  assertThat(users.findAll()).allMatch(u->!u.isLocked()&&u.getFailedAttempts()==0&&u.getLastLogin().equals(Instant.parse("2026-10-14T08:10:00Z")));
  assertThat(support.content(MIGUEL).branches()).hasSize(3);assertThat(chat.query(MIGUEL,new ChatDtos.Request("Account Balance")).message()).contains("142,860.25");
  assertThat(admin.audit(facilitator)).containsAll(historical).anyMatch(e->e.action().equals("RESET_SUCCESS"));assertThat(count("flyway_schema_history")).isEqualTo(migrationCount);
  for(String revoked:List.of(customer,rosa)){mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+revoked)).andExpect(status().isUnauthorized());assertThatThrownBy(()->tokens.verify(revoked)).hasMessage("Revoked bearer");}
  assertThat(tokens.verify(facilitator).getRole()).isEqualTo("ROLE_ADMIN");assertThat(tokens.serviceError(facilitator)).isFalse();
  assertThat(auth.login(new AuthDtos.LoginRequest("miguel","demo-only")).user().lastLogin()).isEqualTo(Instant.parse("2026-10-14T08:10:00Z"));
  assertThat(support.submit(MIGUEL,new SupportDtos.Request("GENERAL","Fresh","Fresh message")).referenceNumber()).isEqualTo("NB-99214");
 }
 /** Repeat reset without losing historical audits, fixture IDs or reference allocation. */
 @Test void resetIsRepeatableAndCleansExpiredSessions() throws Exception {
  String facilitator=login("admin");String customer=login("rosa");jdbc.update("UPDATE active_sessions SET expires_at=now()-interval '1 second' WHERE user_id<>?",users.findByUsername("admin").orElseThrow().getId());
  admin.reset(facilitator);int before=count("audit_events");admin.reset(facilitator);
  assertThat(count("audit_events")).isEqualTo(before+1);assertThat(count("active_sessions")).isEqualTo(1);assertThat(count("token_revocations")).isZero();
  mvc.perform(get("/api/v1/accounts").header("Authorization","Bearer "+customer)).andExpect(status().isUnauthorized());
  assertThat(portfolio.summary(MIGUEL).accounts()).hasSize(4);assertThat(support.submit(MIGUEL,new SupportDtos.Request("GENERAL","Fresh","Fresh message")).referenceNumber()).isEqualTo("NB-99214");
 }
 /** Force an actual database failure at the success audit and prove all earlier reset changes roll back. */
 @Test void failedResetRollsBackDataSessionsAndSequence() throws Exception {
  String customer=login("rosa"),facilitator=login("admin");admin.simulation(facilitator,simulation(true));mutate();
  int before=count("audit_events");
  jdbc.execute("ALTER TABLE audit_events ADD CONSTRAINT reject_reset_test CHECK(action<>'RESET_SUCCESS') NOT VALID");
  try{assertThatThrownBy(()->admin.reset(facilitator)).isInstanceOf(DataIntegrityViolationException.class);}finally{jdbc.execute("ALTER TABLE audit_events DROP CONSTRAINT reject_reset_test");}
  assertThat(count("audit_events")).isEqualTo(before);assertThat(count("customer_inquiries")).isEqualTo(1);assertThat(count("user_offer_interests")).isEqualTo(1);
  assertThat(portfolio.summary(MIGUEL).relationshipBalance()).isEqualTo("26241.15");assertThat(users.findByUsername("miguel").orElseThrow().isLocked()).isTrue();
  assertThat(tokens.verify(customer).getUsername()).isEqualTo("rosa");assertThat(tokens.serviceError(facilitator)).isTrue();
  assertThat(jdbc.queryForObject("SELECT content::text FROM reference_content WHERE content_key='chat.assistant'",String.class)).isEqualTo("{}");
  assertThat(support.submit(MIGUEL,new SupportDtos.Request("GENERAL","After failure","Still simulated")).referenceNumber()).isEqualTo("NB-99215");
 }
 /** Issue each correct customer bearer with audit and remove previous admin token authority immediately. */
 @Test void personaSwitchIsAuditedAndCustomerOnly() throws Exception {
  for(String name:List.of("miguel","rosa")){
   String facilitator=login("admin");int before=count("audit_events");
   var result=mvc.perform(post("/api/v1/admin/persona").header("Authorization","Bearer "+facilitator).contentType("application/json").content(mapper.writeValueAsString(Map.of("username",name)))).andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(name)).andExpect(jsonPath("$.user.role").value("ROLE_CUSTOMER")).andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(86400)).andReturn();
   var response=mapper.readValue(result.getResponse().getContentAsString(),AuthDtos.LoginResponse.class);assertThat(tokens.verify(response.token()).getUsername()).isEqualTo(name);
   mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+response.token())).andExpect(status().isForbidden());
   mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+facilitator)).andExpect(status().isUnauthorized());
   assertThat(count("audit_events")).isEqualTo(before+1);assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action=?",Integer.class,"PERSONA_SWITCH_"+name.toUpperCase(Locale.ROOT))).isPositive();
   mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+response.token())).andExpect(status().isOk()).andExpect(jsonPath("$.accounts.length()").value(name.equals("miguel")?4:2));
  }
 }
 /** Never use persona switching to bypass lockout or a missing/noncustomer target. */
 @Test void personaRejectsLockedMissingAndNoncustomerTargets() throws Exception {
  String facilitator=login("admin");jdbc.update("UPDATE users SET locked=true,failed_attempts=3 WHERE username='rosa'");
  mvc.perform(post("/api/v1/admin/persona").header("Authorization","Bearer "+facilitator).contentType("application/json").content("{\"username\":\"rosa\"}")).andExpect(status().isLocked()).andExpect(jsonPath("$.message").value(AuthService.LOCKED));
  assertThat(users.findByUsername("rosa").orElseThrow().isLocked()).isTrue();assertThat(tokens.verify(facilitator).getRole()).isEqualTo("ROLE_ADMIN");
  jdbc.update("UPDATE users SET role='ROLE_ADMIN',locked=false WHERE username='rosa'");
  try{assertThatThrownBy(()->admin.persona(facilitator,persona("rosa"))).isInstanceOf(AccessDeniedException.class);}finally{jdbc.update("UPDATE users SET role='ROLE_CUSTOMER' WHERE username='rosa'");}
  jdbc.update("DELETE FROM users WHERE username='rosa'");
  try{assertThatThrownBy(()->admin.persona(facilitator,persona("rosa"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status().value()).isEqualTo(404));}finally{auth.seedUsers();portfolio.seedAccounts();}
 }
 /** Isolate two admin sessions, return business 503 only, and allow disable, reset and logout during simulation. */
 @Test void simulationIsSessionScopedAndRecoveryRemainsReachable() throws Exception {
  String first=login("admin"),second=login("admin"),customer=login("rosa");
  mvc.perform(post("/api/v1/admin/simulation").header("Authorization","Bearer "+first).contentType("application/json").content("{\"serviceError\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.serviceError").value(true));
  assertThat(tokens.serviceError(first)).isTrue();assertThat(tokens.serviceError(second)).isFalse();assertThat(tokens.serviceError(customer)).isFalse();
  for(String path:List.of("/api/v1/portfolio/summary","/api/v1/accounts","/api/v1/accounts/"+SAVINGS+"/transactions","/api/v1/accounts/"+SAVINGS+"/statement/download","/api/v1/products","/api/v1/promotions","/api/v1/promotions/"+PROMOTION,"/api/v1/support/content"))mvc.perform(get(path).header("Authorization","Bearer "+first)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
  mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+second)).andExpect(status().isOk());mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+customer)).andExpect(status().isOk());
  mvc.perform(get("/api/health").header("Authorization","Bearer "+first)).andExpect(status().isOk());mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+first)).andExpect(status().isOk());
  mvc.perform(post("/api/v1/chat/query").header("Authorization","Bearer "+first).contentType("application/json").content("{\"message\":\"Speak to Agent\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/v1/admin/simulation").header("Authorization","Bearer "+first).contentType("application/json").content("{\"serviceError\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.serviceError").value(false));
  mvc.perform(get("/api/v1/products").header("Authorization","Bearer "+first)).andExpect(status().isOk());
  admin.simulation(first,simulation(true));mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/products").header("Authorization","Bearer "+first)).andExpect(status().isUnauthorized());assertThat(tokens.serviceError(second)).isFalse();
 }
 /** Exclude legacy free-form PII details and tokens from audit output while retaining persistent UUID events. */
 @Test void auditIsPersistentSafeAndOrdered() throws Exception {
  String facilitator=login("admin");UUID id=UUID.randomUUID();jdbc.update("INSERT INTO audit_events(id,user_id,action,details,created_at) VALUES (?,?,'LEGACY_EVENT',?,now()+interval '1 second')",id,MIGUEL,"secret email person@example.com "+facilitator);
  var response=mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+facilitator)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString())).andExpect(jsonPath("$[0].action").value("LEGACY_EVENT")).andExpect(jsonPath("$[0].userId").value(MIGUEL.toString())).andExpect(jsonPath("$[0].timestamp").exists()).andExpect(jsonPath("$[0].details").doesNotExist()).andReturn();
  assertThat(response.getResponse().getContentAsString()).doesNotContain(facilitator,"person@example.com","demo-only","password","Miguel De La Cruz");assertThat(admin.audit(facilitator)).anyMatch(e->e.id().equals(id));
 }
 /** Verify real session schema defaults, uniqueness, nullability, foreign keys and cascade. */
 @Test void sessionSchemaEnforcesIntegrity(){
  UUID jti=jdbc.queryForObject("INSERT INTO active_sessions(user_id,expires_at) VALUES (?,now()+interval '1 day') RETURNING jti",UUID.class,MIGUEL);
  assertThat(jti).isNotNull();assertThat(jdbc.queryForObject("SELECT service_error FROM active_sessions WHERE jti=?",Boolean.class,jti)).isFalse();assertThat(jdbc.queryForObject("SELECT created_at IS NOT NULL FROM active_sessions WHERE jti=?",Boolean.class,jti)).isTrue();
  assertThatThrownBy(()->jdbc.update("INSERT INTO active_sessions(jti,user_id,expires_at) VALUES (?,?,now())",jti,MIGUEL)).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO active_sessions(user_id,expires_at) VALUES (?,now())",UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO active_sessions(user_id) VALUES (?)",MIGUEL)).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO active_sessions(user_id,expires_at,service_error) VALUES (?,now(),null)",MIGUEL)).isInstanceOf(DataIntegrityViolationException.class);
  UUID extra=UUID.randomUUID();jdbc.update("INSERT INTO users(id,username,password_hash,full_name) VALUES (?,'session-extra','hash','Extra')",extra);jdbc.update("INSERT INTO active_sessions(user_id,expires_at) VALUES (?,now())",extra);jdbc.update("DELETE FROM users WHERE id=?",extra);assertThat(jdbc.queryForObject("SELECT count(*) FROM active_sessions WHERE user_id=?",Integer.class,extra)).isZero();
 }
 /** Serialize reset with an actual in-flight login identity lock and revoke its committed customer token. */
 @Test void resetWaitsForIdentityLockAndRevokesIssuedCustomer() throws Exception {
  String facilitator=login("admin");CountDownLatch held=new CountDownLatch(1),release=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)){
   Future<String> issuing=executor.submit(()->new TransactionTemplate(transactions).execute(status->{User user=users.lockByUsername("miguel").orElseThrow();held.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return tokens.issue(user);}));
   assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();Future<AdminDtos.Notice> resetting=executor.submit(()->admin.reset(facilitator));
   try{assertThatThrownBy(()->resetting.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
   String issued=issuing.get(10,TimeUnit.SECONDS);assertThat(resetting.get(10,TimeUnit.SECONDS).message()).isEqualTo(AdminService.NOTICE);assertThatThrownBy(()->tokens.verify(issued)).hasMessage("Revoked bearer");assertThat(tokens.verify(facilitator).getRole()).isEqualTo("ROLE_ADMIN");
  }
 }
}
