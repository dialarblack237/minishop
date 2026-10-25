package com.example.minishop.service;

import com.example.minishop.domain.IdemRow;
import com.example.minishop.repository.IdemRepository;
import com.example.minishop.web.dto.OrderDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class IdemService {

    private final IdemRepository idemRepo;
    private final ObjectMapper objectMapper;

    public IdemService(IdemRepository idemRepo, ObjectMapper objectMapper) {
        this.idemRepo = idemRepo;
        this.objectMapper = objectMapper;
    }

    public IdemRow claim(String key) {
        Optional<IdemRow> existing = idemRepo.findByIdemKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        return idemRepo.saveAndFlush(new IdemRow(key, "{}"));
    }

    @Transactional
    public void attachResponse(String key, OrderDto dto) throws Exception {
        IdemRow row = idemRepo.findByIdemKey(key)
                .orElseThrow(() -> new IllegalStateException("Idem row missing: " + key));
        row.setResponseJson(objectMapper.writeValueAsString(dto));
        idemRepo.save(row);
    }

    public OrderDto readResponse(IdemRow row) throws Exception {
        if (row.getResponseJson() == null || row.getResponseJson().equals("{}")) {
            throw new IllegalStateException("Response not yet attached for key: " + row.getIdemKey());
        }
        return objectMapper.readValue(row.getResponseJson(), OrderDto.class);
    }
}