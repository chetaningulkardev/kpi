package com.example.kpi.auth;

import com.example.kpi.dto.TeamCreateRequest;
import com.example.kpi.dto.TeamResponse;
import com.example.kpi.entity.Team;
import com.example.kpi.entity.User;
import com.example.kpi.exception.UserNotFoundException;
import com.example.kpi.mapper.TeamMapper;
import com.example.kpi.repository.TeamRepository;
import com.example.kpi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Team Service for managing team operations (CRUD).
 */
@Service
@Transactional
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamMapper teamMapper;

    public TeamService(TeamRepository teamRepository,
                      UserRepository userRepository,
                      TeamMapper teamMapper) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.teamMapper = teamMapper;
    }

    /**
     * Create a new team.
     */
    public TeamResponse createTeam(TeamCreateRequest request) {
        log.info("Creating new team: {}", request.getTeamName());

        if (teamRepository.existsByTeamName(request.getTeamName())) {
            throw new RuntimeException("Team with name '" + request.getTeamName() + "' already exists");
        }

        if (teamRepository.existsByTeamCode(request.getTeamCode())) {
            throw new RuntimeException("Team with code '" + request.getTeamCode() + "' already exists");
        }

        Team team = teamMapper.toEntity(request);
        assignTeamLead(team, request.getTeamLeadId());
        Team createdTeam = teamRepository.save(team);

        log.info("Team created successfully: {}", request.getTeamName());
        return teamMapper.toResponse(createdTeam);
    }

    /**
     * Update team.
     */
    public TeamResponse updateTeam(Long teamId, TeamCreateRequest request) {
        log.info("Updating team with ID: {}", teamId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team with ID '" + teamId + "' not found"));

        teamRepository.findByTeamName(request.getTeamName()).ifPresent(existingTeam -> {
            if (!existingTeam.getId().equals(teamId)) {
                throw new RuntimeException("Team with name '" + request.getTeamName() + "' already exists");
            }
        });

        teamRepository.findByTeamCode(request.getTeamCode()).ifPresent(existingTeam -> {
            if (!existingTeam.getId().equals(teamId)) {
                throw new RuntimeException("Team with code '" + request.getTeamCode() + "' already exists");
            }
        });

        teamMapper.updateEntityFromDto(request, team);
        assignTeamLead(team, request.getTeamLeadId());
        Team updatedTeam = teamRepository.save(team);

        log.info("Team updated successfully with ID: {}", teamId);
        return teamMapper.toResponse(updatedTeam);
    }

    /**
     * Get team by ID.
     */
    @Transactional(readOnly = true)
    public TeamResponse getTeamById(Long teamId) {
        log.info("Fetching team with ID: {}", teamId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team with ID '" + teamId + "' not found"));

        return teamMapper.toResponse(team);
    }

    /**
     * Get all teams.
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getAllTeams() {
        log.info("Fetching all teams");

        List<Team> teams = teamRepository.findAll();
        return teams.stream()
                .map(teamMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get active teams only.
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getActiveTeams() {
        log.info("Fetching active teams");

        List<Team> teams = teamRepository.findByActiveTrue();
        return teams.stream()
                .map(teamMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Enable/Activate team.
     */
    public TeamResponse enableTeam(Long teamId) {
        log.info("Enabling team with ID: {}", teamId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team with ID '" + teamId + "' not found"));

        team.setActive(true);
        Team updatedTeam = teamRepository.save(team);

        log.info("Team enabled successfully with ID: {}", teamId);
        return teamMapper.toResponse(updatedTeam);
    }

    /**
     * Disable team.
     */
    public TeamResponse disableTeam(Long teamId) {
        log.info("Disabling team with ID: {}", teamId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team with ID '" + teamId + "' not found"));

        team.setActive(false);
        Team updatedTeam = teamRepository.save(team);

        log.info("Team disabled successfully with ID: {}", teamId);
        return teamMapper.toResponse(updatedTeam);
    }

    /**
     * Delete team.
     */
    public void deleteTeam(Long teamId) {
        log.info("Deleting team with ID: {}", teamId);

        if (!teamRepository.existsById(teamId)) {
            throw new RuntimeException("Team with ID '" + teamId + "' not found");
        }

        teamRepository.deleteById(teamId);
        log.info("Team deleted successfully with ID: {}", teamId);
    }

    private void assignTeamLead(Team team, Long teamLeadId) {
        if (teamLeadId == null) {
            team.setTeamLead(null);
            return;
        }

        User teamLead = userRepository.findById(teamLeadId)
                .orElseThrow(() -> new UserNotFoundException("User with ID '" + teamLeadId + "' not found"));
        team.setTeamLead(teamLead);
    }
}

