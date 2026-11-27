package com.example.kpi.service;

import com.example.kpi.dto.SimpleRef;
import com.example.kpi.dto.TribeRequest;
import com.example.kpi.dto.TribeResponse;
import com.example.kpi.entity.Tribe;
import com.example.kpi.entity.User;
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
public class TribeService {

    private final TribeRepository tribeRepository;
    private final UserRepository userRepository;

    public TribeService(TribeRepository tribeRepository, UserRepository userRepository) {
        this.tribeRepository = tribeRepository;
        this.userRepository = userRepository;
    }

    public TribeResponse create(TribeRequest request) {
        if (tribeRepository.existsByTribeName(request.getTribeName())) {
            throw new RuntimeException("Tribe with name '" + request.getTribeName() + "' already exists");
        }
        if (tribeRepository.existsByTribeCode(request.getTribeCode())) {
            throw new RuntimeException("Tribe with code '" + request.getTribeCode() + "' already exists");
        }
        Tribe tribe = new Tribe();
        tribe.setTribeName(request.getTribeName());
        tribe.setTribeCode(request.getTribeCode());
        tribe.setDescription(request.getDescription());
        tribe.setActive(true);
        tribe.setLeads(resolveLeads(request.getLeadIds()));
        return toResponse(tribeRepository.save(tribe));
    }

    public TribeResponse update(Long id, TribeRequest request) {
        Tribe tribe = tribeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tribe not found: " + id));

        tribeRepository.findByTribeName(request.getTribeName()).ifPresent(t -> {
            if (!t.getId().equals(id)) throw new RuntimeException("Tribe name already exists");
        });
        tribeRepository.findByTribeCode(request.getTribeCode()).ifPresent(t -> {
            if (!t.getId().equals(id)) throw new RuntimeException("Tribe code already exists");
        });

        tribe.setTribeName(request.getTribeName());
        tribe.setTribeCode(request.getTribeCode());
        tribe.setDescription(request.getDescription());
        if (request.getLeadIds() != null) {
            tribe.setLeads(resolveLeads(request.getLeadIds()));
        }
        return toResponse(tribeRepository.save(tribe));
    }

    @Transactional(readOnly = true)
    public List<TribeResponse> getAll() {
        return tribeRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TribeResponse getById(Long id) {
        return tribeRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Tribe not found: " + id));
    }

    public TribeResponse setActive(Long id, boolean active) {
        Tribe tribe = tribeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tribe not found: " + id));
        tribe.setActive(active);
        return toResponse(tribeRepository.save(tribe));
    }

    public TribeResponse assignLeads(Long id, List<Long> leadIds) {
        Tribe tribe = tribeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tribe not found: " + id));
        tribe.setLeads(resolveLeads(leadIds));
        return toResponse(tribeRepository.save(tribe));
    }

    public void delete(Long id) {
        if (!tribeRepository.existsById(id)) {
            throw new RuntimeException("Tribe not found: " + id);
        }
        tribeRepository.deleteById(id);
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

    private TribeResponse toResponse(Tribe tribe) {
        TribeResponse r = new TribeResponse();
        r.setId(tribe.getId());
        r.setTribeName(tribe.getTribeName());
        r.setTribeCode(tribe.getTribeCode());
        r.setDescription(tribe.getDescription());
        r.setActive(tribe.getActive());
        r.setLeads(tribe.getLeads() == null ? List.of() : tribe.getLeads().stream()
                .map(u -> new SimpleRef(u.getId(), u.getFullName())).collect(Collectors.toList()));
        r.setPortfolioCount(tribe.getPortfolios() != null ? tribe.getPortfolios().size() : 0);
        return r;
    }
}

