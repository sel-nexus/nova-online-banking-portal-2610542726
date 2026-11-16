package com.nova.bank.support;

import com.nova.bank.TestSupport;
import com.nova.bank.common.ApiException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verify real service and HTTP behavior with PostgreSQL, not a substitute or mocked repository. */
class SupportTests extends TestSupport {
 @Autowired SupportService service;
 @Autowired InquiryRepository repository;
 @Autowired PlatformTransactionManager transactions;
 private static final UUID MIGUEL=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
 private static final UUID ROSA=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
 /** Restore only owned fixtures inside the guarded test database. */
 @Override protected void resetFeatureFixtures(){service.clearInquiries();jdbc.update("DELETE FROM reference_content WHERE content_key='support.directory'");service.seedContent();}
 /** Provide the independent PRD request example. */
 private SupportDtos.Request valid(){return new SupportDtos.Request("CARDS","Card Limit Increase","Inquiring about credit limit adjustments.");}
 /** Verify exact contacts, branches, FAQ answers and protected HTTP content. */
 @Test void exactDirectoryAndFaqArePersistedAndReturned() throws Exception {
  var content=service.content(MIGUEL);
  assertThat(content.contacts()).containsExactly(
   new SupportDtos.Contact("Call Us","(+63 2) 8000 0000","Mon–Fri 08:00–17:00","Sample hotline"),
   new SupportDtos.Contact("Secure Message","Online enquiry simulation","Estimated response 24h","No external message is sent"),
   new SupportDtos.Contact("Find a Branch / ATM","Static Metro Manila directory","Ayala, BGC, Quezon Ave","Demonstration directory"),
   new SupportDtos.Contact("Lost or Stolen Card","(+63 2) 8000 0001","24/7","Emergency blocking hotline (sample; no live blocking)"));
  assertThat(content.branches()).containsExactly(
   new SupportDtos.Branch("Ayala Branch","Tower One, Ayala Avenue, Makati City","Full Service Branch","Mon–Fri 09:00–16:30"),
   new SupportDtos.Branch("BGC Branch","High Street South, Bonifacio Global City, Taguig","Extended Hours Branch","Mon–Sat 09:00–18:00"),
   new SupportDtos.Branch("Quezon Avenue ATM","West Triangle, Quezon City","24/7 Secure Automated Teller & Cash Deposit","24/7"));
  assertThat(content.faqs()).containsExactly(
   new SupportDtos.Faq("login","How do I sign in?","Demo credentials are on the login card: miguel / demo-only and rosa / demo-only. Toggle password visibility with Show / Hide."),
   new SupportDtos.Faq("transactions","How do I filter transactions or download a statement?","Account summary supports real-time search, 7/30/90-day ranges and .txt download."),
   new SupportDtos.Faq("interest","What interest rate does Peso Savings earn?","Standard Peso Savings earns 3.25% p.a. Payday Ipon Boost tiered deposits qualify for an additional 0.50% p.a."),
   new SupportDtos.Faq("credit","How is available credit calculated?","Available credit is total credit limit minus current amount due: PHP 50,000.00 − PHP 8,450.00 = PHP 41,550.00."));
  assertThat(content.topics()).containsExactly("LOGIN","ACCOUNTS","TRANSACTIONS","CARDS","LOANS","OFFERS","GENERAL");
  assertThat(content.notice()).isEqualTo("Demonstration only. No live call, email, SMS or support ticket is sent.");
  for(String user:List.of("miguel","rosa","admin")){
   String response=mvc.perform(get("/api/v1/support/content").header("Authorization","Bearer "+login(user))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
   assertThat(mapper.readValue(response,SupportDtos.Content.class)).isEqualTo(content);
  }
  assertThat(jdbc.queryForObject("SELECT pg_typeof(content)::text FROM reference_content WHERE content_key='support.directory'",String.class)).isEqualTo("jsonb");
 }
 /** Verify 201, exact receipt and re-query every text/identity/timestamp field. */
 @Test void createdHttpReceiptPersistsOwnerAndUniqueSequence() throws Exception {
  for(String user:List.of("miguel","rosa","admin")){
   var response=mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+login(user)).contentType("application/json").content(mapper.writeValueAsString(valid())))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RECEIVED")).andExpect(jsonPath("$.estimatedResponse").value("24h")).andReturn();
   var receipt=mapper.readValue(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),SupportDtos.Receipt.class);
   int offset=List.of("miguel","rosa","admin").indexOf(user);assertThat(receipt.referenceNumber()).isEqualTo("NB-"+(99214+offset));
   var stored=repository.findByReferenceNumber(receipt.referenceNumber()).orElseThrow();assertThat(stored.getId()).isNotNull();assertThat(stored.getCreatedAt()).isNotNull();
   assertThat(stored.getTopic()).isEqualTo("CARDS");assertThat(stored.getSubject()).isEqualTo("Card Limit Increase");assertThat(stored.getMessage()).isEqualTo("Inquiring about credit limit adjustments.");
   assertThat(jdbc.queryForObject("SELECT user_id FROM customer_inquiries WHERE id=?",UUID.class,stored.getId())).isEqualTo(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+(11+offset)));
  }
  assertThat(repository.count()).isEqualTo(3);
 }
 /** Reject each field independently for absence, null, whitespace, type and length. */
 @Test void independentHttpValidationNeverWrites() throws Exception {
  String bearer="Bearer "+login("miguel");
  for(String field:List.of("topic","subject","message")){
   for(Object bad:Arrays.asList(null,""," \t\n",12,true,List.of("text"),Map.of("value","text"),"x".repeat(field.equals("topic")?51:field.equals("subject")?151:5001))){
    Map<String,Object> body=new HashMap<>(Map.of("topic","CARDS","subject","subject","message","message"));body.put(field,bad);
    mvc.perform(post("/api/v1/support/inquiries").header("Authorization",bearer).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
   }
   Map<String,Object> body=new HashMap<>(Map.of("topic","CARDS","subject","subject","message","message"));body.remove(field);
   mvc.perform(post("/api/v1/support/inquiries").header("Authorization",bearer).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors."+field).exists());
  }
  for(String topic:List.of("UNKNOWN","cards"," CARDS ","' OR 1=1 --"))mvc.perform(post("/api/v1/support/inquiries").header("Authorization",bearer).contentType("application/json").content(mapper.writeValueAsString(new SupportDtos.Request(topic,"s","m")))).andExpect(status().isBadRequest());
  for(String body:List.of("{","null","[]","{\"topic\":\"CARDS\",\"subject\":\"s\",\"message\":\"m\",\"userId\":\"other\"}"))mvc.perform(post("/api/v1/support/inquiries").header("Authorization",bearer).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  assertThat(repository.count()).isZero();assertThat(service.submit(MIGUEL,valid()).referenceNumber()).isEqualTo("NB-99214");
 }
 /** Exercise all known topics and valid inclusive lengths with preserved hostile text. */
 @Test void serviceAndHttpAcceptBoundariesAndTreatSqlXssAsText() throws Exception {
  for(String topic:List.of("LOGIN","ACCOUNTS","TRANSACTIONS","CARDS","LOANS","OFFERS","GENERAL"))assertThat(service.submit(MIGUEL,new SupportDtos.Request(topic,"s","m")).status()).isEqualTo("RECEIVED");
  for(var request:List.of(new SupportDtos.Request("LOANS","s".repeat(150),"m".repeat(5000)),new SupportDtos.Request("GENERAL","'; DROP TABLE users; --","<script>alert(1)</script>"),new SupportDtos.Request("CARDS"," padded "," padded message "))){
   var result=mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+login("rosa")).contentType("application/json").content(mapper.writeValueAsString(request))).andExpect(status().isCreated()).andReturn();
   var receipt=mapper.readValue(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),SupportDtos.Receipt.class);var stored=repository.findByReferenceNumber(receipt.referenceNumber()).orElseThrow();assertThat(stored.getSubject()).isEqualTo(request.subject());assertThat(stored.getMessage()).isEqualTo(request.message());
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isGreaterThanOrEqualTo(3);assertThat(repository.count()).isEqualTo(10);
 }
 /** Deny removed/absent users and invalid requests at the service boundary as well. */
 @Test void serviceValidationAndIdentityFailBeforeWrites(){
  for(UUID user:Arrays.asList(null,UUID.randomUUID())){
   assertThatThrownBy(()->service.content(user)).isInstanceOf(ApiException.class);assertThatThrownBy(()->service.submit(user,valid())).isInstanceOf(ApiException.class);
  }
  for(var request:Arrays.asList(null,new SupportDtos.Request("BAD","s","m"),new SupportDtos.Request("CARDS"," ","m"),new SupportDtos.Request("CARDS","s","m".repeat(5001))))assertThatThrownBy(()->service.submit(MIGUEL,request)).isInstanceOf(ApiException.class);
  assertThat(repository.count()).isZero();
 }
 /** Protect both APIs against absent, malformed and revoked signed bearer tokens. */
 @Test void activeAuthenticationIsRequiredOnBothRoutes() throws Exception {
  String token=login("miguel");auth.logout(token);
  for(String bearer:List.of("","Bearer invalid","Bearer "+token)){
   mvc.perform(get("/api/v1/support/content").header("Authorization",bearer)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.contacts").doesNotExist());
   mvc.perform(post("/api/v1/support/inquiries").header("Authorization",bearer).contentType("application/json").content(mapper.writeValueAsString(valid()))).andExpect(status().isUnauthorized());
  }
  assertThat(repository.count()).isZero();
 }
 /** Allocate unique references under genuinely concurrent transactions. */
 @Test void concurrentSendsHaveDistinctPersistedReferences() throws Exception {
  try(var executor=Executors.newFixedThreadPool(8)){
   List<Future<SupportDtos.Receipt>> futures=new ArrayList<>();for(int i=0;i<16;i++)futures.add(executor.submit(()->service.submit(MIGUEL,valid())));
   Set<String> refs=new HashSet<>();for(var future:futures)refs.add(future.get(20,TimeUnit.SECONDS).referenceNumber());
   assertThat(refs).hasSize(16);assertThat(refs).contains("NB-99214","NB-99229");assertThat(repository.count()).isEqualTo(16);
   for(String ref:refs)assertThat(repository.findByReferenceNumber(ref)).isPresent();
  }
 }
 /** Preserve edits and enquiries on reseed, and honor a disabled startup runner. */
 @Test void seedingIsIdempotentAndDisabledRunnerDoesNothing() throws Exception {
  service.submit(MIGUEL,valid());jdbc.update("UPDATE reference_content SET content=jsonb_set(content,'{notice}','\"edited\"') WHERE content_key='support.directory'");
  jdbc.update("INSERT INTO reference_content(content_key,content) VALUES ('assistant.future','{}') ON CONFLICT DO NOTHING");
  try{service.seedContent();assertThat(service.content(MIGUEL).notice()).isEqualTo("edited");assertThat(repository.count()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM reference_content WHERE content_key='assistant.future'",Integer.class)).isEqualTo(1);
   jdbc.update("DELETE FROM reference_content WHERE content_key='support.directory'");service.supportSeed(service,auth,false).run(new DefaultApplicationArguments());assertThat(jdbc.queryForObject("SELECT count(*) FROM reference_content WHERE content_key='support.directory'",Integer.class)).isZero();
   service.supportSeed(service,auth,true).run(new DefaultApplicationArguments());assertThat(service.content(MIGUEL).faqs()).hasSize(4);
  }finally{jdbc.update("DELETE FROM reference_content WHERE content_key='assistant.future'");}
 }
 /** Return safe unavailable errors for missing or malformed persisted documents. */
 @Test void missingAndMalformedContentHaveSafeServiceAndHttpErrors() throws Exception {
  for(String operation:List.of("DELETE FROM reference_content WHERE content_key='support.directory'","INSERT INTO reference_content(content_key,content) VALUES ('support.directory','{}')","UPDATE reference_content SET content='[]' WHERE content_key='support.directory'")){
   jdbc.update(operation);assertThatThrownBy(()->service.content(MIGUEL)).isInstanceOf(ApiException.class);
   mvc.perform(get("/api/v1/support/content").header("Authorization","Bearer "+login("miguel"))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Support content is unavailable. Please try again."));
  }
 }
 /** Prove reset rollback does not rewind a committed sequence independently of its rows. */
 @Test void resetAndSequenceRestartAreTransactional(){
  service.submit(MIGUEL,valid());assertThatThrownBy(()->service.resetReference()).isInstanceOf(ApiException.class);
  TransactionTemplate tx=new TransactionTemplate(transactions);tx.executeWithoutResult(status->{service.clearInquiries();status.setRollbackOnly();});
  assertThat(repository.count()).isEqualTo(1);assertThat(service.submit(ROSA,valid()).referenceNumber()).isEqualTo("NB-99215");
  service.clearInquiries();service.clearInquiries();service.resetReference();assertThat(repository.count()).isZero();assertThat(service.submit(MIGUEL,valid()).referenceNumber()).isEqualTo("NB-99214");
 }
 /** Verify real schema uniqueness, FK, nullability, length and cascade constraints. */
 @Test void postgresEnforcesInquiryAndReferenceIntegrity(){
  service.submit(MIGUEL,valid());
  for(String column:List.of("user_id","topic","subject","message","reference_number","created_at"))assertThatThrownBy(()->jdbc.update("UPDATE customer_inquiries SET "+column+"=NULL")).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE customer_inquiries SET user_id=?",UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
  for(String column:List.of("topic","subject","message"))assertThatThrownBy(()->jdbc.update("UPDATE customer_inquiries SET "+column+"=?",column.equals("topic")?"BAD":" ")).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO customer_inquiries(user_id,topic,subject,message,reference_number) SELECT user_id,topic,subject,message,reference_number FROM customer_inquiries")).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE customer_inquiries SET message=?","x".repeat(5001))).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE reference_content SET content=NULL")).isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("INSERT INTO reference_content(content_key,content) VALUES ('support.directory','{}')")).isInstanceOf(DataIntegrityViolationException.class);
  UUID transientUser=UUID.randomUUID();jdbc.update("INSERT INTO users(id,username,password_hash,full_name,role) VALUES (?,'support-transient','unused','Transient','ROLE_CUSTOMER')",transientUser);
  service.submit(transientUser,valid());jdbc.update("DELETE FROM users WHERE id=?",transientUser);assertThat(repository.count()).isEqualTo(1);
 }
}
