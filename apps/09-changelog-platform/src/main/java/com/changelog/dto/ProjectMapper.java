package com.changelog.dto;

import com.changelog.model.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "logoKey", ignore = true)
    @Mapping(target = "faviconKey", ignore = true)
    Project toEntity(CreateProjectRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "logoKey", ignore = true)
    @Mapping(target = "faviconKey", ignore = true)
    void updateEntity(CreateProjectRequest request, @MappingTarget Project project);
}
