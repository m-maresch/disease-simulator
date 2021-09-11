package com.diseasesimulator.simulation;

public class SimulationRequest {
    // how many Individual actors are infected from the beginning
    private int initialNumberOfInfected;
    private int numberOfPeople; // how many Individual actors are to be simulated
    private int probabilityOfInfection; // how likely infections should be on interactions

    public SimulationRequest() {
    }

    public SimulationRequest(int initialNumberOfInfected, int numberOfPeople, int probabilityOfInfection) {
        this.initialNumberOfInfected = initialNumberOfInfected;
        this.numberOfPeople = numberOfPeople;
        this.probabilityOfInfection = probabilityOfInfection;
    }

    public int getInitialNumberOfInfected() {
        return initialNumberOfInfected;
    }

    public int getNumberOfPeople() {
        return numberOfPeople;
    }

    public int getProbabilityOfInfection() {
        return probabilityOfInfection;
    }
}
