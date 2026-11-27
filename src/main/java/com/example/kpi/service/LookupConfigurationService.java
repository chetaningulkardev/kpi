package com.example.kpi.service;

import com.example.kpi.entity.LookupValue;
import com.example.kpi.repository.LookupValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LookupConfigurationService {

    private final LookupValueRepository lookupValueRepository;

    public List<LookupValue> findByType(String lookupType) {
        return lookupValueRepository.findByLookupTypeAndDeletedFalseOrderByLabelAsc(lookupType);
    }

    public LookupValue create(LookupValue value) {
        value.setDeleted(false);
        value.setActive(true);
        return lookupValueRepository.save(value);
    }

    public LookupValue update(Long id, LookupValue request) {
        LookupValue existing = lookupValueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lookup value not found"));
        existing.setLookupType(request.getLookupType());
        existing.setCode(request.getCode());
        existing.setLabel(request.getLabel());
        existing.setDescription(request.getDescription());
        existing.setMetadataJson(request.getMetadataJson());
        existing.setActive(request.isActive());
        return lookupValueRepository.save(existing);
    }

    public void softDelete(Long id) {
        LookupValue existing = lookupValueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lookup value not found"));
        existing.setDeleted(true);
        existing.setActive(false);
        lookupValueRepository.save(existing);
    }
}

