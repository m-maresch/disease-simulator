package com.diseasesimulator.simulation;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.alpakka.amqp.AmqpWriteSettings;
import akka.stream.alpakka.amqp.QueueDeclaration;
import akka.stream.alpakka.amqp.WriteMessage;
import akka.stream.alpakka.amqp.WriteResult;
import akka.stream.alpakka.amqp.javadsl.AmqpFlow;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.util.ByteString;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * The Aggregator actor receives messages about new infections. It's responsible for aggregating
 * all of these messages related to its simulation and publishing them to RabbitMQ.
 */
public class Aggregator {

    interface AggregatorEvent {
    }

    /**
     * Informs the Aggregator actor that an individual has been infected
     */
    public static final class NewInfected implements Aggregator.AggregatorEvent {
        private final String from; // who infected the individual
        private final String infected; // the individual which has been infected
        private final int numberOfInteractions; // number of interactions before infection

        public NewInfected(String from, String infected, int numberOfInteractions) {
            this.from = from;
            this.infected = infected;
            this.numberOfInteractions = numberOfInteractions;
        }

        public String getFrom() {
            return from;
        }

        public String getInfected() {
            return infected;
        }

        public int getNumberOfInteractions() {
            return numberOfInteractions;
        }
    }

    public static Behavior<Aggregator.AggregatorEvent> create(ActorRef<Simulation.SimulationCommand> simulation,
                                                              int numberOfPeople) {
        return Behaviors.setup(context -> new AggregatorBehavior(context, simulation, numberOfPeople));
    }

    public static class AggregatorBehavior extends AbstractBehavior<Aggregator.AggregatorEvent> {

        private final ActorRef<Simulation.SimulationCommand> simulation;
        private final SourceQueueWithComplete<String> queue;
        private final int numberOfPeople;
        private int numberOfInfected;

        /**
         * Sets up the connection to RabbitMQ via Akka Streams
         *
         * @param context
         * @param simulation     the Simulation actor this Aggregator actor is related to
         * @param numberOfPeople the number of people in this simulation
         */
        private AggregatorBehavior(ActorContext<AggregatorEvent> context,
                                   ActorRef<Simulation.SimulationCommand> simulation,
                                   int numberOfPeople) {
            super(context);
            this.simulation = simulation;
            this.numberOfPeople = numberOfPeople;

            String simulationName = simulation.path().name();
            final QueueDeclaration queueDeclaration = QueueDeclaration.create(simulationName);

            final AmqpWriteSettings settings =
                    AmqpWriteSettings.create(Connections.getConnection().getAmqpConnectionProvider())
                            .withRoutingKey(simulationName)
                            .withDeclaration(queueDeclaration)
                            .withBufferSize(numberOfPeople)
                            .withConfirmationTimeout(Duration.ofMillis(5000));

            final Flow<WriteMessage, WriteResult, CompletionStage<Done>> amqpFlow =
                    AmqpFlow.createWithConfirm(settings);

            this.queue = Source.<String>queue(numberOfPeople, OverflowStrategy.dropTail()).async()
                    .map(message -> WriteMessage.create(ByteString.fromString(message))).async()
                    .via(amqpFlow).async()
                    .to(Sink.ignore())
                    .run(Materializer.createMaterializer(getContext()));
        }

        @Override
        public Receive<Aggregator.AggregatorEvent> createReceive() {
            ReceiveBuilder<Aggregator.AggregatorEvent> builder = newReceiveBuilder();

            builder.onMessage(NewInfected.class, this::addInfection);

            return builder.build();
        }

        /**
         * NewInfected event handler
         * Extracts the relevant information from the event and publishes a JSON representation of
         * that information to RabbitMQ. Tracks the number of infected individuals in the simulation and
         * tells the simulation to stop once almost every individual has been infected.
         *
         * @param event the received NewInfected event
         * @return
         */
        private Behavior<AggregatorEvent> addInfection(NewInfected event) {
            String from = event.getFrom();
            String fromId = extractIdFromName(from);

            if (!from.contains("individual")) fromId = "0";

            String infected = event.getInfected();
            String infectedId = extractIdFromName(infected);

            int numberOfInteractions = event.getNumberOfInteractions();

            queue.offer("{\"from\":" + fromId +
                    ",\"infected\":" + infectedId +
                    ",\"numberOfInteractions\":" + numberOfInteractions + "}"
            );

            numberOfInfected++;

            if (numberOfInfected >= Math.floor(numberOfPeople * 0.99)) {
                getContext().scheduleOnce(Duration.ofMillis(2000), simulation, new Simulation.StopSimulation());
            }

            return Behaviors.same();
        }

        private String extractIdFromName(String name) {
            return name.substring(name.lastIndexOf("individual") + "individual".length());
        }
    }
}
