package com.example.kpi.service;

import com.example.kpi.entity.Portfolio;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.Squad;
import com.example.kpi.entity.Tribe;
import com.example.kpi.entity.User;
import com.example.kpi.entity.UserPortfolioAssignment;
import com.example.kpi.entity.UserSquadAssignment;
import com.example.kpi.entity.UserTribeAssignment;
import com.example.kpi.repository.PortfolioRepository;
import com.example.kpi.repository.SquadRepository;
import com.example.kpi.repository.TribeRepository;
import com.example.kpi.repository.UserPortfolioAssignmentRepository;
import com.example.kpi.repository.UserRepository;
import com.example.kpi.repository.UserSquadAssignmentRepository;
import com.example.kpi.repository.UserTribeAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Covers the org-hierarchy visibility rules: a Squad Lead must reach their Tech
 * Focal, and a Tribe Lead must reach everyone beneath the tribe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessControlServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TribeRepository tribeRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private SquadRepository squadRepository;
    @Mock private UserTribeAssignmentRepository tribeAssignmentRepository;
    @Mock private UserPortfolioAssignmentRepository portfolioAssignmentRepository;
    @Mock private UserSquadAssignmentRepository squadAssignmentRepository;

    @InjectMocks
    private AccessControlService service;

    private User user(Long id, String... roleCodes) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@example.invalid");
        for (String code : roleCodes) {
            Role r = new Role();
            r.setRoleCode(code);
            r.setRoleName(code);
            u.getRoles().add(r);
        }
        return u;
    }

    /** No org membership anywhere — the default for a plain employee. */
    private void noOrgLinks() {
        when(tribeRepository.findTribeIdsLedBy(anyLong())).thenReturn(Set.of());
        when(portfolioRepository.findPortfolioIdsLedBy(anyLong())).thenReturn(Set.of());
        when(squadRepository.findSquadIdsLedBy(anyLong())).thenReturn(Set.of());
        when(tribeAssignmentRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(portfolioAssignmentRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(squadAssignmentRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(tribeAssignmentRepository.findByUserIdAndActiveTrue(anyLong())).thenReturn(List.of());
        when(portfolioAssignmentRepository.findByUserIdAndActiveTrue(anyLong())).thenReturn(List.of());
        when(squadAssignmentRepository.findByUserIdAndActiveTrue(anyLong())).thenReturn(List.of());
        when(squadRepository.findMemberIdsBySquadIds(any())).thenReturn(Set.of());
        when(squadRepository.findAllById(any())).thenReturn(List.of());
    }

    // ── Scope resolution ──────────────────────────────────────────────────────

    @Test
    void resolveScope_shouldReturnSelf_forPlainEmployee() {
        noOrgLinks();
        assertEquals(AccessControlService.ReviewScope.SELF,
                service.resolveScope(user(1L, "DEVELOPER")));
    }

    @Test
    void resolveScope_shouldReturnSquad_forSquadLead() {
        noOrgLinks();
        when(squadRepository.findSquadIdsLedBy(2L)).thenReturn(Set.of(100L));
        assertEquals(AccessControlService.ReviewScope.SQUAD,
                service.resolveScope(user(2L, "SQUAD_LEAD")));
    }

    @Test
    void resolveScope_shouldReturnSquad_forActiveSquadAssignment() {
        noOrgLinks();
        Squad squad = new Squad();
        squad.setId(100L);
        UserSquadAssignment assignment = new UserSquadAssignment();
        assignment.setSquad(squad);
        assignment.setActive(true);
        when(squadAssignmentRepository.findByUserIdAndActiveTrue(2L)).thenReturn(List.of(assignment));

        assertEquals(AccessControlService.ReviewScope.SQUAD,
                service.resolveScope(user(2L, "TECH_FOCAL")));
    }

    @Test
    void canReviewEmployee_shouldAllowActiveSquadAssigneeToReviewSquadMembers() {
        noOrgLinks();
        Squad squad = new Squad();
        squad.setId(100L);
        UserSquadAssignment assignment = new UserSquadAssignment();
        assignment.setSquad(squad);
        assignment.setActive(true);
        when(squadAssignmentRepository.findByUserIdAndActiveTrue(2L)).thenReturn(List.of(assignment));
        when(squadRepository.findMemberIdsBySquadIds(Set.of(100L))).thenReturn(Set.of(50L));

        assertTrue(service.canReviewEmployee(user(2L, "TECH_FOCAL"), user(50L, "DEVELOPER")));
    }

    @Test
    void resolveScope_shouldReturnTribe_forTribeLead() {
        noOrgLinks();
        when(tribeRepository.findTribeIdsLedBy(3L)).thenReturn(Set.of(7L));
        assertEquals(AccessControlService.ReviewScope.TRIBE,
                service.resolveScope(user(3L, "TRIBE_LEAD")));
    }

    @Test
    void resolveScope_shouldReturnOrganization_forAdmin() {
        assertEquals(AccessControlService.ReviewScope.ORGANIZATION,
                service.resolveScope(user(4L, "ADMIN")));
    }

    @Test
    void resolveScope_shouldIgnoreRoleNameWithoutAssignment() {
        // A stale SQUAD_LEAD role with no squad must not grant visibility.
        noOrgLinks();
        assertEquals(AccessControlService.ReviewScope.SELF,
                service.resolveScope(user(5L, "SQUAD_LEAD")));
    }

    // ── The reported gap: Squad Lead → Tech Focal ─────────────────────────────

    @Test
    void canReviewEmployee_squadLeadShouldReachTechFocalInSameSquad() {
        noOrgLinks();
        User squadLead = user(2L, "SQUAD_LEAD");
        User techFocal = user(50L, "TECH_FOCAL");   // no reportingManager set

        when(squadRepository.findSquadIdsLedBy(2L)).thenReturn(Set.of(100L));
        when(squadRepository.findMemberIdsBySquadIds(Set.of(100L))).thenReturn(Set.of(50L, 51L));

        assertTrue(service.canReviewEmployee(squadLead, techFocal),
                "squad lead must reach a squad member even without a reporting line");
    }

    @Test
    void canReviewEmployee_tribeLeadShouldReachEmployeeInNestedSquad() {
        noOrgLinks();
        User tribeLead = user(3L, "TRIBE_LEAD");
        User developer = user(70L, "DEVELOPER");

        when(tribeRepository.findTribeIdsLedBy(3L)).thenReturn(Set.of(7L));
        when(portfolioRepository.findPortfolioIdsByTribeIds(Set.of(7L))).thenReturn(Set.of(20L, 21L));
        when(squadRepository.findSquadIdsByPortfolioIds(Set.of(20L, 21L))).thenReturn(Set.of(100L, 101L));
        when(squadRepository.findMemberIdsBySquadIds(Set.of(100L, 101L))).thenReturn(Set.of(70L));

        assertTrue(service.canReviewEmployee(tribeLead, developer),
                "tribe scope must expand through portfolios down to squads");
    }

    @Test
    void canReviewEmployee_shouldIncludeSquadLeadsNotCarriedAsMembers() {
        noOrgLinks();
        User tribeLead = user(3L, "TRIBE_LEAD");
        User squadLead = user(80L, "SQUAD_LEAD");

        Squad squad = new Squad();
        squad.setId(100L);
        squad.setLeads(Set.of(squadLead));

        when(tribeRepository.findTribeIdsLedBy(3L)).thenReturn(Set.of(7L));
        when(portfolioRepository.findPortfolioIdsByTribeIds(Set.of(7L))).thenReturn(Set.of(20L));
        when(squadRepository.findSquadIdsByPortfolioIds(Set.of(20L))).thenReturn(Set.of(100L));
        when(squadRepository.findMemberIdsBySquadIds(Set.of(100L))).thenReturn(Set.of());
        when(squadRepository.findAllById(Set.of(100L))).thenReturn(List.of(squad));

        assertTrue(service.canReviewEmployee(tribeLead, squadLead),
                "a squad lead is not always a squad member and must still be reachable");
    }

    // ── Guard rails ───────────────────────────────────────────────────────────

    @Test
    void canReviewEmployee_shouldRejectUnrelatedEmployee() {
        noOrgLinks();
        User squadLead = user(2L, "SQUAD_LEAD");
        User stranger = user(90L, "DEVELOPER");

        when(squadRepository.findSquadIdsLedBy(2L)).thenReturn(Set.of(100L));
        when(squadRepository.findMemberIdsBySquadIds(Set.of(100L))).thenReturn(Set.of(50L));

        assertFalse(service.canReviewEmployee(squadLead, stranger));
    }

    @Test
    void canReviewEmployee_shouldRejectSelfReview() {
        noOrgLinks();
        User lead = user(2L, "SQUAD_LEAD");
        when(squadRepository.findSquadIdsLedBy(2L)).thenReturn(Set.of(100L));
        when(squadRepository.findMemberIdsBySquadIds(Set.of(100L))).thenReturn(Set.of(2L));

        // The lead is inside their own accessible set; that must not authorise
        // rating their own KPI.
        assertFalse(service.canReviewEmployee(lead, lead));
    }

    @Test
    void canReviewEmployee_shouldHonourDirectReportingLine() {
        noOrgLinks();
        User manager = user(2L, "MANAGER");
        User report = user(60L, "DEVELOPER");
        report.setReportingManager(manager);

        assertTrue(service.canReviewEmployee(manager, report),
                "the reporting line must still work with no org assignments");
    }

    @Test
    void canReviewEmployee_adminShouldReachAnyone() {
        assertTrue(service.canReviewEmployee(user(1L, "ADMIN"), user(999L, "DEVELOPER")));
    }

    @Test
    void canReviewEmployee_shouldRejectNulls() {
        assertFalse(service.canReviewEmployee(null, user(1L)));
        assertFalse(service.canReviewEmployee(user(1L), null));
    }

    // ── Squad expansion ───────────────────────────────────────────────────────

    @Test
    void accessibleSquadIds_shouldCombineMembershipAndLeadership() {
        noOrgLinks();
        User u = user(2L, "SQUAD_LEAD");

        Squad assigned = new Squad();
        assigned.setId(200L);
        UserSquadAssignment assignment = new UserSquadAssignment();
        assignment.setSquad(assigned);

        when(squadAssignmentRepository.findByUserIdAndActiveTrue(2L)).thenReturn(List.of(assignment));
        when(squadRepository.findSquadIdsLedBy(2L)).thenReturn(Set.of(100L));

        Set<Long> ids = service.accessibleSquadIds(u);
        assertTrue(ids.contains(100L));
        assertTrue(ids.contains(200L));
        assertEquals(2, ids.size());
    }

    @Test
    void accessibleSquadIds_shouldIgnoreInactiveAssignmentsAtEveryHierarchyLevel() {
        noOrgLinks();

        Squad oldSquad = new Squad();
        oldSquad.setId(100L);
        UserSquadAssignment inactiveSquadAssignment = new UserSquadAssignment();
        inactiveSquadAssignment.setSquad(oldSquad);
        inactiveSquadAssignment.setActive(false);

        Portfolio oldPortfolio = new Portfolio();
        oldPortfolio.setId(200L);
        UserPortfolioAssignment inactivePortfolioAssignment = new UserPortfolioAssignment();
        inactivePortfolioAssignment.setPortfolio(oldPortfolio);
        inactivePortfolioAssignment.setActive(false);

        Tribe oldTribe = new Tribe();
        oldTribe.setId(300L);
        UserTribeAssignment inactiveTribeAssignment = new UserTribeAssignment();
        inactiveTribeAssignment.setTribe(oldTribe);
        inactiveTribeAssignment.setActive(false);

        when(squadAssignmentRepository.findByUserId(2L)).thenReturn(List.of(inactiveSquadAssignment));
        when(portfolioAssignmentRepository.findByUserId(2L)).thenReturn(List.of(inactivePortfolioAssignment));
        when(tribeAssignmentRepository.findByUserId(2L)).thenReturn(List.of(inactiveTribeAssignment));
        when(squadRepository.findSquadIdsByPortfolioIds(Set.of(200L))).thenReturn(Set.of(201L));
        when(portfolioRepository.findPortfolioIdsByTribeIds(Set.of(300L))).thenReturn(Set.of(301L));
        when(squadRepository.findSquadIdsByPortfolioIds(Set.of(301L))).thenReturn(Set.of(302L));

        assertTrue(service.accessibleSquadIds(user(2L, "DEVELOPER")).isEmpty());
    }

    @Test
    void accessibleUserIds_shouldAlwaysContainSelf() {
        noOrgLinks();
        assertTrue(service.accessibleUserIds(user(42L, "DEVELOPER")).contains(42L));
    }
}
