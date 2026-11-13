package com.nova.bank.auth;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
/** Query authoritative identities and serialize login attempts. */
@Repository
public interface UserRepository extends JpaRepository<User,UUID> {
 /** Find an identity without exposing its existence to callers. */ Optional<User> findByUsername(String username);
 /** Hold a database row lock until the login transaction commits. */
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from User u where u.username = :username")
 Optional<User> lockByUsername(@Param("username") String username);
}
