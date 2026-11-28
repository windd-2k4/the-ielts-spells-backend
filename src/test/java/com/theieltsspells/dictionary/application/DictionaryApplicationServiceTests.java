package com.theieltsspells.dictionary.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.dictionary.application.dto.SaveDictionaryWordRequest;
import com.theieltsspells.dictionary.application.dto.SavedWordResponse;
import com.theieltsspells.dictionary.application.dto.TranslateTextRequest;
import com.theieltsspells.dictionary.application.dto.TranslateTextResponse;
import com.theieltsspells.dictionary.domain.DictionaryDirection;
import com.theieltsspells.dictionary.infrastructure.DictionaryProperties;
import com.theieltsspells.dictionary.infrastructure.LibreTranslateClient;
import com.theieltsspells.shared.ai.AiProviderRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictionaryApplicationServiceTests {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private LibreTranslateClient libreTranslateClient;
    @Mock
    private AiProviderRouter aiProviderRouter;

    @Test
    void detectsVietnameseAndEnglishDirectionsProperly() {
        assertEquals(DictionaryDirection.EN_VI, DictionaryDirection.resolve("resilient", DictionaryDirection.AUTO));
        assertEquals(DictionaryDirection.VI_EN, DictionaryDirection.resolve("giáo dục", DictionaryDirection.AUTO));
        assertEquals("giao duc", DictionaryDirection.normalizeForSearch("Giáo  Dục"));
        assertEquals("a", DictionaryDirection.normalizeForSearch("\uFEFFa"));
    }

    @Test
    void translatesUsingLibreTranslateWhenAvailable() {
        DictionaryApplicationService service = new DictionaryApplicationService(
                jdbc, new ObjectMapper(), libreTranslateClient, aiProviderRouter, new DictionaryProperties()
        );
        UUID userId = UUID.randomUUID();
        when(libreTranslateClient.translate("Resilient communities recover quickly.", "en", "vi"))
                .thenReturn(Optional.of("Những cộng đồng kiên cường phục hồi nhanh chóng."));

        TranslateTextResponse response = service.translate(
                userId,
                new TranslateTextRequest("Resilient communities recover quickly.", "auto", "auto", false)
        );

        assertEquals("en", response.sourceLang());
        assertEquals("vi", response.targetLang());
        assertEquals("LIBRETRANSLATE", response.provider());
        assertEquals("Những cộng đồng kiên cường phục hồi nhanh chóng.", response.translatedText());
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesWordIntoStudentVocabularyTopic() {
        DictionaryApplicationService service = new DictionaryApplicationService(
                jdbc, new ObjectMapper(), libreTranslateClient, aiProviderRouter, new DictionaryProperties()
        );
        UUID userId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), any(RowMapper.class),
                eq(userId), eq(topicId), eq("resilient"), eq("kiên cường"),
                eq("They are resilient."), eq("adjective"), eq("IPA: /rɪˈzɪl.i.ənt/")))
                .thenReturn(new SavedWordResponse(cardId, topicId, "resilient", "kiên cường", "NOT_LEARNED"));

        SavedWordResponse saved = service.saveToNotebook(
                userId,
                new SaveDictionaryWordRequest("resilient", "kiên cường", "They are resilient.", "adjective", "/rɪˈzɪl.i.ənt/", topicId)
        );

        assertEquals("resilient", saved.term());
        assertEquals("NOT_LEARNED", saved.masteryStatus());
    }
}
