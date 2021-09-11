package com.diseasesimulator.simulation;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class Main extends AllDirectives {

    interface MainCommand {
    }

    private static final class NewSimulation implements MainCommand {

        private final String simulationId = "simulation" + UUID.randomUUID();

        private final SimulationRequest request;

        public NewSimulation(SimulationRequest request) {
            this.request = request;
        }

        public String getSimulationId() {
            return simulationId;
        }

        public SimulationRequest getRequest() {
            return request;
        }
    }

    public static Behavior<Main.MainCommand> create() {
        return Behaviors.setup(
                context -> Behaviors.receive(MainCommand.class)
                        .onMessage(NewSimulation.class, (command) -> {
                            SimulationRequest request = command.getRequest();

                            ActorRef<Simulation.SimulationCommand> simulation = context.spawn(Simulation.create(),
                                    command.getSimulationId());
                            simulation.tell(new Simulation.StartSimulation(
                                    request.getInitialNumberOfInfected(),
                                    request.getNumberOfPeople(),
                                    request.getProbabilityOfInfection())
                            );

                            return Behaviors.same();
                        })
                        .onSignal(Terminated.class, sig -> Behaviors.stopped())
                        .build());
    }

    public static void main(String[] args) {
        final ActorSystem<Main.MainCommand> system = ActorSystem.create(Main.create(), "SimulationApplication");

        final var classicSystem = Adapter.toClassic(system);

        final Http http = Http.get(classicSystem);
        final Materializer materializer = Materializer.createMaterializer(classicSystem);

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = new Main()
                .createRoute(system)
                .flow(classicSystem, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
                ConnectHttp.toHost("localhost", 8080), materializer);

        System.out.println("Server started: http://localhost:8080/simulation/start ");

        try {
            System.in.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            binding.thenCompose(ServerBinding::unbind)
                    .thenAccept(unbound -> system.terminate());
        }
    }

    private Route createRoute(final ActorSystem<Main.MainCommand> system) {
        return concat(
                pathPrefix("simulation", () ->
                        path("start", () ->
                                post(() -> entity(
                                        Jackson.unmarshaller(SimulationRequest.class),
                                        simulationReq -> {
                                            NewSimulation command = new NewSimulation(simulationReq);
                                            system.tell(command);
                                            return complete(StatusCodes.OK, command.getSimulationId());
                                        })
                                ))
                ));
    }
}
