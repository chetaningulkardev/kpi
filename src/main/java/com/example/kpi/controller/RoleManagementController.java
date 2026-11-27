package com.example.kpi.controller;

import com.example.kpi.auth.RoleService;
import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.RoleCreateRequest;
import com.example.kpi.dto.RoleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleManagementController {

    private final RoleService roleService;

    public RoleManagementController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", roleService.createRole(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.success("Roles fetched successfully", roleService.getAllRoles()));
    }

    @GetMapping("/hierarchy")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoleHierarchy() {
        return ResponseEntity.ok(ApiResponse.success("Role hierarchy fetched successfully", roleService.getRoleHierarchy()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Role fetched successfully", roleService.getRoleById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(@PathVariable Long id,
                                                                @Valid @RequestBody RoleCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", roleService.updateRole(id, request)));
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<RoleResponse>> enableRole(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Role enabled successfully", roleService.enableRole(id)));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<RoleResponse>> disableRole(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Role disabled successfully", roleService.disableRole(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully", "deleted"));
    }
}
