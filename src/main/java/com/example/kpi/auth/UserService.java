package com.example.kpi.auth;

import com.example.kpi.dto.ChangePasswordRequest;
import com.example.kpi.dto.UserCreateRequest;
import com.example.kpi.dto.UserResponse;
import com.example.kpi.dto.UserUpdateRequest;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.Team;
import com.example.kpi.entity.User;
import com.example.kpi.exception.InvalidCredentialsException;
import com.example.kpi.exception.RoleNotFoundException;
import com.example.kpi.exception.UserAlreadyExistsException;
import com.example.kpi.exception.UserNotFoundException;
import com.example.kpi.mapper.UserMapper;
import com.example.kpi.repository.RoleRepository;
import com.example.kpi.repository.TeamRepository;
import com.example.kpi.repository.UserRepository;
import com.example.kpi.repository.UserTribeAssignmentRepository;
import com.example.kpi.repository.UserPortfolioAssignmentRepository;
import com.example.kpi.repository.UserSquadAssignmentRepository;
import com.example.kpi.repository.TribeRepository;
import com.example.kpi.repository.PortfolioRepository;
import com.example.kpi.repository.SquadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Service for managing user operations (CRUD, password change, etc.).
 */
@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TeamRepository teamRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserTribeAssignmentRepository tribeAssignmentRepository;
    private final UserPortfolioAssignmentRepository portfolioAssignmentRepository;
    private final UserSquadAssignmentRepository squadAssignmentRepository;
    private final TribeRepository tribeRepository;
    private final PortfolioRepository portfolioRepository;
    private final SquadRepository squadRepository;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       TeamRepository teamRepository,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       UserTribeAssignmentRepository tribeAssignmentRepository,
                       UserPortfolioAssignmentRepository portfolioAssignmentRepository,
                       UserSquadAssignmentRepository squadAssignmentRepository,
                       TribeRepository tribeRepository,
                       PortfolioRepository portfolioRepository,
                       SquadRepository squadRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.teamRepository = teamRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tribeAssignmentRepository = tribeAssignmentRepository;
        this.portfolioAssignmentRepository = portfolioAssignmentRepository;
        this.squadAssignmentRepository = squadAssignmentRepository;
        this.tribeRepository = tribeRepository;
        this.portfolioRepository = portfolioRepository;
        this.squadRepository = squadRepository;
    }

    /**
     * Create a new user.
     */
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Validate unique constraints
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("User with email already exists: {}", request.getEmail());
            throw new UserAlreadyExistsException("User with email '" + request.getEmail() + "' already exists");
        }

        if (userRepository.existsByEmployeeCode(request.getEmployeeCode())) {
            log.warn("User with employee code already exists: {}", request.getEmployeeCode());
            throw new UserAlreadyExistsException("User with employee code '" + request.getEmployeeCode() + "' already exists");
        }

        // Create user entity
        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Assign roles
        Set<Role> roles = request.getRoleNames().stream()
                .map(roleName -> roleRepository.findByRoleName(roleName)
                        .orElseThrow(() -> new RoleNotFoundException("Role '" + roleName + "' not found")))
                .collect(Collectors.toSet());
        user.setRoles(roles);

        // Assign team if provided
        assignTeam(user, request.getTeamId());

        // Assign reporting manager if provided
        assignReportingManager(user, request.getReportingManagerId());

        User createdUser = userRepository.save(user);
        log.info("User created successfully with email: {}", request.getEmail());

        return userMapper.toResponse(createdUser);
    }

    /**
     * Update user details.
     */
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        log.info("Updating user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID '" + userId + "' not found"));

        userMapper.updateEntityFromDto(request, user);

        // Update roles if provided
        if (request.getRoleNames() != null && !request.getRoleNames().isEmpty()) {
            Set<Role> roles = request.getRoleNames().stream()
                    .map(roleName -> roleRepository.findByRoleName(roleName)
                            .orElseThrow(() -> new RoleNotFoundException("Role '" + roleName + "' not found")))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        // Update team if provided
        if (request.getTeamId() != null) {
            assignTeam(user, request.getTeamId());
        }

        // Update reporting manager if provided
        if (request.getReportingManagerId() != null) {
            assignReportingManager(user, request.getReportingManagerId());
        }

        User updatedUser = userRepository.save(user);
        log.info("User updated successfully with ID: {}", userId);

        return userMapper.toResponse(updatedUser);
    }

    /**
     * Change user password.
     */
    public void changePassword(ChangePasswordRequest request) {
        log.info("Changing password for current user");

        // Get current user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("Invalid current password for user: {}", email);
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        // Validate new password confirmation
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            log.warn("Password confirmation does not match for user: {}", email);
            throw new InvalidCredentialsException("New password and confirmation do not match");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", email);
    }

    /**
     * Get user by ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        log.info("Fetching user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID '" + userId + "' not found"));

        return userMapper.toResponse(user);
    }

    /**
     * Get all users.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.info("Fetching all users");

        List<User> users = userRepository.findAll();
        return users.stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get active users only.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getActiveUsers() {
        log.info("Fetching all active users");

        List<User> users = userRepository.findByActiveTrue();
        return users.stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Search users by name or email.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(String searchTerm) {
        log.info("Searching users with term: {}", searchTerm);

        List<User> users = userRepository.searchUsers(searchTerm);
        return users.stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Activate user.
     */
    public UserResponse activateUser(Long userId) {
        log.info("Activating user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID '" + userId + "' not found"));

        user.setActive(true);
        User updatedUser = userRepository.save(user);

        log.info("User activated successfully with ID: {}", userId);
        return userMapper.toResponse(updatedUser);
    }

    /**
     * Deactivate user.
     */
    public UserResponse deactivateUser(Long userId) {
        log.info("Deactivating user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID '" + userId + "' not found"));

        user.setActive(false);
        User updatedUser = userRepository.save(user);

        log.info("User deactivated successfully with ID: {}", userId);
        return userMapper.toResponse(updatedUser);
    }

    /**
     * Delete user.
     */
    public void deleteUser(Long userId) {
        log.info("Deleting user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID '" + userId + "' not found"));

        // Clean up organizational assignments referencing this user
        tribeAssignmentRepository.deleteByUserId(userId);
        portfolioAssignmentRepository.deleteByUserId(userId);
        squadAssignmentRepository.deleteByUserId(userId);

        // Remove the user from any lead memberships (tribe/portfolio/squad leads)
        tribeRepository.findAll().forEach(t -> {
            if (t.getLeads() != null && t.getLeads().removeIf(u -> u.getId().equals(userId))) {
                tribeRepository.save(t);
            }
        });
        portfolioRepository.findAll().forEach(p -> {
            if (p.getLeads() != null && p.getLeads().removeIf(u -> u.getId().equals(userId))) {
                portfolioRepository.save(p);
            }
        });
        squadRepository.findAll().forEach(s -> {
            if (s.getLeads() != null && s.getLeads().removeIf(u -> u.getId().equals(userId))) {
                squadRepository.save(s);
            }
        });

        userRepository.delete(user);
        log.info("User deleted successfully with ID: {}", userId);
    }

    /**
     * Get current user.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        log.info("Fetching current user profile");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Current user not found"));

        return userMapper.toResponse(user);
    }

    private void assignTeam(User user, Long teamId) {
        if (teamId == null) {
            user.setTeam(null);
            return;
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team with ID '" + teamId + "' not found"));
        user.setTeam(team);
    }

    private void assignReportingManager(User user, Long reportingManagerId) {
        if (reportingManagerId == null) {
            user.setReportingManager(null);
            return;
        }

        if (user.getId() != null && user.getId().equals(reportingManagerId)) {
            throw new RuntimeException("User cannot be their own reporting manager");
        }

        User manager = userRepository.findById(reportingManagerId)
                .orElseThrow(() -> new UserNotFoundException("Reporting manager with ID '" + reportingManagerId + "' not found"));

        validateReportingHierarchy(user, manager);
        user.setReportingManager(manager);
    }

    private void validateReportingHierarchy(User user, User reportingManager) {
        User current = reportingManager.getReportingManager();
        while (current != null) {
            if (user.getId() != null && user.getId().equals(current.getId())) {
                throw new RuntimeException("Invalid hierarchy: reporting relationship would create a cycle");
            }
            current = current.getReportingManager();
        }
    }
}

