package com.example.kpi.service;

import com.example.kpi.dto.OrgTreeNode;
import com.example.kpi.entity.*;
import com.example.kpi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Builds the Tribe -> Portfolio -> Squad -> Member hierarchy tree.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final TribeRepository tribeRepository;
    private final PortfolioRepository portfolioRepository;
    private final SquadRepository squadRepository;
    private final UserSquadAssignmentRepository squadAssignmentRepository;

    public OrganizationService(TribeRepository tribeRepository,
                               PortfolioRepository portfolioRepository,
                               SquadRepository squadRepository,
                               UserSquadAssignmentRepository squadAssignmentRepository) {
        this.tribeRepository = tribeRepository;
        this.portfolioRepository = portfolioRepository;
        this.squadRepository = squadRepository;
        this.squadAssignmentRepository = squadAssignmentRepository;
    }

    public List<OrgTreeNode> getTree() {
        List<Tribe> tribes = tribeRepository.findAll();
        return tribes.stream().map(this::buildTribeNode).toList();
    }

    private OrgTreeNode buildTribeNode(Tribe tribe) {
        OrgTreeNode tribeNode = new OrgTreeNode(tribe.getId(), tribe.getTribeName(),
                tribe.getTribeCode(), "TRIBE", tribe.getActive());
        for (Portfolio portfolio : portfolioRepository.findByTribeId(tribe.getId())) {
            OrgTreeNode portfolioNode = new OrgTreeNode(portfolio.getId(), portfolio.getPortfolioName(),
                    portfolio.getPortfolioCode(), "PORTFOLIO", portfolio.getActive());
            for (Squad squad : squadRepository.findByPortfolioId(portfolio.getId())) {
                OrgTreeNode squadNode = new OrgTreeNode(squad.getId(), squad.getSquadName(),
                        squad.getSquadCode(), "SQUAD", squad.getActive());
                for (UserSquadAssignment a : squadAssignmentRepository.findBySquadId(squad.getId())) {
                    User u = a.getUser();
                    OrgTreeNode memberNode = new OrgTreeNode(u.getId(), u.getFullName(),
                            u.getEmployeeCode(), "MEMBER", u.getActive());
                    if (u.getRoles() != null && !u.getRoles().isEmpty()) {
                        memberNode.setRoleName(u.getRoles().iterator().next().getRoleName());
                    }
                    squadNode.getChildren().add(memberNode);
                }
                portfolioNode.getChildren().add(squadNode);
            }
            tribeNode.getChildren().add(portfolioNode);
        }
        return tribeNode;
    }
}

