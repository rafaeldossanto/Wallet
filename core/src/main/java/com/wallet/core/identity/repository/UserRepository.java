package com.wallet.core.identity.repository;

import com.wallet.core.identity.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Row lock: a sign-up and a confirmation of the same pending account must not interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * {@code FOR UPDATE} by hand: Hibernate turns PESSIMISTIC_WRITE into {@code FOR NO KEY UPDATE}
     * on Postgres, which lets a new row referencing the user (a connection being linked) through.
     * This one makes that insert wait for the caller's transaction.
     */
    @Query(value = "select * from users where id = :id for update", nativeQuery = true)
    Optional<User> findByIdLockingReferences(@Param("id") UUID id);

    /**
     * A new account waiting for its e-mail, unless the e-mail already has one: 0 then, and no
     * exception, so two sign-ups with the same e-mail at once both answer the same.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into users (id, email, password_hash, display_name, created_at)
            values (:id, :email, :passwordHash, :displayName, :createdAt)
            on conflict (email) do nothing
            """, nativeQuery = true)
    int insertUnconfirmedIfAbsent(@Param("id") UUID id, @Param("email") String email,
                                  @Param("passwordHash") String passwordHash, @Param("displayName") String displayName,
                                  @Param("createdAt") Instant createdAt);

    /** Compare-and-set: a TOTP code counts once, even when two requests bring it together. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update User u set u.totpLastStep = :step
             where u.id = :id and (u.totpLastStep is null or u.totpLastStep < :step)
            """)
    int acceptTotpStep(@Param("id") UUID id, @Param("step") long step);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from User u where u.emailConfirmedAt is null and u.createdAt < :before")
    int deleteUnconfirmedCreatedBefore(@Param("before") Instant before);
}
