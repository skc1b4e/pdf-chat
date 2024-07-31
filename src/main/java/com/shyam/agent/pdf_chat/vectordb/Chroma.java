package com.shyam.agent.pdf_chat.vectordb;

import java.time.Duration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;

public class Chroma {
    
        public static final String VECTOR_DB_HOST = System.getenv("CHROMA_URL");
        public static final String API_CHROMA_COLLECTIONS_URL = VECTOR_DB_HOST+"/api/v1/collections";

        public static final EmbeddingStore<TextSegment> embeddingStore =
            ChromaEmbeddingStore.builder()
                    .baseUrl(VECTOR_DB_HOST)
                    .collectionName("my-collection")
                    .timeout(Duration.ofSeconds(30))
                    .build();

    public static final EmbeddingModel embeddingModel =
     VertexAiEmbeddingModel.builder()
    .endpoint(System.getenv("LOCATION") + "-aiplatform.googleapis.com:443")
    .project(System.getenv("PROJECT_ID"))
    .location(System.getenv("LOCATION"))
    .publisher("google")
    .modelName("textembedding-gecko@003")
    .maxRetries(3) // Increase retries for temporary issues
    .build();

    public static final ChatLanguageModel chatModel =
            VertexAiGeminiChatModel.builder()
            .project(System.getenv("PROJECT_ID"))
            .location(System.getenv("LOCATION"))
            .modelName("gemini-1.5-flash-001")
            .maxOutputTokens(1000)
            .build();
}
