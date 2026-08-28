package com.oms.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Separate from the application class so @DataJpaTest slices can import it. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
