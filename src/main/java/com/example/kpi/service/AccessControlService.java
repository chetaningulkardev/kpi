package com.example.kpi.service;

import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the set of organizational units a user can access based on
 * Role + Organizational Assignment.
 *
 * Rules:
 * - Tribe Lead / Tribe assignment -> access to tribe, all its portfolios, all squads under them.
 * - Portfolio Lead / Portfolio assignment -> access to portfolio, all squads under it.
 * - Squad Lead / Tech Focal / Squad assignment -> access to that squad only.
 * - Others -> own data only (no org-wide access).
 */
@Service
@Transactional(readOnly = true)
public class AccessControlService {

    private final UserRepository userRepository;
    private final TribeRepository tribeRepository;
    private final PortfolioRepository portfolioRepository;
    private final SquadRepository squadRepository;
    private final UserTribeAssignmentRepository tribeAssignmentRepository;
    private final UserPortfolioAssignmentRepository portfolioAssignmentRepository;
    private final UserSquadAssignmentRepository squadAssignmentRepository;

    public AccessControlService(UserRepository userRepository,
                                TribeRepository tribeRepository,
                                PortfolioRepository portfolioRepository,
                                SquadRepository squadRepository,
                                UserTribeAssignmentRepository tribeAssignmentRepository,
                                UserPortfolioAssignmentRepository portfolioAssignmentRepository,
                                UserSquadAssignmentRepository squadAssignmentRepository) {
        this.userRepository = userRepository;
        this.tribeRepository = tribeRepository;
        this.portfolioRepository = portfolioRepository;
        this.squadRepository = squadRepository;
        this.tribeAssignmentRepository = tribeAssignmentRepository;
        this.portfolioAssignmentRepository = portfolioAssignmentRepository;
        this.squadAssignmentRepository = squadAssignmentRepository;
    }

    /** Breadth of the organisation a user can act across. Ordered narrow to wide. */
    public enum ReviewScope {
        SELF, SQUAD, PORTFOLIO, TRIBE, ORGANIZATION
    }

    private static final Set<String> ADMIN_CODES =
            Set.of("ADMIN", "ROLE_ADMIN", "SYSTEM_ADMINISTRATOR");

    public boolean isAdmin(User user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .filter(Objects::nonNull)
                .anyMatch(r -> ADMIN_CODES.contains(normalize(r.getRoleCode()))
                        || ADMIN_CODES.contains(normalize(r.getRoleName())));
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * The widest scope this user holds, derived from real assignments and
     * leadership rather than role names. A stale role without a matching
     * assignment therefore grants nothing.
     */
    public ReviewScope resolveScope(User user) {
        if (user == null || user.getId() == null) {
            return ReviewScope.SELF;
        }
        if (isAdmin(user)) {
            return ReviewScope.ORGANIZATION;
        }
        Long userId = user.getId();

        if (!tribeRepository.findTribeIdsLedBy(userId).isEmpty()
                || !tribeAssignmentRepository.findByUserIdAndActiveTrue(userId).isEmpty()) {
            return ReviewScope.TRIBE;
        }
        if (!portfolioRepository.findPortfolioIdsLedBy(userId).isEmpty()
                || !portfolioAssignmentRepository.findByUserIdAndActiveTrue(userId).isEmpty()) {
            return ReviewScope.PORTFOLIO;
        }
        if (!squadRepository.findSquadIdsLedBy(userId).isEmpty()
                || !squadAssignmentRepository.findByUserIdAndActiveTrue(userId).isEmpty()) {
            return ReviewScope.SQUAD;
        }
        return ReviewScope.SELF;
    }

    /** All squad IDs a user can access, expanding tribe/portfolio assignments downward. */
    public Set<Long> accessibleSquadIds(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        return user == null ? Set.of() : accessibleSquadIds(user);
    }

    /** Overload that avoids a redundant lookup when the caller already has the user. */
    public Set<Long> accessibleSquadIds(User user) {
        if (user == null || user.getId() == null) {
            return Set.of();
        }
        if (isAdmin(user)) {
            return squadRepository.findAll().stream()
                    .map(Squad::getId).collect(Collectors.toSet());
        }

        Long userId = user.getId();
        Set<Long> squadIds = new HashSet<>();

        // Direct squad membership plus squad leadership.
        squadAssignmentRepository.findByUserIdAndActiveTrue(userId)
                .forEach(a -> { if (a.getSquad() != null) squadIds.add(a.getSquad().getId()); });
        squadIds.addAll(squadRepository.findSquadIdsLedBy(userId));

        // Portfolio membership/leadership.
        Set<Long> portfolioIds = new HashSet<>();
        portfolioAssignmentRepository.findByUserIdAndActiveTrue(userId)
                .forEach(a -> { if (a.getPortfolio() != null) portfolioIds.add(a.getPortfolio().getId()); });
        portfolioIds.addAll(portfolioRepository.findPortfolioIdsLedBy(userId));

        // Tribe membership/leadership expands down to portfolios.
        Set<Long> tribeIds = new HashSet<>();
        tribeAssignmentRepository.findByUserIdAndActiveTrue(userId)
                .forEach(a -> { if (a.getTribe() != null) tribeIds.add(a.getTribe().getId()); });
        tribeIds.addAll(tribeRepository.findTribeIdsLedBy(userId));

        if (!tribeIds.isEmpty()) {
            portfolioIds.addAll(portfolioRepository.findPortfolioIdsByTribeIds(tribeIds));
        }
        if (!portfolioIds.isEmpty()) {
            squadIds.addAll(squadRepository.findSquadIdsByPortfolioIds(portfolioIds));
        }
        return squadIds;
    }

    /** IDs of users the given user can access (members of accessible squads + self). */
    public Set<Long> accessibleUserIds(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        return user == null ? Set.of() : accessibleUserIds(user);
    }

    /** Overload that avoids a redundant lookup when the caller already has the user. */
    public Set<Long> accessibleUserIds(User user) {
        if (user == null || user.getId() == null) {
            return Set.of();
        }
        Set<Long> userIds = new HashSet<>();
        userIds.add(user.getId());

        Set<Long> squadIds = accessibleSquadIds(user);
        if (!squadIds.isEmpty()) {
            userIds.addAll(squadRepository.findMemberIdsBySquadIds(squadIds));
            // Squad leads are not always carried as squad members, so add them
            // explicitly; otherwise a Tribe Lead cannot review their Squad Leads.
            squadRepository.findAllById(squadIds).forEach(squad -> {
                if (squad.getLeads() != null) {
                    squad.getLeads().stream()
                            .filter(Objects::nonNull)
                            .forEach(lead -> userIds.add(lead.getId()));
                }
            });
        }
        return userIds;
    }

    public boolean canAccessSquad(String email, Long squadId) {
        return accessibleSquadIds(email).contains(squadId);
    }

    /**
     * Whether {@code reviewer} may act on {@code employee}.
     *
     * <p>Combines the direct reporting line with org-structure scope, so a Squad
     * Lead reaches their Tech Focal and a Tribe Lead reaches everyone beneath —
     * neither of which the reporting-manager link alone can express.</p>
     */
    public boolean canReviewEmployee(User reviewer, User employee) {
        if (reviewer == null || employee == null
                || reviewer.getId() == null || employee.getId() == null) {
            return false;
        }
        if (isAdmin(reviewer)) {
            return true;
        }
        // Reviewing your own KPI is never a management action.
        if (reviewer.getId().equals(employee.getId())) {
            return false;
        }
        User manager = employee.getReportingManager();
        if (manager != null && manager.getId() != null && reviewer.getId().equals(manager.getId())) {
            return true;
        }
        return accessibleUserIds(reviewer).contains(employee.getId());
    }
}
