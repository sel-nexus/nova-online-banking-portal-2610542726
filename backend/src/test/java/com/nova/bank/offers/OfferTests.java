package com.nova.bank.offers;

import com.nova.bank.TestSupport;
import com.nova.bank.common.ApiException;
import com.nova.bank.auth.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise service, HTTP and constraints against the real PostgreSQL fixture database. */
class OfferTests extends TestSupport {
 @Autowired OfferService service;
 @Autowired PromotionRepository repository;
 @Autowired UserRepository users;
 private static final UUID MIGUEL=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
 private static final UUID ROSA=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
 private static final UUID ADMIN=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13");
 /** Construct independently specified fixture IDs. */
 private UUID id(int suffix){return UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+suffix);}
 /** Reset only this feature's data in the guarded test database. */
 @Override protected void resetFeatureFixtures(){jdbc.update("DELETE FROM user_offer_interests");jdbc.update("DELETE FROM promotions");service.seedPromotions();}
 /** Verify all exact fixture identities, titles, keys, conditions and validity in storage. */
 @Test void exactFixturesPersistAndSeedDoesNotResetChanges(){
  String[] keys={"payday-ipon-boost","payday-ipon-bonus","peso-savings-bonus","cashback-week","dining-cashback-5","personal-loan-offer","home-loan-fee-waiver","term-deposit-promo","referral-reward","new-year-cashback-expired"};
  String[] titles={"Payday Ipon Boost","Payday Ipon Bonus","Peso Savings Bonus","Cashback Week","5% Dining Cashback","Personal Loan Offer","Home Loan Fee Waiver","Term Deposit Promo","Referral Reward","New Year Cashback"};
  String[] terms={"Extra goal interest upon monthly target achievement.","Exclusive to Ipon Goal Saver enrollees and non-transferable.","Additional 0.50% p.a. on tiered balances maintained across the promotion quarter.","Capped at PHP 2,000 monthly rebate and requiring a registered Nova Rewards Credit Card.","For active credit card holders and applied as bill credit in the subsequent billing cycle.","Pre-qualified based on continuous payroll credits.","Personalized for growth depositors considering home acquisition.","Requires minimum PHP 50,000.","Friend opens a funded deposit account within 30 days of invite.","Inactive expired campaign retained for audit testing."};
  assertThat(repository.count()).isEqualTo(10);
  for(int n=0;n<10;n++){
   var p=repository.findById(id(n+20)).orElseThrow();
   assertThat(p.getOfferKey()).isEqualTo(keys[n]);assertThat(p.getTitle()).isEqualTo(titles[n]);assertThat(p.getTermsAndConditions()).isEqualTo(terms[n]);
   assertThat(p.getCategory()).isEqualTo(n<3||n==7||n==8?"SAVINGS":n==3||n==4||n==9?"CARDS":"LOANS");
   assertThat(p.getTargetSegment()).isEqualTo(n<6?"MIGUEL":n==6?"ROSA":"ALL");
   assertThat(p.getEligibility()).isNotBlank();assertThat(p.getBenefitSummary()).isNotBlank();
   assertThat(p.getValidUntil()).isEqualTo(LocalDate.parse(n==9?"2026-01-31":"2026-12-31"));assertThat(p.isActive()).isEqualTo(n!=9);
  }
  service.expressInterest(id(20),MIGUEL);jdbc.update("UPDATE promotions SET benefit_summary='changed' WHERE id=?",id(20));
  service.seedPromotions();assertThat(repository.count()).isEqualTo(10);
  assertThat(repository.findById(id(20)).orElseThrow().getBenefitSummary()).isEqualTo("changed");
  assertThat(service.detail(id(20),MIGUEL).interested()).isTrue();
 }
 /** Verify both persona segments and administrator visibility with safe full DTOs. */
 @Test void serviceCataloguesAndProductsMatchApprovedBoundaries(){
  assertThat(service.list(MIGUEL)).extracting(OfferDtos.Offer::id).containsExactly(id(20),id(21),id(22),id(23),id(24),id(25),id(27),id(28));
  assertThat(service.list(ROSA)).extracting(OfferDtos.Offer::id).containsExactly(id(26),id(27),id(28));
  assertThat(service.list(ADMIN)).hasSize(9);
  for(UUID user: new UUID[]{MIGUEL,ROSA,ADMIN}){
   assertThat(service.products(user)).containsExactly(new OfferDtos.Product("Ipon Goal Saver","SAVINGS","Build savings automatically on payday"),new OfferDtos.Product("Rewards Credit Card","CARDS","Earn points on dining & groceries"));
   for(var offer:service.list(user)){assertThat(service.detail(offer.id(),user)).isEqualTo(offer);assertThat(offer.interested()).isFalse();assertThat(offer.terms()).isNotBlank();assertThat(offer.eligibility()).isNotBlank();}
  }
  assertThat(service.detail(id(20),MIGUEL).personalization()).isEqualTo("Personalized for you");
  assertThat(service.detail(id(27),ROSA).personalization()).isEqualTo("For everyone");
 }
 /** Verify inclusive validity before, on and after expiry independently of machine time. */
 @Test void dateBoundaryAndInactiveCampaignsFailClosed(){
  for(LocalDate date: new LocalDate[]{LocalDate.parse("2026-12-30"),LocalDate.parse("2026-12-31"),LocalDate.parse("2027-01-01")}){
   var dated=new OfferService(repository,users,jdbc,date);
   if(date.getYear()==2026){assertThat(dated.list(MIGUEL)).hasSize(8);assertThat(dated.detail(id(20),MIGUEL).id()).isEqualTo(id(20));}
   else {assertThat(dated.list(MIGUEL)).isEmpty();assertThatThrownBy(()->dated.detail(id(20),MIGUEL)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));assertThatThrownBy(()->dated.expressInterest(id(20),MIGUEL)).isInstanceOf(ApiException.class);}
  }
  for(String date:new String[]{"2026-01-30","2026-01-31","2026-02-01"})assertThatThrownBy(()->new OfferService(repository,users,jdbc,LocalDate.parse(date)).detail(id(29),ADMIN)).isInstanceOf(ApiException.class);
  jdbc.update("UPDATE promotions SET active=false WHERE id=?",id(20));assertThat(service.list(MIGUEL)).hasSize(7);
  assertThatThrownBy(()->service.detail(id(20),MIGUEL)).isInstanceOf(ApiException.class);
  jdbc.update("UPDATE promotions SET active=true,valid_until='2026-10-13' WHERE id=?",id(20));
  assertThatThrownBy(()->service.expressInterest(id(20),MIGUEL)).isInstanceOf(ApiException.class);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isZero();
 }
 /** Verify all service denial branches before mutation, including unknown identities. */
 @Test void serviceRejectsWrongSegmentUnknownExpiredAndMissingIdentity(){
  for(UUID user:new UUID[]{MIGUEL,ROSA}){
   UUID wrong=user.equals(MIGUEL)?id(26):id(20);
   assertThatThrownBy(()->service.detail(wrong,user)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
   assertThatThrownBy(()->service.expressInterest(wrong,user)).isInstanceOf(ApiException.class);
   for(UUID missing:new UUID[]{UUID.randomUUID(),id(29)}){
    assertThatThrownBy(()->service.detail(missing,user)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    assertThatThrownBy(()->service.expressInterest(missing,user)).isInstanceOf(ApiException.class);
   }
  }
  UUID missing=UUID.randomUUID();assertThatThrownBy(()->service.products(missing)).isInstanceOf(ApiException.class);assertThatThrownBy(()->service.list(missing)).isInstanceOf(ApiException.class);assertThatThrownBy(()->service.detail(id(20),missing)).isInstanceOf(ApiException.class);assertThatThrownBy(()->service.expressInterest(id(20),missing)).isInstanceOf(ApiException.class);
  jdbc.update("INSERT INTO users(id,username,password_hash,full_name,role) VALUES (?,'other','unused','Other','ROLE_CUSTOMER')",missing);
  try {assertThat(service.list(missing)).extracting(OfferDtos.Offer::id).containsExactly(id(27),id(28));assertThatThrownBy(()->service.detail(id(20),missing)).isInstanceOf(ApiException.class);}finally{jdbc.update("DELETE FROM users WHERE id=?",missing);}
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isZero();
 }
 /** Re-query writes and prove uniqueness, owner association and reset extension. */
 @Test void repeatedInterestReturnsSamePersistedIdentityAndTimestamp(){
  var first=service.expressInterest(id(27),MIGUEL);assertThat(first.message()).isEqualTo(OfferService.NOTICE);
  assertThat(service.expressInterest(id(27),MIGUEL)).isEqualTo(first);
  var rosa=service.expressInterest(id(27),ROSA);var admin=service.expressInterest(id(26),ADMIN);
  assertThat(rosa.id()).isNotEqualTo(first.id());assertThat(admin.promotionId()).isEqualTo(id(26));
  assertThat(jdbc.queryForObject("SELECT user_id FROM user_offer_interests WHERE id=?",UUID.class,first.id())).isEqualTo(MIGUEL);
  assertThat(jdbc.queryForObject("SELECT user_id FROM user_offer_interests WHERE id=?",UUID.class,rosa.id())).isEqualTo(ROSA);
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isEqualTo(3);
  assertThat(service.detail(id(27),MIGUEL).interested()).isTrue();assertThat(service.detail(id(27),ADMIN).interested()).isFalse();
  service.clearInterests();service.clearInterests();assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isZero();
 }
 /** Exercise each endpoint with real issued JWTs and persisted repeated HTTP writes. */
 @Test void httpHappyPathsAndSegmentOverrideCannotLeak() throws Exception {
  for(String name:new String[]{"miguel","rosa","admin"}){
   String bearer="Bearer "+login(name);int size=name.equals("miguel")?8:name.equals("rosa")?3:9;
   mvc.perform(get("/api/v1/products").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].title").value("Ipon Goal Saver")).andExpect(jsonPath("$[1].benefit").value("Earn points on dining & groceries"));
   mvc.perform(get("/api/v1/promotions").param("segment","MIGUEL' OR 1=1 --").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(size));
   UUID selected=name.equals("rosa")?id(26):id(20);
   mvc.perform(get("/api/v1/promotions/"+selected).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.terms").isNotEmpty()).andExpect(jsonPath("$.eligibility").isNotEmpty()).andExpect(jsonPath("$.targetSegment").doesNotExist()).andExpect(jsonPath("$.interested").value(false));
   String first=mvc.perform(post("/api/v1/promotions/"+selected+"/express-interest").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(OfferService.NOTICE)).andReturn().getResponse().getContentAsString();
   String repeat=mvc.perform(post("/api/v1/promotions/"+selected+"/express-interest").header("Authorization",bearer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   assertThat(mapper.readTree(repeat)).isEqualTo(mapper.readTree(first));
   mvc.perform(get("/api/v1/promotions/"+selected).header("Authorization",bearer)).andExpect(jsonPath("$.interested").value(true));
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isEqualTo(3);
 }
 /** Verify safe HTTP denial bodies and no writes for unavailable or malformed identifiers. */
 @Test void httpDenialsValidateIdentityWithoutMetadata() throws Exception {
  for(String name:new String[]{"miguel","rosa"}){
   String bearer="Bearer "+login(name);
   for(String suffix:new String[]{"","/express-interest"}){
    boolean write=!suffix.isEmpty();
    for(String bad:new String[]{"not-a-uuid","1-1-1-1-1","' OR 1=1 --","<img onerror=alert(1)>","x".repeat(500)})
     mvc.perform((write?post("/api/v1/promotions/{id}"+suffix,bad):get("/api/v1/promotions/{id}"+suffix,bad)).header("Authorization",bearer)).andExpect(status().isBadRequest());
    for(UUID missing:new UUID[]{UUID.randomUUID(),id(29)})mvc.perform((write?post("/api/v1/promotions/"+missing+suffix):get("/api/v1/promotions/"+missing+suffix)).header("Authorization",bearer)).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Offer Not Available")).andExpect(jsonPath("$.title").doesNotExist());
    UUID wrong=name.equals("miguel")?id(26):id(20);
    mvc.perform((write?post("/api/v1/promotions/"+wrong+suffix):get("/api/v1/promotions/"+wrong+suffix)).header("Authorization",bearer)).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Offer Not Available"));
   }
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isZero();
 }
 /** Reject absent, malformed and revoked bearer tokens on every route. */
 @Test void everyHttpEndpointRequiresActiveBearer() throws Exception {
  String token=login("miguel");auth.logout(token);
  for(String path:new String[]{"/products","/promotions","/promotions/"+id(20),"/promotions/"+id(20)+"/express-interest"}){
   for(String bearer:new String[]{"","Bearer invalid","Bearer "+token})mvc.perform((path.endsWith("express-interest")?post("/api/v1"+path):get("/api/v1"+path)).header("Authorization",bearer)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));
  }
 }
 /** Verify actual FK, uniqueness, nullability and constrained enums rather than DTO validation. */
 @Test void databaseEnforcesCampaignAndInterestIntegrity(){
  for(String column:new String[]{"offer_key","title","category","target_segment","benefit_summary","terms_and_conditions","eligibility","valid_until","active"})assertThatThrownBy(()->jdbc.update("UPDATE promotions SET "+column+"=NULL WHERE id=?",id(20))).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE promotions SET category='INJECTION' WHERE id=?",id(20))).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE promotions SET target_segment='OTHER' WHERE id=?",id(20))).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE promotions SET offer_key='payday-ipon-boost' WHERE id=?",id(21))).isInstanceOf(DataIntegrityViolationException.class);
  for(UUID user:new UUID[]{null,UUID.randomUUID()})assertThatThrownBy(()->jdbc.update("INSERT INTO user_offer_interests(user_id,promotion_id) VALUES (?,?)",user,id(20))).isInstanceOf(DataIntegrityViolationException.class);
  for(UUID promotion:new UUID[]{null,UUID.randomUUID()})assertThatThrownBy(()->jdbc.update("INSERT INTO user_offer_interests(user_id,promotion_id) VALUES (?,?)",MIGUEL,promotion)).isInstanceOf(DataIntegrityViolationException.class);
  service.expressInterest(id(20),MIGUEL);
  assertThatThrownBy(()->jdbc.update("INSERT INTO user_offer_interests(user_id,promotion_id) VALUES (?,?)",MIGUEL,id(20))).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE user_offer_interests SET registered_at=NULL")).isInstanceOf(DataIntegrityViolationException.class);
  jdbc.update("DELETE FROM promotions WHERE id=?",id(20));assertThat(jdbc.queryForObject("SELECT count(*) FROM user_offer_interests",Integer.class)).isZero();
 }
}
