package com.example.kpi.service;

import com.example.kpi.entity.KpiMaster;
import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.KpiTemplateItem;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.Role;
import com.example.kpi.entity.User;
import com.example.kpi.repository.KpiTemplateItemRepository;
import com.example.kpi.repository.KpiTemplateRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.repository.RoleRepository;
import com.example.kpi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiAssignmentServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ReviewCycleRepository reviewCycleRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private KpiTemplateRepository templateRepository;
    @Mock
    private KpiGenerationService generationService;
    @Mock
    private KpiTemplateItemRepository templateItemRepository;

    @InjectMocks
    private KpiAssignmentService service;

    @Test
    void generateAssignmentsForRole_shouldFail_whenNoEligibleActiveTemplate() {
        Role role = new Role();
        role.setId(1L);
        role.setRoleCode("DEV");
        role.setRoleName("Developer");

        ReviewCycle cycle = ReviewCycle.builder()
                .id(9L)
                .cycleCode("Q1_2027")
                .status(ReviewCycle.CycleStatus.OPEN)
                .build();

        KpiTemplate invalidTemplate = KpiTemplate.builder()
                .id(12L)
                .status(KpiTemplate.TemplateStatus.ACTIVE)
                .active(true)
                .templateItems(Set.of())
                .build();

        when(reviewCycleRepository.findByCycleCode("Q1_2027")).thenReturn(Optional.of(cycle));
        when(roleRepository.findByRoleCode("DEV")).thenReturn(Optional.of(role));
        when(templateRepository.findByRoleAndStatusAndActiveTrue(role, KpiTemplate.TemplateStatus.ACTIVE))
                .thenReturn(List.of(invalidTemplate));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generateAssignmentsForRole("DEV", "Q1_2027"));

        assertTrue(ex.getMessage().contains("No ACTIVE role template"));
    }

    @Test
    void generateAssignmentsForRole_shouldReturnSummaryFromGenerationService() {
        Role role = new Role();
        role.setId(2L);
        role.setRoleCode("QA");
        role.setRoleName("QA Engineer");

        ReviewCycle cycle = ReviewCycle.builder()
                .id(10L)
                .cycleCode("Q2_2027")
                .status(ReviewCycle.CycleStatus.IN_PROGRESS)
                .build();

        User user = new User();
        user.setId(50L);
        user.setActive(true);
        user.setRoles(Set.of(role));

        KpiMaster master = KpiMaster.builder().id(88L).active(true).build();
        KpiTemplateItem item = KpiTemplateItem.builder()
                .id(77L)
                .kpiMaster(master)
                .weight(100.0)
                .active(true)
                .build();
        KpiTemplate eligibleTemplate = KpiTemplate.builder()
                .id(44L)
                .status(KpiTemplate.TemplateStatus.ACTIVE)
                .active(true)
                .templateItems(Set.of(item))
                .build();

        KpiGenerationService.GenerationSummary expected = new KpiGenerationService.GenerationSummary(6, 1, 0, 0);

        when(reviewCycleRepository.findByCycleCode("Q2_2027")).thenReturn(Optional.of(cycle));
        when(roleRepository.findByRoleCode("QA")).thenReturn(Optional.of(role));
        when(templateRepository.findByRoleAndStatusAndActiveTrue(role, KpiTemplate.TemplateStatus.ACTIVE))
                .thenReturn(List.of(eligibleTemplate));
        // Eligibility reads items through the repository, not the entity graph.
        when(templateItemRepository.findByTemplate(eligibleTemplate)).thenReturn(List.of(item));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(generationService.generateKpisForRole(role, cycle)).thenReturn(expected);

        KpiGenerationService.GenerationSummary result = service.generateAssignmentsForRole("QA", "Q2_2027");

        assertEquals(6, result.getCreatedKpis());
        assertEquals(1, result.getProcessedUsers());
        assertEquals(0, result.getFailedUsers());
    }

    @Test
    void evaluateRoleGenerationReadiness_shouldReportCycleAndTemplateIssues() {
        Role role = new Role();
        role.setId(3L);
        role.setRoleCode("TL");
        role.setRoleName("Tech Lead");

        ReviewCycle cycle = ReviewCycle.builder()
                .id(11L)
                .cycleCode("Q3_2027")
                .status(ReviewCycle.CycleStatus.DRAFT)
                .build();

        KpiMaster master = KpiMaster.builder().id(99L).active(true).build();
        KpiTemplateItem item = KpiTemplateItem.builder()
                .id(88L)
                .kpiMaster(master)
                .weight(40.0)
                .active(true)
                .build();
        KpiTemplate template = KpiTemplate.builder()
                .id(55L)
                .templateCode("TL_TEMPLATE")
                .templateName("TL Template")
                .status(KpiTemplate.TemplateStatus.ACTIVE)
                .active(true)
                .templateItems(Set.of(item))
                .build();

        when(roleRepository.findByRoleCode("TL")).thenReturn(Optional.of(role));
        when(reviewCycleRepository.findByCycleCode("Q3_2027")).thenReturn(Optional.of(cycle));
        when(userRepository.findAll()).thenReturn(List.of());
        when(templateRepository.findByRoleAndStatusAndActiveTrue(role, KpiTemplate.TemplateStatus.ACTIVE))
                .thenReturn(List.of(template));
        // 40% total weight, so the template must be reported as ineligible.
        when(templateItemRepository.findByTemplate(template)).thenReturn(List.of(item));

        KpiAssignmentService.RoleGenerationReadiness readiness =
                service.evaluateRoleGenerationReadiness("TL", "Q3_2027");

        assertTrue(!readiness.isReady());
        assertTrue(readiness.getReasons().stream().anyMatch(r -> r.contains("OPEN or IN_PROGRESS")));
        assertTrue(readiness.getReasons().stream().anyMatch(r -> r.contains("No active users")));
        assertTrue(readiness.getTemplates().stream().anyMatch(t -> !t.isEligible()));
    }
}

