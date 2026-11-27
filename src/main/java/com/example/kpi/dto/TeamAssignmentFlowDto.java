package com.example.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamAssignmentFlowDto {
    private String transitionKey;
    private RoleRef fromRole;
    private RoleRef toRole;
    private int pendingUsers;
    private int pendingKpis;
    private List<UserRequestSummary> requests = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleRef {
        private String roleCode;
        private String roleName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserRequestSummary {
        private Long userId;
        private String employeeName;
        private String email;
        private int pendingKpis;
        private String cycleCodes;
        private String statuses;
        private Long reviewerId;
        private String reviewerName;
    }
}

