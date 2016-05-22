import com.rabbitmq.client.Channel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import spark.Service;
import uk.co.andrewrea.claim.award.config.ClaimAwardServiceConfiguration;
import uk.co.andrewrea.claim.award.domain.dtos.ClaimDto;
import uk.co.andrewrea.claim.award.domain.events.publish.ClaimAwardedEvent;
import uk.co.andrewrea.claim.award.domain.events.subscribe.ClaimVerifiedEvent;
import uk.co.andrewrea.claim.award.services.ClaimAwardedHttpService;
import uk.co.andrewrea.infrastructure.core.Publisher;
import uk.co.andrewrea.infrastructure.rabbitmq.RabbitMQPublisher;
import uk.co.andrewrea.infrastructure.rabbitmq.test.RabbitMQExpections;
import uk.co.andrewrea.infrastructure.rabbitmq.test.RabbitMQFacadeForTest;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Created by vagrant on 5/13/16.
 */
public class TestClaimAwardHttpService {

    private RabbitMQFacadeForTest rabbitMQFacadeForTest;
    private SystemUnderTest sut;
    private ClaimAwardServiceConfiguration config;

    @Before
    public void before() throws IOException, TimeoutException {
        this.rabbitMQFacadeForTest = new RabbitMQFacadeForTest();
        this.rabbitMQFacadeForTest.startRabbitMQSystem();
        this.sut = new SystemUnderTest();
        this.config = new ClaimAwardServiceConfiguration();
    }

    @After
    public void after() throws IOException, TimeoutException {
        this.rabbitMQFacadeForTest.stopRabbitMQSystem();
    }

    @Test
    public void publishesClaimAwardedEvent() throws IOException, TimeoutException, InterruptedException {

        //ARRANGE
        this.rabbitMQFacadeForTest.setupTopicExchangeFor(this.config.claimFraudServiceExchangeName);

        ClaimAwardedHttpService claimAwardedHttpService = new ClaimAwardedHttpService(this.config);
        claimAwardedHttpService.start();

        Channel expectationsChannel = this.rabbitMQFacadeForTest.createLocalRabbitMQChannel();
        RabbitMQExpections expectations = new RabbitMQExpections(expectationsChannel);
        expectations.ExpectForExchange(this.config.claimAwardServiceExchangeName, messages -> {
            return messages.size() == 1 && messages.get(0).envelope.getRoutingKey().equals(ClaimAwardedEvent.NAME);
        });

        ClaimDto claim = this.sut.getSampleClaim();
        ClaimVerifiedEvent claimVerifiedEvent = new ClaimVerifiedEvent();
        claimVerifiedEvent.id = "somseId";
        claimVerifiedEvent.claim = claim;

        //ACT
        this.rabbitMQFacadeForTest.publishAsJson(this.config.claimFraudServiceExchangeName, ClaimVerifiedEvent.NAME, claimVerifiedEvent);

        //ASSERT
        try {
            expectations.VerifyAllExpectations();
        } finally {
            claimAwardedHttpService.stop();
        }
    }
}
