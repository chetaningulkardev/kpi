package com.example.kpi.service;

import com.example.kpi.dto.AssignmentRequest;
import com.example.kpi.dto.AssignmentResponse;
import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages many-to-many assignments of users to Tribes, Portfolios and Squads.
 */
@Service
@Transactional
public class AssignmentService {

    private final UserRepository userRepository;
    private final TribeRepository tribeRepository;
    private final PortfolioRepository portfolioRepository;
    private final SquadRepository squadRepository;
    private final UserTribeAssignmentRepository tribeAssignmentRepository;
    private final UserPortfolioAssignmentRepository portfolioAssignmentRepository;
    private final UserSquadAssignmentRepository squadAssignmentRepository;

    public AssignmentService(UserRepository userRepository,
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

    // ----------------- Tribe -----------------
    public AssignmentResponse assignTribe(AssignmentRequest req) {
        User user = requireUser(req.getUserId());
        Tribe tribe = tribeRepository.findById(req.getUnitId())
                .orElseThrow(() -> new RuntimeException("Tribe not found: " + req.getUnitId()));
        if (tribeAssignmentRepository.existsByUserIdAndTribeId(user.getId(), tribe.getId())) {
            throw new RuntimeException("User already assigned to this tribe");
        }
        UserTribeAssignment a = new UserTribeAssignment();
        a.setUser(user);
        a.setTribe(tribe);
        applyMeta(req, a);
        return toResponse(tribeAssignmentRepository.save(a));
    }

    // ----------------- Portfolio -----------------
    public AssignmentResponse assignPortfolio(AssignmentRequest req) {
        User user = requireUser(req.getUserId());
        Portfolio portfolio = portfolioRepository.findById(req.getUnitId())
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + req.getUnitId()));
        if (portfolioAssignmentRepository.existsByUserIdAndPortfolioId(user.getId(), portfolio.getId())) {
            throw new RuntimeException("User already assigned to this portfolio");
        }
        UserPortfolioAssignment a = new UserPortfolioAssignment();
        a.setUser(user);
        a.setPortfolio(portfolio);
        applyMeta(req, a);
        return toResponse(portfolioAssignmentRepository.save(a));
    }

    // ----------------- Squad -----------------
    public AssignmentResponse assignSquad(AssignmentRequest req) {
        User user = requireUser(req.getUserId());
        Squad squad = squadRepository.findById(req.getUnitId())
                .orElseThrow(() -> new RuntimeException("Squad not found: " + req.getUnitId()));
        if (squadAssignmentRepository.existsByUserIdAndSquadId(user.getId(), squad.getId())) {
            throw new RuntimeException("User already assigned to this squad");
        }
        UserSquadAssignment a = new UserSquadAssignment();
        a.setUser(user);
        a.setSquad(squad);
        applyMeta(req, a);
        return toResponse(squadAssignmentRepository.save(a));
    }

    // ----------------- Updates -----------------
    public AssignmentResponse updateTribeAssignment(Long id, AssignmentRequest req) {
        UserTribeAssignment a = tribeAssignmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Assignment not found: " + id));
        applyMeta(req, a);
        return toResponse(tribeAssignmentRepository.save(a));
    }

    public AssignmentResponse updatePortfolioAssignment(Long id, AssignmentRequest req) {
        UserPortfolioAssignment a = portfolioAssignmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Assignment not found: " + id));
        applyMeta(req, a);
        return toResponse(portfolioAssignmentRepository.save(a));
    }

    public AssignmentResponse updateSquadAssignment(Long id, AssignmentRequest req) {
        UserSquadAssignment a = squadAssignmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Assignment not found: " + id));
        applyMeta(req, a);
        return toResponse(squadAssignmentRepository.save(a));
    }

    // ----------------- Removals -----------------
    public void removeTribeAssignment(Long id) { tribeAssignmentRepository.deleteById(id); }
    public void removePortfolioAssignment(Long id) { portfolioAssignmentRepository.deleteById(id); }
    public void removeSquadAssignment(Long id) { squadAssignmentRepository.deleteById(id); }

    // ----------------- Queries -----------------
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getUserAssignments(Long userId) {
        List<AssignmentResponse> all = new ArrayList<>();
        all.addAll(tribeAssignmentRepository.findByUserId(userId).stream().map(this::toResponse).collect(Collectors.toList()));
        all.addAll(portfolioAssignmentRepository.findByUserId(userId).stream().map(this::toResponse).collect(Collectors.toList()));
        all.addAll(squadAssignmentRepository.findByUserId(userId).stream().map(this::toResponse).collect(Collectors.toList()));
        return all;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAllAssignments() {
        List<AssignmentResponse> all = new ArrayList<>();
        all.addAll(tribeAssignmentRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList()));
        all.addAll(portfolioAssignmentRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList()));
        all.addAll(squadAssignmentRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList()));
        return all;
    }

    /**
     * Bulk-replace a user's assignments in one call (used by the user form).
     */
    public void setUserAssignments(Long userId, List<Long> tribeIds, List<Long> portfolioIds, List<Long> squadIds) {
        User user = requireUser(userId);

        if (tribeIds != null) {
            tribeAssignmentRepository.deleteByUserId(userId);
            for (Long tid : tribeIds) {
                tribeRepository.findById(tid).ifPresent(tribe -> {
                    UserTribeAssignment a = new UserTribeAssignment();
                    a.setUser(user);
                    a.setTribe(tribe);
                    a.setActive(true);
                    a.setPrimaryAssignment(false);
                    a.setStartDate(LocalDate.now());
                    tribeAssignmentRepository.save(a);
                });
            }
        }
        if (portfolioIds != null) {
            portfolioAssignmentRepository.deleteByUserId(userId);
            for (Long pid : portfolioIds) {
                portfolioRepository.findById(pid).ifPresent(portfolio -> {
                    UserPortfolioAssignment a = new UserPortfolioAssignment();
                    a.setUser(user);
                    a.setPortfolio(portfolio);
                    a.setActive(true);
                    a.setPrimaryAssignment(false);
                    a.setStartDate(LocalDate.now());
                    portfolioAssignmentRepository.save(a);
                });
            }
        }
        if (squadIds != null) {
            squadAssignmentRepository.deleteByUserId(userId);
            for (Long sid : squadIds) {
                squadRepository.findById(sid).ifPresent(squad -> {
                    UserSquadAssignment a = new UserSquadAssignment();
                    a.setUser(user);
                    a.setSquad(squad);
                    a.setActive(true);
                    a.setPrimaryAssignment(false);
                    a.setStartDate(LocalDate.now());
                    squadAssignmentRepository.save(a);
                });
            }
        }
    }

    // ----------------- helpers -----------------
    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    private void applyMeta(AssignmentRequest req, UserTribeAssignment a) {
        a.setStartDate(req.getStartDate());
        a.setEndDate(req.getEndDate());
        a.setActive(req.getActive() == null || req.getActive());
        a.setPrimaryAssignment(Boolean.TRUE.equals(req.getPrimaryAssignment()));
    }

    private void applyMeta(AssignmentRequest req, UserPortfolioAssignment a) {
        a.setStartDate(req.getStartDate());
        a.setEndDate(req.getEndDate());
        a.setActive(req.getActive() == null || req.getActive());
        a.setPrimaryAssignment(Boolean.TRUE.equals(req.getPrimaryAssignment()));
    }

    private void applyMeta(AssignmentRequest req, UserSquadAssignment a) {
        a.setStartDate(req.getStartDate());
        a.setEndDate(req.getEndDate());
        a.setActive(req.getActive() == null || req.getActive());
        a.setPrimaryAssignment(Boolean.TRUE.equals(req.getPrimaryAssignment()));
    }

    private AssignmentResponse toResponse(UserTribeAssignment a) {
        AssignmentResponse r = new AssignmentResponse();
        r.setId(a.getId());
        r.setUnitType("TRIBE");
        r.setUserId(a.getUser().getId());
        r.setUserName(a.getUser().getFullName());
        r.setUnitId(a.getTribe().getId());
        r.setUnitName(a.getTribe().getTribeName());
        r.setStartDate(a.getStartDate());
        r.setEndDate(a.getEndDate());
        r.setActive(a.getActive());
        r.setPrimaryAssignment(a.getPrimaryAssignment());
        return r;
    }

    private AssignmentResponse toResponse(UserPortfolioAssignment a) {
        AssignmentResponse r = new AssignmentResponse();
        r.setId(a.getId());
        r.setUnitType("PORTFOLIO");
        r.setUserId(a.getUser().getId());
        r.setUserName(a.getUser().getFullName());
        r.setUnitId(a.getPortfolio().getId());
        r.setUnitName(a.getPortfolio().getPortfolioName());
        r.setStartDate(a.getStartDate());
        r.setEndDate(a.getEndDate());
        r.setActive(a.getActive());
        r.setPrimaryAssignment(a.getPrimaryAssignment());
        return r;
    }

    private AssignmentResponse toResponse(UserSquadAssignment a) {
        AssignmentResponse r = new AssignmentResponse();
        r.setId(a.getId());
        r.setUnitType("SQUAD");
        r.setUserId(a.getUser().getId());
        r.setUserName(a.getUser().getFullName());
        r.setUnitId(a.getSquad().getId());
        r.setUnitName(a.getSquad().getSquadName());
        r.setStartDate(a.getStartDate());
        r.setEndDate(a.getEndDate());
        r.setActive(a.getActive());
        r.setPrimaryAssignment(a.getPrimaryAssignment());
        return r;
    }
}

