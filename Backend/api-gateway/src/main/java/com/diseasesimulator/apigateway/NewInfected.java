package com.diseasesimulator.apigateway;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class NewInfected {
    private Integer from; // who infected the individual
    private Integer infected; // the individual which has been infected
    private Integer numberOfInteractions; // number of interactions before infection
}
