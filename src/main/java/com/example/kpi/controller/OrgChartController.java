package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.OrgChartNode;
import com.example.kpi.service.OrgChartService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hierarchy/tree")
public class OrgChartController {

    private final OrgChartService orgChartService;

    public OrgChartController(OrgChartService orgChartService) {
        this.orgChartService = orgChartService;
    }

    /**
     * Get complete organization hierarchy tree.
     * GET /api/hierarchy/tree
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OrgChartNode>>> getOrgChart() {
        List<OrgChartNode> chart = orgChartService.buildOrgChart();
        return ResponseEntity.ok(ApiResponse.success("Organization chart fetched successfully", chart));
    }

    /**
     * Get hierarchy tree for a specific team.
     * GET /api/hierarchy/tree/team/{teamId}
     */
    @GetMapping("/team/{teamId}")
    public ResponseEntity<ApiResponse<List<OrgChartNode>>> getTeamOrgChart(@PathVariable Long teamId) {
        List<OrgChartNode> chart = orgChartService.buildTeamOrgChart(teamId);
        return ResponseEntity.ok(ApiResponse.success("Team hierarchy fetched successfully", chart));
    }

    /**
     * Get hierarchy tree for a specific user and subordinates.
     * GET /api/hierarchy/tree/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<OrgChartNode>> getUserHierarchy(@PathVariable Long userId) {
        OrgChartNode node = orgChartService.buildSubordinateHierarchy(userId);
        return ResponseEntity.ok(ApiResponse.success("User hierarchy fetched successfully", node));
    }
}
