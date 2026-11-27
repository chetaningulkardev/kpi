package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {

    private Long id;

    @JsonProperty("portfolio_name")
    private String portfolioName;

    @JsonProperty("portfolio_code")
    private String portfolioCode;

    private String description;

    private Boolean active;

    @JsonProperty("tribe_id")
    private Long tribeId;

    @JsonProperty("tribe_name")
    private String tribeName;

    private List<SimpleRef> leads;

    @JsonProperty("squad_count")
    private Integer squadCount;
}

