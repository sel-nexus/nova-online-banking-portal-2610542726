package com.nova.bank.admin;

import com.nova.bank.auth.*;
import com.nova.bank.activity.ActivityService;
import com.nova.bank.portfolio.PortfolioService;
import com.nova.bank.offers.OfferService;
import com.nova.bank.support.SupportService;
import com.nova.bank.chat.ChatService;
import com.nova.bank.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinate privileged demo transitions on persistent sessions and one atomic reset transaction. */
@Service
public class AdminService {
 public static final String NOTICE="Database successfully restored to original demonstration fixtures.";
 private final TokenService tokens; private final UserRepository users; private final JdbcTemplate jdbc;
 private final AuthService auth; private final PortfolioService portfolio; private final ActivityService activity;
 private final OfferService offers; private final SupportService support; private final ChatService chat;
 /** Bind public transactional seed proxies rather than re-executing Flyway migrations. */
 public AdminService(TokenService tokens,UserRepository users,JdbcTemplate jdbc,AuthService auth,PortfolioService portfolio,ActivityService activity,OfferService offers,SupportService support,ChatService chat){
  this.tokens=tokens;this.users=users;this.jdbc=jdbc;this.auth=auth;this.portfolio=portfolio;this.activity=activity;this.offers=offers;this.support=support;this.chat=chat;
 }
 /** Verify a current stored administrator, ignoring all request role or identity claims. */
 private User administrator(String bearer){
  User user;
  try{user=tokens.verify(bearer);}catch(IllegalArgumentException|io.jsonwebtoken.JwtException e){throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required.");}
  if(!user.getRole().equals("ROLE_ADMIN"))throw new AccessDeniedException("Access denied.");
  return user;
 }
 /** Read historical events without exposing unrestricted details, usernames, credentials or tokens. */
 @Transactional(readOnly=true) public List<AdminDtos.Audit> audit(String bearer){
  administrator(bearer);
  return jdbc.query("SELECT id,user_id,action,created_at FROM audit_events ORDER BY created_at DESC,id DESC",(rs,row)->new AdminDtos.Audit(rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),rs.getString("action"),rs.getTimestamp("created_at").toInstant()));
 }
 /** Restore exact fixtures, retain historical audits and revoke customers while keeping administrators usable.
  * Notes: acquire the enquiry advisory lock before table locks, excluding sends without lock inversion.
  * Business table locks precede the identity lock; login row locks finish before reset proceeds.
  * All deletions, sequence restart, seeds, session changes and the success audit roll back together.
  */
 @Transactional public AdminDtos.Notice reset(String bearer){
  jdbc.execute("SELECT pg_advisory_xact_lock(99214)");
  jdbc.execute("LOCK TABLE customer_inquiries,user_offer_interests,transactions,accounts,promotions IN SHARE ROW EXCLUSIVE MODE");
  jdbc.execute("LOCK TABLE users IN EXCLUSIVE MODE");
  User admin=administrator(bearer);
  // Protect session revocation against issuance; every login holds its identity row until issue commits.
  jdbc.execute("LOCK TABLE active_sessions IN SHARE ROW EXCLUSIVE MODE");
  tokens.resetCustomerSessions();
  support.clearInquiries();offers.clearInterests();
  jdbc.update("DELETE FROM transactions");jdbc.update("DELETE FROM accounts");jdbc.update("DELETE FROM promotions");
  auth.restoreUsers();portfolio.seedAccounts();activity.seedTransactions();offers.seedPromotions();
  jdbc.update("DELETE FROM reference_content WHERE content_key IN ('support.directory','chat.assistant')");
  support.seedContent();chat.seedContent();
  event(admin.getId(),"RESET_SUCCESS");
  return new AdminDtos.Notice(NOTICE);
 }
 /** Issue an audited customer bearer without unlocking a locked target or retaining hidden admin authority. */
 @Transactional public AuthDtos.LoginResponse persona(String bearer,AdminDtos.Persona request){
  User admin=administrator(bearer);
  if(request==null)throw new IllegalArgumentException("Invalid persona");
  User target=users.lockByUsername(request.target()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Persona Not Available"));
  if(target.isLocked())throw new ApiException(HttpStatus.LOCKED,AuthService.LOCKED);
  if(!target.getRole().equals("ROLE_CUSTOMER"))throw new AccessDeniedException("Access denied.");
  var safe=new AuthDtos.SafeUser(target.getId(),target.getUsername(),target.getFullName(),target.getRole(),target.getLastLogin());
  target.succeed();users.saveAndFlush(target);
  String customer=tokens.issue(target);tokens.revoke(bearer);
  event(admin.getId(),"PERSONA_SWITCH_"+target.getUsername().toUpperCase(java.util.Locale.ROOT));
  return new AuthDtos.LoginResponse(customer,"Bearer",tokens.lifetime(),safe);
 }
 /** Set and audit only the issuing session; disabled state stays reachable during failure simulation. */
 @Transactional public AdminDtos.SimulationState simulation(String bearer,AdminDtos.Simulation request){
  User admin=administrator(bearer);if(request==null)throw new IllegalArgumentException("Invalid simulation");
  boolean enabled=request.enabled();tokens.setServiceError(bearer,enabled);
  event(admin.getId(),enabled?"SIMULATION_ENABLED":"SIMULATION_DISABLED");
  return new AdminDtos.SimulationState(enabled);
 }
 /** Persist an allowlisted action without user-submitted details. */
 private void event(UUID actor,String action){jdbc.update("INSERT INTO audit_events(user_id,action) VALUES (?,?)",actor,action);}
}
