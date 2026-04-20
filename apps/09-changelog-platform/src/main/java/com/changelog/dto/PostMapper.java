package com.changelog.dto;

import com.changelog.model.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PostMapper {

    PostResponse toResponse(Post post);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "tags", ignore = true)
    Post toEntity(CreatePostRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "authorId", ignore = true)
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "tags", ignore = true)
    void updateEntity(CreatePostRequest request, @MappingTarget Post post);
}
