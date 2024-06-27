package ru.test.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SemaphoreWrapper semaphore;
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new SemaphoreWrapper(timeUnit, requestLimit);
    }
    public void createDocument(Object document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        try {
            String jsonDocument = objectMapper.writeValueAsString(document);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Signature", signature);
            HttpEntity<String> entity = new HttpEntity<>(jsonDocument, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException("Failed to create document, status code: " + response.getStatusCode());
            }
        } finally {
            semaphore.release();
        }
    }
    private static class SemaphoreWrapper {
        private final ScheduledExecutorService scheduler;
        private final AtomicInteger permits;
        private final int maxPermits;
        public SemaphoreWrapper(TimeUnit timeUnit, int maxPermits) {
            this.maxPermits = maxPermits;
            this.permits = new AtomicInteger(maxPermits);
            this.scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> permits.set(maxPermits),
                    timeUnit.toMillis(1),
                    timeUnit.toMillis(1),
                    TimeUnit.MILLISECONDS);
        }
        public void acquire() throws InterruptedException {
            while (true) {
                int currentPermits = permits.get();
                if (currentPermits > 0 && permits.compareAndSet(currentPermits, currentPermits - 1)) {
                    break;
                }
                Thread.sleep(100);
            }
        }
        public void release() {
        }
    }
    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);
        TestDocument document = new TestDocument("test inn", "test doc id", "test status");
        api.createDocument(document, "test-signature");
    }
    static class TestDocument {
        public String participantInn;
        public String doc_id;
        public String doc_status;
        public TestDocument(String participantInn, String doc_id, String doc_status) {
            this.participantInn = participantInn;
            this.doc_id = doc_id;
            this.doc_status = doc_status;
        }
        public String getParticipantInn() {
            return participantInn;
        }
        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
        public String getDoc_id() {
            return doc_id;
        }
        public void setDoc_id(String doc_id) {
            this.doc_id = doc_id;
        }
        public String getDoc_status() {
            return doc_status;
        }
        public void setDoc_status(String doc_status) {
            this.doc_status = doc_status;
        }
    }
}


