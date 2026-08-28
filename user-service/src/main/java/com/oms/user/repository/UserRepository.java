package com.oms.user.repository;

import com.oms.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndActiveTrue(String email);

    boolean existsByEmail(String email);

    /**
     * Fetch join avoids the extra query per user when the response includes addresses.
     * DISTINCT is required because the join multiplies rows by address count.
     */
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.addresses WHERE u.id = :id")
    Optional<User> findByIdWithAddresses(@Param("id") Long id);

    /**
     * The caller passes an empty string for "no filter" rather than null:
     * a bare ":keyword IS NULL" check on an untyped null parameter is a
     * portability trap across Hibernate dialects.
     */
    @Query("SELECT u FROM User u WHERE "
            + "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<User> search(@Param("keyword") String keyword, Pageable pageable);
}
