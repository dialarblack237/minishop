package com.example.minishop.service;

import com.example.minishop.domain.Customer;
import com.example.minishop.repository.CustomerRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final CustomerRepository customerRepo;
    private final PasswordEncoder encoder;

    public AuthService(CustomerRepository customerRepo, PasswordEncoder encoder) {
        this.customerRepo = customerRepo;
        this.encoder = encoder;
    }

    public Customer verifyCredentials(String email, String password) {
        Customer user = customerRepo.findByEmail(email)
                .orElseThrow(InvalidCredentials::new);
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentials();
        }
        return user;
    }

    public Customer findForSigning(long customerId) {
        return customerRepo.findById(customerId)
                .orElseThrow(InvalidCredentials::new);
    }

    public static class InvalidCredentials extends RuntimeException {
        public InvalidCredentials() {
            super("Invalid email or password");
        }
    }
}