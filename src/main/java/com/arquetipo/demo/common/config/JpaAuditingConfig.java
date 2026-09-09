package com.arquetipo.demo.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa el poblado automatico de {@code createdAt} / {@code updatedAt} en las entidades
 * anotadas con {@code @EntityListeners(AuditingEntityListener.class)}.
 *
 * <p>Se aisla en su propia clase para poder importarla de forma explicita en los slices de
 * test de persistencia ({@code @DataJpaTest}).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
