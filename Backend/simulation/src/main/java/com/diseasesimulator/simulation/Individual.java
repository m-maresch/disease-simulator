package com.diseasesimulator.simulation;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Individual actors interact with each other and form the core of a simulation. Individuals
 * simulate the spread of a disease by interacting with other Individuals nearby and
 * potentially infecting them as well. A chosen number of Individual actors are infected
 * from the very beginning and the disease spreads from there.
 */
public class Individual {

    interface IndividualEvent {
    }

    /**
     * Occurs when an Individual actor interacts with another Individual actor.
     * Acts as the Superclass for the concrete events which represent different
     * types of interactions based on the Individual actors group.
     */
    public static abstract class InteractedWith implements Individual.IndividualEvent {

        private final String interactedWith; // who the Individual actor interacted with

        public InteractedWith(String interactedWith) {
            this.interactedWith = interactedWith;
        }

        public String getInteractedWith() {
            return interactedWith;
        }
    }

    public static final class InteractedWithSusceptible extends InteractedWith {

        public InteractedWithSusceptible(String interactedWith) {
            super(interactedWith);
        }
    }

    public static final class InteractedWithInfected extends InteractedWith {

        public InteractedWithInfected(String interactedWith) {
            super(interactedWith);
        }
    }

    /**
     * Occurs when an Individual actor is forced to change its group to Infected.
     * At the beginning of a simulation the Simulation actor infects a specific number
     * of Individuals chosen by the user using this event.
     */
    public static final class Infected implements Individual.IndividualEvent {
    }

    /**
     * Occurs as a response by the Simulation actor when it receives a request to
     * query individuals. Contains the requested Individual actors.
     */
    public static final class QueriedIndividuals implements Individual.IndividualEvent {

        private final List<ActorRef<Individual.IndividualEvent>> individuals;

        public QueriedIndividuals(List<ActorRef<Individual.IndividualEvent>> individuals) {
            this.individuals = individuals;
        }

        public List<ActorRef<Individual.IndividualEvent>> getIndividuals() {
            return individuals;
        }
    }

    public static Behavior<Individual.IndividualEvent> create(ActorRef<Simulation.SimulationCommand> simulation,
                                                              ActorRef<Aggregator.AggregatorEvent> aggregator,
                                                              IndividualProps props) {
        return Behaviors.setup(context -> new IndividualBehavior(context, simulation, aggregator, props));
    }

    public static class IndividualBehavior extends AbstractBehavior<Individual.IndividualEvent> {

        private int numberOfInteractions;
        private Group group = Group.SUSCEPTIBLE;
        private final ActorRef<Simulation.SimulationCommand> simulation;
        private final ActorRef<Aggregator.AggregatorEvent> aggregator;
        private final IndividualProps props;

        private IndividualBehavior(ActorContext<Individual.IndividualEvent> context,
                                   ActorRef<Simulation.SimulationCommand> simulation,
                                   ActorRef<Aggregator.AggregatorEvent> aggregator,
                                   IndividualProps props) {
            super(context);
            this.simulation = simulation;
            this.aggregator = aggregator;
            this.props = props;
        }

        @Override
        public Receive<Individual.IndividualEvent> createReceive() {
            ReceiveBuilder<Individual.IndividualEvent> builder = newReceiveBuilder();

            builder.onMessage(Individual.InteractedWithSusceptible.class, this::interactionWithSusceptible);
            builder.onMessage(Individual.InteractedWithInfected.class, this::interactionWithInfected);

            builder.onMessage(Individual.QueriedIndividuals.class, this::interactWithIndividuals);

            builder.onMessage(Individual.Infected.class, (event) -> infect());

            return builder.build();
        }

        /**
         * InteractedWithSusceptible event handler
         * Handles interactions with non infected Individual actors. Every interaction
         * with a non infected Individual actor has a chance to trigger further
         * interactions with other individuals.
         *
         * @param event the received event
         * @return
         */
        private Behavior<IndividualEvent> interactionWithSusceptible(IndividualEvent event) {
            numberOfInteractions++;

            if (ThreadLocalRandom.current().nextInt(10) < 4) queryIndividuals();

            return Behaviors.same();
        }

        /**
         * InteractedWithInfected event handler
         * Handles interactions with infected Individual actors. Every interaction with
         * a infected Individual actor has a chance to infect the Individual actor which
         * received the event as well as a chance to trigger further interactions with
         * other individuals.
         *
         * @param event the received event
         * @return
         */
        private Behavior<IndividualEvent> interactionWithInfected(InteractedWithInfected event) {
            numberOfInteractions++;

            if (ThreadLocalRandom.current().nextInt(100) < props.getProbabilityOfInfection()) {
                setInfected(event.getInteractedWith());
            }

            if (ThreadLocalRandom.current().nextInt(10) < 4) queryIndividuals();

            return Behaviors.same();
        }

        /**
         * QueriedIndividuals event handler
         * Handles the response sent by the Simulation actor to a request to query individuals.
         * Based on the Individual actors group a interaction event is scheduled for every received
         * actor.
         *
         * @param event the received event
         * @return
         */
        private Behavior<IndividualEvent> interactWithIndividuals(QueriedIndividuals event) {
            String interactedWith = getContext()
                    .getSelf()
                    .path()
                    .name();

            for (ActorRef<Individual.IndividualEvent> individual : event.getIndividuals()) {
                Individual.IndividualEvent individualEvent = new Individual.InteractedWithSusceptible(interactedWith);

                if (group == Group.INFECTED) individualEvent = new Individual.InteractedWithInfected(interactedWith);

                getContext().scheduleOnce(Duration.ofMillis(2), individual, individualEvent);
            }

            return Behaviors.same();
        }

        /**
         * Infected event handler
         * Handles requests to force the Individual actor to change its group to Infected.
         * The Individual actors group is updated and further interactions are triggered
         * (in any case, not by chance).
         *
         * @return
         */
        private Behavior<IndividualEvent> infect() {
            numberOfInteractions++;
            setInfected(simulation.path().name());
            queryIndividuals();
            return Behaviors.same();
        }

        /**
         * Sets the Individual actors group to Infected and reports the infection if the
         * actor hasn't already been infected.
         *
         * @param from who infected the Individual actor
         */
        private void setInfected(String from) {
            if (this.group != Group.INFECTED) reportNewInfection(from);
            this.group = Group.INFECTED;
        }

        /**
         * Triggers interactions with other Individual actors by sending a request to the
         * Simulation actor to query a couple of Individual actors and respond with their actor
         * refs. Within a range based on the Individual actors name (more specifically the id at the end
         * of it) a couple of Individual actors which are "near" the Individual actor are chosen to be
         * requested as interaction partners.
         */
        private void queryIndividuals() {
            int numberOfIndividuals = ThreadLocalRandom.current().nextInt(2, 12);

            int[] ids = new int[numberOfIndividuals];

            String name = getContext()
                    .getSelf()
                    .path()
                    .name();

            int id = Integer.parseInt(name.substring(name.lastIndexOf("individual") + "individual".length()));

            int min = id - numberOfIndividuals / 2;
            int max = id + numberOfIndividuals / 2;

            if (min < 1) min = 1;
            if (max > props.getNumberOfPeople()) max = props.getNumberOfPeople();

            for (int i = 0; i < numberOfIndividuals; i++) {
                if (min >= max) continue;

                ids[i] = ThreadLocalRandom.current().nextInt(min, max);

                if (ids[i] == id) {
                    id = id % 2 == 0 ? id - 1 : id + 1;
                }
            }

            simulation.tell(new Simulation.QueryIndividuals(getContext().getSelf(), ids));
        }

        /**
         * Informs the Aggregator actor that a Individual which hasn't been infected has become
         * infected.
         *
         * @param from who infected the Individual actor
         */
        private void reportNewInfection(String from) {
            aggregator.tell(new Aggregator.NewInfected(
                    from,
                    getContext().getSelf().path().name(),
                    numberOfInteractions
            ));
        }
    }
}
