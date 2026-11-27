package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponse {

    private Long id;

    @JsonProperty("team_name")
    private String teamName;

    @JsonProperty("team_code")
    private String teamCode;

    private String description;

    @JsonProperty("team_lead_id")
    private Long teamLeadId;

    @JsonProperty("team_lead_name")
    private String teamLeadName;

    private Boolean active;

    @JsonProperty("member_count")
    private Integer memberCount;

    @JsonProperty("created_date")
    private LocalDateTime createdDate;

    @JsonProperty("updated_date")
    private LocalDateTime updatedDate;
}

