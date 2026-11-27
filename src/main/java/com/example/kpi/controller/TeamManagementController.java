package com.example.kpi.controller;

import com.example.kpi.auth.TeamService;
import com.example.kpi.dto.ApiResponse;
import com.example.kpi.dto.TeamCreateRequest;
import com.example.kpi.dto.TeamResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamManagementController {

    private final TeamService teamService;

    public TeamManagementController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamResponse>> createTeam(@Valid @RequestBody TeamCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Team created successfully", teamService.createTeam(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamResponse>>> getAllTeams() {
        return ResponseEntity.ok(ApiResponse.success("Teams fetched successfully", teamService.getAllTeams()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> getActiveTeams() {
        return ResponseEntity.ok(ApiResponse.success("Active teams fetched successfully", teamService.getActiveTeams()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> getTeamById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Team fetched successfully", teamService.getTeamById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> updateTeam(@PathVariable Long id,
                                                                @Valid @RequestBody TeamCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Team updated successfully", teamService.updateTeam(id, request)));
    }

    @PatchMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<TeamResponse>> enableTeam(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Team enabled successfully", teamService.enableTeam(id)));
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<TeamResponse>> disableTeam(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Team disabled successfully", teamService.disableTeam(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.ok(ApiResponse.success("Team deleted successfully", "deleted"));
    }
}
