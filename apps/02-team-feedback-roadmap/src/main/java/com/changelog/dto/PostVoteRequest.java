package com.changelog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostVoteRequest {
    @NotBlank
    private String email;
    
    private String name;
}
