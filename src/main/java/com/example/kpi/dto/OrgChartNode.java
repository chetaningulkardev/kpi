package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for organization hierarchy tree nodes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrgChartNode {

    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("full_name")
    private String fullName;

    private String email;

    @JsonProperty("employee_code")
    private String employeeCode;

    @JsonProperty("role_name")
    private String roleName;

    @JsonProperty("role_code")
    private String roleCode;

    @JsonProperty("team_name")
    private String teamName;

    @JsonProperty("team_code")
    private String teamCode;

    private List<OrgChartNode> children = new ArrayList<>();

    public void addChild(OrgChartNode child) {
        this.children.add(child);
    }
}

