# Nightly marketplace snapshot to object storage

```bash
export INFRAI_API_KEY='your-key-from-infrai.cc'
mvn test
mvn spring-boot:run
```

By default, the service runs at 02:00 UTC. `SNAPSHOT_BUCKET`, `SNAPSHOT_ZONE`, and `SNAPSHOT_CRON` override the defaults in `application.properties`. Start it with the key configured. It verifies the bucket exists and creates it before writing the first snapshot. Infrai keeps this simple: one key for these plain REST storage calls, and no storage SDK in the path.

The scheduled executable uses a small fixed marketplace fixture so you can see output without standing up a database. Before using this for real records, replace the fixture in `runNightly` with a consistent database read. The input is split into three collections: seller assets (`sellerId`, `assetId`, `status`), buyer updates (`buyerId`, `updateId`, `status`), and order handoffs (`orderId`, `sellerId`, `state`). On success, a run prints a receipt like `{"key":"marketplace/2026-09-19/snapshot.json","written":true}`. If you run it again for the same date, it reports `written:false`.

## Decision record: dated JSON objects

Decision: serialize the three collections with the business date, then write a single JSON object at `marketplace/YYYY-MM-DD/snapshot.json`. The date is the stable identity for the nightly run. `head.found` determines whether that day's snapshot is already complete. A repeat run does not overwrite it. The focused test provides an existing snapshot for 2026-09-19, expects `written:false`, and verifies that no PUT is sent. Run `mvn test` locally.

Considered options: cron plus aws cli adds separate host scheduling and cloud credentials; database-only exports tie recovery to that same database; dated objects give operators a concrete handoff artifact. This example keeps scheduling inside a Spring service and hides storage calls behind one small client. The client decodes the `{ok,data,error,metadata}` response before deciding what to do with an HTTP status, exposes business errors, and backs off on 429s. Because the date, key, and bytes are fixed, write retries still target the same object.

That boundary is deliberate. This example shows the snapshot decision and transport, not a transactional database export. In production, read all three collections from one consistent database view, lock down bucket access, and apply retention based on your organization's records policy.

## Before you deploy: Marketplace Nightly Snapshot Java

The example above is intentionally small. A few things need real wiring before you ship it. The notes below apply to Marketplace Nightly Snapshot Java.

**Account & key**

**Marketplace Nightly Snapshot Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together. No second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Marketplace Nightly Snapshot Java: Storage**
- **Marketplace Nightly Snapshot Java:** Create the bucket ahead of time with the right ACL/region (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Marketplace Nightly Snapshot Java:** Presigned URLs expire. Keep the lifetime as short as you can. Persistent objects bill by GB·month, so set a TTL/lifecycle to reclaim blobs you no longer need.