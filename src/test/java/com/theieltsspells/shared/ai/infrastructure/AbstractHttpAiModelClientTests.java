package com.theieltsspells.shared.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiProviderException;
import com.theieltsspells.shared.ai.AiRoutingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractHttpAiModelClientTests {

    private final AbstractHttpAiModelClient client = new AbstractHttpAiModelClient(
            new ObjectMapper(), new AiRoutingProperties()) { };

    @Test
    void goneModelIsClassifiedAsUnavailableSoTheRouterCanFallBack() {
        AiProviderException result = client.translate(
                "NVIDIA",
                "retired-model",
                new HttpClientErrorException(HttpStatus.GONE));

        assertThat(result.kind()).isEqualTo(AiProviderException.Kind.MODEL_UNAVAILABLE);
    }
}
