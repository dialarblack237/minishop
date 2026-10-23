package com.example.minishop.service;

import com.example.minishop.domain.Customer;
import com.example.minishop.repository.CustomerRepository;
import com.example.minishop.web.dto.CustomerDto;
import com.example.minishop.web.dto.RegisterRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final PasswordEncoder encoder;
    private final CustomerRepository repo;

    public CustomerService(PasswordEncoder encoder, CustomerRepository repo) {
        this.encoder = encoder;
        this.repo = repo;
    }

    @Transactional
    public CustomerDto register(RegisterRequest req) {
        if (repo.existsByEmail(req.email())) {
            throw new EmailTaken(req.email());
        }
        String hash = encoder.encode(req.password());
        try {
            Customer saved = repo.save(new Customer(req.email(), req.displayName(), hash));
            return CustomerDto.from(saved);
        } catch (DataIntegrityViolationException race) {
            throw new EmailTaken(req.email());
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Page<CustomerDto> list(Pageable pageable) {
        return repo.findAll(pageable).map(CustomerDto::from);
    }

    @PreAuthorize("isAuthenticated()")
    public CustomerDto get(long id, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        boolean isSelf = String.valueOf(id).equals(auth.getName());
        if (!isAdmin && !isSelf) {
            throw new Forbidden();
        }
        return repo.findById(id)
                .map(CustomerDto::from)
                .orElseThrow(CustomerNotFound::new);
    }
}