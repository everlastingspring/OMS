package com.oms.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept separate from the application class so repository slice tests can
 * @Import it explicitly; @DataJpaTest does not enable auditing on its own.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
