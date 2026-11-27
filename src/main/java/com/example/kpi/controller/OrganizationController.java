package com.example.kpi.controller;

import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.OrgTreeNode;
import com.example.kpi.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<OrgTreeNode>>> getTree() {
        return ResponseEntity.ok(ApiResponse.success("Organization tree fetched", organizationService.getTree()));
    }
}
