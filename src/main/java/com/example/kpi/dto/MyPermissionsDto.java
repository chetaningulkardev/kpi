package com.example.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Effective capabilities of the signed-in user.
 *
 * <p>The UI renders from this rather than from hardcoded role-name lists, which
 * previously drifted from the seeded roles (for example {@code PORTFOLIO_MANAGER}
 * in the frontend versus {@code PORTFOLIO_LEAD} in the database).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyPermissionsDto {

    private Long userId;
    private String email;
    private String fullName;

    /** Normalised role codes held by the user. */
    private List<String> roles;

    /** Widest org scope: SELF, SQUAD, PORTFOLIO, TRIBE or ORGANIZATION. */
    private String reviewScope;

    private boolean admin;

    /** May open the manager review queue. */
    private boolean canReviewKpis;

    /** May view the team assignment flow. */
    private boolean canViewTeamFlow;

    /** May generate, view and calibrate scorecards. */
    private boolean canManageScorecards;

    /** May reach the admin configuration screens. */
    private boolean canConfigureKpis;

    /** Squads within the user's scope, for client-side filtering. */
    private Set<Long> accessibleSquadIds;

    /** How many people fall inside the user's review scope. */
    private int accessibleUserCount;
}

