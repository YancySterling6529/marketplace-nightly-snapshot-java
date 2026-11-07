package example.marketplace;

import com.fasterxml.jackson.databind.JsonNode;

public interface StoragePort {
    JsonNode call(String method, String path, Object body) throws Exception;
}
