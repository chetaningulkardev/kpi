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
public class SquadRequest {

    @NotBlank(message = "Squad name cannot be blank")
    @JsonProperty("squad_name")
    private String squadName;

    @NotBlank(message = "Squad code cannot be blank")
    @JsonProperty("squad_code")
    private String squadCode;

    private String description;

    @JsonProperty("portfolio_id")
    private Long portfolioId;

    @JsonProperty("lead_ids")
    private List<Long> leadIds;
}

