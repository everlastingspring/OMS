package com.oms.user.repository;

import com.oms.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdAndActiveTrue(Long userId);

    Optional<Address> findByIdAndUserIdAndActiveTrue(Long id, Long userId);

    Optional<Address> findFirstByUserIdAndDefaultAddressTrueAndActiveTrue(Long userId);
}
