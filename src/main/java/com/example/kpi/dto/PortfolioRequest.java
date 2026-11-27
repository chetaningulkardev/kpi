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
public class PortfolioRequest {

    @NotBlank(message = "Portfolio name cannot be blank")
    @JsonProperty("portfolio_name")
    private String portfolioName;

    @NotBlank(message = "Portfolio code cannot be blank")
    @JsonProperty("portfolio_code")
    private String portfolioCode;

    private String description;

    @JsonProperty("tribe_id")
    private Long tribeId;

    @JsonProperty("lead_ids")
    private List<Long> leadIds;
}

