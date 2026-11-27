package com.example.kpi.mapper;

import com.example.kpi.dto.TeamCreateRequest;
import com.example.kpi.dto.TeamResponse;
import com.example.kpi.entity.Team;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface TeamMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "teamLead", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Team toEntity(TeamCreateRequest dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "teamName", ignore = true)
    @Mapping(target = "teamCode", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "teamLead", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromDto(TeamCreateRequest dto, @MappingTarget Team entity);

    @Mapping(target = "teamLeadId", expression = "java(team.getTeamLead() != null ? team.getTeamLead().getId() : null)")
    @Mapping(target = "teamLeadName", expression = "java(team.getTeamLead() != null ? team.getTeamLead().getFullName() : null)")
    @Mapping(target = "memberCount", expression = "java(team.getMembers() != null ? team.getMembers().size() : 0)")
    TeamResponse toResponse(Team team);
}

