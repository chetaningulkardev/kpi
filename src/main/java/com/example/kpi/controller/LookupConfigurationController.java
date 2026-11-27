package com.example.kpi.controller;

import com.example.kpi.entity.LookupValue;
import com.example.kpi.service.LookupConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config/lookups")
@RequiredArgsConstructor
public class LookupConfigurationController {

    private final LookupConfigurationService service;

    @GetMapping("/{lookupType}")
    public List<LookupValue> findByType(@PathVariable String lookupType) {
        return service.findByType(lookupType);
    }

    @PostMapping
    public ResponseEntity<LookupValue> create(@RequestBody LookupValue value) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(value));
    }

    @PutMapping("/{id}")
    public LookupValue update(@PathVariable Long id, @RequestBody LookupValue value) {
        return service.update(id, value);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}

