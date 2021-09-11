package com.diseasesimulator.apigateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Delivery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.annotation.ConnectMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.ClientResponse;
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
    public void connectClient(RSocketRequester requester, @Payload String client) {
        requester.rsocket()
                .onClose()
                .doFirst(() -> requesters.add(requester))
                .doFinally(signalType -> requesters.remove(requester))
                .subscribe();
    }

    @PreDestroy
    public void onShutdown() {
        requesters.forEach(requester -> requester.rsocket().dispose());
    }

    @MessageMapping("start-simulation")
    public Flux<NewInfected> startSimulation(final NewSimulation newSimulation) {
        return requestSimulation(newSimulation)
                .flatMap(queueNameResp -> queueNameResp.bodyToMono(String.class))
                .delayElement(Duration.ofMillis(500))
                .flatMapMany(receiver::consumeAutoAck)
                .map(this::deserializeMessage);
    }

    private Mono<ClientResponse> requestSimulation(final NewSimulation newSimulation) {
        return webClient.post()
                .uri("/simulation/start")
                .body(Mono.just(newSimulation), NewSimulation.class)
                .accept(MediaType.TEXT_PLAIN)
                .exchange();
    }

    private NewInfected deserializeMessage(Delivery message) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            return mapper.readValue(json, NewInfected.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
