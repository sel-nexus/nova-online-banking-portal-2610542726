package com.nova.bank.support;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/** Re-query receipts without exposing an inquiry-list HTTP surface. */
@Repository
public interface InquiryRepository extends JpaRepository<Inquiry,UUID> {
 /** Retrieve a receipt for internal persistence verification. */
 Optional<Inquiry> findByReferenceNumber(String referenceNumber);
}
