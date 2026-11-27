package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    @NotBlank(message = "Employee code cannot be blank")
    @JsonProperty("employee_code")
    private String employeeCode;

    @NotBlank(message = "First name cannot be blank")
    @JsonProperty("first_name")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank")
    @JsonProperty("last_name")
    private String lastName;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotEmpty(message = "At least one role must be assigned")
    private Set<String> roleNames;

    @JsonProperty("team_id")
    private Long teamId;

    @JsonProperty("reporting_manager_id")
    private Long reportingManagerId;
}

