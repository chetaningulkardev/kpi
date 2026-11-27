package com.example.kpi.service;

import com.example.kpi.entity.Kpi;
import com.example.kpi.entity.KpiAssignment;
import com.example.kpi.entity.KpiMaster;
import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.KpiTemplateItem;
import com.example.kpi.entity.ReviewCycle;
import com.example.kpi.entity.Squad;
import com.example.kpi.entity.User;
import com.example.kpi.entity.UserSquadAssignment;
import com.example.kpi.repository.KPIRepository;
import com.example.kpi.repository.KpiAssignmentRepository;
import com.example.kpi.repository.KpiTemplateItemRepository;
import com.example.kpi.repository.KpiTemplateRepository;
import com.example.kpi.repository.ReviewCycleRepository;
import com.example.kpi.repository.UserPortfolioAssignmentRepository;
import com.example.kpi.repository.UserRepository;
import com.example.kpi.repository.UserSquadAssignmentRepository;
import com.example.kpi.repository.UserTribeAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiGenerationServiceTest {

    @Mock
    private KPIRepository kpiRepository;
    @Mock
    private KpiAssignmentRepository assignmentRepository;
    @Mock
    private KpiTemplateRepository templateRepository;
    @Mock
    private ReviewCycleRepository reviewCycleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSquadAssignmentRepository userSquadAssignmentRepository;
    @Mock
    private UserPortfolioAssignmentRepository userPortfolioAssignmentRepository;
    @Mock
    private UserTribeAssignmentRepository userTribeAssignmentRepository;
    @Mock
    private KpiTemplateItemRepository templateItemRepository;

    @InjectMocks
    private KpiGenerationService service;

    @Test
    void generateKpisForUser_shouldUseMatchingSquadAssignment_notRandomSquadAssignment() {
        User user = new User();
        user.setId(10L);
        user.setActive(true);

        ReviewCycle cycle = ReviewCycle.builder().id(11L).build();

        Squad mySquad = new Squad();
        mySquad.setId(100L);
        Squad otherSquad = new Squad();
        otherSquad.setId(200L);

        UserSquadAssignment userSquadAssignment = new UserSquadAssignment();
        userSquadAssignment.setUser(user);
        userSquadAssignment.setSquad(mySquad);
        userSquadAssignment.setActive(true);

        KpiMaster master = KpiMaster.builder()
                .id(300L)
                .kpiCode("DELIVERY")
                .kpiName("Delivery")
                .active(true)
                .build();

        KpiTemplateItem item = KpiTemplateItem.builder()
                .id(1L)
                .kpiMaster(master)
                .weight(100.0)
                .target("OK")
                .sequenceOrder(1)
                .active(true)
                .build();

        KpiTemplate matchingTemplate = KpiTemplate.builder()
                .id(500L)
                .active(true)
                .status(KpiTemplate.TemplateStatus.ACTIVE)
                .templateItems(Set.of(item))
                .build();

        KpiTemplate wrongTemplate = KpiTemplate.builder()
                .id(501L)
                .active(true)
                .status(KpiTemplate.TemplateStatus.ACTIVE)
                .templateItems(Set.of(item))
                .build();

        KpiAssignment matchingAssignment = KpiAssignment.builder()
                .id(1L)
                .assignmentType(KpiAssignment.AssignmentType.SQUAD)
                .active(true)
                .priority(1)
                .squad(mySquad)
                .template(matchingTemplate)
                .build();

        KpiAssignment wrongAssignment = KpiAssignment.builder()
                .id(2L)
                .assignmentType(KpiAssignment.AssignmentType.SQUAD)
                .active(true)
                .priority(1)
                .squad(otherSquad)
                .template(wrongTemplate)
                .build();

        when(assignmentRepository.findByUserAndActive(user, true)).thenReturn(List.of());
        when(userSquadAssignmentRepository.findByUserIdAndActiveTrue(10L)).thenReturn(List.of(userSquadAssignment));
        when(assignmentRepository.findByAssignmentTypeAndActive(KpiAssignment.AssignmentType.SQUAD, true))
                .thenReturn(List.of(wrongAssignment, matchingAssignment));
        when(kpiRepository.findByReviewCycleAndUser(cycle, user)).thenReturn(List.of());
        // Items are loaded through the repository, not the entity graph, to avoid
        // ConcurrentModificationException during generation.
        when(templateItemRepository.findByTemplate(matchingTemplate)).thenReturn(List.of(item));
        when(kpiRepository.save(any(Kpi.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KpiGenerationService.UserGenerationResult result = service.generateKpisForUser(user, cycle);

        assertEquals(1, result.getCreatedKpis());
        ArgumentCaptor<Kpi> kpiCaptor = ArgumentCaptor.forClass(Kpi.class);
        verify(kpiRepository).save(kpiCaptor.capture());
        assertEquals(500L, kpiCaptor.getValue().getTemplate().getId());
    }

    @Test
    void generateKpisForReviewCycle_shouldBeIdempotent_whenAlreadyGeneratedFlagIsTrue() {
        ReviewCycle cycle = ReviewCycle.builder()
                .id(88L)
                .cycleCode("KPIM2")
                .status(ReviewCycle.CycleStatus.OPEN)
                .kpisGenerated(true)
                .build();

        User user = new User();
        user.setId(7L);
        user.setActive(true);

        when(reviewCycleRepository.findById(88L)).thenReturn(Optional.of(cycle));
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(assignmentRepository.findByUserAndActive(user, true)).thenReturn(List.of());
        when(userSquadAssignmentRepository.findByUserIdAndActiveTrue(7L)).thenReturn(List.of());
        when(userPortfolioAssignmentRepository.findByUserIdAndActiveTrue(7L)).thenReturn(List.of());
        when(userTribeAssignmentRepository.findByUserIdAndActiveTrue(7L)).thenReturn(List.of());
        when(kpiRepository.findByReviewCycle(cycle)).thenReturn(List.of());
        when(reviewCycleRepository.save(any(ReviewCycle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KpiGenerationService.GenerationSummary summary = service.generateKpisForReviewCycle(88L);

        assertEquals(1, summary.getProcessedUsers());
        assertEquals(1, summary.getSkippedUsers());
        assertEquals(0, summary.getCreatedKpis());
        assertEquals(0, summary.getFailedUsers());
        assertTrue(!cycle.getKpisGenerated());
    }
}

