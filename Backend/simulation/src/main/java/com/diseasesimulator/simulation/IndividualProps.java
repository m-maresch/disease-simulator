package com.diseasesimulator.simulation;

/**
 * The IndividualProps class is used to supply Individual actors with required
 * information about the simulation.
 */
public class IndividualProps {

    private final int numberOfPeople; // how many Individual actors are in this simulation
    private final int probabilityOfInfection; // how likely infections are on interactions

    public IndividualProps(int numberOfPeople, int probabilityOfInfection) {
        this.numberOfPeople = numberOfPeople;
        this.probabilityOfInfection = probabilityOfInfection;
    }

    public int getNumberOfPeople() {
        return numberOfPeople;
    }

    public int getProbabilityOfInfection() {
        return probabilityOfInfection;
    }
}
