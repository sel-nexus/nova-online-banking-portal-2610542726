package com.nova.bank.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.bank.auth.AuthService;
import com.nova.bank.auth.UserRepository;
import com.nova.bank.common.ApiException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read deterministic PostgreSQL scripts without storing prompts, replies or activity. */
@Service
public class ChatService {
 private static final String KEY="chat.assistant";
 private static final List<String> TOPICS=List.of("balance","card","offers","agent","unknown");
 private final UserRepository users;
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 private final Validator validator;
 /** Bind authoritative identities and the real database client. */
 public ChatService(UserRepository users,JdbcTemplate jdbc,ObjectMapper mapper,Validator validator){this.users=users;this.jdbc=jdbc;this.mapper=mapper;this.validator=validator;}
 /** Resolve only the current verified username, never a client-supplied persona.
  * Raises: safe unauthorized, invalid-request or unavailable errors.
  * Notes: all responses are read afresh; there is no process-local fallback or conversation write.
  */
 @Transactional(readOnly=true) public ChatDtos.Reply query(UUID principal,ChatDtos.Request request){
  if(principal==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required.");
  var user=users.findById(principal).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required."));
  if(user.isLocked())throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required.");
  if(request==null||!validator.validate(request).isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"Please check the required fields.");
  String topic=switch(request.message().strip().toLowerCase(Locale.ROOT)){
   case "account balance"->"balance";
   case "card help"->"card";
   case "offers & promos"->"offers";
   case "speak to agent","speak to representative"->"agent";
   default->"unknown";
  };
  String json=jdbc.query("SELECT content::text FROM reference_content WHERE content_key=?",(rs,row)->rs.getString(1),KEY).stream().findFirst().orElseThrow(this::unavailable);
  try{
   JsonNode content=mapper.readTree(json);
   validateContent(content);
   String persona=List.of("miguel","rosa").contains(user.getUsername())?user.getUsername():"generic";
   JsonNode reply=content.get(persona).get(topic);
   return new ChatDtos.Reply(reply.get("message").textValue(),reply.get("actionLabel").textValue(),reply.get("actionRoute").textValue());
  }catch(JsonProcessingException|IllegalArgumentException e){throw unavailable();}
 }
 /** Fail closed on malformed structure or unsafe navigation anywhere in the script. */
 private void validateContent(JsonNode content){
  if(content==null||!content.isObject())throw unavailable();
  for(String persona:List.of("miguel","rosa","generic")){
   JsonNode scripts=content.get(persona);
   if(scripts==null||!scripts.isObject())throw unavailable();
   for(String topic:TOPICS){
    JsonNode reply=scripts.get(topic);
    if(reply==null||!reply.isObject())throw unavailable();
    JsonNode text=reply.get("message"),label=reply.get("actionLabel"),route=reply.get("actionRoute");
    if(text==null||!text.isTextual()||text.textValue().isBlank()||text.textValue().length()>2000||label==null||route==null)throw unavailable();
    if(topic.equals("unknown")||persona.equals("generic")&&!topic.equals("agent")){
     if(!label.isNull()||!route.isNull())throw unavailable();
    }else{
     String expected=switch(topic){case "balance"->"/accounts";case "card"->"/support";case "offers"->"/offers";default->"close";};
     if(!label.isTextual()||label.textValue().isBlank()||label.textValue().length()>100||!route.isTextual()||!expected.equals(route.textValue()))throw unavailable();
    }
   }
  }
 }
 /** Produce one non-leaking content error. */
 private ApiException unavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Assistant content is unavailable. Please try again.");}
 /** Insert the exact scripts only when absent, preserving edits and unrelated keys.
  * Notes: administrator reset may delete this key and call this transactional method.
  */
 @Transactional public void seedContent(){
  var guidance=new ChatDtos.Reply("Choose Account Balance, Card Help, Offers & Promos, or Speak to Agent. This is a scripted demonstration assistant.",null,null);
  var generic=new ChatDtos.Reply("This demonstration persona has no customer-specific assistant information. Choose Account Balance, Card Help, Offers & Promos, or Speak to Agent.",null,null);
  var agent=new ChatDtos.Reply("Handoff is simulated. No live agent was contacted and no support ticket opened.","Close Chat","close");
  var content=Map.of(
   "miguel",Map.of(
    "balance",new ChatDtos.Reply("Your available deposit balance is PHP 142,860.25 across 2 deposit accounts. Your latest Salary credit was PHP 48,500.00 on 13 Oct.","View Account Summary","/accounts"),
    "card",new ChatDtos.Reply("Your Rewards Credit Card •••• 1188 has PHP 8,450.00 due and PHP 41,550.00 available credit. Your next payment is due 28 Oct.","Emergency Lost Card Hotline","/support"),
    "offers",new ChatDtos.Reply("You have 2 personalized campaigns: Payday Ipon Boost and 5% Dining Cashback. These are demonstration offers.","Open Offers Page","/offers"),"agent",agent,"unknown",guidance),
   "rosa",Map.of(
    "balance",new ChatDtos.Reply("Your available deposit balance is PHP 54,300.00 in your Salary Account.","View Account Summary","/accounts"),
    "card",new ChatDtos.Reply("No active credit cards were found for your profile.","Emergency Lost Card Hotline","/support"),
    "offers",new ChatDtos.Reply("Your personalized Home Loan Fee Waiver offers 100% appraisal fee waived. This is a demonstration offer.","Open Offers Page","/offers"),"agent",agent,"unknown",guidance),
   "generic",Map.of("balance",generic,"card",generic,"offers",generic,"agent",agent,"unknown",guidance));
  try{jdbc.update("INSERT INTO reference_content(content_key,content) VALUES (?,?::jsonb) ON CONFLICT(content_key) DO NOTHING",KEY,mapper.writeValueAsString(content));}
  catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize assistant fixtures",e);}
 }
 /** Initialize after ensuring identity fixtures, honoring the shared startup gate. */
 @Bean ApplicationRunner chatSeed(ChatService service,AuthService auth,@Value("${nova.seed-on-startup}") boolean enabled){return args->{if(enabled){auth.seedUsers();service.seedContent();}};}
}
