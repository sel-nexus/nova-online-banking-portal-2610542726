package com.nova.bank.offers;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/** Hydrate persisted demonstration campaigns; never expose this entity directly. */
@Entity @Table(name="promotions")
public class Promotion {
 @Id private UUID id;
 @Column(name="offer_key",nullable=false,length=50,unique=true) private String offerKey;
 @Column(nullable=false,length=150) private String title;
 @Column(nullable=false,length=50) private String category;
 @Column(name="target_segment",nullable=false,length=50) private String targetSegment;
 @Column(name="benefit_summary",nullable=false,columnDefinition="text") private String benefitSummary;
 @Column(name="terms_and_conditions",nullable=false,columnDefinition="text") private String termsAndConditions;
 @Column(nullable=false,columnDefinition="text") private String eligibility;
 @Column(name="valid_until",nullable=false) private LocalDate validUntil;
 @Column(nullable=false) private boolean active;
 /** Permit Hibernate hydration. */ protected Promotion() {}
 /** Identify the persisted campaign. */ public UUID getId(){return id;}
 /** Return the stable public campaign key. */ public String getOfferKey(){return offerKey;}
 /** Return the campaign heading. */ public String getTitle(){return title;}
 /** Return its constrained retail category. */ public String getCategory(){return category;}
 /** Return the internal targeting rule. */ public String getTargetSegment(){return targetSegment;}
 /** Return the demonstration benefit. */ public String getBenefitSummary(){return benefitSummary;}
 /** Return full fixture terms. */ public String getTermsAndConditions(){return termsAndConditions;}
 /** Return the qualifying fixture conditions. */ public String getEligibility(){return eligibility;}
 /** Return the inclusive last valid day. */ public LocalDate getValidUntil(){return validUntil;}
 /** Report whether the campaign is enabled. */ public boolean isActive(){return active;}
 /** Compare stable database identities. */ @Override public boolean equals(Object other){return other instanceof Promotion p && id!=null && id.equals(p.id);}
 /** Hash without loading other fields. */ @Override public int hashCode(){return id==null?0:id.hashCode();}
}
