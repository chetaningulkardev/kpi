package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;

    @JsonProperty("employee_code")
    private String employeeCode;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String email;

    private Set<RoleResponse> roles;

    private Boolean active;

    @JsonProperty("last_login")
    private LocalDateTime lastLogin;

    @JsonProperty("team_id")
    private Long teamId;

    @JsonProperty("team_name")
    private String teamName;

    @JsonProperty("reporting_manager_id")
    private Long reportingManagerId;

    @JsonProperty("reporting_manager_name")
    private String reportingManagerName;

    @JsonProperty("created_date")
    private LocalDateTime createdDate;

    @JsonProperty("updated_date")
    private LocalDateTime updatedDate;
}

