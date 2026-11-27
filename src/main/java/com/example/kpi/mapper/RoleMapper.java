package com.example.kpi.mapper;

import com.example.kpi.dto.RoleCreateRequest;
import com.example.kpi.dto.RoleResponse;
import com.example.kpi.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "parentRole", ignore = true)
    @Mapping(target = "childRoles", ignore = true)
    @Mapping(target = "users", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Role toEntity(RoleCreateRequest dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roleName", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "parentRole", ignore = true)
    @Mapping(target = "childRoles", ignore = true)
    @Mapping(target = "users", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "updatedDate", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromDto(RoleCreateRequest dto, @MappingTarget Role entity);

    @Mapping(target = "parentRoleId", expression = "java(entity.getParentRole() != null ? entity.getParentRole().getId() : null)")
    @Mapping(target = "parentRoleName", expression = "java(entity.getParentRole() != null ? entity.getParentRole().getRoleName() : null)")
    @Mapping(target = "parentRoleCode", expression = "java(entity.getParentRole() != null ? entity.getParentRole().getRoleCode() : null)")
    RoleResponse toResponse(Role entity);
}

