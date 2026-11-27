package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private Long id;

    @JsonProperty("unit_type")
    private String unitType; // TRIBE | PORTFOLIO | SQUAD

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("unit_id")
    private Long unitId;

    @JsonProperty("unit_name")
    private String unitName;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    private Boolean active;

    @JsonProperty("primary_assignment")
    private Boolean primaryAssignment;
}

