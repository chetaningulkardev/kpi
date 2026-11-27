package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TribeRequest {

    @NotBlank(message = "Tribe name cannot be blank")
    @JsonProperty("tribe_name")
    private String tribeName;

    @NotBlank(message = "Tribe code cannot be blank")
    @JsonProperty("tribe_code")
    private String tribeCode;

    private String description;

    @JsonProperty("lead_ids")
    private List<Long> leadIds;
}

