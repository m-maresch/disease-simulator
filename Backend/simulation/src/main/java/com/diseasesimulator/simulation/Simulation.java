package com.diseasesimulator.simulation;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.*;

/**
 * The Simulation actor covers a single simulation started by a user. It's responsible for
 * setting up the simulation and getting it going as well as stopping it once everyone has
 * been infected. Individual actors use their related Simulation actor to access the actor
 * refs of other Individual actors they want to interact with.
 */
public class Simulation {

    private final static Random random = new Random();

    interface SimulationCommand {
    }

    /**
     * Tells the Simulation actor to start a new simulation with the provided configuration.
     */
    public static final class StartSimulation implements SimulationCommand {

        // how many Individual actors are infected from the beginning
        private final int initialNumberOfInfected;
        private final int numberOfPeople; // how many Individual actors are to be simulated
        private final int probabilityOfInfection; // how likely infections should be on interactions

        public StartSimulation(int initialNumberOfInfected, int numberOfPeople, int probabilityOfInfection) {
            if (numberOfPeople > 2000) {
                numberOfPeople = 100;
            }

            if (initialNumberOfInfected > numberOfPeople) {
                initialNumberOfInfected = 5;
                if (numberOfPeople < 5) {
                    numberOfPeople = 5;
                }
            }

            if (probabilityOfInfection > 100) {
                probabilityOfInfection = 10;
            }

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

        @Override
        public String toString() {
            return "initialNumberOfInfected=" + initialNumberOfInfected +
                    ", numberOfPeople=" + numberOfPeople +
                    ", probabilityOfInfection=" + probabilityOfInfection;
        }
    }

    /**
     * Used by Individual actors to request the Simulation actor to query the simulations
     * Individual actors and respond with the actor refs of the supplied Individual actor
     * ids
     */
    public static final class QueryIndividuals implements SimulationCommand {

        private final ActorRef<Individual.IndividualEvent> respondTo;
        private final int[] ids; // Requested Individual actor ids

        public QueryIndividuals(ActorRef<Individual.IndividualEvent> respondTo, int[] ids) {
            this.respondTo = respondTo;
            this.ids = ids;
        }

        public ActorRef<Individual.IndividualEvent> getRespondTo() {
            return respondTo;
        }

        public int[] getIds() {
            return ids;
        }
    }

    /**
     * Used by the Aggregator actor to tell the Simulation actor that every Individual actor
     * has been infected and the simulation can be stopped.
     */
    public static final class StopSimulation implements SimulationCommand {
    }

    public static Behavior<SimulationCommand> create() {
        return Behaviors.setup(SimulationBehavior::new);
    }

    public static class SimulationBehavior extends AbstractBehavior<SimulationCommand> {

        // the Individual actors in the started simulation
        private final List<ActorRef<Individual.IndividualEvent>> individuals = new ArrayList<>();

        private SimulationBehavior(ActorContext<SimulationCommand> context) {
            super(context);
        }

        @Override
        public Receive<SimulationCommand> createReceive() {
            ReceiveBuilder<SimulationCommand> builder = newReceiveBuilder();

            builder.onMessage(StartSimulation.class, this::startSimulation);
            builder.onMessage(QueryIndividuals.class, this::queryIndividuals);
            builder.onMessage(StopSimulation.class, this::stopSimulation);

            return builder.build();
        }

        /**
         * StartSimulation command handler
         * On receiving this command a new simulation is started. A command gets scheduled to
         * stop the simulation after 20 seconds. This acts as a hard limit on how long a simulation
         * can run. The Aggregator actor and the Individual actors get spawned and the first
         * interactions are kicked off by infecting a specific number of Individual actors chosen
         * by the user. The number of people and the probability of infection in the simulation
         * are chosen by the user as well. These values are contained in the received command.
         * The Simulation actor keeps track of all its related Individual actors so they can be
         * queried later on request of a Individual actor.
         *
         * @param command the received command
         * @return
         */
        private Behavior<SimulationCommand> startSimulation(StartSimulation command) {
            getContext().scheduleOnce(Duration.ofMillis(20000),
                    getContext().getSelf(),
                    new Simulation.StopSimulation());

            ActorRef<Aggregator.AggregatorEvent> aggregator = getContext()
                    .spawn(Aggregator.create(getContext().getSelf(),
                                    command.getNumberOfPeople()),
                            "aggregator" + UUID.randomUUID());

            IndividualProps props = new IndividualProps(command.getNumberOfPeople(), command.getProbabilityOfInfection());

            for (int i = 1; i <= command.getNumberOfPeople(); i++) {
                ActorRef<Individual.IndividualEvent> individual = getContext()
                        .spawn(Individual.create(getContext().getSelf(), aggregator, props), getContext()
                                .getSelf()
                                .path()
                                .name() + "individual" + i);
                individuals.add(individual);
            }

            List<Integer> generatedNumbers = new ArrayList<>(List.of(-1));
            for (int i = 0; i < command.getInitialNumberOfInfected(); i++) {
                int next = -1;
                while (generatedNumbers.contains(next)) {
                    int bound = individuals.size() - 1;
                    if (bound >= 1) {
                        next = random.nextInt(bound) + 1;
                    } else {
                        next = 0;
                    }
                }
                generatedNumbers.add(next);

                ActorRef<Individual.IndividualEvent> individual = individuals.get(next);
                individual.tell(new Individual.Infected());
            }

            return Behaviors.same();
        }

        /**
         * QueryIndividuals command handler
         * On receiving this command the requested ids are collected and the respective actor refs are
         * sent back to the requester.
         *
         * @param command the received command
         * @return
         */
        private Behavior<SimulationCommand> queryIndividuals(QueryIndividuals command) {
            boolean invalid = Arrays.stream(command.getIds()).anyMatch(id -> id >= individuals.size());

            if (invalid) return Behaviors.same();

            List<ActorRef<Individual.IndividualEvent>> individuals = new ArrayList<>();

            for (int id : command.getIds()) {
                if (id <= 0) continue;
                individuals.add(this.individuals.get(id - 1));
            }

            Individual.QueriedIndividuals response = new Individual.QueriedIndividuals(individuals);

            command.getRespondTo().tell(response);

            return Behaviors.same();
        }

        /**
         * StopSimulation command handler
         * On receiving this command the started simulation is stopped.
         *
         * @param command the received command
         * @return
         */
        private Behavior<SimulationCommand> stopSimulation(StopSimulation command) {
            return Behaviors.stopped();
        }
    }
}
