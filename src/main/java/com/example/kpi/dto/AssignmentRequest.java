package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Request to assign a user to an organizational unit (tribe/portfolio/squad).
 * The unit is determined by the endpoint path.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRequest {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("unit_id")
    private Long unitId;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    private Boolean active = true;

    @JsonProperty("primary_assignment")
    private Boolean primaryAssignment = false;
}

