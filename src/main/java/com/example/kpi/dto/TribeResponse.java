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
public class TribeResponse {

    private Long id;

    @JsonProperty("tribe_name")
    private String tribeName;

    @JsonProperty("tribe_code")
    private String tribeCode;

    private String description;

    private Boolean active;

    private List<SimpleRef> leads;

    @JsonProperty("portfolio_count")
    private Integer portfolioCount;
}

