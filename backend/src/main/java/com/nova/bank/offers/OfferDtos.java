package com.nova.bank.offers;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
/** Define safe product, campaign and persisted simulated-interest contracts. */
public final class OfferDtos {
 /** Prevent utility construction. */ private OfferDtos() {}
 /** Describe only the approved bounded product recommendation. */
 public record Product(String title,String category,String benefit) {}
 /** Expose full terms without customer identifiers or internal targeting fields. */
 public record Offer(UUID id,String offerKey,String title,String category,String benefit,String terms,
                     String eligibility,LocalDate validUntil,String personalization,boolean interested) {}
 /** Confirm the same durable registration on every repeated request. */
 public record Interest(UUID id,UUID promotionId,Instant registeredAt,String message) {}
}
