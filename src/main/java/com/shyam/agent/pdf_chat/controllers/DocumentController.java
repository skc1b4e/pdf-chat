package com.shyam.agent.pdf_chat.controllers;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.shyam.agent.pdf_chat.vectordb.Chroma;
import com.shyam.agent.pdf_chat.vectordb.ChromaSearcher;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;

@RestController
public class DocumentController {

    interface LlmExpert {
        String ask(String question);
    }

    RestTemplate rt = new RestTemplate();
    private static final Logger logger = Logger.getLogger(DocumentController.class.getName());
    private static final String API_INIT_1_MSG = "Trying to initialise back up vector database in cloud run by calling the collections url";
    private static final String API_UPLOAD_INFO_MSG = "Chunking and embedding PDF into chromaDB using VertexAI model";
    private static final String API_UPLOAD_1_MSG = "Summarize the pdf contents";

    @GetMapping("/api/init")
    public ResponseEntity<Map<String, Object>> isWorking() {
        logger.info(API_INIT_1_MSG);
        Map<String, Object> response = new HashMap<>();
        ResponseEntity<Object[]> cloudRunVectorDbResponse = rt.getForEntity(Chroma.API_CHROMA_COLLECTIONS_URL,
                Object[].class);

        if (cloudRunVectorDbResponse.getStatusCode().is2xxSuccessful()) {
            logger.info("HttpStatus: " + cloudRunVectorDbResponse.getStatusCode());
            logger.info("Body: " + cloudRunVectorDbResponse.getBody());
            response.put("status", "Working");
        } else {
            logger.info("Vector DB call failed");
            response.put("status", "Vector DB not working");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * This method forms a prompt template with question and information format.
     * Information format is fetched from the relevant match from embeddings.
     * Using question and matched relevant information from vector db, this is then
     * fed to
     * chatModel to generate response.
     * 
     * @param query
     * @return max results 3
     * @throws IOException
     */
    @GetMapping("/api/ask-pdf")
    public ResponseEntity<Map<String, Object>> queryChromaDb(@RequestParam("question") String query)
            throws IOException {
        Map<String, Object> response = new HashMap<>();
        EmbeddingSearchResult<TextSegment> assumptions = ChromaSearcher.search(query, 3);
        if (assumptions != null) {
            /*
             * StringBuilder responseText = new StringBuilder();
             * assumptions.matches().forEach(a -> {
             * System.out.println(a.embedded().text());
             * System.out.println(a.score());
             * responseText.append(a.embedded().text());
             * });
             * 
             * System.out.println(assumptions.matches().size());
             * response.put("answer",
             * responseText.toString());
             * return ResponseEntity.ok(response);
             */

            PromptTemplate promptTemplate = PromptTemplate.from(
                    "Answer the following question to the best of your ability:\n"
                            + "\n"
                            + "Question:\n"
                            + "{{question}}\n"
                            + "\n"
                            + "Form your answer on the following information:\n"
                            + "{{information}}");

            String matchingInformation = assumptions.matches().stream().map(match -> match.embedded().text())
                    .collect(Collectors.joining("\n\n"));

            Map<String, Object> variables = new HashMap<>();
            variables.put("question", query);
            variables.put("information", matchingInformation);

            Prompt prompt = promptTemplate.apply(variables);

            ChatLanguageModel model = Chroma.chatModel;
            AiMessage aiMessage = model.generate(prompt.toUserMessage()).content();
            String answer = aiMessage.text();
            logger.info(answer);
            response.put("answer",
                    answer);
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Error");
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Langchan4j AiServices is used to retrieve the answer.
     * ChromaEmbedding store and VertexAI Model is used to provide the information
     * from which
     * the agent provides the answer.
     * 
     * @param question
     * @return Max Messages 10
     * @throws IOException
     */
    @GetMapping("/api/ask")
    public ResponseEntity<Map<String, Object>> queryVector(@RequestParam("question") String question)
            throws IOException {
        Map<String, Object> response = new HashMap<>();
        EmbeddingModel embeddingModel = Chroma.embeddingModel;
        EmbeddingStore<TextSegment> embeddingStore = Chroma.embeddingStore;

        ChatLanguageModel model = Chroma.chatModel;

        EmbeddingStoreContentRetriever retriever = new EmbeddingStoreContentRetriever(embeddingStore, embeddingModel);

        LlmExpert expert = AiServices.builder(LlmExpert.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(retriever)
                .build();

        List.of(
                question).forEach(query -> {
                    response.put("answer", expert.ask(query));
                });
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/upload")
    public ResponseEntity<Map<String, Object>> documentUpload(@RequestParam("file") MultipartFile file)
            throws IOException {
        Map<String, Object> response = new HashMap<>();
        if (file.isEmpty()) {
            response.put("error", "Document is missing");
            return ResponseEntity.badRequest().body(response);
        } else if (!file.getOriginalFilename().endsWith(".pdf")) {
            response.put("error", "Invalid file. Expecting only pdf document");
            return ResponseEntity.badRequest().body(response);
        } else {
            ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
            Document document = pdfParser.parse(file.getInputStream());
            EmbeddingModel embeddingModel = Chroma.embeddingModel;
            EmbeddingStore<TextSegment> embeddingStore = Chroma.embeddingStore;

            EmbeddingStoreIngestor storeIngestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500, 50))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            logger.info(API_UPLOAD_INFO_MSG);
            storeIngestor.ingest(document);

            ChatLanguageModel model = Chroma.chatModel;

            EmbeddingStoreContentRetriever retriever = new EmbeddingStoreContentRetriever(embeddingStore,
                    embeddingModel);

            LlmExpert expert = AiServices.builder(LlmExpert.class)
                    .chatLanguageModel(model)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .contentRetriever(retriever)
                    .build();

            List.of(API_UPLOAD_1_MSG).forEach(query -> {
                response.put("summary", expert.ask(query));
            });
        }
        return ResponseEntity.ok(response);
    }

}
