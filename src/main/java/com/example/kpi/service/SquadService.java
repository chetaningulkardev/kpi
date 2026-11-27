package com.example.kpi.service;

import com.example.kpi.dto.SimpleRef;
import com.example.kpi.dto.SquadRequest;
import com.example.kpi.dto.SquadResponse;
import com.example.kpi.entity.Portfolio;
import com.example.kpi.entity.Squad;
import com.example.kpi.entity.User;
import com.example.kpi.repository.PortfolioRepository;
import com.example.kpi.repository.SquadRepository;
import com.example.kpi.repository.UserRepository;
import com.example.kpi.repository.UserSquadAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class SquadService {

    private final SquadRepository squadRepository;
    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final UserSquadAssignmentRepository squadAssignmentRepository;

    public SquadService(SquadRepository squadRepository,
                        PortfolioRepository portfolioRepository,
                        UserRepository userRepository,
                        UserSquadAssignmentRepository squadAssignmentRepository) {
        this.squadRepository = squadRepository;
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.squadAssignmentRepository = squadAssignmentRepository;
    }

    public SquadResponse create(SquadRequest request) {
        if (squadRepository.existsBySquadName(request.getSquadName())) {
            throw new RuntimeException("Squad name already exists");
        }
        if (squadRepository.existsBySquadCode(request.getSquadCode())) {
            throw new RuntimeException("Squad code already exists");
        }
        // Every Squad must have at least one Squad Lead
        if (request.getLeadIds() == null || request.getLeadIds().isEmpty()) {
            throw new RuntimeException("Every squad must have at least one Squad Lead");
        }
        Squad s = new Squad();
        s.setSquadName(request.getSquadName());
        s.setSquadCode(request.getSquadCode());
        s.setDescription(request.getDescription());
        s.setActive(true);
        assignPortfolio(s, request.getPortfolioId());
        s.setLeads(resolveLeads(request.getLeadIds()));
        return toResponse(squadRepository.save(s));
    }

    public SquadResponse update(Long id, SquadRequest request) {
        Squad s = squadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Squad not found: " + id));
        squadRepository.findBySquadName(request.getSquadName()).ifPresent(x -> {
            if (!x.getId().equals(id)) throw new RuntimeException("Squad name already exists");
        });
        squadRepository.findBySquadCode(request.getSquadCode()).ifPresent(x -> {
            if (!x.getId().equals(id)) throw new RuntimeException("Squad code already exists");
        });
        s.setSquadName(request.getSquadName());
        s.setSquadCode(request.getSquadCode());
        s.setDescription(request.getDescription());
        assignPortfolio(s, request.getPortfolioId());
        if (request.getLeadIds() != null) {
            if (request.getLeadIds().isEmpty()) {
                throw new RuntimeException("Every squad must have at least one Squad Lead");
            }
            s.setLeads(resolveLeads(request.getLeadIds()));
        }
        return toResponse(squadRepository.save(s));
    }

    @Transactional(readOnly = true)
    public List<SquadResponse> getAll() {
        return squadRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SquadResponse> getByPortfolio(Long portfolioId) {
        return squadRepository.findByPortfolioId(portfolioId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SquadResponse getById(Long id) {
        return squadRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Squad not found: " + id));
    }

    public SquadResponse setActive(Long id, boolean active) {
        Squad s = squadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Squad not found: " + id));
        s.setActive(active);
        return toResponse(squadRepository.save(s));
    }

    public SquadResponse assignLeads(Long id, List<Long> leadIds) {
        if (leadIds == null || leadIds.isEmpty()) {
            throw new RuntimeException("Every squad must have at least one Squad Lead");
        }
        Squad s = squadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Squad not found: " + id));
        s.setLeads(resolveLeads(leadIds));
        return toResponse(squadRepository.save(s));
    }

    public void delete(Long id) {
        if (!squadRepository.existsById(id)) {
            throw new RuntimeException("Squad not found: " + id);
        }
        squadRepository.deleteById(id);
    }

    private void assignPortfolio(Squad s, Long portfolioId) {
        if (portfolioId == null) {
            s.setPortfolio(null);
            return;
        }
        Portfolio p = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + portfolioId));
        s.setPortfolio(p);
    }

    private Set<User> resolveLeads(List<Long> leadIds) {
        Set<User> leads = new HashSet<>();
        if (leadIds != null) {
            for (Long uid : leadIds) {
                userRepository.findById(uid).ifPresent(leads::add);
            }
        }
        return leads;
    }

    private SquadResponse toResponse(Squad s) {
        SquadResponse r = new SquadResponse();
        r.setId(s.getId());
        r.setSquadName(s.getSquadName());
        r.setSquadCode(s.getSquadCode());
        r.setDescription(s.getDescription());
        r.setActive(s.getActive());
        if (s.getPortfolio() != null) {
            r.setPortfolioId(s.getPortfolio().getId());
            r.setPortfolioName(s.getPortfolio().getPortfolioName());
            if (s.getPortfolio().getTribe() != null) {
                r.setTribeId(s.getPortfolio().getTribe().getId());
                r.setTribeName(s.getPortfolio().getTribe().getTribeName());
            }
        }
        r.setLeads(s.getLeads() == null ? List.of() : s.getLeads().stream()
                .map(u -> new SimpleRef(u.getId(), u.getFullName())).collect(Collectors.toList()));
        r.setMemberCount(squadAssignmentRepository.findBySquadId(s.getId()).size());
        return r;
    }
}

