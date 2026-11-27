package com.example.kpi.auth;

import com.example.kpi.dto.RoleCreateRequest;
import com.example.kpi.dto.RoleResponse;
import com.example.kpi.entity.Role;
import com.example.kpi.exception.RoleNotFoundException;
import com.example.kpi.mapper.RoleMapper;
import com.example.kpi.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Role Service for managing role operations (CRUD).
 */
@Service
@Transactional
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public RoleService(RoleRepository roleRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    /**
     * Create a new role.
     */
    public RoleResponse createRole(RoleCreateRequest request) {
        log.info("Creating new role: {}", request.getRoleName());

        if (roleRepository.existsByRoleName(request.getRoleName())) {
            log.warn("Role with name already exists: {}", request.getRoleName());
            throw new RuntimeException("Role with name '" + request.getRoleName() + "' already exists");
        }

        if (roleRepository.existsByRoleCode(request.getRoleCode())) {
            log.warn("Role with code already exists: {}", request.getRoleCode());
            throw new RuntimeException("Role with code '" + request.getRoleCode() + "' already exists");
        }

        Role role = roleMapper.toEntity(request);
        assignParentRole(role, request.getParentRoleId());
        Role createdRole = roleRepository.save(role);

        log.info("Role created successfully: {}", request.getRoleName());
        return roleMapper.toResponse(createdRole);
    }

    /**
     * Update role.
     */
    public RoleResponse updateRole(Long roleId, RoleCreateRequest request) {
        log.info("Updating role with ID: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role with ID '" + roleId + "' not found"));

        // Check if new role name already exists (and is not the same role)
        roleRepository.findByRoleName(request.getRoleName()).ifPresent(existingRole -> {
            if (!existingRole.getId().equals(roleId)) {
                throw new RuntimeException("Role with name '" + request.getRoleName() + "' already exists");
            }
        });

        roleRepository.findByRoleCode(request.getRoleCode()).ifPresent(existingRole -> {
            if (!existingRole.getId().equals(roleId)) {
                throw new RuntimeException("Role with code '" + request.getRoleCode() + "' already exists");
            }
        });

        roleMapper.updateEntityFromDto(request, role);
        assignParentRole(role, request.getParentRoleId());
        validateNoRoleCycle(role);
        Role updatedRole = roleRepository.save(role);

        log.info("Role updated successfully with ID: {}", roleId);
        return roleMapper.toResponse(updatedRole);
    }

    /**
     * Get role by ID.
     */
    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long roleId) {
        log.info("Fetching role with ID: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role with ID '" + roleId + "' not found"));

        return roleMapper.toResponse(role);
    }

    /**
     * Get all roles.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        log.info("Fetching all roles");

        List<Role> roles = roleRepository.findAll();
        return roles.stream()
                .map(roleMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get roles in hierarchy-friendly order (parent first).
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> getRoleHierarchy() {
        log.info("Fetching role hierarchy");

        List<Role> roles = roleRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getParentRole() == null && b.getParentRole() != null) {
                        return -1;
                    }
                    if (a.getParentRole() != null && b.getParentRole() == null) {
                        return 1;
                    }
                    return a.getRoleName().compareToIgnoreCase(b.getRoleName());
                })
                .collect(Collectors.toList());

        return roles.stream().map(roleMapper::toResponse).collect(Collectors.toList());
    }

    /**
     * Get active roles only.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> getActiveRoles() {
        log.info("Fetching all active roles");

        List<Role> roles = roleRepository.findAll().stream()
                .filter(Role::getActive)
                .collect(Collectors.toList());

        return roles.stream()
                .map(roleMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Enable/Activate role.
     */
    public RoleResponse enableRole(Long roleId) {
        log.info("Enabling role with ID: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role with ID '" + roleId + "' not found"));

        role.setActive(true);
        Role updatedRole = roleRepository.save(role);

        log.info("Role enabled successfully with ID: {}", roleId);
        return roleMapper.toResponse(updatedRole);
    }

    /**
     * Disable role.
     */
    public RoleResponse disableRole(Long roleId) {
        log.info("Disabling role with ID: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role with ID '" + roleId + "' not found"));

        role.setActive(false);
        Role updatedRole = roleRepository.save(role);

        log.info("Role disabled successfully with ID: {}", roleId);
        return roleMapper.toResponse(updatedRole);
    }

    /**
     * Delete role.
     */
    public void deleteRole(Long roleId) {
        log.info("Deleting role with ID: {}", roleId);

        if (!roleRepository.existsById(roleId)) {
            throw new RoleNotFoundException("Role with ID '" + roleId + "' not found");
        }

        roleRepository.deleteById(roleId);
        log.info("Role deleted successfully with ID: {}", roleId);
    }

    /**
     * Get role by name.
     */
    @Transactional(readOnly = true)
    public RoleResponse getRoleByName(String roleName) {
        log.info("Fetching role by name: {}", roleName);

        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new RoleNotFoundException("Role '" + roleName + "' not found"));

        return roleMapper.toResponse(role);
    }

    private void assignParentRole(Role role, Long parentRoleId) {
        if (parentRoleId == null) {
            role.setParentRole(null);
            return;
        }

        if (role.getId() != null && role.getId().equals(parentRoleId)) {
            throw new RuntimeException("Role cannot be its own parent");
        }

        Role parentRole = roleRepository.findById(parentRoleId)
                .orElseThrow(() -> new RoleNotFoundException("Parent role with ID '" + parentRoleId + "' not found"));
        role.setParentRole(parentRole);
    }

    private void validateNoRoleCycle(Role role) {
        Role current = role.getParentRole();
        while (current != null) {
            if (role.getId() != null && role.getId().equals(current.getId())) {
                throw new RuntimeException("Invalid hierarchy: cycle detected for role '" + role.getRoleName() + "'");
            }
            current = current.getParentRole();
        }
    }
}

