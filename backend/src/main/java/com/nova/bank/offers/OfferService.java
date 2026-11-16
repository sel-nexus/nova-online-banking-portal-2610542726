package com.nova.bank.offers;

import com.nova.bank.auth.AuthService;
import com.nova.bank.auth.UserRepository;
import com.nova.bank.common.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforce persisted identity targeting and record only simulated, idempotent interest. */
@Service
public class OfferService {
 public static final String NOTICE="Interest recorded for this demonstration. No real credit check or application was submitted.";
 private final PromotionRepository promotions;
 private final UserRepository users;
 private final JdbcTemplate jdbc;
 private final LocalDate demoDate;
 /** Bind catalogue persistence and the deployment's fixture-relative calendar. */
 public OfferService(PromotionRepository promotions,UserRepository users,JdbcTemplate jdbc,
                     @Value("${nova.demo-date}") LocalDate demoDate){
  this.promotions=promotions;this.users=users;this.jdbc=jdbc;this.demoDate=demoDate;
 }
 /** Return the two approved recommendations only after verifying identity. */
 @Transactional(readOnly=true)
 public List<OfferDtos.Product> products(UUID principal){
  segment(principal);
  return List.of(new OfferDtos.Product("Ipon Goal Saver","SAVINGS","Build savings automatically on payday"),
   new OfferDtos.Product("Rewards Credit Card","CARDS","Earn points on dining & groceries"));
 }
 /** List only currently permitted campaigns, including each user's persisted interest state. */
 @Transactional(readOnly=true)
 public List<OfferDtos.Offer> list(UUID principal){
  return promotions.catalogue(segment(principal),demoDate).stream().map(p->dto(p,principal)).toList();
 }
 /** Reject missing, disabled or expired detail before considering segment membership. */
 @Transactional(readOnly=true)
 public OfferDtos.Offer detail(UUID id,UUID principal){return dto(permitted(id,principal),principal);}
 /** Insert atomically and re-query the unique registration; no application or external action occurs.
  * Returns: the original persisted identifier and timestamp for repeated requests.
  * Raises: safe authorization errors before any write.
  */
 @Transactional
 public OfferDtos.Interest expressInterest(UUID id,UUID principal){
  permitted(id,principal);
  jdbc.update("INSERT INTO user_offer_interests(user_id,promotion_id) VALUES (?,?) ON CONFLICT(user_id,promotion_id) DO NOTHING",principal,id);
  return jdbc.queryForObject("SELECT id,registered_at FROM user_offer_interests WHERE user_id=? AND promotion_id=?",
   (rs,row)->new OfferDtos.Interest(rs.getObject("id",UUID.class),id,rs.getTimestamp("registered_at").toInstant(),NOTICE),principal,id);
 }
 /** Derive targeting only from the authoritative stored identity; unknown customers fail closed. */
 private String segment(UUID principal){
  var user=users.findById(principal).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required."));
  if(user.getRole().equals("ROLE_ADMIN"))return "ADMIN";
  return switch(user.getUsername()){
   case "miguel" -> "MIGUEL";
   case "rosa" -> "ROSA";
   default -> "ALL";
  };
 }
 /** Share the same availability and targeting boundary for detail and mutation. */
 private Promotion permitted(UUID id,UUID principal){
  String segment=segment(principal);
  Promotion p=promotions.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Offer Not Available"));
  if(!p.isActive()||p.getValidUntil().isBefore(demoDate))throw new ApiException(HttpStatus.NOT_FOUND,"Offer Not Available");
  if(!segment.equals("ADMIN")&&!p.getTargetSegment().equals("ALL")&&!p.getTargetSegment().equals(segment))
   throw new ApiException(HttpStatus.FORBIDDEN,"Offer Not Available");
  return p;
 }
 /** Serialize full fixture conditions and an owner-bound registration indicator. */
 private OfferDtos.Offer dto(Promotion p,UUID principal){
  boolean interested=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_offer_interests WHERE user_id=? AND promotion_id=?)",Boolean.class,principal,p.getId()));
  return new OfferDtos.Offer(p.getId(),p.getOfferKey(),p.getTitle(),p.getCategory(),p.getBenefitSummary(),
   p.getTermsAndConditions(),p.getEligibility(),p.getValidUntil(),p.getTargetSegment().equals("ALL")?"For everyone":"Personalized for you",interested);
 }
 /** Clear only demonstration interests for future administrator reset coordination. */
 @Transactional public void clearInterests(){jdbc.update("DELETE FROM user_offer_interests");}
 /** Insert absent exact fixture IDs without resetting modified campaigns or registrations on restart. */
 @Transactional public void seedPromotions(){
  String[][] fixtures={
   {"20","payday-ipon-boost","Payday Ipon Boost","SAVINGS","MIGUEL","Demonstration bonus","Extra goal interest upon monthly target achievement.","Active Peso Savings accounts"},
   {"21","payday-ipon-bonus","Payday Ipon Bonus","SAVINGS","MIGUEL","Automatic payroll transfer goal reward","Exclusive to Ipon Goal Saver enrollees and non-transferable.","Ipon Goal Saver enrollees"},
   {"22","peso-savings-bonus","Peso Savings Bonus","SAVINGS","MIGUEL","Additional 0.50% p.a.","Additional 0.50% p.a. on tiered balances maintained across the promotion quarter.","Tiered balances maintained across the promotion quarter"},
   {"23","cashback-week","Cashback Week","CARDS","MIGUEL","Accelerated cashback on department stores and weekend groceries","Capped at PHP 2,000 monthly rebate and requiring a registered Nova Rewards Credit Card.","Registered Nova Rewards Credit Card"},
   {"24","dining-cashback-5","5% Dining Cashback","CARDS","MIGUEL","5% dining spend return at Metro Manila partner restaurants","For active credit card holders and applied as bill credit in the subsequent billing cycle.","Active credit card holders"},
   {"25","personal-loan-offer","Personal Loan Offer","LOANS","MIGUEL","Indicative low APR personal financing with quick online approval","Pre-qualified based on continuous payroll credits.","Continuous payroll credits"},
   {"26","home-loan-fee-waiver","Home Loan Fee Waiver","LOANS","ROSA","100% appraisal fee waived on prime residential mortgage applications","Personalized for growth depositors considering home acquisition.","Growth depositors considering home acquisition"},
   {"27","term-deposit-promo","Term Deposit Promo","SAVINGS","ALL","Guaranteed 4.25% p.a. on 12-month placements","Requires minimum PHP 50,000.","Minimum PHP 50,000 placement"},
   {"28","referral-reward","Referral Reward","SAVINGS","ALL","PHP 500 per successful friend onboarding","Friend opens a funded deposit account within 30 days of invite.","Successful friend onboarding with a funded deposit account within 30 days"},
   {"29","new-year-cashback-expired","New Year Cashback","CARDS","ALL","Expired demonstration campaign","Inactive expired campaign retained for audit testing.","Not available: inactive expired campaign"}
  };
  for(String[] f:fixtures)jdbc.update("""
   INSERT INTO promotions(id,offer_key,title,category,target_segment,benefit_summary,terms_and_conditions,eligibility,valid_until,active)
   VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING
   """,UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+f[0]),f[1],f[2],f[3],f[4],f[5],f[6],f[7],
    LocalDate.parse(f[0].equals("29")?"2026-01-31":"2026-12-31"),!f[0].equals("29"));
 }
 /** Ensure identity seeds precede offers regardless of runner ordering. */
 @Bean ApplicationRunner offerSeed(OfferService service,AuthService auth,@Value("${nova.seed-on-startup}") boolean enabled){
  return args->{if(enabled){auth.seedUsers();service.seedPromotions();}};
 }
}
