package com.example.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight reference to a user or org unit for display.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimpleRef {
    private Long id;
    private String name;
}

