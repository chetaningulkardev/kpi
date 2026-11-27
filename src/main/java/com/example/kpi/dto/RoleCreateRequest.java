package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreateRequest {

    @NotBlank(message = "Role name cannot be blank")
    @JsonProperty("role_name")
    private String roleName;

    @NotBlank(message = "Role code cannot be blank")
    @JsonProperty("role_code")
    private String roleCode;

    @JsonProperty("parent_role_id")
    private Long parentRoleId;

    private String description;
}

