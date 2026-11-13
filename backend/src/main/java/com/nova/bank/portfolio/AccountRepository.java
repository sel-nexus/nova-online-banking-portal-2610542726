package com.nova.bank.portfolio;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Query persisted relationships without broadening customer scope. */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    /** Retrieve only the verified owner's accounts in stable fixture order. */
    @Query("select a from Account a where a.user.id = :userId order by a.id")
    List<Account> findByUserIdOrderByIdAsc(@Param("userId") UUID userId);
}
