package com.oms.user.repository;

import com.oms.user.config.JpaAuditingConfig;
import com.oms.user.entity.Address;
import com.oms.user.entity.Role;
import com.oms.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against H2 in MySQL compatibility mode. Testcontainers would exercise a
 * real MySQL but would make `mvn test` require a running Docker daemon, which
 * is a worse trade for a repository slice.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("UserRepository")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User newUser(String email) {
        User user = new User();
        user.setName("Priya Nair");
        user.setEmail(email);
        user.setPassword("$2a$10$hashed");
        user.setPhone("9880000002");
        user.setRole(Role.USER);
        user.setActive(true);
        return user;
    }

    private Address newAddress(String label, boolean isDefault) {
        Address address = new Address();
        address.setLabel(label);
        address.setLine1("14, 3rd Cross, Indiranagar");
        address.setCity("Bengaluru");
        address.setState("Karnataka");
        address.setPostalCode("560038");
        address.setCountry("India");
        address.setDefaultAddress(isDefault);
        return address;
    }

    @BeforeEach
    void seed() {
        userRepository.saveAndFlush(newUser("priya@oms.com"));
    }

    @Test
    @DisplayName("finds a user by email")
    void findByEmail_returnsUser() {
        Optional<User> found = userRepository.findByEmail("priya@oms.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Priya Nair");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("email lookup is exact, not partial")
    void findByEmail_unknownEmail_returnsEmpty() {
        assertThat(userRepository.findByEmail("priya@oms")).isEmpty();
        assertThat(userRepository.existsByEmail("priya@oms.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@oms.com")).isFalse();
    }

    @Test
    @DisplayName("the unique constraint on email is enforced by the database, not only by the service")
    void duplicateEmail_violatesUniqueConstraint() {
        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("priya@oms.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("skips deactivated accounts when active-only lookup is used")
    void findByEmailAndActiveTrue_ignoresDeactivated() {
        User deactivated = newUser("rahul@oms.com");
        deactivated.setActive(false);
        userRepository.saveAndFlush(deactivated);

        assertThat(userRepository.findByEmailAndActiveTrue("rahul@oms.com")).isEmpty();
        assertThat(userRepository.findByEmail("rahul@oms.com")).isPresent();
    }

    @Test
    @DisplayName("fetch join loads addresses in one query without a lazy-init failure")
    void findByIdWithAddresses_loadsAddresses() {
        User user = userRepository.findByEmail("priya@oms.com").orElseThrow(IllegalStateException::new);
        user.addAddress(newAddress("HOME", true));
        user.addAddress(newAddress("WORK", false));
        userRepository.saveAndFlush(user);

        entityManager.clear();

        Optional<User> loaded = userRepository.findByIdWithAddresses(user.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getAddresses())
                .hasSize(2)
                .extracting(Address::getLabel)
                .containsExactlyInAnyOrder("HOME", "WORK");
    }

    @Test
    @DisplayName("auditing populates createdAt and updatedAt")
    void auditing_populatesTimestamps() {
        User user = userRepository.findByEmail("priya@oms.com").orElseThrow(IllegalStateException::new);

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("search matches on name or email, case-insensitively")
    void search_matchesNameOrEmail() {
        userRepository.saveAndFlush(newUser("rahul@oms.com"));

        Page<User> byEmail = userRepository.search("RAHUL", PageRequest.of(0, 10));
        Page<User> byName = userRepository.search("priya nair", PageRequest.of(0, 10));
        Page<User> everything = userRepository.search("", PageRequest.of(0, 10));

        assertThat(byEmail.getTotalElements()).isEqualTo(1);
        assertThat(byName.getTotalElements()).isEqualTo(2);
        assertThat(everything.getTotalElements()).isEqualTo(2);
    }
}
