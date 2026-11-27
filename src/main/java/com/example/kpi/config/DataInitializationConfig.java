package com.example.kpi.config;

import com.example.kpi.entity.KpiCategory;
import com.example.kpi.entity.KpiDataSource;
import com.example.kpi.entity.KpiFrequency;
import com.example.kpi.entity.Role;
import com.example.kpi.repository.KpiCategoryRepository;
import com.example.kpi.repository.KpiDataSourceRepository;
import com.example.kpi.repository.KpiFrequencyRepository;
import com.example.kpi.repository.RoleRepository;
import com.example.kpi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class DataInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DataInitializationConfig.class);

    @Bean
    public ApplicationRunner initDatabase(RoleRepository roleRepository,
                                          UserRepository userRepository,
                                          KpiCategoryRepository kpiCategoryRepository,
                                          KpiDataSourceRepository kpiDataSourceRepository,
                                          KpiFrequencyRepository kpiFrequencyRepository,
                                          PasswordEncoder passwordEncoder,
                                          Environment environment) {
        return args -> {
            log.info("Starting data initialization...");

            // Initialize KPI categories
            initializeKpiCategories(kpiCategoryRepository);

            // Initialize KPI frequencies
            initializeKpiFrequencies(kpiFrequencyRepository);

            // Initialize KPI data sources
            initializeKpiDataSources(kpiDataSourceRepository);

            // Initialize default/system and hierarchy roles
            initializeDefaultRoles(roleRepository);

            // The dev profile supplies its own fictional account set.
            if (!environment.acceptsProfiles(Profiles.of("dev & !prod"))) {
                BootstrapAdministrator.initialize(userRepository, roleRepository, passwordEncoder, environment);
            }


            log.info("Data initialization completed!");
        };
    }

    private void initializeKpiCategories(KpiCategoryRepository kpiCategoryRepository) {
        // Initialize KPI categories with meaningful descriptions
        Map<String, Object[]> categories = new LinkedHashMap<>();
        // categoryCode, {categoryName, description, displayOrder}
        categories.put("DELIVERY", new Object[]{"Delivery", "On-time and reliable delivery of work commitments and sprint goals", 10});
        categories.put("DELIVERY_EXECUTION", new Object[]{"Delivery & Execution", "Quality of execution in delivering planned work and meeting timelines", 20});
        categories.put("DELIVERY_CRAFT", new Object[]{"Delivery & Craft", "Technical excellence and craftsmanship in delivering owned work and deliverables", 30});
        categories.put("QUALITY", new Object[]{"Quality", "General quality metrics and adherence to quality standards", 40});
        categories.put("QUALITY_TECHNICAL_HEALTH", new Object[]{"Quality & Technical Health", "Code quality, system reliability, and technical maintenance practices", 50});
        categories.put("QUALITY_ARCHITECTURE", new Object[]{"Quality & Architecture", "Architectural excellence, design decisions, and system scalability", 60});
        categories.put("QUALITY_TEST_COVERAGE", new Object[]{"Quality & Test Coverage", "Test coverage, testing practices, and quality assurance processes", 70});
        categories.put("RELIABILITY", new Object[]{"Reliability", "System reliability, incident management, and uptime metrics", 80});
        categories.put("INNOVATION", new Object[]{"Innovation", "Innovation initiatives, new technology exploration, and creative problem-solving", 90});
        categories.put("TECHNICAL_LEADERSHIP", new Object[]{"Technical Leadership", "Technical guidance, mentoring, and subject matter expertise", 100});
        categories.put("TECHNICAL_LEADERSHIP_INFLUENCE", new Object[]{"Technical Leadership & Influence", "Technical influence, thought leadership, and cross-team technical direction", 110});
        categories.put("STAKEHOLDER_MANAGEMENT", new Object[]{"Stakeholder Management", "Communication with stakeholders, expectation management, and relationship building", 120});
        categories.put("PEOPLE_TEAM_HEALTH", new Object[]{"People & Team Health", "Team dynamics, morale, psychological safety, and team well-being", 130});
        categories.put("MENTORSHIP_ENABLEMENT", new Object[]{"Mentorship & Enablement", "Mentoring junior team members, knowledge transfer, and team enablement", 140});
        categories.put("COLLABORATION", new Object[]{"Collaboration", "Cross-team collaboration, communication, and teamwork effectiveness", 150});
        categories.put("GROWTH", new Object[]{"Growth", "Personal and professional growth, learning, and skill development", 160});
        categories.put("TECHNICAL_DEBT", new Object[]{"Technical Debt", "Management and reduction of technical debt and legacy system improvements", 170});
        categories.put("LEADERSHIP_SENIOR", new Object[]{"Leadership (Senior only)", "Strategic leadership, decision-making, and organizational impact for senior roles", 180});
        categories.put("PROCESS_DELIVERY_HEALTH", new Object[]{"Process & Delivery Health", "Process efficiency, delivery pipeline, and workflow optimization", 190});
        categories.put("TEAM_FACILITATION_HEALTH", new Object[]{"Team Facilitation & Health", "Team facilitation, retrospectives, communication, and team coordination", 200});
        categories.put("CONTINUOUS_IMPROVEMENT", new Object[]{"Continuous Improvement", "Process improvements, feedback incorporation, and organizational optimization", 210});
        categories.put("COORDINATION", new Object[]{"Coordination", "Cross-team coordination, dependency management, and synchronization", 220});
        categories.put("COACHING", new Object[]{"Coaching", "Coaching and development of team members and peers", 230});
        categories.put("DEFECT_MANAGEMENT", new Object[]{"Defect Management", "Bug identification, tracking, resolution, and quality assurance practices", 240});
        categories.put("RELEASE_READINESS", new Object[]{"Release Readiness", "Release planning, deployment readiness, and release quality metrics", 250});
        categories.put("PROCESS_AUTOMATION", new Object[]{"Process & Automation", "Automation initiatives, tooling, and process automation improvements", 260});

        int createdCount = 0;
        int skippedCount = 0;

        for (Map.Entry<String, Object[]> entry : categories.entrySet()) {
            String categoryCode = entry.getKey();
            Object[] details = entry.getValue();
            String categoryName = (String) details[0];
            String description = (String) details[1];
            Integer displayOrder = (Integer) details[2];

            // Check by code OR by name to avoid duplicates
            if (kpiCategoryRepository.findByCategoryCode(categoryCode).isEmpty()) {
                try {
                    KpiCategory category = KpiCategory.builder()
                            .categoryCode(categoryCode)
                            .categoryName(categoryName)
                            .description(description)
                            .displayOrder(displayOrder)
                            .active(true)
                            .build();
                    kpiCategoryRepository.save(category);
                    createdCount++;
                    log.info("Created KPI category: {} ({})", categoryName, categoryCode);
                } catch (Exception e) {
                    log.warn("Failed to create category {} ({}), likely already exists: {}", categoryName, categoryCode, e.getMessage());
                    skippedCount++;
                }
            } else {
                skippedCount++;
            }
        }

        log.info("KPI categories initialization completed. Created: {}, Skipped (already exists): {}", createdCount, skippedCount);
    }

    private void initializeKpiFrequencies(KpiFrequencyRepository kpiFrequencyRepository) {
        // Initialize KPI frequencies with meaningful descriptions
        Map<String, Object[]> frequencies = new LinkedHashMap<>();
        // frequencyCode, {frequencyName, description, displayOrder}
        frequencies.put("DAILY", new Object[]{"Daily", "Measured and reported on a daily basis", 10});
        frequencies.put("WEEKLY", new Object[]{"Weekly", "Measured and reported every week", 20});
        frequencies.put("BI_WEEKLY", new Object[]{"Bi-Weekly", "Measured and reported every two weeks", 30});
        frequencies.put("MONTHLY", new Object[]{"Monthly", "Measured and reported every month", 40});
        frequencies.put("QUARTERLY", new Object[]{"Quarterly", "Measured and reported every quarter", 50});
        frequencies.put("HALF_YEARLY", new Object[]{"Half-Yearly", "Measured and reported every six months", 60});
        frequencies.put("YEARLY", new Object[]{"Yearly", "Measured and reported annually", 70});
        frequencies.put("PER_SPRINT", new Object[]{"Per Sprint", "Measured and reported per sprint cycle", 80});
        frequencies.put("PER_BUILD", new Object[]{"Per Build", "Measured and reported per build release", 90});
        frequencies.put("PER_RELEASE", new Object[]{"Per Release", "Measured and reported per production release", 100});
        frequencies.put("ON_DEMAND", new Object[]{"On Demand", "Measured and reported as needed or on request", 110});
        frequencies.put("CUSTOM", new Object[]{"Custom", "Custom frequency defined by user or team", 120});

        int createdCount = 0;
        int skippedCount = 0;

        for (Map.Entry<String, Object[]> entry : frequencies.entrySet()) {
            String frequencyCode = entry.getKey();
            Object[] details = entry.getValue();
            String frequencyName = (String) details[0];
            String description = (String) details[1];
            Integer displayOrder = (Integer) details[2];

            // Check by code to avoid duplicates
            if (kpiFrequencyRepository.findByFrequencyCode(frequencyCode).isEmpty()) {
                try {
                    KpiFrequency frequency = KpiFrequency.builder()
                            .frequencyCode(frequencyCode)
                            .frequencyName(frequencyName)
                            .description(description)
                            .displayOrder(displayOrder)
                            .active(true)
                            .build();
                    kpiFrequencyRepository.save(frequency);
                    createdCount++;
                    log.info("Created KPI frequency: {} ({})", frequencyName, frequencyCode);
                } catch (Exception e) {
                    log.warn("Failed to create frequency {} ({}), likely already exists: {}", frequencyName, frequencyCode, e.getMessage());
                    skippedCount++;
                }
            } else {
                skippedCount++;
            }
        }

        log.info("KPI frequencies initialization completed. Created: {}, Skipped (already exists): {}", createdCount, skippedCount);
    }

    private void initializeKpiDataSources(KpiDataSourceRepository kpiDataSourceRepository) {
        Map<String, Object[]> dataSources = new LinkedHashMap<>();
        dataSources.put("SPRINT_TOOL", new Object[]{"Sprint Tool", "Sprint tracking tool used for execution metrics", 10});
        dataSources.put("SPRINT_ROADMAP_TOOL", new Object[]{"Sprint/Roadmap Tool", "Combined sprint and roadmap planning tool", 20});
        dataSources.put("CICD_TICKETING", new Object[]{"CI/CD + Ticketing", "Delivery signals from CI/CD and issue ticketing systems", 30});
        dataSources.put("INCIDENT_TRACKER", new Object[]{"Incident Tracker", "Incident and outage tracking platform", 40});
        dataSources.put("BUG_TRACKER", new Object[]{"Bug Tracker", "Defect lifecycle and bug tracking system", 50});
        dataSources.put("ROADMAP_TOOL", new Object[]{"Roadmap Tool", "Roadmap planning and milestone tracking source", 60});
        dataSources.put("MANAGER_SELF_REPORT", new Object[]{"Manager Self-Report", "Manager provided qualitative and quantitative report", 70});
        dataSources.put("PEER_FEEDBACK", new Object[]{"Peer Feedback", "Feedback collected from peers", 80});
        dataSources.put("MANAGER_JUDGMENT", new Object[]{"Manager Judgment", "Manager assessment based on observed performance", 90});
        dataSources.put("STAKEHOLDER_SURVEY", new Object[]{"Stakeholder Survey", "Structured feedback from stakeholders", 100});
        dataSources.put("MANAGER_STAKEHOLDER_INPUT", new Object[]{"Manager + Stakeholder Input", "Combined input from manager and stakeholders", 110});
        dataSources.put("CI_GIT_PROVIDER", new Object[]{"CI + Git Provider", "Pipeline and source control activity metrics", 120});
        dataSources.put("MANAGER_REVIEW", new Object[]{"Manager Review", "Formal manager review process", 130});
        dataSources.put("JIRA", new Object[]{"Jira", "Issue and project tracking data from Jira", 140});
        dataSources.put("RETRO_TRACKER", new Object[]{"Retro Tracker", "Retrospective actions and follow-up tracking", 150});
        dataSources.put("CI_DASHBOARD", new Object[]{"CI Dashboard", "Build and pipeline health dashboard", 160});
        dataSources.put("CI_TEST_TOOLING", new Object[]{"CI/Test Tooling", "Automated test and CI tooling metrics", 170});
        dataSources.put("RELEASE_TRACKER", new Object[]{"Release Tracker", "Release planning and deployment tracking source", 180});
        dataSources.put("TEST_MANAGEMENT_TOOL", new Object[]{"Test Management Tool", "Test planning, execution, and result tracking", 190});
        dataSources.put("MANAGER_PEER_FEEDBACK", new Object[]{"Manager + Peer Feedback", "Combined manager and peer feedback", 200});
        dataSources.put("GIT_PROVIDER", new Object[]{"Git Provider", "Source control activity and contribution metrics", 210});

        int createdCount = 0;
        int skippedCount = 0;

        for (Map.Entry<String, Object[]> entry : dataSources.entrySet()) {
            String sourceCode = entry.getKey();
            Object[] details = entry.getValue();
            String sourceName = (String) details[0];
            String description = (String) details[1];
            Integer displayOrder = (Integer) details[2];

            if (kpiDataSourceRepository.findBySourceCode(sourceCode).isEmpty()) {
                try {
                    KpiDataSource dataSource = KpiDataSource.builder()
                            .sourceCode(sourceCode)
                            .sourceName(sourceName)
                            .description(description)
                            .displayOrder(displayOrder)
                            .active(true)
                            .build();
                    kpiDataSourceRepository.save(dataSource);
                    createdCount++;
                    log.info("Created KPI data source: {} ({})", sourceName, sourceCode);
                } catch (Exception e) {
                    log.warn("Failed to create data source {} ({}), likely already exists: {}", sourceName, sourceCode, e.getMessage());
                    skippedCount++;
                }
            } else {
                skippedCount++;
            }
        }

        log.info("KPI data sources initialization completed. Created: {}, Skipped (already exists): {}", createdCount, skippedCount);
    }

    private void initializeDefaultRoles(RoleRepository roleRepository) {
        // Seed base system roles used by auth and legacy paths.
        createRoleIfMissing(roleRepository, "ADMIN", "ADMIN", "Administrator with full system access", null);
        createRoleIfMissing(roleRepository, "MANAGER", "MANAGER", "Manager with team oversight capabilities", null);
        createRoleIfMissing(roleRepository, "EMPLOYEE", "EMPLOYEE", "Regular employee with limited access", null);

        // Seed configurable engineering hierarchy roles.
        Map<String, String> engineeringHierarchy = new LinkedHashMap<>();
        engineeringHierarchy.put("TRIBE_LEAD", null);
        engineeringHierarchy.put("PORTFOLIO_LEAD", "TRIBE_LEAD");
        engineeringHierarchy.put("SQUAD_LEAD", "PORTFOLIO_LEAD");
        engineeringHierarchy.put("TECH_FOCAL", "SQUAD_LEAD");
        engineeringHierarchy.put("SENIOR_DEVELOPER", "TECH_FOCAL");
        engineeringHierarchy.put("MID_DEVELOPER", "SENIOR_DEVELOPER");
        engineeringHierarchy.put("JUNIOR_DEVELOPER", "MID_DEVELOPER");

        engineeringHierarchy.put("QA_TECH_FOCAL", "SQUAD_LEAD");
        engineeringHierarchy.put("QA_ENGINEER", "QA_TECH_FOCAL");

        engineeringHierarchy.put("SCRUM_MASTER_TECH_FOCAL", "PORTFOLIO_LEAD");
        engineeringHierarchy.put("SCRUM_MASTER", "SCRUM_MASTER_TECH_FOCAL");

        for (Map.Entry<String, String> entry : engineeringHierarchy.entrySet()) {
            String roleCode = entry.getKey();
            String parentCode = entry.getValue();
            String roleName = roleCode.replace('_', ' ');
            createRoleIfMissing(roleRepository, roleName, roleCode, roleName + " role", parentCode);
        }
    }

    private void createRoleIfMissing(RoleRepository roleRepository,
                                     String roleName,
                                     String roleCode,
                                     String description,
                                     String parentRoleCode) {
        Role role = roleRepository.findByRoleCode(roleCode)
                .or(() -> roleRepository.findByRoleName(roleName))
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setRoleName(roleName);
                    newRole.setRoleCode(roleCode);
                    newRole.setDescription(description);
                    newRole.setActive(true);
                    return roleRepository.save(newRole);
                });

        if (role.getRoleCode() == null || role.getRoleCode().isBlank()) {
            role.setRoleCode(roleCode);
        }

        if (parentRoleCode != null) {
            roleRepository.findByRoleCode(parentRoleCode).ifPresent(role::setParentRole);
        }

        roleRepository.save(role);
        log.info("Ensured role exists: {} ({})", roleName, roleCode);
    }

}
