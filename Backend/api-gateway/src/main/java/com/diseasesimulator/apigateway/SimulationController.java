package com.diseasesimulator.apigateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.annotation.ConnectMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.Receiver;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Controller
public class SimulationController {

    private final WebClient webClient;
    private final Receiver receiver;

    private final ObjectMapper mapper = new ObjectMapper();

    private final List<RSocketRequester> requesters = new ArrayList<>();

    @Autowired
    public SimulationController(WebClient webClient, Receiver receiver) {
        this.webClient = webClient;
        this.receiver = receiver;
    }

    @ConnectMapping
    private void connectClient(RSocketRequester requester, @Payload String client) {
        requester.rsocket()
                .onClose()
                .doFirst(() -> requesters.add(requester))
                .doFinally(signalType -> requesters.remove(requester))
                .subscribe();
    }

    @PreDestroy
    private void onShutdown() {
        requesters.forEach(requester -> requester.rsocket().dispose());
    }

    @MessageMapping("start-simulation")
    private Flux<NewInfected> startSimulation(final NewSimulation newSimulation) {
        return webClient.post()
                .uri("/simulation/start")
                .body(Mono.just(newSimulation), NewSimulation.class)
                .accept(MediaType.TEXT_PLAIN)
                .exchange()
                .flatMap(resp -> resp.bodyToMono(String.class))
                .delayElement(Duration.ofMillis(500))
                .flatMapMany(receiver::consumeAutoAck)
                .map(message -> new String(message.getBody(), StandardCharsets.UTF_8))
                .map(this::deserialize);
    }

    private NewInfected deserialize(String message) {
        try {
            return mapper.readValue(message, NewInfected.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
