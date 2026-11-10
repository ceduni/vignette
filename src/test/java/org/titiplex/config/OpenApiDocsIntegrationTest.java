package org.titiplex.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.titiplex.Application;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the generated API documentation (OpenApiConfig). */
@SpringBootTest(
        classes = Application.class,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.datasource.url=jdbc:h2:mem:vignette-docs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "app.storage.root=target/test-storage-docs"
        }
)
@AutoConfigureMockMvc
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode docs() throws Exception {
        String json = mvc.perform(get("/api/docs/openapi/public"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void docsDescribeTheApiAndTheJwtScheme() throws Exception {
        JsonNode docs = docs();
        assertEquals("Vignette API", docs.path("info").path("title").asText());
        assertEquals("bearer", docs.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText());
    }

    @Test
    void tagsAreSortedAlphabetically() throws Exception {
        List<String> tags = new ArrayList<>();
        docs().path("tags").forEach(tag -> tags.add(tag.path("name").asText()));

        assertFalse(tags.isEmpty());
        List<String> sorted = new ArrayList<>(tags);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals(sorted, tags);
    }
}
