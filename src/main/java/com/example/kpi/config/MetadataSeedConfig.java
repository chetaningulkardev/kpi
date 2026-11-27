package com.example.kpi.config;

import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.LocalDate;

@Configuration
@Slf4j
public class MetadataSeedConfig {

    @Bean
    public ApplicationRunner seedMetadata(
            KpiCategoryRepository kpiCategoryRepository,
            KpiMasterRepository kpiMasterRepository,
            KpiFrequencyRepository kpiFrequencyRepository,
            EmployeeLevelRepository employeeLevelRepository,
            ReviewCycleRepository reviewCycleRepository,
            Environment environment
    ) {
        return args -> {
            try {
                if (kpiCategoryRepository.count() == 0) {
                    seedKpiCategories(kpiCategoryRepository);
                }
                if (employeeLevelRepository.count() == 0) {
                    seedEmployeeLevels(employeeLevelRepository);
                }
                if (kpiMasterRepository.count() == 0) {
                    seedKpiMasters(kpiCategoryRepository, kpiFrequencyRepository, kpiMasterRepository);
                }
                if (reviewCycleRepository.count() == 0
                        && !environment.acceptsProfiles(Profiles.of("dev & !prod"))) {
                    seedReviewCycles(reviewCycleRepository);
                }
                log.info("Metadata seeding completed successfully");
            } catch (Exception e) {
                log.error("Error during metadata seeding", e);
            }
        };
    }

    private void seedKpiCategories(KpiCategoryRepository repository) {
        log.info("Seeding KPI Categories");

        KpiCategory delivery = KpiCategory.builder()
                .categoryCode("DELIVERY")
                .categoryName("Delivery")
                .description("Time and delivery related KPIs")
                .displayOrder(1)
                .active(true)
                .build();
        repository.save(delivery);

        KpiCategory quality = KpiCategory.builder()
                .categoryCode("QUALITY")
                .categoryName("Quality")
                .description("Quality and defect related KPIs")
                .displayOrder(2)
                .active(true)
                .build();
        repository.save(quality);

        KpiCategory innovation = KpiCategory.builder()
                .categoryCode("INNOVATION")
                .categoryName("Innovation")
                .description("Innovation and improvement related KPIs")
                .displayOrder(3)
                .active(true)
                .build();
        repository.save(innovation);

        log.info("KPI Categories seeded: 3 categories created");
    }

    private void seedEmployeeLevels(EmployeeLevelRepository repository) {
        log.info("Seeding Employee Levels");

        EmployeeLevel junior = EmployeeLevel.builder()
                .levelCode("JUNIOR")
                .levelName("Junior")
                .description("Junior level employees")
                .levelOrder(1)
                .active(true)
                .build();
        repository.save(junior);

        EmployeeLevel mid = EmployeeLevel.builder()
                .levelCode("MID")
                .levelName("Mid Level")
                .description("Mid level employees")
                .levelOrder(2)
                .active(true)
                .build();
        repository.save(mid);

        EmployeeLevel senior = EmployeeLevel.builder()
                .levelCode("SENIOR")
                .levelName("Senior")
                .description("Senior level employees")
                .levelOrder(3)
                .active(true)
                .build();
        repository.save(senior);

        log.info("Employee Levels seeded: 3 levels created");
    }

    private void seedKpiMasters(KpiCategoryRepository categoryRepository, KpiFrequencyRepository frequencyRepository, KpiMasterRepository repository) {
        log.info("Seeding KPI Masters");

        KpiCategory delivery = categoryRepository.findByCategoryCode("DELIVERY")
                .orElseThrow(() -> new RuntimeException("Delivery category not found"));
        KpiCategory quality = categoryRepository.findByCategoryCode("QUALITY")
                .orElseThrow(() -> new RuntimeException("Quality category not found"));

        KpiFrequency quarterly = frequencyRepository.findByFrequencyCode("QUARTERLY")
                .orElseThrow(() -> new RuntimeException("Quarterly frequency not found"));

        KpiMaster onTimeDelivery = KpiMaster.builder()
                .kpiName("On Time Delivery")
                .kpiCode("ON_TIME_DELIVERY")
                .category(delivery)
                .description("Percentage of deliverables delivered on time")
                .kpiType(KpiMaster.KpiType.PERCENTAGE)
                .targetType(KpiMaster.TargetType.PERCENTAGE_TARGET)
                .measurementUnit("%")
                .frequency(quarterly)
                .dataSource("Project Management System")
                .defaultWeight(35.0)
                .active(true)
                .build();
        repository.save(onTimeDelivery);

        KpiMaster defectRate = KpiMaster.builder()
                .kpiName("Defect Rate")
                .kpiCode("DEFECT_RATE")
                .category(quality)
                .description("Percentage of defects found in QA")
                .kpiType(KpiMaster.KpiType.PERCENTAGE)
                .targetType(KpiMaster.TargetType.PERCENTAGE_TARGET)
                .measurementUnit("%")
                .frequency(quarterly)
                .dataSource("QA System")
                .defaultWeight(30.0)
                .active(true)
                .build();
        repository.save(defectRate);

        log.info("KPI Masters seeded: 2 masters created");
    }

    private void seedReviewCycles(ReviewCycleRepository repository) {
        log.info("Seeding Review Cycles");

        ReviewCycle cycle = ReviewCycle.builder()
                .cycleName("Q3 2027")
                .cycleCode("Q3_2027")
                .startDate(LocalDate.of(2027, 7, 1))
                .endDate(LocalDate.of(2027, 9, 30))
                .description("Quarterly review cycle for Q3 2027")
                .status(ReviewCycle.CycleStatus.DRAFT)
                .kpisGenerated(false)
                .build();
        repository.save(cycle);

        log.info("Review Cycles seeded: 1 cycle created");
    }
}
