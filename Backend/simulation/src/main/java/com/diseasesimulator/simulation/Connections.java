package com.diseasesimulator.simulation;

import akka.stream.alpakka.amqp.AmqpConnectionProvider;
import akka.stream.alpakka.amqp.AmqpUriConnectionProvider;

/**
 * The Connections class is used to access the AmqpConnectionProvider instance that is used
 * to connect to RabbitMQ.
 */
public class Connections {

    private final AmqpConnectionProvider amqpConnectionProvider = new AmqpUriConnectionProvider("amqp://guest:guest@localhost:5672/%2F");

    private static final class InstanceHolder {
        static final Connections INSTANCE = new Connections();
    }

    private Connections() {
    }

    public static Connections getConnection() {
        return InstanceHolder.INSTANCE;
    }

    public AmqpConnectionProvider getAmqpConnectionProvider() {
        return amqpConnectionProvider;
    }
}
