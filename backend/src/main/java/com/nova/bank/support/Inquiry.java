package com.nova.bank.support;

import com.nova.bank.auth.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Persist a local simulation with its verified owner; never expose this entity as JSON. */
@Entity @Table(name="customer_inquiries")
public class Inquiry {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(nullable=false,length=50) private String topic;
 @Column(nullable=false,length=150) private String subject;
 @Column(nullable=false,columnDefinition="text") private String message;
 @Column(name="reference_number",nullable=false,length=50,unique=true) private String referenceNumber;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 /** Allow Hibernate hydration. */ protected Inquiry() {}
 /** Record validated text without delivery to an external service. */
 public Inquiry(User user,SupportDtos.Request request,String reference){this.user=user;topic=request.topic();subject=request.subject();message=request.message();referenceNumber=reference;createdAt=Instant.now();}
 /** Identify this persisted simulation. */ public UUID getId(){return id;}
 /** Return the owner inside the transaction boundary. */ public User getUser(){return user;}
 /** Return selected topic. */ public String getTopic(){return topic;}
 /** Return subject as plain text. */ public String getSubject(){return subject;}
 /** Return message as plain text. */ public String getMessage(){return message;}
 /** Return the unique receipt. */ public String getReferenceNumber(){return referenceNumber;}
 /** Return creation instant. */ public Instant getCreatedAt(){return createdAt;}
 /** Compare only stable identity. */ @Override public boolean equals(Object other){return other instanceof Inquiry i&&id!=null&&id.equals(i.id);}
 /** Hash without loading owner data. */ @Override public int hashCode(){return id==null?0:id.hashCode();}
}
