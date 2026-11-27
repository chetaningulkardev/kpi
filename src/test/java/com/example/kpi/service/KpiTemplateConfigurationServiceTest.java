package com.example.kpi.service;

import com.example.kpi.entity.KpiMaster;
import com.example.kpi.entity.KpiTemplate;
import com.example.kpi.entity.KpiTemplateItem;
import com.example.kpi.repository.KpiMasterRepository;
import com.example.kpi.repository.KpiTemplateItemRepository;
import com.example.kpi.repository.KpiTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiTemplateConfigurationServiceTest {

    @Mock
    private KpiTemplateRepository kpiTemplateRepository;

    @Mock
    private KpiTemplateService kpiTemplateService;

    @Mock
    private KpiTemplateItemRepository kpiTemplateItemRepository;

    @Mock
    private KpiMasterRepository kpiMasterRepository;

    @InjectMocks
    private KpiTemplateConfigurationService service;

    private KpiTemplate template;
    private KpiMaster master;

    @BeforeEach
    void setUp() {
        template = KpiTemplate.builder().id(1L).templateCode("TMP_1").build();
        master = KpiMaster.builder().id(10L).kpiCode("KPI_10").kpiName("Test KPI").build();
    }

    @Test
    void addTemplateItem_shouldCreateItem_whenInputsAreValid() {
        when(kpiTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(kpiMasterRepository.findById(10L)).thenReturn(Optional.of(master));
        when(kpiTemplateItemRepository.findByTemplate(template)).thenReturn(List.of());
        when(kpiTemplateItemRepository.findByTemplateAndActiveTrue(template)).thenReturn(List.of());
        when(kpiTemplateItemRepository.save(any(KpiTemplateItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        KpiTemplateItem created = service.addTemplateItem(1L, 10L, 40.0, "target");

        assertNotNull(created);
        assertEquals(40.0, created.getWeight());
        assertEquals("target", created.getTarget());
        assertEquals(1, created.getSequenceOrder());
        assertTrue(created.getActive());
    }

    @Test
    void addTemplateItem_shouldFail_whenTotalWeightExceeds100() {
        KpiTemplateItem existing = KpiTemplateItem.builder().weight(80.0).active(true).build();

        when(kpiTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(kpiMasterRepository.findById(10L)).thenReturn(Optional.of(master));
        when(kpiTemplateItemRepository.findByTemplate(template)).thenReturn(List.of());
        when(kpiTemplateItemRepository.findByTemplateAndActiveTrue(template)).thenReturn(List.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.addTemplateItem(1L, 10L, 30.5, "target"));

        assertTrue(ex.getMessage().contains("weight"));
    }
}

