package com.example.kpi.service;

import com.example.kpi.dto.PortfolioRequest;
import com.example.kpi.dto.PortfolioResponse;
import com.example.kpi.dto.SimpleRef;
import com.example.kpi.entity.Portfolio;
import com.example.kpi.entity.Tribe;
import com.example.kpi.entity.User;
import com.example.kpi.repository.PortfolioRepository;
import com.example.kpi.repository.TribeRepository;
import com.example.kpi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final TribeRepository tribeRepository;
    private final UserRepository userRepository;

    public PortfolioService(PortfolioRepository portfolioRepository,
                            TribeRepository tribeRepository,
                            UserRepository userRepository) {
        this.portfolioRepository = portfolioRepository;
        this.tribeRepository = tribeRepository;
        this.userRepository = userRepository;
    }

    public PortfolioResponse create(PortfolioRequest request) {
        if (portfolioRepository.existsByPortfolioName(request.getPortfolioName())) {
            throw new RuntimeException("Portfolio name already exists");
        }
        if (portfolioRepository.existsByPortfolioCode(request.getPortfolioCode())) {
            throw new RuntimeException("Portfolio code already exists");
        }
        Portfolio p = new Portfolio();
        p.setPortfolioName(request.getPortfolioName());
        p.setPortfolioCode(request.getPortfolioCode());
        p.setDescription(request.getDescription());
        p.setActive(true);
        assignTribe(p, request.getTribeId());
        p.setLeads(resolveLeads(request.getLeadIds()));
        return toResponse(portfolioRepository.save(p));
    }

    public PortfolioResponse update(Long id, PortfolioRequest request) {
        Portfolio p = portfolioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + id));
        portfolioRepository.findByPortfolioName(request.getPortfolioName()).ifPresent(x -> {
            if (!x.getId().equals(id)) throw new RuntimeException("Portfolio name already exists");
        });
        portfolioRepository.findByPortfolioCode(request.getPortfolioCode()).ifPresent(x -> {
            if (!x.getId().equals(id)) throw new RuntimeException("Portfolio code already exists");
        });
        p.setPortfolioName(request.getPortfolioName());
        p.setPortfolioCode(request.getPortfolioCode());
        p.setDescription(request.getDescription());
        assignTribe(p, request.getTribeId());
        if (request.getLeadIds() != null) {
            p.setLeads(resolveLeads(request.getLeadIds()));
        }
        return toResponse(portfolioRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getAll() {
        return portfolioRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> getByTribe(Long tribeId) {
        return portfolioRepository.findByTribeId(tribeId).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getById(Long id) {
        return portfolioRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + id));
    }

    public PortfolioResponse setActive(Long id, boolean active) {
        Portfolio p = portfolioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + id));
        p.setActive(active);
        return toResponse(portfolioRepository.save(p));
    }

    public PortfolioResponse assignLeads(Long id, List<Long> leadIds) {
        Portfolio p = portfolioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + id));
        p.setLeads(resolveLeads(leadIds));
        return toResponse(portfolioRepository.save(p));
    }

    public void delete(Long id) {
        if (!portfolioRepository.existsById(id)) {
            throw new RuntimeException("Portfolio not found: " + id);
        }
        portfolioRepository.deleteById(id);
    }

    private void assignTribe(Portfolio p, Long tribeId) {
        if (tribeId == null) {
            p.setTribe(null);
            return;
        }
        Tribe tribe = tribeRepository.findById(tribeId)
                .orElseThrow(() -> new RuntimeException("Tribe not found: " + tribeId));
        p.setTribe(tribe);
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

    private PortfolioResponse toResponse(Portfolio p) {
        PortfolioResponse r = new PortfolioResponse();
        r.setId(p.getId());
        r.setPortfolioName(p.getPortfolioName());
        r.setPortfolioCode(p.getPortfolioCode());
        r.setDescription(p.getDescription());
        r.setActive(p.getActive());
        if (p.getTribe() != null) {
            r.setTribeId(p.getTribe().getId());
            r.setTribeName(p.getTribe().getTribeName());
        }
        r.setLeads(p.getLeads() == null ? List.of() : p.getLeads().stream()
                .map(u -> new SimpleRef(u.getId(), u.getFullName())).collect(Collectors.toList()));
        r.setSquadCount(p.getSquads() != null ? p.getSquads().size() : 0);
        return r;
    }
}

