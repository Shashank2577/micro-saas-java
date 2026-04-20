package com.changelog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePostRequest {
    @NotBlank
    private String title;
    
    private String description;
    
    private String submitterEmail;
    
    private String submitterName;
}
