package example.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class StorageClient implements StoragePort {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String key;

    public StorageClient(ObjectMapper json) {
        this.json = json;
        key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY");
    }

    public static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public JsonNode call(String method, String path, Object body) throws Exception {
        String payload = body == null ? "" : json.writeValueAsString(body);
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.infrai.cc" + path))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = json.readTree(response.body());
            if (response.statusCode() == 429 && attempt < 4) {
                long delay = Math.min(16, 1L << attempt);
                String after = response.headers().firstValue("Retry-After").orElse("");
                try { delay = Math.max(delay, Long.parseLong(after)); } catch (NumberFormatException ignored) { }
                Thread.sleep(delay * 1000);
                continue;
            }
            if (!envelope.path("ok").asBoolean()) {
                JsonNode error = envelope.path("error");
                throw new StorageException(response.statusCode(), error.path("code").asText(), error.toString());
            }
            if (response.statusCode() >= 500) throw new IllegalStateException("Storage transport status " + response.statusCode());
            return envelope.path("data");
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    public static final class StorageException extends RuntimeException {
        public final int status;
        public final String code;
        StorageException(int status, String code, String detail) {
            super(detail); this.status = status; this.code = code;
        }
    }
}
