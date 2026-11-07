package example.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NightlySnapshot {
    private final StoragePort storage;
    private final ObjectMapper json;
    private final String bucket;
    private final ZoneId zone;

    public NightlySnapshot(StoragePort storage, ObjectMapper json,
                           @Value("${snapshot.bucket}") String bucket,
                           @Value("${snapshot.zone}") String zone) {
        this.storage = storage; this.json = json; this.bucket = bucket; this.zone = ZoneId.of(zone);
    }

    public record SellerAsset(String sellerId, String assetId, String status) { }
    public record BuyerUpdate(String buyerId, String updateId, String status) { }
    public record OrderHandoff(String orderId, String sellerId, String state) { }
    public record MarketplaceData(List<SellerAsset> sellerAssets, List<BuyerUpdate> buyerUpdates,
                                  List<OrderHandoff> orderHandoffs) { }
    public record Receipt(String key, boolean written) { }

    public Receipt snapshot(LocalDate day, MarketplaceData input) throws Exception {
        String objectKey = "marketplace/" + day + "/snapshot.json";
        // Provisioning is a normal startup step; GET keeps repeat runs deterministic.
        try {
            storage.call("GET", "/v1/storage/bucket/get/" + StorageClient.segment(bucket), null);
        } catch (StorageClient.StorageException error) {
            if (error.status != 404) throw error;
            storage.call("POST", "/v1/storage/bucket/create", Map.of("name", bucket));
        }
        if (storage.call("GET", "/v1/storage/object/head/%s/%s".formatted(
                StorageClient.segment(bucket), StorageClient.segment(objectKey)), null).path("found").asBoolean())
            return new Receipt(objectKey, false);
        String encoded = Base64.getEncoder().encodeToString(json.writeValueAsBytes(Map.of("day", day.toString(), "marketplace", input)));
        storage.call("PUT", "/v1/storage/object/put/%s/%s".formatted(
                StorageClient.segment(bucket), StorageClient.segment(objectKey)), Map.of("data_base64", encoded));
        return new Receipt(objectKey, true);
    }

    @Scheduled(cron = "${snapshot.cron}", zone = "${snapshot.zone}")
    public void runNightly() throws Exception {
        // Replace this local fixture with an atomic read from the marketplace database.
        MarketplaceData data = new MarketplaceData(
            List.of(new SellerAsset("seller-7", "asset-4", "active")),
            List.of(new BuyerUpdate("buyer-3", "update-8", "sent")),
            List.of(new OrderHandoff("order-9", "seller-7", "accepted")));
        System.out.println(json.writeValueAsString(snapshot(LocalDate.now(zone).minusDays(1), data)));
    }
}
