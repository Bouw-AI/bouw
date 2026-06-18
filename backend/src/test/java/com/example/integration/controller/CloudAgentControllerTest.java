package com.example.integration.controller;

import com.example.agent.CloudAgentService;
import com.example.integration.service.CloudAgentEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CloudAgentControllerTest {

    @Mock
    CloudAgentService cloudAgentService;

    @Mock
    CloudAgentEventStore eventStore;

    CloudAgentController controller;

    @BeforeEach
    void setUp() {
        controller = new CloudAgentController(
                cloudAgentService,
                eventStore,
                new ObjectMapper(),
                Executors.newCachedThreadPool(),
                Duration.ofMinutes(5)
        );
    }

    @Test
    void handleErrorReturnsInternalServerError() {
        var response = controller.handleError(
                new RuntimeException("Something went wrong"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Something went wrong"));
    }

    @Test
    void handleErrorSuppressesBodyForSseRequests() {
        var request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/event-stream");
        var response = new MockHttpServletResponse();

        var result = controller.handleError(new RuntimeException("stream failed"), request, response);

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void handleErrorSuppressesBodyWhenHandlerProducesEventStream() {
        var request = new MockHttpServletRequest();
        request.setAttribute(
                HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE,
                java.util.Set.of(MediaType.TEXT_EVENT_STREAM));
        var response = new MockHttpServletResponse();

        var result = controller.handleError(new RuntimeException("stream failed"), request, response);

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(500);
    }
}
