package com.nova.bank.chat;

import com.nova.bank.TestSupport;
import com.nova.bank.auth.TokenService;
import com.nova.bank.auth.User;
import com.nova.bank.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Exercise service and authenticated HTTP contracts against actual PostgreSQL fixtures. */
class ChatTests extends TestSupport {
 @Autowired ChatService service;
 @Autowired TokenService tokens;
 private static final UUID MIGUEL=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
 private static final UUID ROSA=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
 private static final UUID ADMIN=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13");
 private static final String GUIDANCE="Choose Account Balance, Card Help, Offers & Promos, or Speak to Agent. This is a scripted demonstration assistant.";
 private static final String GENERIC="This demonstration persona has no customer-specific assistant information. Choose Account Balance, Card Help, Offers & Promos, or Speak to Agent.";
 private static final String AGENT="Handoff is simulated. No live agent was contacted and no support ticket opened.";
 /** Restore only this feature's document, preserving unrelated fixtures and activity. */
 @Override protected void resetFeatureFixtures(){jdbc.update("DELETE FROM reference_content WHERE content_key=?","chat.assistant");service.seedContent();}
 /** Verify the whole response shape at the HTTP boundary. */
 private ChatDtos.Reply http(String token,String prompt) throws Exception {
  var result=mvc.perform(post("/api/v1/chat/query").header("Authorization","Bearer "+token).contentType("application/json").content(mapper.writeValueAsString(Map.of("message",prompt))))
   .andExpect(status().isOk()).andReturn();
  String json=result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  assertThat(mapper.readTree(json).size()).isEqualTo(3);
  return mapper.readValue(json,ChatDtos.Reply.class);
 }
 /** Compare service and API replies with independent exact PRD expectations and repeat determinism. */
 private void exact(UUID id,String username,String topic,ChatDtos.Reply expected) throws Exception {
  String token=login(username);
  for(String alias:List.of(topic,topic.toUpperCase(Locale.ROOT),"  "+topic.toLowerCase(Locale.ROOT)+"  ")){
   assertThat(service.query(id,new ChatDtos.Request(alias))).isEqualTo(expected);
   assertThat(http(token,alias)).isEqualTo(expected);
   assertThat(http(token,alias)).isEqualTo(expected);
  }
 }
 /** Verify Miguel's deposit-only total, count and latest salary. */
 @Test void miguelBalanceIsExact() throws Exception {exact(MIGUEL,"miguel","Account Balance",new ChatDtos.Reply("Your available deposit balance is PHP 142,860.25 across 2 deposit accounts. Your latest Salary credit was PHP 48,500.00 on 13 Oct.","View Account Summary","/accounts"));}
 /** Verify Rosa's salary deposit without Miguel information. */
 @Test void rosaBalanceIsExact() throws Exception {exact(ROSA,"rosa","Account Balance",new ChatDtos.Reply("Your available deposit balance is PHP 54,300.00 in your Salary Account.","View Account Summary","/accounts"));}
 /** Verify masked card, exact due/credit and due date. */
 @Test void miguelCardIsExact() throws Exception {exact(MIGUEL,"miguel","Card Help",new ChatDtos.Reply("Your Rewards Credit Card •••• 1188 has PHP 8,450.00 due and PHP 41,550.00 available credit. Your next payment is due 28 Oct.","Emergency Lost Card Hotline","/support"));}
 /** Verify Rosa's no-card branch still provides support navigation. */
 @Test void rosaHasNoActiveCard() throws Exception {exact(ROSA,"rosa","Card Help",new ChatDtos.Reply("No active credit cards were found for your profile.","Emergency Lost Card Hotline","/support"));}
 /** Verify precisely the two assistant campaigns, not the complete offer catalogue. */
 @Test void miguelOffersAreExact() throws Exception {exact(MIGUEL,"miguel","Offers & Promos",new ChatDtos.Reply("You have 2 personalized campaigns: Payday Ipon Boost and 5% Dining Cashback. These are demonstration offers.","Open Offers Page","/offers"));}
 /** Verify Rosa's appraisal waiver. */
 @Test void rosaOfferIsExact() throws Exception {exact(ROSA,"rosa","Offers & Promos",new ChatDtos.Reply("Your personalized Home Loan Fee Waiver offers 100% appraisal fee waived. This is a demonstration offer.","Open Offers Page","/offers"));}
 /** Cover both handoff aliases for every seeded identity without live-contact claims. */
 @Test void handoffIsExplicitlySimulatedForAllPersonas() throws Exception {
  for(String name:List.of("miguel","rosa","admin"))for(String topic:List.of("Speak to Agent","Speak to Representative"))exact(id(name),name,topic,new ChatDtos.Reply(AGENT,"Close Chat","close"));
 }
 /** Resolve the seeded identity solely for tests. */
 private UUID id(String name){return name.equals("miguel")?MIGUEL:name.equals("rosa")?ROSA:ADMIN;}
 /** Admin known topics and arbitrary real customer personas never invent account details. */
 @Test void genericPersonaAndCurrentUsernameAreSafe() throws Exception {
  for(String topic:List.of("Account Balance","Card Help","Offers & Promos"))exact(ADMIN,"admin",topic,new ChatDtos.Reply(GENERIC,null,null));
  UUID other=UUID.randomUUID();jdbc.update("INSERT INTO users(id,username,password_hash,full_name,role) VALUES (?,?,?,?,?)",other,"chat-other","unused","Other","ROLE_CUSTOMER");
  try{String token=tokens.issue(new User(other,"ignored","unused","Other","ROLE_CUSTOMER"));for(String topic:List.of("Account Balance","Card Help","Offers & Promos")){assertThat(service.query(other,new ChatDtos.Request(topic))).isEqualTo(new ChatDtos.Reply(GENERIC,null,null));assertThat(http(token,topic)).isEqualTo(new ChatDtos.Reply(GENERIC,null,null));}}
  finally{jdbc.update("DELETE FROM users WHERE id=?",other);}
  jdbc.update("UPDATE users SET username=? WHERE id=?","chat-renamed",MIGUEL);
  try{assertThat(service.query(MIGUEL,new ChatDtos.Request("Account Balance"))).isEqualTo(new ChatDtos.Reply(GENERIC,null,null));}
  finally{jdbc.update("UPDATE users SET username=? WHERE id=?","miguel",MIGUEL);}
 }
 /** Unknown prompts including security strings remain guidance, never an echo or SQL command. */
 @Test void unknownAndHostilePromptsAreGuidance() throws Exception {
  for(String name:List.of("miguel","rosa","admin")){
   String token=login(name);
   for(String prompt:List.of("x","x".repeat(500),"pay my loan","balance for rosa","'; DROP TABLE users; --","<script>alert(1)</script>","../../etc/passwd")){
    var expected=new ChatDtos.Reply(GUIDANCE,null,null);assertThat(service.query(id(name),new ChatDtos.Request(prompt))).isEqualTo(expected);assertThat(http(token,prompt)).isEqualTo(expected);
   }
  }
  assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isGreaterThanOrEqualTo(3);
 }
 /** Reject absence, malformed bodies, every non-string kind and inclusive length violations. */
 @Test void httpStrictValidationRejectsBadBodiesWithoutLeakingText() throws Exception {
  String token=login("miguel");
  for(Object bad:Arrays.asList(null,""," \t\n",123,12.5,true,List.of("Account Balance"),Map.of("text","Card Help"),"x".repeat(501))){
   Map<String,Object> body=new HashMap<>();body.put("message",bad);
   mvc.perform(post("/api/v1/chat/query").header("Authorization","Bearer "+token).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.actionRoute").doesNotExist());
  }
  for(String json:List.of("{}","{","null","[]","{\"message\":\"Account Balance\",\"username\":\"rosa\"}"))mvc.perform(post("/api/v1/chat/query").header("Authorization","Bearer "+token).contentType("application/json").content(json)).andExpect(status().isBadRequest());
 }
 /** Validate the service separately from MVC, including locked and nonexistent identities. */
 @Test void serviceValidationAndMissingIdentityFailClosed(){
  for(UUID principal:Arrays.asList(null,UUID.randomUUID()))assertThatThrownBy(()->service.query(principal,new ChatDtos.Request("Account Balance"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
  for(ChatDtos.Request request:Arrays.asList(null,new ChatDtos.Request(null),new ChatDtos.Request(""),new ChatDtos.Request(" \n"),new ChatDtos.Request("x".repeat(501))))assertThatThrownBy(()->service.query(MIGUEL,request)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
  jdbc.update("UPDATE users SET locked=true WHERE id=?",MIGUEL);
  assertThatThrownBy(()->service.query(MIGUEL,new ChatDtos.Request("Card Help"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
 }
 /** Cover no bearer, invalid, revoked, deleted signed subject and unexpected security principals. */
 @Test void authenticationIsRequired() throws Exception {
  String revoked=login("miguel");auth.logout(revoked);
  String absent=tokens.issue(new User(UUID.randomUUID(),"missing","unused","Missing","ROLE_CUSTOMER"));
  for(String bearer:List.of("","Bearer invalid","Bearer "+revoked,"Bearer "+absent))mvc.perform(post("/api/v1/chat/query").header("Authorization",bearer).contentType("application/json").content("{\"message\":\"Account Balance\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required."));
  for(Object principal:List.of(UUID.randomUUID(),"miguel"))mvc.perform(post("/api/v1/chat/query").with(authentication(new UsernamePasswordAuthenticationToken(principal,"unused",List.of()))).contentType("application/json").content("{\"message\":\"Card Help\"}")).andExpect(status().isUnauthorized());
 }
 /** Compare every public table count before/after chat, proving zero retained messages or activity. */
 @Test void conversationsAndActivityAreNeverRetained() throws Exception {
  String token=login("miguel");var before=counts();
  for(String topic:List.of("Account Balance","Card Help","Offers & Promos","Speak to Agent","Speak to Representative","private-text-never-retained"))for(int i=0;i<3;i++){service.query(MIGUEL,new ChatDtos.Request(topic));http(token,topic);}
  assertThat(counts()).isEqualTo(before);
  assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND (table_name LIKE '%chat%' OR table_name LIKE '%conversation%' OR table_name LIKE '%message%')",String.class)).isEmpty();
  assertThat(jdbc.queryForObject("SELECT content::text FROM reference_content WHERE content_key=?",String.class,"chat.assistant")).doesNotContain("private-text-never-retained");
 }
 /** Read all table counts through the real database, escaping metadata identifiers only. */
 private Map<String,Long> counts(){Map<String,Long> result=new TreeMap<>();for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public'",String.class))result.put(table,jdbc.queryForObject("SELECT count(*) FROM \""+table.replace("\"","\"\"")+"\"",Long.class));return result;}
 /** Preserve existing JSONB edits, identity locks, unrelated documents and disabled startup state. */
 @Test void seedIsIdempotentAndRespectsStartupGate() throws Exception {
  jdbc.update("UPDATE reference_content SET content=jsonb_set(content,'{miguel,balance,message}',?::jsonb) WHERE content_key=?","\"Edited reference answer\"","chat.assistant");
  var support=jdbc.queryForList("SELECT content::text FROM reference_content WHERE content_key=?",String.class,"support.directory");
  service.seedContent();service.seedContent();assertThat(service.query(MIGUEL,new ChatDtos.Request("Account Balance")).message()).isEqualTo("Edited reference answer");
  assertThat(jdbc.queryForObject("SELECT count(*) FROM reference_content WHERE content_key=?",Integer.class,"chat.assistant")).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT pg_typeof(content)::text FROM reference_content WHERE content_key=?",String.class,"chat.assistant")).isEqualTo("jsonb");
  assertThat(jdbc.queryForList("SELECT content::text FROM reference_content WHERE content_key=?",String.class,"support.directory")).isEqualTo(support);
  jdbc.update("DELETE FROM reference_content WHERE content_key=?","chat.assistant");service.chatSeed(service,auth,false).run(new DefaultApplicationArguments());assertThat(jdbc.queryForObject("SELECT count(*) FROM reference_content WHERE content_key=?",Integer.class,"chat.assistant")).isZero();
  service.chatSeed(service,auth,true).run(new DefaultApplicationArguments());assertThat(service.query(ROSA,new ChatDtos.Request("Card Help")).message()).isEqualTo("No active credit cards were found for your profile.");
  assertThatThrownBy(()->jdbc.update("INSERT INTO reference_content(content_key,content) VALUES (?,?::jsonb)","chat.assistant","{}")).isInstanceOf(DataIntegrityViolationException.class);
 }
 /** Malformed or unsafe persisted scripts yield one safe 503, never an in-process fallback. */
 @Test void missingMalformedAndUnsafeContentReturnSafe503() throws Exception {
  String token=login("miguel");String original=jdbc.queryForObject("SELECT content::text FROM reference_content WHERE content_key=?",String.class,"chat.assistant");
  jdbc.update("DELETE FROM reference_content WHERE content_key=?","chat.assistant");safe503(token);
  service.seedContent();
  for(String json:List.of("null","[]","{}","{\"miguel\":123}")){jdbc.update("UPDATE reference_content SET content=?::jsonb WHERE content_key=?",json,"chat.assistant");safe503(token);}
  for(String path:List.of("{miguel,balance,message}","{rosa,card,actionLabel}","{generic,unknown,actionRoute}")){
   jdbc.update("UPDATE reference_content SET content=jsonb_set(?::jsonb,?::text[],'123') WHERE content_key=?",original,path,"chat.assistant");safe503(token);
  }
  for(String value:List.of("null","\"\"","\"javascript:alert(1)\"","\"/admin\"")){
   jdbc.update("UPDATE reference_content SET content=jsonb_set(?::jsonb,'{miguel,balance,actionRoute}',?::jsonb) WHERE content_key=?",original,value,"chat.assistant");safe503(token);
  }
 }
 /** Assert identical safe status and public message at both boundaries. */
 private void safe503(String token) throws Exception {
  assertThatThrownBy(()->service.query(MIGUEL,new ChatDtos.Request("Account Balance"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  mvc.perform(post("/api/v1/chat/query").header("Authorization","Bearer "+token).contentType("application/json").content("{\"message\":\"Account Balance\"}"))
   .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Assistant content is unavailable. Please try again.")).andExpect(jsonPath("$.actionRoute").doesNotExist());
 }
}
