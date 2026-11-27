package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic node for the organization hierarchy tree
 * (Tribe -> Portfolio -> Squad -> Member).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrgTreeNode {

    private Long id;
    private String name;
    private String code;
    private String type; // TRIBE | PORTFOLIO | SQUAD | MEMBER
    private Boolean active;

    @JsonProperty("role_name")
    private String roleName;

    private List<OrgTreeNode> children = new ArrayList<>();

    public OrgTreeNode(Long id, String name, String code, String type, Boolean active) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.type = type;
        this.active = active;
        this.children = new ArrayList<>();
    }
}

