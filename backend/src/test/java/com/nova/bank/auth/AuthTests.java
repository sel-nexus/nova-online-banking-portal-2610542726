package com.nova.bank.auth;
import com.nova.bank.TestSupport;
import com.nova.bank.common.ApiException;
import com.nova.bank.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import java.sql.DriverManager;
import org.springframework.core.env.Environment;
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
 @Autowired Environment environment;
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
 /** Enumerate each field independently so coercion cannot hide behind another invalid field. */
 static Stream<Arguments> invalidCredentials(){
  List<Arguments> cases=new ArrayList<>();
  for(String field:List.of("username","password")){
   String required=field.equals("username")?"User ID is required":"Password is required.";
   for(String kind:List.of("omitted","null","empty","whitespace","integer","decimal","boolean","object","array","too-long")){
    Map<String,Object> body=new LinkedHashMap<>(Map.of("username","miguel","password","demo-only"));
    Object value=switch(kind){case "null"->null;case "empty"->"";case "whitespace"->" \t\r\n ";case "integer"->12345;case "decimal"->12.5;case "boolean"->true;case "object"->Map.of("secret","credential-marker");case "array"->List.of("credential-marker");case "too-long"->"x".repeat(field.equals("username")?51:73);default->null;};
    if(kind.equals("omitted"))body.remove(field);else body.put(field,value);
    boolean malformed=List.of("integer","decimal","boolean","object","array").contains(kind);
    String guidance=kind.equals("too-long")?(field.equals("username")?"User ID must be at most 50 characters":"Password must be at most 72 characters"):required;
    cases.add(Arguments.of(field,kind,body,malformed,guidance));
   }
  }
  return cases.stream();
 }
 /** Reject invalid credentials with exact safe errors and no durable authentication mutation. */
 @ParameterizedTest(name="{0} {1} returns safe 400") @MethodSource("invalidCredentials")
 void eachInvalidCredentialReturns400(String field,String kind,Map<String,Object> body,boolean malformed,String guidance) throws Exception {
  jdbc.update("UPDATE users SET failed_attempts=1 WHERE username='miguel'");
  var response=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.writeValueAsString(body)))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
   .andExpect(jsonPath("$.message").value(malformed?"Invalid request.":"Please check the required fields."))
   .andExpect(jsonPath("$.timestamp").isString()).andReturn();
  var error=mapper.readTree(response.getResponse().getContentAsString());
  assertThat(error.get("errors")).isEqualTo(mapper.valueToTree(malformed?Map.of():Map.of(field,guidance)));
  assertThat(response.getResponse().getContentAsString()).doesNotContain("demo-only","credential-marker","passwordHash","Exception","java.lang");
  assertThat(users.findByUsername("miguel").orElseThrow().getFailedAttempts()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT sum(failed_attempts) FROM users",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM active_sessions",Integer.class)).isZero();
 }
 /** Keep combined omissions, malformed JSON and unknown fields on their existing error contracts. */
 @Test void validationRejectsInvalidRequestsWithoutMutation() throws Exception {
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please check the required fields."))
   .andExpect(jsonPath("$.errors.username").value("User ID is required")).andExpect(jsonPath("$.errors.password").value("Password is required."));
  for(String body:List.of("{","{\"username\":\"miguel\",\"password\":\"demo-only\",\"role\":\"ROLE_ADMIN\"}")){
   var r=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.message").value("Invalid request.")).andExpect(jsonPath("$.errors").isEmpty()).andExpect(jsonPath("$.timestamp").isString()).andReturn();
   assertThat(r.getResponse().getContentAsString()).doesNotContain("demo-only","ROLE_ADMIN","Exception");
   assertThat(jdbc.queryForObject("SELECT sum(failed_attempts) FROM users",Integer.class)).isZero();
  }
 }
 /** Accept each exact maximum with matching real BCrypt credentials, rather than treating it as invalid input. */
 @ParameterizedTest @ValueSource(strings={"username","password"})
 void maximumCredentialLengthCanAuthenticate(String field) throws Exception {
  User original=users.findByUsername("miguel").orElseThrow();
  String name=field.equals("username")?"m".repeat(50):"miguel";
  String password=field.equals("password")?"p".repeat(72):"demo-only";
  try{
   jdbc.update("UPDATE users SET username=?,password_hash=?,failed_attempts=1 WHERE id=?",name,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),original.getId());
   var r=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("username",name,"password",password))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value(name)).andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
   assertThat(tokens.verify(mapper.readTree(r.getResponse().getContentAsString()).get("token").asText()).getId()).isEqualTo(original.getId());
   assertThat(users.findById(original.getId()).orElseThrow().getFailedAttempts()).isZero();
   assertThat(r.getResponse().getContentAsString()).doesNotContain(password,"passwordHash","failedAttempts");
  }finally{jdbc.update("UPDATE users SET username=?,password_hash=? WHERE id=?",original.getUsername(),original.getPasswordHash(),original.getId());}
 }
 /** Reject short keys in every deployment and the known unsafe demonstration key outside local mode. */
 @Test void tokenConstructorRejectsUnsafeConfiguration(){
  for(String deployment:List.of("local","production"))assertThatThrownBy(()->new TokenService("x".repeat(31),60,deployment,jdbc,users)).isInstanceOf(IllegalStateException.class).hasMessage("JWT_SECRET must be a random signing key of at least 32 bytes");
  String unsafe="local-demonstration-only-signing-key-replace-before-deploy";
  assertThatThrownBy(()->new TokenService(unsafe,60,"production",jdbc,users)).isInstanceOf(IllegalStateException.class).hasMessage("JWT_SECRET must be a random signing key of at least 32 bytes");
  for(long lifetime:new long[]{0,-1})assertThatThrownBy(()->new TokenService("x".repeat(32),lifetime,"local",jdbc,users)).isInstanceOf(IllegalStateException.class).hasMessage("JWT_SECONDS must be positive");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM active_sessions",Integer.class)).isZero();
  assertThat(new TokenService(unsafe,1,"local",jdbc,users).lifetime()).isEqualTo(1);
 }
 /** Sign with HS256 even when the key is long enough for another HMAC algorithm, and persist a verifiable session. */
 @ParameterizedTest @ValueSource(ints={32,64})
 void tokenConstructorAcceptsSafeKeysAndPositiveLifetime(int bytes){
  String signingSecret="s".repeat(bytes);var service=new TokenService(signingSecret,60,"production",jdbc,users);
  User user=users.findByUsername("miguel").orElseThrow();String token=service.issue(user);
  var parsed=Jwts.parser().verifyWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8))).build().parseSignedClaims(token);
  assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");assertThat(service.lifetime()).isEqualTo(60);
  assertThat(parsed.getPayload().getExpiration().toInstant()).isBetween(Instant.now().plusSeconds(55),Instant.now().plusSeconds(60));
  assertThat(service.verify(token).getId()).isEqualTo(user.getId());assertThat(jdbc.queryForObject("SELECT count(*) FROM active_sessions",Integer.class)).isEqualTo(1);
 }
 /** Reject each individually absent signed claim before any logout mutation. */
 @ParameterizedTest @ValueSource(strings={"exp","sub","jti"})
 void missingSignedClaimIsSafe401(String missing) throws Exception {
  String valid=login("miguel");var key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  var claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(valid).getPayload();
  var builder=Jwts.builder();
  if(!missing.equals("exp"))builder.expiration(claims.getExpiration());
  if(!missing.equals("sub"))builder.subject(claims.getSubject());
  if(!missing.equals("jti"))builder.id(claims.getId());
  String incomplete=builder.signWith(key,Jwts.SIG.HS256).compact();
  assertThatThrownBy(()->tokens.verify(incomplete)).isInstanceOf(IllegalArgumentException.class).hasMessage("Missing claims");
  assertSafeUnauthorizedWithoutRevocation(incomplete);assertThat(tokens.verify(valid).getUsername()).isEqualTo("miguel");
 }
 /** Reject a correctly signed alternate algorithm even with all authoritative live session claims. */
 @Test void alternateSignedAlgorithmIsSafe401() throws Exception {
  String valid=login("miguel");
  // A separate sufficiently long key reaches the application algorithm branch regardless of environment key length.
  String longSecret="a".repeat(64);var service=new TokenService(longSecret,60,"production",jdbc,users);
  String issued=service.issue(users.findByUsername("miguel").orElseThrow());var longKey=Keys.hmacShaKeyFor(longSecret.getBytes(StandardCharsets.UTF_8));
  var claims=Jwts.parser().verifyWith(longKey).build().parseSignedClaims(issued).getPayload();
  String alternate=Jwts.builder().claims(claims).signWith(longKey,Jwts.SIG.HS384).compact();
  assertThat(Jwts.parser().verifyWith(longKey).build().parseSignedClaims(alternate).getHeader().getAlgorithm()).isEqualTo("HS384");
  assertThatThrownBy(()->service.verify(alternate)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid algorithm");
  assertThatThrownBy(()->service.revoke(alternate)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid algorithm");
  assertSafeUnauthorizedWithoutRevocation(alternate);assertThat(service.verify(issued).getUsername()).isEqualTo("miguel");assertThat(tokens.verify(valid).getUsername()).isEqualTo("miguel");
 }
 /** Assert the exact public authentication failure and retain all live session rows. */
 private void assertSafeUnauthorizedWithoutRevocation(String token) throws Exception {
  int sessions=jdbc.queryForObject("SELECT count(*) FROM active_sessions",Integer.class);
  var r=mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.message").value("Authentication required.")).andExpect(jsonPath("$.errors").isEmpty()).andExpect(jsonPath("$.timestamp").isString()).andReturn();
  assertThat(r.getResponse().getContentAsString()).doesNotContain(token,"Exception","claims","algorithm");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM token_revocations",Integer.class)).isZero();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM active_sessions",Integer.class)).isEqualTo(sessions);
 }
 /** Keep only health/login public and deny customers administrator paths before routing. */
 @Test void securityMatrixIsFailClosed() throws Exception {mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));for(String path:List.of("/api/v1/portfolio/summary","/api/v1/accounts","/api/v1/support/content","/api/v1/admin/audit"))mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));mvc.perform(post("/api/v1/admin/reset").header("Authorization","Bearer "+login("rosa"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Access denied."));}
 /** Reject locked or missing identities even with an otherwise valid signature. */
 @Test void currentIdentityIsRequired(){String token=auth.login(new AuthDtos.LoginRequest("miguel","demo-only")).token();jdbc.update("UPDATE users SET locked=true WHERE username='miguel'");assertThatThrownBy(()->tokens.verify(token)).hasMessage("Locked identity");var key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));String absent=Jwts.builder().subject(UUID.randomUUID().toString()).id(UUID.randomUUID().toString()).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(key,Jwts.SIG.HS256).compact();assertThatThrownBy(()->tokens.verify(absent)).hasMessage("Unknown bearer");}
 /** Prove maintenance provisioning selects the named real database and rejects unsafe identifiers. */
 @Test void databaseProvisioningIsIdempotentAndSafe() throws Exception {
  String base=environment.getRequiredProperty("spring.datasource.url");
  String user=environment.getRequiredProperty("spring.datasource.username"),password=environment.getRequiredProperty("spring.datasource.password");
  String name="nova_provision_"+UUID.randomUUID().toString().replace("-","")+"_test";
  assertThat(name).matches("[a-zA-Z][a-zA-Z0-9_]{0,62}").endsWith("_test");
  var env=new MockEnvironment().withProperty("spring.datasource.url",base).withProperty("spring.datasource.username",user).withProperty("spring.datasource.password",password).withProperty("nova.db-name",name);
  try(var maintenance=DriverManager.getConnection(base,user,password)){
   maintenance.setAutoCommit(true);
   try(var q=maintenance.prepareStatement("SELECT count(*) FROM pg_database WHERE datname=?")){q.setString(1,name);try(var r=q.executeQuery()){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isZero();}}
   try{
    for(int attempt=0;attempt<2;attempt++)try(var pool=(com.zaxxer.hikari.HikariDataSource)new DatabaseConfig().dataSource(env);var c=pool.getConnection();var stmt=c.createStatement();var r=stmt.executeQuery("SELECT current_database()")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo(name);}
    env.setProperty("nova.db-name","bad;DROP DATABASE postgres");assertThatThrownBy(()->new DatabaseConfig().dataSource(env)).isInstanceOf(IllegalArgumentException.class).hasMessage("DB_NAME must be a safe PostgreSQL identifier");
   }finally{try(var stmt=maintenance.createStatement()){stmt.executeUpdate("DROP DATABASE IF EXISTS \""+name+"\" ");}}
   try(var q=maintenance.prepareStatement("SELECT count(*) FROM pg_database WHERE datname=?")){q.setString(1,name);try(var r=q.executeQuery()){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isZero();}}
  }
 }
 /** Prove revocation-store failures deny requests rather than accepting a verified signature alone. */
 @Test void revocationDatabaseFailureIsFailClosed() throws Exception {String token=login("miguel");jdbc.execute("ALTER TABLE token_revocations RENAME TO token_revocations_unavailable");try{mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));}finally{jdbc.execute("ALTER TABLE token_revocations_unavailable RENAME TO token_revocations");}assertThat(tokens.verify(token).getUsername()).isEqualTo("miguel");}
}
