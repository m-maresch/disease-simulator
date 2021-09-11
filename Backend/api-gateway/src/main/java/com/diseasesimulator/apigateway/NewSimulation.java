package com.diseasesimulator.apigateway;

import lombok.Data;

@Data
public class NewSimulation {
    // how many Individual actors are infected from the beginning
    private final int initialNumberOfInfected;
    private final int numberOfPeople; // how many Individual actors are to be simulated
    private final int probabilityOfInfection; // how likely infections should be on interactions
}
