package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;

    @JsonProperty("role_name")
    private String roleName;

    @JsonProperty("role_code")
    private String roleCode;

    @JsonProperty("parent_role_id")
    private Long parentRoleId;

    @JsonProperty("parent_role_name")
    private String parentRoleName;

    @JsonProperty("parent_role_code")
    private String parentRoleCode;

    private String description;

    private Boolean active;
}

