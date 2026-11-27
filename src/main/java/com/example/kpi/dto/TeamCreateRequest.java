package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamCreateRequest {

    @NotBlank(message = "Team name cannot be blank")
    @JsonProperty("team_name")
    private String teamName;

    @NotBlank(message = "Team code cannot be blank")
    @JsonProperty("team_code")
    private String teamCode;

    private String description;

    @JsonProperty("team_lead_id")
    private Long teamLeadId;
}

