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
public class SquadResponse {

    private Long id;

    @JsonProperty("squad_name")
    private String squadName;

    @JsonProperty("squad_code")
    private String squadCode;

    private String description;

    private Boolean active;

    @JsonProperty("portfolio_id")
    private Long portfolioId;

    @JsonProperty("portfolio_name")
    private String portfolioName;

    @JsonProperty("tribe_id")
    private Long tribeId;

    @JsonProperty("tribe_name")
    private String tribeName;

    private List<SimpleRef> leads;

    @JsonProperty("member_count")
    private Integer memberCount;
}

