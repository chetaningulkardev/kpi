package com.example.kpi.config;

import com.example.kpi.dto.ManagerReviewRequest;
import com.example.kpi.dto.SelfAssessmentRequest;
import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import com.example.kpi.service.KpiGenerationService;
import com.example.kpi.service.KpiReviewWorkflowService;
import com.example.kpi.service.KpiScoringService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Profile("dev & !prod")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Welcome2026!";
    public static final String ADMIN_EMAIL = "john.doe@example.com";
    private static final String MANAGER_EMAIL = "maya.chen@example.com";
    private static final String CYCLE_CODE = "FY26_Q4";

    private static final List<RoleSeed> ROLES = List.of(
            new RoleSeed("ADMIN", "ADMIN"),
            new RoleSeed("MANAGER", "MANAGER"),
            new RoleSeed("EMPLOYEE", "EMPLOYEE"),
            new RoleSeed("TRIBE_LEAD", "TRIBE LEAD"),
            new RoleSeed("PORTFOLIO_LEAD", "PORTFOLIO LEAD"),
            new RoleSeed("SQUAD_LEAD", "SQUAD LEAD"),
            new RoleSeed("TECH_FOCAL", "TECH FOCAL"),
            new RoleSeed("SENIOR_DEVELOPER", "SENIOR DEVELOPER"),
            new RoleSeed("MID_DEVELOPER", "MID DEVELOPER"),
            new RoleSeed("JUNIOR_DEVELOPER", "JUNIOR DEVELOPER"),
            new RoleSeed("QA_TECH_FOCAL", "QA TECH FOCAL"),
            new RoleSeed("QA_ENGINEER", "QA ENGINEER"),
            new RoleSeed("SCRUM_MASTER_TECH_FOCAL", "SCRUM MASTER TECH FOCAL"),
            new RoleSeed("SCRUM_MASTER", "SCRUM MASTER")
    );

    private static final List<TribeSeed> TRIBES = List.of(
            new TribeSeed("CUSTOMER", "Customer Experience",
                    "Digital experiences and support tools for fictional customers."),
            new TribeSeed("ENGINEERING", "Engineering Platforms",
                    "Secure, reliable foundations and developer tools."),
            new TribeSeed("OPERATIONS", "Business Operations",
                    "Workforce insights and internal workflow products.")
    );

    private static final List<PortfolioSeed> PORTFOLIOS = List.of(
            new PortfolioSeed("JOURNEYS", "Digital Customer Journeys",
                    "Web and mobile experiences across the customer lifecycle.", "CUSTOMER"),
            new PortfolioSeed("CARE", "Customer Care Technology",
                    "Digital tools and automation for customer support teams.", "CUSTOMER"),
            new PortfolioSeed("CLOUD", "Cloud Foundations",
                    "Secure, resilient cloud infrastructure and shared services.", "ENGINEERING"),
            new PortfolioSeed("DEVELOPER", "Developer Enablement",
                    "Tooling and delivery practices that help teams ship reliable software.", "ENGINEERING"),
            new PortfolioSeed("WORKFORCE", "Workforce Insights",
                    "Fictional people analytics and workforce planning products.", "OPERATIONS"),
            new PortfolioSeed("AUTOMATION", "Enterprise Automation",
                    "Workflow automation and operational reporting.", "OPERATIONS")
    );

    private static final List<SquadSeed> SQUADS = List.of(
            new SquadSeed("JOURNEY_INSIGHTS", "Journey Insights", "Customer journey analytics.", "JOURNEYS"),
            new SquadSeed("MOBILE_EXPERIENCE", "Mobile Experience", "Mobile app experience and accessibility.", "JOURNEYS"),
            new SquadSeed("CONTACT_CENTER", "Contact Center Tools", "Agent-facing customer care tools.", "CARE"),
            new SquadSeed("SERVICE_AUTOMATION", "Service Automation", "Customer support self-service.", "CARE"),
            new SquadSeed("CLOUD_RELIABILITY", "Cloud Reliability", "Observability, resilience, and service health.", "CLOUD"),
            new SquadSeed("PLATFORM_SECURITY", "Platform Security", "Identity, security controls, and platform assurance.", "CLOUD"),
            new SquadSeed("DEVELOPER_TOOLING", "Developer Tooling", "Developer experience and code quality.", "DEVELOPER"),
            new SquadSeed("CICD_ENABLEMENT", "CI/CD Enablement", "Shared continuous delivery capabilities.", "DEVELOPER"),
            new SquadSeed("PEOPLE_ANALYTICS", "People Analytics", "Workforce dashboards and people data insights.", "WORKFORCE"),
            new SquadSeed("TALENT_PLANNING", "Talent Planning", "Fictional skills forecasting and workforce planning.", "WORKFORCE"),
            new SquadSeed("WORKFLOW_AUTOMATION", "Workflow Automation", "Repeatable internal business processes.", "AUTOMATION"),
            new SquadSeed("OPERATIONS_REPORTING", "Operations Reporting", "Operational dashboards and service metrics.", "AUTOMATION")
    );

    private static final List<TeamSeed> TEAMS = List.of(
            new TeamSeed("JOURNEYS", "Digital Journeys Team", "leila.haddad@example.com"),
            new TeamSeed("CARE", "Customer Care Team", "priya.nair@example.com"),
            new TeamSeed("CLOUD", "Cloud Foundations Team", "ethan.park@example.com"),
            new TeamSeed("DEVELOPER", "Developer Enablement Team", "sofia.alvarez@example.com"),
            new TeamSeed("WORKFORCE", "Workforce Insights Team", "lucas.martin@example.com"),
            new TeamSeed("AUTOMATION", "Enterprise Automation Team", "zoe.carter@example.com")
    );

    private static final List<UserSeed> USERS = List.of(
            new UserSeed("KPI-1001", "John", "Doe", ADMIN_EMAIL, "ADMIN", null, null, null, null, null),
            new UserSeed("KPI-1002", "Maya", "Chen", MANAGER_EMAIL, "MANAGER",
                    "CUSTOMER", "JOURNEYS", "MOBILE_EXPERIENCE", "JOURNEYS", null),
            new UserSeed("KPI-1003", "Alex", "Rivera", "alex.rivera@example.com", "EMPLOYEE",
                    "CUSTOMER", "JOURNEYS", "JOURNEY_INSIGHTS", "JOURNEYS", MANAGER_EMAIL),
            new UserSeed("KPI-1004", "Priya", "Nair", "priya.nair@example.com", "TRIBE_LEAD",
                    "CUSTOMER", "CARE", "CONTACT_CENTER", "CARE", MANAGER_EMAIL),
            new UserSeed("KPI-1005", "Noah", "Bennett", "noah.bennett@example.com", "PORTFOLIO_LEAD",
                    "CUSTOMER", "JOURNEYS", "JOURNEY_INSIGHTS", "JOURNEYS", MANAGER_EMAIL),
            new UserSeed("KPI-1006", "Leila", "Haddad", "leila.haddad@example.com", "SQUAD_LEAD",
                    "CUSTOMER", "JOURNEYS", "JOURNEY_INSIGHTS", "JOURNEYS", MANAGER_EMAIL),
            new UserSeed("KPI-1007", "Ethan", "Park", "ethan.park@example.com", "TECH_FOCAL",
                    "ENGINEERING", "CLOUD", "CLOUD_RELIABILITY", "CLOUD", MANAGER_EMAIL),
            new UserSeed("KPI-1008", "Sofia", "Alvarez", "sofia.alvarez@example.com", "SENIOR_DEVELOPER",
                    "ENGINEERING", "DEVELOPER", "DEVELOPER_TOOLING", "DEVELOPER", MANAGER_EMAIL),
            new UserSeed("KPI-1009", "Owen", "Brooks", "owen.brooks@example.com", "MID_DEVELOPER",
                    "ENGINEERING", "DEVELOPER", "CICD_ENABLEMENT", "DEVELOPER", MANAGER_EMAIL),
            new UserSeed("KPI-1010", "Ava", "Patel", "ava.patel@example.com", "JUNIOR_DEVELOPER",
                    "ENGINEERING", "DEVELOPER", "DEVELOPER_TOOLING", "DEVELOPER", MANAGER_EMAIL),
            new UserSeed("KPI-1011", "Daniel", "Kim", "daniel.kim@example.com", "QA_TECH_FOCAL",
                    "ENGINEERING", "CLOUD", "PLATFORM_SECURITY", "CLOUD", MANAGER_EMAIL),
            new UserSeed("KPI-1012", "Grace", "Wilson", "grace.wilson@example.com", "QA_ENGINEER",
                    "CUSTOMER", "CARE", "SERVICE_AUTOMATION", "CARE", MANAGER_EMAIL),
            new UserSeed("KPI-1013", "Lucas", "Martin", "lucas.martin@example.com", "SCRUM_MASTER_TECH_FOCAL",
                    "OPERATIONS", "WORKFORCE", "PEOPLE_ANALYTICS", "WORKFORCE", MANAGER_EMAIL),
            new UserSeed("KPI-1014", "Zoe", "Carter", "zoe.carter@example.com", "SCRUM_MASTER",
                    "OPERATIONS", "AUTOMATION", "WORKFLOW_AUTOMATION", "AUTOMATION", MANAGER_EMAIL),
            new UserSeed("KPI-1015", "Evelyn", "Hart", "evelyn.hart@example.com", "EMPLOYEE",
                    "CUSTOMER", "CARE", "SERVICE_AUTOMATION", "CARE", MANAGER_EMAIL),
            new UserSeed("KPI-1016", "Samir", "Shah", "samir.shah@example.com", "EMPLOYEE",
                    "ENGINEERING", "CLOUD", "CLOUD_RELIABILITY", "CLOUD", MANAGER_EMAIL),
            new UserSeed("KPI-1017", "Mia", "Turner", "mia.turner@example.com", "EMPLOYEE",
                    "OPERATIONS", "WORKFORCE", "TALENT_PLANNING", "WORKFORCE", MANAGER_EMAIL),
            new UserSeed("KPI-1018", "Theo", "Green", "theo.green@example.com", "EMPLOYEE",
                    "OPERATIONS", "AUTOMATION", "OPERATIONS_REPORTING", "AUTOMATION", MANAGER_EMAIL)
    );

    private static final List<MasterSeed> KPI_MASTERS = List.of(
            new MasterSeed("DELIVERY", "Sprint Commitments Delivered", "SPRINT_DELIVERY",
                    "Planned sprint work completed within the sprint.", "PERCENTAGE", "%", ">= 90%"),
            new MasterSeed("QUALITY", "Automated Test Coverage", "AUTOMATED_TEST_COVERAGE",
                    "Automated regression coverage for owned services.", "PERCENTAGE", "%", ">= 85%"),
            new MasterSeed("COLLABORATION", "Knowledge Sharing and Mentoring", "KNOWLEDGE_SHARING",
                    "Practical knowledge sharing, pairing, and team enablement.", "NUMBER", "sessions", "At least 3 sessions")
    );

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TribeRepository tribeRepository;
    private final PortfolioRepository portfolioRepository;
    private final SquadRepository squadRepository;
    private final TeamRepository teamRepository;
    private final UserTribeAssignmentRepository tribeAssignmentRepository;
    private final UserPortfolioAssignmentRepository portfolioAssignmentRepository;
    private final UserSquadAssignmentRepository squadAssignmentRepository;
    private final KpiCategoryRepository categoryRepository;
    private final KpiFrequencyRepository frequencyRepository;
    private final KpiMasterRepository masterRepository;
    private final KpiTemplateRepository templateRepository;
    private final KpiTemplateItemRepository templateItemRepository;
    private final KpiAssignmentRepository kpiAssignmentRepository;
    private final ReviewCycleRepository reviewCycleRepository;
    private final KPIRepository kpiRepository;
    private final KpiStatusHistoryRepository statusHistoryRepository;
    private final KpiScorecardRepository scorecardRepository;
    private final KpiGenerationService generationService;
    private final KpiReviewWorkflowService reviewWorkflowService;
    private final KpiScoringService scoringService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(RoleRepository roleRepository,
                          UserRepository userRepository,
                          TribeRepository tribeRepository,
                          PortfolioRepository portfolioRepository,
                          SquadRepository squadRepository,
                          TeamRepository teamRepository,
                          UserTribeAssignmentRepository tribeAssignmentRepository,
                          UserPortfolioAssignmentRepository portfolioAssignmentRepository,
                          UserSquadAssignmentRepository squadAssignmentRepository,
                          KpiCategoryRepository categoryRepository,
                          KpiFrequencyRepository frequencyRepository,
                          KpiMasterRepository masterRepository,
                          KpiTemplateRepository templateRepository,
                          KpiTemplateItemRepository templateItemRepository,
                          KpiAssignmentRepository kpiAssignmentRepository,
                          ReviewCycleRepository reviewCycleRepository,
                          KPIRepository kpiRepository,
                          KpiStatusHistoryRepository statusHistoryRepository,
                          KpiScorecardRepository scorecardRepository,
                          KpiGenerationService generationService,
                          KpiReviewWorkflowService reviewWorkflowService,
                          KpiScoringService scoringService,
                          PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.tribeRepository = tribeRepository;
        this.portfolioRepository = portfolioRepository;
        this.squadRepository = squadRepository;
        this.teamRepository = teamRepository;
        this.tribeAssignmentRepository = tribeAssignmentRepository;
        this.portfolioAssignmentRepository = portfolioAssignmentRepository;
        this.squadAssignmentRepository = squadAssignmentRepository;
        this.categoryRepository = categoryRepository;
        this.frequencyRepository = frequencyRepository;
        this.masterRepository = masterRepository;
        this.templateRepository = templateRepository;
        this.templateItemRepository = templateItemRepository;
        this.kpiAssignmentRepository = kpiAssignmentRepository;
        this.reviewCycleRepository = reviewCycleRepository;
        this.kpiRepository = kpiRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.scorecardRepository = scorecardRepository;
        this.generationService = generationService;
        this.reviewWorkflowService = reviewWorkflowService;
        this.scoringService = scoringService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Role> roles = seedRoles();
        Map<String, Tribe> tribes = seedTribes();
        Map<String, Portfolio> portfolios = seedPortfolios(tribes);
        Map<String, Squad> squads = seedSquads(portfolios);
        Map<String, User> users = seedUsers(roles);
        Map<String, Team> teams = seedTeams(users);
        seedOrganizationAssignments(tribes, portfolios, squads, teams, users);
        Map<String, KpiCategory> categories = seedCategories();
        KpiFrequency frequency = seedFrequency();
        Map<String, KpiMaster> masters = seedKpiMasters(categories, frequency);
        seedTemplatesAndRoleAssignments(roles, masters);
        seedKpiWorkflow(users);
    }

    private Map<String, Role> seedRoles() {
        Map<String, Role> result = new HashMap<>();
        for (RoleSeed seed : ROLES) {
            Role role = roleRepository.findByRoleCode(seed.code()).orElseGet(() -> {
                Role created = new Role();
                created.setRoleCode(seed.code());
                created.setRoleName(seed.name());
                created.setDescription(seed.name() + " permissions for the sample organization.");
                created.setActive(true);
                return roleRepository.save(created);
            });
            if (!seed.name().equals(role.getRoleName())) {
                role.setRoleName(seed.name());
                role = roleRepository.save(role);
            }
            result.put(seed.code(), role);
        }
        return result;
    }

    private Map<String, Tribe> seedTribes() {
        Map<String, Tribe> result = new HashMap<>();
        for (TribeSeed seed : TRIBES) {
            Tribe tribe = tribeRepository.findByTribeCode(seed.code()).orElseGet(() -> {
                Tribe created = new Tribe();
                created.setTribeCode(seed.code());
                created.setTribeName(seed.name());
                created.setDescription(seed.description());
                created.setActive(true);
                return tribeRepository.save(created);
            });
            result.put(seed.code(), tribe);
        }
        return result;
    }

    private Map<String, Portfolio> seedPortfolios(Map<String, Tribe> tribes) {
        Map<String, Portfolio> result = new HashMap<>();
        for (PortfolioSeed seed : PORTFOLIOS) {
            Portfolio portfolio = portfolioRepository.findByPortfolioCode(seed.code()).orElseGet(() -> {
                Portfolio created = new Portfolio();
                created.setPortfolioCode(seed.code());
                created.setPortfolioName(seed.name());
                created.setDescription(seed.description());
                created.setTribe(tribes.get(seed.tribeCode()));
                created.setActive(true);
                return portfolioRepository.save(created);
            });
            result.put(seed.code(), portfolio);
        }
        return result;
    }

    private Map<String, Squad> seedSquads(Map<String, Portfolio> portfolios) {
        Map<String, Squad> result = new HashMap<>();
        for (SquadSeed seed : SQUADS) {
            Squad squad = squadRepository.findBySquadCode(seed.code()).orElseGet(() -> {
                Squad created = new Squad();
                created.setSquadCode(seed.code());
                created.setSquadName(seed.name());
                created.setDescription(seed.description());
                created.setPortfolio(portfolios.get(seed.portfolioCode()));
                created.setActive(true);
                return squadRepository.save(created);
            });
            result.put(seed.code(), squad);
        }
        return result;
    }

    private Map<String, User> seedUsers(Map<String, Role> roles) {
        Map<String, User> result = new HashMap<>();
        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);
        for (UserSeed seed : USERS) {
            User user = userRepository.findByEmail(seed.email()).orElseGet(() -> {
                User created = new User();
                created.setEmployeeCode(seed.employeeCode());
                created.setFirstName(seed.firstName());
                created.setLastName(seed.lastName());
                created.setEmail(seed.email());
                created.setPassword(encodedPassword);
                created.setActive(true);
                created.setRoles(new HashSet<>(Set.of(roles.get(seed.roleCode()))));
                return userRepository.save(created);
            });
            if (!seed.employeeCode().equals(user.getEmployeeCode())
                    || !seed.firstName().equals(user.getFirstName())
                    || !seed.lastName().equals(user.getLastName())) {
                throw new IllegalStateException("Sample email belongs to a different account: " + seed.email());
            }
            if (user.getPassword() == null || !passwordEncoder.matches(DEMO_PASSWORD, user.getPassword())) {
                user.setPassword(encodedPassword);
            }
            if (!Boolean.TRUE.equals(user.getActive())) {
                user.setActive(true);
            }
            Role expectedRole = roles.get(seed.roleCode());
            if (user.getRoles() == null) {
                user.setRoles(new HashSet<>());
            }
            boolean hasExpectedRole = user.getRoles().stream()
                    .anyMatch(role -> role.getId().equals(expectedRole.getId()));
            if (!hasExpectedRole) user.getRoles().add(expectedRole);
            user = userRepository.save(user);
            result.put(seed.email(), user);
        }
        return result;
    }

    private Map<String, Team> seedTeams(Map<String, User> users) {
        Map<String, Team> result = new HashMap<>();
        for (TeamSeed seed : TEAMS) {
            Team team = teamRepository.findByTeamCode(seed.code()).orElseGet(() -> {
                Team created = new Team();
                created.setTeamCode(seed.code());
                created.setTeamName(seed.name());
                created.setDescription("Fictional team within " + seed.name() + ".");
                created.setTeamLead(users.get(seed.leadEmail()));
                created.setActive(true);
                return teamRepository.save(created);
            });
            result.put(seed.code(), team);
        }
        return result;
    }

    private void seedOrganizationAssignments(Map<String, Tribe> tribes,
                                             Map<String, Portfolio> portfolios,
                                             Map<String, Squad> squads,
                                             Map<String, Team> teams,
                                             Map<String, User> users) {
        LocalDate today = LocalDate.now();
        for (UserSeed seed : USERS) {
            if (seed.tribeCode() == null) {
                continue;
            }
            User user = users.get(seed.email());
            user.setTeam(teams.get(seed.teamCode()));
            if (seed.managerEmail() != null) {
                user.setReportingManager(users.get(seed.managerEmail()));
            }
            userRepository.save(user);

            Tribe tribe = tribes.get(seed.tribeCode());
            Portfolio portfolio = portfolios.get(seed.portfolioCode());
            Squad squad = squads.get(seed.squadCode());
            ensureTribeAssignment(user, tribe, today);
            ensurePortfolioAssignment(user, portfolio, today);
            ensureSquadAssignment(user, squad, today);

            switch (seed.roleCode()) {
                case "TRIBE_LEAD" -> tribe.getLeads().add(user);
                case "PORTFOLIO_LEAD" -> portfolio.getLeads().add(user);
                case "SQUAD_LEAD" -> squad.getLeads().add(user);
                default -> { }
            }
        }
        tribes.values().forEach(tribeRepository::save);
        portfolios.values().forEach(portfolioRepository::save);
        squads.values().forEach(squadRepository::save);
    }

    private void ensureTribeAssignment(User user, Tribe tribe, LocalDate date) {
        if (!tribeAssignmentRepository.existsByUserIdAndTribeId(user.getId(), tribe.getId())) {
            UserTribeAssignment assignment = new UserTribeAssignment();
            assignment.setUser(user);
            assignment.setTribe(tribe);
            assignment.setStartDate(date);
            assignment.setActive(true);
            assignment.setPrimaryAssignment(true);
            tribeAssignmentRepository.save(assignment);
        }
    }

    private void ensurePortfolioAssignment(User user, Portfolio portfolio, LocalDate date) {
        if (!portfolioAssignmentRepository.existsByUserIdAndPortfolioId(user.getId(), portfolio.getId())) {
            UserPortfolioAssignment assignment = new UserPortfolioAssignment();
            assignment.setUser(user);
            assignment.setPortfolio(portfolio);
            assignment.setStartDate(date);
            assignment.setActive(true);
            assignment.setPrimaryAssignment(true);
            portfolioAssignmentRepository.save(assignment);
        }
    }

    private void ensureSquadAssignment(User user, Squad squad, LocalDate date) {
        if (!squadAssignmentRepository.existsByUserIdAndSquadId(user.getId(), squad.getId())) {
            UserSquadAssignment assignment = new UserSquadAssignment();
            assignment.setUser(user);
            assignment.setSquad(squad);
            assignment.setStartDate(date);
            assignment.setActive(true);
            assignment.setPrimaryAssignment(true);
            squadAssignmentRepository.save(assignment);
        }
    }

    private Map<String, KpiCategory> seedCategories() {
        Map<String, KpiCategory> result = new HashMap<>();
        for (MasterSeed seed : KPI_MASTERS) {
            KpiCategory category = categoryRepository.findByCategoryCode(seed.categoryCode())
                    .orElseGet(() -> {
                        KpiCategory created = KpiCategory.builder()
                                .categoryCode(seed.categoryCode())
                                .categoryName(titleCase(seed.categoryCode()))
                                .description("Performance measures for " + titleCase(seed.categoryCode()).toLowerCase() + ".")
                                .displayOrder(result.size() + 1)
                                .active(true)
                                .build();
                        return categoryRepository.save(created);
                    });
            result.put(seed.categoryCode(), category);
        }
        return result;
    }

    private KpiFrequency seedFrequency() {
        return frequencyRepository.findByFrequencyCode("QUARTERLY").orElseGet(() ->
                frequencyRepository.save(KpiFrequency.builder()
                        .frequencyCode("QUARTERLY")
                        .frequencyName("Quarterly")
                        .description("Measured for each quarterly review cycle.")
                        .displayOrder(50)
                        .active(true)
                        .build()));
    }

    private Map<String, KpiMaster> seedKpiMasters(Map<String, KpiCategory> categories, KpiFrequency frequency) {
        Map<String, KpiMaster> result = new HashMap<>();
        for (MasterSeed seed : KPI_MASTERS) {
            KpiMaster master = masterRepository.findByKpiCode(seed.code()).orElseGet(() ->
                    masterRepository.save(KpiMaster.builder()
                            .kpiCode(seed.code())
                            .kpiName(seed.name())
                            .category(categories.get(seed.categoryCode()))
                            .description(seed.description())
                            .kpiType(KpiMaster.KpiType.valueOf(seed.type()))
                            .targetType(seed.type().equals("PERCENTAGE")
                                    ? KpiMaster.TargetType.PERCENTAGE_TARGET
                                    : KpiMaster.TargetType.NUMBER_TARGET)
                            .measurementUnit(seed.unit())
                            .frequency(frequency)
                            .frequencyCodeLegacy(frequency.getFrequencyCode())
                            .dataSource("Quarterly self-assessment and manager review")
                            .defaultWeight(100.0 / KPI_MASTERS.size())
                            .active(true)
                            .build()));
            result.put(seed.code(), master);
        }
        return result;
    }

    private void seedTemplatesAndRoleAssignments(Map<String, Role> roles, Map<String, KpiMaster> masters) {
        int priority = 100;
        for (RoleSeed roleSeed : ROLES) {
            Role role = roles.get(roleSeed.code());
            String code = "SAMPLE_" + roleSeed.code();
            KpiTemplate template = templateRepository.findByTemplateCode(code).stream().findFirst().orElseGet(() -> {
                KpiTemplate created = KpiTemplate.builder()
                        .templateCode(code)
                        .templateName(roleSeed.name() + " Performance Review")
                        .versionNumber(1)
                        .description("Quarterly delivery, quality, and collaboration goals for " + roleSeed.name() + ".")
                        .status(KpiTemplate.TemplateStatus.ACTIVE)
                        .role(role)
                        .active(true)
                        .build();
                return templateRepository.save(created);
            });

            if (templateItemRepository.findByTemplate(template).isEmpty()) {
                addTemplateItem(template, masters.get("SPRINT_DELIVERY"), 40.0, ">= 90%", 1);
                addTemplateItem(template, masters.get("AUTOMATED_TEST_COVERAGE"), 35.0, ">= 85%", 2);
                addTemplateItem(template, masters.get("KNOWLEDGE_SHARING"), 25.0, "At least 3 sessions", 3);
            }

            if (kpiAssignmentRepository.findByRoleAndActive(role, true).stream()
                    .noneMatch(assignment -> assignment.getTemplate().getId().equals(template.getId()))) {
                kpiAssignmentRepository.save(KpiAssignment.builder()
                        .template(template)
                        .assignmentType(KpiAssignment.AssignmentType.ROLE)
                        .role(role)
                        .priority(priority++)
                        .active(true)
                        .build());
            }
        }
    }

    private void addTemplateItem(KpiTemplate template, KpiMaster master, double weight, String target, int order) {
        templateItemRepository.save(KpiTemplateItem.builder()
                .template(template)
                .kpiMaster(master)
                .weight(weight)
                .target(target)
                .sequenceOrder(order)
                .active(true)
                .build());
    }

    private void seedKpiWorkflow(Map<String, User> users) {
        ReviewCycle cycle = reviewCycleRepository.findByCycleCode(CYCLE_CODE).orElseGet(() ->
                reviewCycleRepository.save(ReviewCycle.builder()
                        .cycleName("Q4 2026 Performance Review")
                        .cycleCode(CYCLE_CODE)
                        .startDate(LocalDate.of(2026, 10, 1))
                        .endDate(LocalDate.of(2026, 12, 31))
                        .description("Fictional quarterly review cycle showing KPI generation through manager decision.")
                        .status(ReviewCycle.CycleStatus.IN_PROGRESS)
                        .kpisGenerated(false)
                        .build()));

        if (kpiRepository.findByReviewCycle(cycle).isEmpty()) {
            KpiGenerationService.GenerationSummary summary =
                    generationService.generateKpisForReviewCycle(cycle.getId());
            if (summary.hasFailures() || summary.getCreatedKpis() == 0) {
                throw new IllegalStateException("Unable to generate the sample KPI workflow: "
                        + summary.getFailedUsers() + " users failed and " + summary.getCreatedKpis()
                        + " KPIs were created.");
            }
        }

        User admin = users.get(ADMIN_EMAIL);
        List<Kpi> kpis = kpiRepository.findByReviewCycle(cycle);
        if (kpis.stream().allMatch(kpi -> kpi.getStatus() == Kpi.KpiStatus.PENDING)) {
            for (int i = 0; i < kpis.size(); i++) {
                Kpi kpi = kpis.get(i);
                kpi.setReviewer(admin);
                kpiRepository.save(kpi);
                switch (i % 6) {
                    case 0 -> { }
                    case 1 -> submit(kpi, "Progress is on track; key results are recorded.");
                    case 2 -> markUnderReview(kpi);
                    case 3 -> {
                        submit(kpi, "Target achieved with supporting sprint results.");
                        review(kpi, admin, "APPROVE", 4.5, "Results meet the agreed target.");
                    }
                    case 4 -> {
                        submit(kpi, "Initial result needs a little more supporting detail.");
                        review(kpi, admin, "REWORK", null, "Please add a clearer outcome and evidence.");
                    }
                    case 5 -> {
                        submit(kpi, "The target was not reached this quarter.");
                        review(kpi, admin, "REJECT", null, "The result is below the minimum agreed threshold.");
                    }
                    default -> throw new IllegalStateException("Unexpected sample workflow position.");
                }
            }

            Kpi reworkExample = kpiRepository.findByReviewCycleAndStatusIn(
                            cycle, List.of(Kpi.KpiStatus.REWORK_REQUIRED)).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Sample rework step was not created."));
            submit(reworkExample, "Updated result and supporting evidence have been added.");
            review(reworkExample, admin, "APPROVE", 4.0, "Revised submission meets the review criteria.");
        }

        User publishedEmployee = users.get("ava.patel@example.com");
        User calibrationEmployee = users.get("sofia.alvarez@example.com");
        User finalEmployee = users.get("ethan.park@example.com");
        User draftEmployee = users.get("alex.rivera@example.com");
        User reviewEmployee = users.get("owen.brooks@example.com");
        approveAllKpisForEmployee(cycle, publishedEmployee, admin);
        approveAllKpisForEmployee(cycle, calibrationEmployee, admin);
        approveAllKpisForEmployee(cycle, finalEmployee, admin);
        approveAllKpisForEmployee(cycle, draftEmployee, admin);
        approveAllKpisForEmployee(cycle, reviewEmployee, admin);
        seedScorecards(cycle, users, admin);
    }

    private void approveAllKpisForEmployee(ReviewCycle cycle, User employee, User reviewer) {
        for (Kpi kpi : kpiRepository.findByReviewCycleAndUser(cycle, employee)) {
            if (kpi.getStatus() == Kpi.KpiStatus.APPROVED) {
                continue;
            }
            if (kpi.getStatus() == Kpi.KpiStatus.PENDING
                    || kpi.getStatus() == Kpi.KpiStatus.REWORK_REQUIRED
                    || kpi.getStatus() == Kpi.KpiStatus.REJECTED) {
                submit(kpi, "Final sample assessment includes the agreed result and supporting details.");
            }
            review(kpi, reviewer, "APPROVE", 4.2, "Approved for the completed sample scorecard.");
        }
    }

    private void seedScorecards(ReviewCycle cycle, Map<String, User> users, User calibrator) {
        seedMissingScorecards(cycle, users);

        setScorecardStage(cycle, users.get("owen.brooks@example.com"),
                KpiScorecard.ScorecardStatus.UNDER_REVIEW, calibrator);
        calibrateTo(cycle, users.get("sofia.alvarez@example.com"), calibrator,
                4.1, "Scores reviewed and moved into cross-team calibration.", "CALIBRATION");
        calibrateTo(cycle, users.get("ethan.park@example.com"), calibrator,
                4.3, "Calibration completed; scorecard is ready for final approval.", "FINAL");
        calibrateTo(cycle, users.get("ava.patel@example.com"), calibrator,
                4.2, "Calibration approved and performance results released to the employee.", "PUBLISHED");
    }

    void seedMissingScorecards(ReviewCycle cycle, Map<String, User> users) {
        for (User user : users.values()) {
            if (scorecardRepository.findByReviewCycleAndUser(cycle, user).isEmpty()
                    && scoringService.generateScorecard(user, cycle, null) == null) {
                throw new IllegalStateException("Could not generate a sample scorecard for " + user.getEmail());
            }
        }
    }

    private void setScorecardStage(ReviewCycle cycle,
                                   User employee,
                                   KpiScorecard.ScorecardStatus status,
                                   User reviewer) {
        KpiScorecard scorecard = scorecardRepository.findByReviewCycleAndUser(cycle, employee)
                .orElseThrow(() -> new IllegalStateException("Sample scorecard not found for " + employee.getEmail()));
        if (scorecard.getStatus() == KpiScorecard.ScorecardStatus.DRAFT) {
            scorecard.setStatus(status);
            scorecard.setCalibrationNotes("Manager review is complete; this scorecard is awaiting calibration.");
            scorecard.setCalibratedBy(reviewer);
            scorecardRepository.save(scorecard);
        }
    }

    private void calibrateTo(ReviewCycle cycle,
                             User employee,
                             User calibrator,
                             Double score,
                             String notes,
                             String targetStatus) {
        KpiScorecard scorecard = scorecardRepository.findByReviewCycleAndUser(cycle, employee)
                .orElseThrow(() -> new IllegalStateException("Sample scorecard not found for " + employee.getEmail()));
        if (scorecard.getStatus() == KpiScorecard.ScorecardStatus.DRAFT
                || scorecard.getStatus() == KpiScorecard.ScorecardStatus.UNDER_REVIEW) {
            scoringService.calibrateScorecard(scorecard.getId(), score, notes, targetStatus, calibrator);
        }
    }

    private void submit(Kpi kpi, String comments) {
        SelfAssessmentRequest request = new SelfAssessmentRequest();
        request.setActualValue(kpi.getKpiCode().equals("SPRINT_DELIVERY") ? 92.0
                : kpi.getKpiCode().equals("AUTOMATED_TEST_COVERAGE") ? 87.0 : 4.0);
        request.setComments(comments);
        request.setJustification("Measured against the goals agreed at the start of the quarter.");
        reviewWorkflowService.submitSelfAssessment(kpi.getId(), kpi.getUser(), request);
    }

    private void markUnderReview(Kpi kpi) {
        submit(kpi, "Quarterly results are ready for manager review.");
        Kpi submitted = kpiRepository.findById(kpi.getId())
                .orElseThrow(() -> new IllegalStateException("Generated KPI disappeared during seeding."));
        submitted.setStatus(Kpi.KpiStatus.UNDER_REVIEW);
        kpiRepository.save(submitted);

        KpiStatusHistory history = KpiStatusHistory.builder()
                .kpi(submitted)
                .fromStatus(Kpi.KpiStatus.SUBMITTED)
                .toStatus(Kpi.KpiStatus.UNDER_REVIEW)
                .changedBy(submitted.getReviewer())
                .note("Manager opened the submission and is reviewing the supporting results.")
                .build();
        statusHistoryRepository.save(history);
    }

    private void review(Kpi kpi, User reviewer, String decision, Double rating, String comments) {
        ManagerReviewRequest request = new ManagerReviewRequest();
        request.setDecision(decision);
        request.setRating(rating);
        request.setReviewerComments(comments);
        reviewWorkflowService.submitManagerReview(kpi.getId(), reviewer, request);
    }

    private String titleCase(String value) {
        String[] words = value.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private record RoleSeed(String code, String name) {}
    private record TribeSeed(String code, String name, String description) {}
    private record PortfolioSeed(String code, String name, String description, String tribeCode) {}
    private record SquadSeed(String code, String name, String description, String portfolioCode) {}
    private record TeamSeed(String code, String name, String leadEmail) {}
    private record MasterSeed(String categoryCode, String name, String code, String description,
                              String type, String unit, String target) {}
    private record UserSeed(String employeeCode, String firstName, String lastName, String email, String roleCode,
                            String tribeCode, String portfolioCode, String squadCode, String teamCode,
                            String managerEmail) {}
}
