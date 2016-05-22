package uk.co.andrewrea.communication.services;

import com.google.gson.Gson;
import com.rabbitmq.client.*;
import uk.co.andrewrea.communication.config.ClaimCommunicationServiceConfiguration;
import uk.co.andrewrea.communication.domain.core.CommunicationService;
import uk.co.andrewrea.communication.domain.events.subscribe.ClaimAwardPaidEvent;
import uk.co.andrewrea.communication.domain.events.subscribe.ClaimRegisteredEvent;
import uk.co.andrewrea.communication.domain.models.Communication;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;


public class ClaimCommunicationHttpService {
    private CommunicationService communicationService;
    private ClaimCommunicationServiceConfiguration config;

    public ClaimCommunicationHttpService(CommunicationService communicationService, ClaimCommunicationServiceConfiguration config){

        this.communicationService = communicationService;
        this.config = config;
    }

    public void start() throws IOException, TimeoutException {
        //Create a connection
        ConnectionFactory factory = new ConnectionFactory();
        factory.setVirtualHost("/");
        factory.setHost("localhost");
        factory.setPort(5672);
        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();

        runRegistrationConsumer(channel);
        runPaymentConsumer(channel);
    }

    private void runRegistrationConsumer(final Channel channel) throws IOException {
        //Create a queue and bind to the exchange for PAYMENT
        String queueName = String.format("%s.%s",this.config.claimRegistrationServiceExchangeName, this.config.claimCommunicationServiceExchangeName);
        channel.queueDeclare(queueName,false, true, true, null);
        channel.queueBind(queueName, this.config.claimRegistrationServiceExchangeName, ClaimRegisteredEvent.NAME);

        //Create a consumer of the queue
        Runnable consumer = () -> {
            try {
                channel.basicConsume(queueName, false,
                        new DefaultConsumer(channel) {
                            @Override
                            public void handleDelivery(String consumerTag,
                                                       Envelope envelope,
                                                       AMQP.BasicProperties properties,
                                                       byte[] body)
                                    throws IOException
                            {
                                ClaimRegisteredEvent claimRegisteredEvent = new Gson().fromJson(new String(body), ClaimRegisteredEvent.class);

                                Communication communication = new Communication(claimRegisteredEvent.id,claimRegisteredEvent.claim.email);
                                communicationService.save(communication);

                                long deliveryTag = envelope.getDeliveryTag();
                                channel.basicAck(deliveryTag, false);
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        consumer.run();
    }

    private void runPaymentConsumer(final Channel channel) throws IOException {
        //Create a queue and bind to the exchange for PAYMENT
        String queueName = String.format("%s.%s",this.config.claimPaymentServiceExchangeName, this.config.claimCommunicationServiceExchangeName);
        channel.queueDeclare(queueName,false, true, true, null);
        channel.queueBind(queueName, this.config.claimPaymentServiceExchangeName, ClaimAwardPaidEvent.NAME);

        //Create a consumer of the queue
        Runnable consumer = () -> {
            boolean autoAck = false;
            try {
                channel.basicConsume(queueName, autoAck,
                        new DefaultConsumer(channel) {
                            @Override
                            public void handleDelivery(String consumerTag,
                                                       Envelope envelope,
                                                       AMQP.BasicProperties properties,
                                                       byte[] body)
                                    throws IOException
                            {
                                ClaimAwardPaidEvent claimAwardedEvent = new Gson().fromJson(new String(body), ClaimAwardPaidEvent.class);
                                Optional<Communication> communication = communicationService.getByClaimId(claimAwardedEvent.id);

                                if(!communication.isPresent()){
                                    throw new RuntimeException("No claim communication exists with that id");
                                }

                                communicationService.send(communication.get());

                                long deliveryTag = envelope.getDeliveryTag();
                                channel.basicAck(deliveryTag, false);
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        consumer.run();
    }
}
