package com.example.test.repo;

import com.example.test.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Emails are stored lower-cased, so lookups use exact matches on the normalised value; this lets the database
 * use the unique index (a case-insensitive query would wrap the column in {@code upper()} and skip it).
 */
public interface UserRepo extends JpaRepository<User, Long> {

    boolean existsByEmail(String normalisedEmail);

    Optional<User> findByEmail(String normalisedEmail);

    boolean existsByPhoneNumber(String e164PhoneNumber);

    /** Row-locks the user so concurrent PIN attempts cannot lose failed-attempt increments. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
