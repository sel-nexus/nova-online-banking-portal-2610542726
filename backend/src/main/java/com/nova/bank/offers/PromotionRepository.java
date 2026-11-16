package com.nova.bank.offers;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
/** Query only active, unexpired campaigns permitted by the verified segment. */
@Repository
public interface PromotionRepository extends JpaRepository<Promotion,UUID> {
 /** Include universal campaigns and allow the facilitator to inspect all current segments. */
 @Query("select p from Promotion p where p.active=true and p.validUntil>=:date and (:segment='ADMIN' or p.targetSegment='ALL' or p.targetSegment=:segment) order by p.id")
 List<Promotion> catalogue(@Param("segment") String segment,@Param("date") LocalDate date);
}
