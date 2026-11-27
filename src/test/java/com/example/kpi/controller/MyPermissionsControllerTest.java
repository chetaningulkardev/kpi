package com.example.kpi.controller;

import com.example.kpi.entity.User;
import com.example.kpi.service.AccessControlService;
import com.example.kpi.service.KpiReviewWorkflowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyPermissionsControllerTest {

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"ROLE_ADMIN,true", "ROLE_MANAGER,true", "ROLE_TECH_FOCAL,false",
            "ROLE_PORTFOLIO_LEAD,false", "ROLE_SQUAD_LEAD,false"})
    void scorecardCapabilityMatchesEndpointAuthorities(String authority, boolean expected) {
        User user = new User();
        user.setId(1L);
        user.setEmail("reviewer@example.com");
        KpiReviewWorkflowService workflow = mock(KpiReviewWorkflowService.class);
        AccessControlService access = mock(AccessControlService.class);
        when(workflow.findAuthenticatedUserByEmail(user.getEmail())).thenReturn(user);
        when(access.resolveScope(user)).thenReturn(AccessControlService.ReviewScope.SQUAD);
        when(access.accessibleSquadIds(user)).thenReturn(Set.of(1L));
        when(access.accessibleUserIds(user)).thenReturn(Set.of(1L, 2L));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                        List.of(new SimpleGrantedAuthority(authority))));

        var permissions = new MyPermissionsController(workflow, access).myPermissions().getBody();

        assertTrue(permissions.isCanReviewKpis());
        assertEquals(expected, permissions.isCanManageScorecards());
    }
}
