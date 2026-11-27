package com.example.kpi.mapper;

import com.example.kpi.dto.UserCreateRequest;
import com.example.kpi.dto.UserResponse;
import com.example.kpi.dto.UserUpdateRequest;
import com.example.kpi.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "reportingManager", ignore = true)
    @Mapping(target = "directReports", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    User toEntity(UserCreateRequest dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "employeeCode", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "lastLogin", ignore = true)
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "reportingManager", ignore = true)
    @Mapping(target = "directReports", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromDto(UserUpdateRequest dto, @MappingTarget User entity);

    @Mapping(target = "teamId", expression = "java(user.getTeam() != null ? user.getTeam().getId() : null)")
    @Mapping(target = "teamName", expression = "java(user.getTeam() != null ? user.getTeam().getTeamName() : null)")
    @Mapping(target = "reportingManagerId", expression = "java(user.getReportingManager() != null ? user.getReportingManager().getId() : null)")
    @Mapping(target = "reportingManagerName", expression = "java(user.getReportingManager() != null ? user.getReportingManager().getFullName() : null)")
    UserResponse toResponse(User user);
}

