package com.shyam.agent.pdf_chat.vectordb;

import java.util.logging.Logger;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

public class ChromaSearcher {

  private static final Logger logger = Logger.getLogger(ChromaSearcher.class.getName());

  /**
   * Using the embedding model , and user query directly matching the information based on minimum 
   * score of 0.6
   * @param query
   * @param maxResults
   * @return
   */
  public static EmbeddingSearchResult<TextSegment>  search(String query, int maxResults) { 
      logger.info("Searching vector db");
     Embedding queryEmbedding = Chroma.embeddingModel.embed(query).content();
     // Create an EmbeddingSearchRequest
    EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding)
    .minScore(0.6)
    .maxResults(maxResults).build();

     return Chroma.embeddingStore.search(searchRequest);
  }
}
