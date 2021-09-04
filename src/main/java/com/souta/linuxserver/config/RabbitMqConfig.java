package com.souta.linuxserver.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.souta.linuxserver.controller.Host.id;

@Configuration
public class RabbitMqConfig {
    public static final String REDIALING_EXCHANGE = "server.line.redialing";
    public static final String SERVER_NOT_EXIST_EXCHANGE = "server.line.redialing.not_exist";
    public static final String REDIALING_DEAD_LETTER_EXCHANGE = "server.line.redialing.dead_letter";
    public static final String REDIALING_DEAD_LETTER_ROUTING_KEY = "redialing.dead_letter";
    public final String SERVER_ID = id;
    public final String REDIALING_ROUTE_KEY = "server#" + SERVER_ID;

    public final String REDIALING_QUEUE_NAME = "redialingQueue#" + SERVER_ID;


    @Bean("redialingExchange")
    public DirectExchange redialingExchange() {
        Map<String, Object> args = new HashMap();
        args.put("alternate-exchange", SERVER_NOT_EXIST_EXCHANGE);
        args.put("x-dead-letter-exchange", REDIALING_DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", REDIALING_DEAD_LETTER_ROUTING_KEY);
        args.put("x-message-ttl", TimeUnit.MINUTES.toMillis(5));
        return new DirectExchange(REDIALING_EXCHANGE, true, false, args);
    }

    @Bean("redialingQueue")
    public Queue redialingQueue() {
        return new Queue(REDIALING_QUEUE_NAME);
    }

    @Bean
    public Binding redialingDeadLetterBinding(@Qualifier("redialingQueue") Queue queue,
                                              @Qualifier("redialingExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(REDIALING_ROUTE_KEY);
    }
}
