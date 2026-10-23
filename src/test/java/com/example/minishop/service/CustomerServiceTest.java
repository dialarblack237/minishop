package com.example.minishop.service;

import com.example.minishop.config.JpaAuditingConfig;
import com.example.minishop.repository.CustomerRepository;
import com.example.minishop.web.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({CustomerService.class, JpaAuditingConfig.class, CustomerServiceTest.EncoderConfig.class})
@ActiveProfiles("dev")
class CustomerServiceTest {

    @TestConfiguration
    static class EncoderConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Autowired
    private CustomerService service;

    @Autowired
    private CustomerRepository repo;

    @Test
    void register_storesHashedPassword_notPlainText() {
        service.register(new RegisterRequest("alice@minishop.local", "pw-1234-xyz", "Alice"));
        String stored = repo.findByEmail("alice@minishop.local").orElseThrow().getPasswordHash();
        assertThat(stored).doesNotContain("pw-1234-xyz");
        assertThat(new BCryptPasswordEncoder().matches("pw-1234-xyz", stored)).isTrue();
    }

    @Test
    void register_duplicateEmail_throws() {
        service.register(new RegisterRequest("bob@minishop.local", "pw-1234", "Bob"));
        assertThatThrownBy(() ->
            service.register(new RegisterRequest("bob@minishop.local", "other-pw", "Bob 2"))
        ).isInstanceOf(EmailTaken.class);
    }
}