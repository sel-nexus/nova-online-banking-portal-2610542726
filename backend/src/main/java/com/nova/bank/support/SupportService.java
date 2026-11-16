package com.nova.bank.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.bank.auth.AuthService;
import com.nova.bank.auth.UserRepository;
import com.nova.bank.common.ApiException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persist reference fixtures and simulated enquiries without external communications. */
@Service
public class SupportService {
 public static final String NOTICE="Demonstration only. No live call, email, SMS or support ticket is sent.";
 private final InquiryRepository inquiries;
 private final UserRepository users;
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 private final Validator validator;
 /** Bind real persistence, strict serialization and service-level validation. */
 public SupportService(InquiryRepository inquiries,UserRepository users,JdbcTemplate jdbc,ObjectMapper mapper,Validator validator){this.inquiries=inquiries;this.users=users;this.jdbc=jdbc;this.mapper=mapper;this.validator=validator;}
 /** Read the persisted directory only for an existing verified identity. */
 @Transactional(readOnly=true) public SupportDtos.Content content(UUID principal){
  requireUser(principal);
  String json=jdbc.query("SELECT content::text FROM reference_content WHERE content_key='support.directory'",(rs,row)->rs.getString(1)).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Support content is unavailable. Please try again."));
  try{var content=mapper.readValue(json,SupportDtos.Content.class);if(content==null||content.contacts()==null||content.branches()==null||content.faqs()==null||content.topics()==null||content.notice()==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Support content is unavailable. Please try again.");return content;}catch(JsonProcessingException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Support content is unavailable. Please try again.");}
 }
 /** Validate independently of HTTP and allocate a database-unique local receipt.
  * Notes: shared transaction advisory lock permits concurrent sends but excludes a coordinated reset.
  */
 @Transactional public SupportDtos.Receipt submit(UUID principal,SupportDtos.Request request){
  var user=requireUser(principal);
  if(request==null||!validator.validate(request).isEmpty())throw new ApiException(HttpStatus.BAD_REQUEST,"Please check the required fields.");
  jdbc.execute("SELECT pg_advisory_xact_lock_shared(99214)");
  Long number=jdbc.queryForObject("SELECT nextval('inquiry_reference_seq')",Long.class);
  String reference="NB-"+number;
  inquiries.saveAndFlush(new Inquiry(user,request,reference));
  return new SupportDtos.Receipt(reference,"RECEIVED","24h");
 }
 /** Reject absent or removed users before reads or writes. */
 private com.nova.bank.auth.User requireUser(UUID principal){
  if(principal==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required.");
  return users.findById(principal).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required."));
 }
 /** Delete local simulations and restart references atomically for future administrator reset.
  * Notes: ALTER SEQUENCE RESTART is transactional, unlike setval; an outer rollback restores both rows and sequence.
  */
 @Transactional public void clearInquiries(){jdbc.execute("SELECT pg_advisory_xact_lock(99214)");jdbc.update("DELETE FROM customer_inquiries");jdbc.execute("ALTER SEQUENCE inquiry_reference_seq RESTART WITH 99214");}
 /** Restore an empty sequence only; refuse a reset that could duplicate existing receipts. */
 @Transactional public void resetReference(){
  jdbc.execute("SELECT pg_advisory_xact_lock(99214)");
  if(inquiries.count()!=0)throw new ApiException(HttpStatus.CONFLICT,"Clear inquiries before resetting references.");
  jdbc.execute("ALTER SEQUENCE inquiry_reference_seq RESTART WITH 99214");
 }
 /** Insert absent support fixtures without overwriting edits, other reference keys, or enquiries.
  * Notes: later assistant fixtures can use separate content_key documents in this same JSONB table; conversations are not retained.
  */
 @Transactional public void seedContent(){
  var content=new SupportDtos.Content(
   List.of(new SupportDtos.Contact("Call Us","(+63 2) 8000 0000","Mon–Fri 08:00–17:00","Sample hotline"),
    new SupportDtos.Contact("Secure Message","Online enquiry simulation","Estimated response 24h","No external message is sent"),
    new SupportDtos.Contact("Find a Branch / ATM","Static Metro Manila directory","Ayala, BGC, Quezon Ave","Demonstration directory"),
    new SupportDtos.Contact("Lost or Stolen Card","(+63 2) 8000 0001","24/7","Emergency blocking hotline (sample; no live blocking)")),
   List.of(new SupportDtos.Branch("Ayala Branch","Tower One, Ayala Avenue, Makati City","Full Service Branch","Mon–Fri 09:00–16:30"),
    new SupportDtos.Branch("BGC Branch","High Street South, Bonifacio Global City, Taguig","Extended Hours Branch","Mon–Sat 09:00–18:00"),
    new SupportDtos.Branch("Quezon Avenue ATM","West Triangle, Quezon City","24/7 Secure Automated Teller & Cash Deposit","24/7")),
   List.of(new SupportDtos.Faq("login","How do I sign in?","Demo credentials are on the login card: miguel / demo-only and rosa / demo-only. Toggle password visibility with Show / Hide."),
    new SupportDtos.Faq("transactions","How do I filter transactions or download a statement?","Account summary supports real-time search, 7/30/90-day ranges and .txt download."),
    new SupportDtos.Faq("interest","What interest rate does Peso Savings earn?","Standard Peso Savings earns 3.25% p.a. Payday Ipon Boost tiered deposits qualify for an additional 0.50% p.a."),
    new SupportDtos.Faq("credit","How is available credit calculated?","Available credit is total credit limit minus current amount due: PHP 50,000.00 − PHP 8,450.00 = PHP 41,550.00.")),
   List.of("LOGIN","ACCOUNTS","TRANSACTIONS","CARDS","LOANS","OFFERS","GENERAL"),NOTICE);
  try{jdbc.update("INSERT INTO reference_content(content_key,content) VALUES ('support.directory',?::jsonb) ON CONFLICT(content_key) DO NOTHING",mapper.writeValueAsString(content));}
  catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize support fixtures",e);}
 }
 /** Run the idempotent fixture initializer after identity, respecting the shared startup flag. */
 @Bean ApplicationRunner supportSeed(SupportService service,AuthService auth,@Value("${nova.seed-on-startup}") boolean enabled){return args->{if(enabled){auth.seedUsers();service.seedContent();}};}
}
