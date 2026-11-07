package example.marketplace;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NightlySnapshotTest {
    @Test
    void existingDailySnapshotIsNeverOverwritten() throws Exception {
        ObjectMapper json = new ObjectMapper();
        StoragePort storage = (method, path, body) -> {
            if (method.equals("PUT")) fail("Existing snapshots must not be overwritten");
            if (path.contains("/object/head/")) return json.readTree("{\"found\":true}");
            return json.readTree("{}");
        };
        NightlySnapshot service = new NightlySnapshot(storage, json, "marketplace-nightly", "UTC");
        var data = new NightlySnapshot.MarketplaceData(List.of(), List.of(), List.of());
        assertEquals(new NightlySnapshot.Receipt("marketplace/2026-09-19/snapshot.json", false),
            service.snapshot(LocalDate.of(2026, 9, 19), data));
    }
}
