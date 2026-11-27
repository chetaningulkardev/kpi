package com.example.kpi.service;

import com.example.kpi.dto.OrgChartNode;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.User;
import com.example.kpi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for building organization hierarchy chart.
 */
@Service
@Transactional(readOnly = true)
public class OrgChartService {

    private static final Logger log = LoggerFactory.getLogger(OrgChartService.class);

    private final UserRepository userRepository;

    public OrgChartService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Build complete organization hierarchy tree for all users.
     */
    public List<OrgChartNode> buildOrgChart() {
        log.info("Building complete organization chart");

        List<User> allUsers = userRepository.findAll();
        Map<Long, OrgChartNode> nodeMap = new HashMap<>();
        List<OrgChartNode> rootNodes = new ArrayList<>();

        // First pass: create all nodes
        for (User user : allUsers) {
            OrgChartNode node = toOrgChartNode(user);
            nodeMap.put(user.getId(), node);
        }

        // Second pass: build hierarchy
        for (User user : allUsers) {
            OrgChartNode node = nodeMap.get(user.getId());

            if (user.getReportingManager() != null) {
                OrgChartNode parentNode = nodeMap.get(user.getReportingManager().getId());
                if (parentNode != null) {
                    parentNode.addChild(node);
                }
            } else {
                rootNodes.add(node);
            }
        }

        return rootNodes;
    }

    /**
     * Build hierarchy tree for a specific team.
     */
    public List<OrgChartNode> buildTeamOrgChart(Long teamId) {
        log.info("Building organization chart for team ID: {}", teamId);

        List<User> teamUsers = userRepository.findAll().stream()
                .filter(u -> u.getTeam() != null && u.getTeam().getId().equals(teamId))
                .collect(Collectors.toList());

        Map<Long, OrgChartNode> nodeMap = new HashMap<>();
        List<OrgChartNode> rootNodes = new ArrayList<>();

        // First pass: create all nodes
        for (User user : teamUsers) {
            OrgChartNode node = toOrgChartNode(user);
            nodeMap.put(user.getId(), node);
        }

        // Second pass: build hierarchy within team
        for (User user : teamUsers) {
            OrgChartNode node = nodeMap.get(user.getId());

            if (user.getReportingManager() != null && nodeMap.containsKey(user.getReportingManager().getId())) {
                OrgChartNode parentNode = nodeMap.get(user.getReportingManager().getId());
                parentNode.addChild(node);
            } else {
                rootNodes.add(node);
            }
        }

        return rootNodes;
    }

    /**
     * Build hierarchy tree for a specific user and their direct reports.
     */
    public OrgChartNode buildSubordinateHierarchy(Long userId) {
        log.info("Building subordinate hierarchy for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<Long, OrgChartNode> nodeMap = new HashMap<>();
        List<User> subordinates = getAllSubordinates(user);

        // Add the user and all subordinates
        subordinates.add(0, user);

        for (User subordinate : subordinates) {
            nodeMap.put(subordinate.getId(), toOrgChartNode(subordinate));
        }

        // Build hierarchy
        OrgChartNode rootNode = nodeMap.get(user.getId());
        for (User subordinate : subordinates) {
            if (!subordinate.getId().equals(user.getId()) && subordinate.getReportingManager() != null) {
                OrgChartNode parentNode = nodeMap.get(subordinate.getReportingManager().getId());
                if (parentNode != null) {
                    parentNode.addChild(nodeMap.get(subordinate.getId()));
                }
            }
        }

        return rootNode;
    }

    private List<User> getAllSubordinates(User manager) {
        List<User> allSubordinates = new ArrayList<>();
        List<User> directReports = userRepository.findAll().stream()
                .filter(u -> u.getReportingManager() != null && u.getReportingManager().getId().equals(manager.getId()))
                .collect(Collectors.toList());

        for (User report : directReports) {
            allSubordinates.add(report);
            allSubordinates.addAll(getAllSubordinates(report));
        }

        return allSubordinates;
    }

    private OrgChartNode toOrgChartNode(User user) {
        OrgChartNode node = new OrgChartNode();
        node.setId(user.getId());
        node.setUserId(user.getId());
        node.setFullName(user.getFullName());
        node.setEmail(user.getEmail());
        node.setEmployeeCode(user.getEmployeeCode());

        // Get primary role
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            Role primaryRole = user.getRoles().stream().findFirst().orElse(null);
            if (primaryRole != null) {
                node.setRoleName(primaryRole.getRoleName());
                node.setRoleCode(primaryRole.getRoleCode());
            }
        }

        // Get team
        if (user.getTeam() != null) {
            node.setTeamName(user.getTeam().getTeamName());
            node.setTeamCode(user.getTeam().getTeamCode());
        }

        return node;
    }
}

