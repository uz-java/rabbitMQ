package com.example.publisher;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.math.BigDecimal;
import java.sql.Timestamp;

@SpringBootApplication
@EnableRabbit
public class PublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(PublisherApplication.class, args);
    }

}
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Transaction {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String pan;

    @Column(nullable = false)
    @Formula("amount > 0.0")
    private BigDecimal amount;

    @CreatedDate
    @CreationTimestamp
    @Column(columnDefinition = "timestamp default current_timestamp")
    private Timestamp createdAt;

}
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
class TransferCreateVO {
    private String pan;
    private BigDecimal amount;
}

interface TransactionRepository extends JpaRepository<Transaction, Long> {

}
@Service
@RequiredArgsConstructor
class TransactionService {

    private final TransactionRepository repository;
    private final RabbitMQService rabbitMQService;

    public Transaction create(TransferCreateVO vo) {
        Transaction transaction = Transaction.builder()
                .pan(vo.getPan())
                .amount(vo.getAmount())
                .build();
        transaction = repository.save(transaction);
        rabbitMQService.send(transaction);
        return transaction;
    }
}

@RestController
@RequestMapping("/transfer")
@RequiredArgsConstructor
class PublisherController {

    private final TransactionService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction transfer(@RequestBody TransferCreateVO vo) {
        return service.create(vo);
    }
}


@Configuration
class RabbitMQConfig {
    public static final String QUEUE = "pdp_queue";
    public static final String EXCHANGE = "pdp_exchange";
    public static final String ROUTING_KEY = "pdp_routing_key";

    @Bean
    public Queue queue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }


    @Bean
    public Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

}

@Service
@RequiredArgsConstructor
class RabbitMQService {

    private final RabbitTemplate rabbitTemplate;

    public void send(Object message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, message);
    }
}
