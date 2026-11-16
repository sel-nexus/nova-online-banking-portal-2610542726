package com.nova.bank.auth;
import com.nova.bank.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
/** Commit serialized authentication transitions even when the response is rejection. */
@Service
public class AuthService {
 public static final String INVALID="Invalid User ID or password. Please try again.";
 public static final String LOCKED="Account locked due to multiple failed login attempts. Use the Facilitator Reset to unlock.";
 private final UserRepository users; private final TokenService tokens; private final JdbcTemplate jdbc;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(10);
 private final String dummyHash=encoder.encode("unavailable-identity-timing-check");
 /** Connect identity verification to durable token handling. */
 public AuthService(UserRepository users,TokenService tokens,JdbcTemplate jdbc){this.users=users;this.tokens=tokens;this.jdbc=jdbc;}
 /** Authenticate under a row lock and commit failed counts independently of the caller transaction. */
 @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=ApiException.class)
 public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request){
  User user=users.lockByUsername(request.username()).orElse(null);
  if(user==null){encoder.matches(request.password(),dummyHash);throw new ApiException(HttpStatus.UNAUTHORIZED,INVALID);}
  if(user.isLocked()) throw new ApiException(HttpStatus.LOCKED,LOCKED);
  if(!encoder.matches(request.password(),user.getPasswordHash())){user.fail();users.saveAndFlush(user);throw new ApiException(user.isLocked()?HttpStatus.LOCKED:HttpStatus.UNAUTHORIZED,user.isLocked()?LOCKED:INVALID);}
  var safe=new AuthDtos.SafeUser(user.getId(),user.getUsername(),user.getFullName(),user.getRole(),user.getLastLogin());
  user.succeed(); users.saveAndFlush(user); String token=tokens.issue(user);
  jdbc.update("INSERT INTO audit_events(user_id,action) VALUES (?,?)",user.getId(),"LOGIN_SUCCESS");
  return new AuthDtos.LoginResponse(token,"Bearer",tokens.lifetime(),safe);
 }
 /** Revoke the authenticated bearer durably before confirming logout. */
 @Transactional public void logout(String token){tokens.revoke(token);}
 /** Insert missing fixtures only; restarting never unlocks existing users. */
 @Transactional public void seedUsers(){
  String[] names={"miguel","rosa","admin"}; String[] full={"Miguel De La Cruz","Rosa Santos","Platform Facilitator"};
  for(int i=0;i<3;i++) jdbc.update("INSERT INTO users(id,username,password_hash,full_name,role,last_login) VALUES (?,?,?,?,?,?::timestamptz) ON CONFLICT(username) DO NOTHING",UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a1"+(i+1)),names[i],encoder.encode("demo-only"),full[i],i==2?"ROLE_ADMIN":"ROLE_CUSTOMER","2026-10-14T08:10:00Z");
 }
 /** Restore seeded credentials and profile fields without deleting identities referenced by audit history.
  * Notes: the coordinator holds identity locks; extra users are retained but runtime activity is cleared.
  */
 @Transactional public void restoreUsers(){
  seedUsers();
  jdbc.update("UPDATE users SET failed_attempts=0,locked=false,last_login='2026-10-14T08:10:00Z',updated_at=now()");
  String[] names={"miguel","rosa","admin"};String[] full={"Miguel De La Cruz","Rosa Santos","Platform Facilitator"};
  for(int i=0;i<3;i++)jdbc.update("UPDATE users SET password_hash=?,full_name=?,role=? WHERE username=?",encoder.encode("demo-only"),full[i],i==2?"ROLE_ADMIN":"ROLE_CUSTOMER",names[i]);
 }
 /** Seed after Flyway and JPA initialize using the proxied transactional service. */
 @Bean ApplicationRunner identitySeed(AuthService service,@Value("${nova.seed-on-startup}") boolean enabled){return args->{if(enabled)service.seedUsers();};}
}
