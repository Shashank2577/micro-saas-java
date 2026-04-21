package com.changelog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateBoardRequest {
    @NotBlank
    private String name;
    
    @NotBlank
    private String slug;
    
    private String description;
    
    @NotBlank
    private String visibility = "public";
}
