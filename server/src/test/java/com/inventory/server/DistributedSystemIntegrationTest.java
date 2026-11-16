package com.inventory.server;

import com.inventory.common.dto.ConflictDTO;
import com.inventory.common.dto.InventoryStatsDTO;
import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.ServerStatusDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.model.EventType;
import com.inventory.common.model.SyncStatus;
import com.inventory.common.service.InventoryService;
import com.inventory.common.service.SynchronizationService;
import com.inventory.common.util.JsonUtils;
import com.inventory.server.config.ServerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DistributedSystemIntegrationTest {

    private static ServerApplication server1;
    private static ServerApplication server2;
    private static InventoryService clientToServer1;
    private static InventoryService clientToServer2;

    private static final String S1_DB = "target/test-db/server1_test.db";
    private static final String S2_DB = "target/test-db/server2_test.db";

    @BeforeAll
    public static void setUp() throws Exception {
        // Clean test DBs
        new File(S1_DB).delete();
        new File(S2_DB).delete();
        new File(S1_DB + "-wal").delete();
        new File(S2_DB + "-wal").delete();
        new File(S1_DB + "-shm").delete();
        new File(S2_DB + "-shm").delete();

        // Server 1 Config
        System.setProperty("server.id", "SERVER_1");
        System.setProperty("server.host", "127.0.0.1");
        System.setProperty("server.rmi.registry.port", "12099");
        System.setProperty("server.rmi.export.port", "12091");
        System.setProperty("server.db.path", S1_DB);
        System.setProperty("peer.server.id", "SERVER_2");
        System.setProperty("peer.server.host", "127.0.0.1");
        System.setProperty("peer.server.rmi.registry.port", "12100");
        System.setProperty("server.sync.interval.ms", "500");

        ServerConfig cfg1 = ServerConfig.load("server1.properties");
        server1 = new ServerApplication(cfg1);
        server1.start();

        // Server 2 Config
        System.setProperty("server.id", "SERVER_2");
        System.setProperty("server.host", "127.0.0.1");
        System.setProperty("server.rmi.registry.port", "12100");
        System.setProperty("server.rmi.export.port", "12101");
        System.setProperty("server.db.path", S2_DB);
        System.setProperty("peer.server.id", "SERVER_1");
        System.setProperty("peer.server.host", "127.0.0.1");
        System.setProperty("peer.server.rmi.registry.port", "12099");
        System.setProperty("server.sync.interval.ms", "500");

        ServerConfig cfg2 = ServerConfig.load("server2.properties");
        server2 = new ServerApplication(cfg2);
        server2.start();

        Thread.sleep(1000);
        reconnectClients();
    }

    private static void reconnectClients() throws Exception {
        Registry reg1 = LocateRegistry.getRegistry("127.0.0.1", 12099);
        clientToServer1 = (InventoryService) reg1.lookup("InventoryService");

        Registry reg2 = LocateRegistry.getRegistry("127.0.0.1", 12100);
        clientToServer2 = (InventoryService) reg2.lookup("InventoryService");
    }

    @AfterAll
    public static void tearDown() {
        if (server1 != null) server1.stop();
        if (server2 != null) server2.stop();
    }

    @Test
    @Order(1)
    public void test1_SyncServer1ToServer2() throws Exception {
        System.out.println("\n--- RUNNING TEST 1: Sync Server 1 -> Server 2 ---");

        ProductDTO product = new ProductDTO();
        product.setProductCode("LAPTOP-DELL-01");
        product.setProductName("Dell XPS 15");
        product.setCategory("Electronics");
        product.setQuantity(25);
        product.setPrice(1850.0);

        ProductDTO created = clientToServer1.addProduct(product);
        assertNotNull(created.getId());
        assertEquals(1, created.getVersion());

        // Wait for active RMI sync to push to Server 2
        Thread.sleep(1500);

        ProductDTO onServer2 = clientToServer2.getProductById(created.getId());
        assertNotNull(onServer2, "Product created on Server 1 must be automatically synchronized to Server 2");
        assertEquals("LAPTOP-DELL-01", onServer2.getProductCode());
        assertEquals("Dell XPS 15", onServer2.getProductName());
        assertEquals(25, onServer2.getQuantity());
        assertEquals(1850.0, onServer2.getPrice());
        System.out.println(">>> TEST 1 PASSED: Product synchronized from Server 1 to Server 2 successfully.");
    }

    @Test
    @Order(2)
    public void test2_SyncServer2ToServer1() throws Exception {
        System.out.println("\n--- RUNNING TEST 2: Sync Server 2 -> Server 1 ---");

        // Fetch product via Server 2, update its quantity and price
        ProductDTO productOnS2 = clientToServer2.searchProducts("LAPTOP-DELL-01").get(0);
        productOnS2.setQuantity(30);
        productOnS2.setPrice(1900.0);

        ProductDTO updatedOnS2 = clientToServer2.updateProduct(productOnS2);
        assertEquals(2, updatedOnS2.getVersion());

        // Wait for sync worker
        Thread.sleep(1500);

        ProductDTO onServer1 = clientToServer1.getProductById(productOnS2.getId());
        assertNotNull(onServer1);
        assertEquals(30, onServer1.getQuantity(), "Server 1 should reflect updated quantity from Server 2");
        assertEquals(1900.0, onServer1.getPrice(), "Server 1 should reflect updated price from Server 2");
        assertEquals(2, onServer1.getVersion());
        System.out.println(">>> TEST 2 PASSED: Update on Server 2 successfully synchronized back to Server 1.");
    }

    @Test
    @Order(3)
    public void test3_MultipleConcurrentClients() throws Exception {
        System.out.println("\n--- RUNNING TEST 3: Multiple Concurrent Clients ---");

        int clientCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        List<String> createdIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < clientCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    ProductDTO p = new ProductDTO();
                    p.setProductCode("ITEM-CONCURRENT-" + index);
                    p.setProductName("Concurrent Gadget " + index);
                    p.setCategory("Gadgets");
                    p.setQuantity(10 + index);
                    p.setPrice(100.0 + index);

                    InventoryService targetService = (index % 2 == 0) ? clientToServer1 : clientToServer2;
                    ProductDTO created = targetService.addProduct(p);
                    createdIds.add(created.getId());
                } catch (Exception e) {
                    System.err.println("Concurrent client error: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(clientCount, createdIds.size());

        // Wait for sync to stabilize
        Thread.sleep(2500);

        List<ProductDTO> allS1 = clientToServer1.getAllProducts();
        List<ProductDTO> allS2 = clientToServer2.getAllProducts();

        assertEquals(allS1.size(), allS2.size(), "Both servers must have identical number of products after concurrent writes");
        System.out.println(">>> TEST 3 PASSED: 10 concurrent clients added products successfully without deadlocks or data loss.");
    }

    @Test
    @Order(4)
    public void test4_Server2Offline_CatchUpSync() throws Exception {
        System.out.println("\n--- RUNNING TEST 4: Server 2 Offline & Catch-Up Sync ---");

        // 1. Stop Server 2
        System.out.println("Stopping Server 2 to simulate network partition / offline crash...");
        server2.stop();
        Thread.sleep(1000);

        // 2. Add product on Server 1 while Server 2 is dead
        ProductDTO offlineProduct = new ProductDTO();
        offlineProduct.setProductCode("OFFLINE-ITEM-99");
        offlineProduct.setProductName("Emergency Power Pack");
        offlineProduct.setCategory("Accessories");
        offlineProduct.setQuantity(50);
        offlineProduct.setPrice(75.0);

        ProductDTO addedWhileS2Down = clientToServer1.addProduct(offlineProduct);
        assertNotNull(addedWhileS2Down);

        // Allow push attempt to fail so Server 1 updates peer reachability
        Thread.sleep(1200);

        // Verify pending sync count on Server 1 increases
        ServerStatusDTO s1Status = clientToServer1.getServerStatus();
        assertTrue(s1Status.getPendingSyncEvents() > 0, "Server 1 must hold pending sync events while Server 2 is down");
        assertFalse(s1Status.isPeerConnected(), "Server 1 should report peer as disconnected");

        // 3. Restart Server 2
        System.out.println("Restarting Server 2 to test automatic catch-up synchronization...");
        System.setProperty("server.id", "SERVER_2");
        System.setProperty("server.host", "127.0.0.1");
        System.setProperty("server.rmi.registry.port", "12100");
        System.setProperty("server.rmi.export.port", "12101");
        System.setProperty("server.db.path", S2_DB);
        System.setProperty("peer.server.id", "SERVER_1");
        System.setProperty("peer.server.host", "127.0.0.1");
        System.setProperty("peer.server.rmi.registry.port", "12099");
        System.setProperty("server.sync.interval.ms", "500");

        ServerConfig cfg2 = ServerConfig.load("server2.properties");
        server2 = new ServerApplication(cfg2);
        server2.start();

        Thread.sleep(1500);
        reconnectClients();

        // Wait for catch-up synchronization
        Thread.sleep(2500);

        ProductDTO onS2AfterCatchup = clientToServer2.getProductById(addedWhileS2Down.getId());
        assertNotNull(onS2AfterCatchup, "Server 2 must catch up missed events after coming back online");
        assertEquals("OFFLINE-ITEM-99", onS2AfterCatchup.getProductCode());
        System.out.println(">>> TEST 4 PASSED: Server 2 caught up with all missed events upon restart.");
    }

    @Test
    @Order(5)
    public void test5_Server1Offline_ClientFailover() throws Exception {
        System.out.println("\n--- RUNNING TEST 5: Server 1 Offline & Client Failover ---");

        server1.stop();
        Thread.sleep(1000);

        // Client queries Server 2 directly
        InventoryStatsDTO statsOnS2 = clientToServer2.getInventoryStatistics();
        assertTrue(statsOnS2.getTotalProducts() > 0);

        ProductDTO failoverItem = new ProductDTO();
        failoverItem.setProductCode("FAILOVER-01");
        failoverItem.setProductName("Failover Test Item");
        failoverItem.setCategory("Failover");
        failoverItem.setQuantity(12);
        failoverItem.setPrice(45.0);

        ProductDTO createdOnS2 = clientToServer2.addProduct(failoverItem);
        assertNotNull(createdOnS2);

        // Restart Server 1
        System.setProperty("server.id", "SERVER_1");
        System.setProperty("server.host", "127.0.0.1");
        System.setProperty("server.rmi.registry.port", "12099");
        System.setProperty("server.rmi.export.port", "12091");
        System.setProperty("server.db.path", S1_DB);
        System.setProperty("peer.server.id", "SERVER_2");
        System.setProperty("peer.server.host", "127.0.0.1");
        System.setProperty("peer.server.rmi.registry.port", "12100");
        System.setProperty("server.sync.interval.ms", "500");

        ServerConfig cfg1 = ServerConfig.load("server1.properties");
        server1 = new ServerApplication(cfg1);
        server1.start();

        Thread.sleep(1500);
        reconnectClients();

        Thread.sleep(2000);
        ProductDTO onS1AfterCatchup = clientToServer1.getProductById(createdOnS2.getId());
        assertNotNull(onS1AfterCatchup, "Server 1 should receive the failover item added on Server 2");
        System.out.println(">>> TEST 5 PASSED: Client operates seamlessly on Server 2 when Server 1 goes offline.");
    }

    @Test
    @Order(6)
    public void test6_ConflictDetection() throws Exception {
        System.out.println("\n--- RUNNING TEST 6: Concurrent Conflict Detection ---");

        // Create a product known to both
        ProductDTO p = new ProductDTO();
        p.setProductCode("CONFLICT-ITEM-01");
        p.setProductName("Dual Modified Asset");
        p.setCategory("Assets");
        p.setQuantity(100);
        p.setPrice(500.0);

        ProductDTO original = clientToServer1.addProduct(p);
        Thread.sleep(1500);

        // Verify it exists on both
        ProductDTO onS2 = clientToServer2.getProductById(original.getId());
        assertNotNull(onS2);

        // Create a synthetic peer event representing concurrent conflict on Server 2
        ProductDTO conflictingRemote = new ProductDTO(
                original.getId(),
                original.getProductCode(),
                "Dual Modified Asset - Remote Edition",
                original.getCategory(),
                80,
                550.0,
                original.getCreatedAt(),
                System.currentTimeMillis() + 100,
                original.getVersion(), // Same version diverged data!
                false,
                "SERVER_2"
        );

        SyncEventDTO conflictEvent = new SyncEventDTO(
                UUID.randomUUID().toString(),
                "PRODUCT",
                original.getId(),
                EventType.UPDATE,
                JsonUtils.toJson(conflictingRemote),
                "SERVER_2",
                "SERVER_1",
                System.currentTimeMillis(),
                original.getVersion(),
                SyncStatus.PENDING,
                0,
                null
        );

        // Deliver directly via SynchronizationService
        Registry reg1 = LocateRegistry.getRegistry("127.0.0.1", 12099);
        SynchronizationService s1Sync = (SynchronizationService) reg1.lookup("SynchronizationService");
        s1Sync.receiveEvents(List.of(conflictEvent));

        // Check if conflict was logged
        List<ConflictDTO> conflicts = clientToServer1.getConflicts();
        assertFalse(conflicts.isEmpty(), "Server 1 must detect and record the conflicting concurrent modification");
        ConflictDTO detected = conflicts.get(0);
        assertEquals(original.getId(), detected.getEntityId());
        assertFalse(detected.isResolved());
        System.out.println("Conflict successfully detected and logged: " + detected.getResolutionNote());

        // Resolve conflict
        ProductDTO resolved = new ProductDTO(
                original.getId(),
                original.getProductCode(),
                "Dual Modified Asset - Reconciled",
                original.getCategory(),
                90,
                525.0,
                original.getCreatedAt(),
                System.currentTimeMillis(),
                original.getVersion() + 10,
                false,
                "SERVER_1"
        );

        boolean resolvedOk = clientToServer1.resolveConflict(detected.getConflictId(), resolved);
        assertTrue(resolvedOk);

        // Check conflict is marked resolved
        List<ConflictDTO> remaining = clientToServer1.getConflicts();
        assertTrue(remaining.isEmpty());
        System.out.println(">>> TEST 6 PASSED: Concurrent conflict detected, logged, and resolved safely without silent data loss.");
    }

    @Test
    @Order(7)
    public void test7_IdempotencyAndNoDuplicateReplay() throws Exception {
        System.out.println("\n--- RUNNING TEST 7: Idempotency & Loop Prevention ---");

        ProductDTO product = new ProductDTO();
        product.setProductCode("IDEMPOTENT-01");
        product.setProductName("Idempotency Probe");
        product.setCategory("Testing");
        product.setQuantity(7);
        product.setPrice(10.0);
        product.setCreatedAt(System.currentTimeMillis());
        product.setUpdatedAt(System.currentTimeMillis());
        product.setVersion(1);
        product.setDeleted(false);
        product.setLastModifiedByServer("SERVER_2");

        String eventId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        product.setId(productId);

        SyncEventDTO event = new SyncEventDTO(
                eventId,
                "PRODUCT",
                productId,
                EventType.CREATE,
                JsonUtils.toJson(product),
                "SERVER_2",
                "SERVER_1",
                System.currentTimeMillis(),
                1,
                SyncStatus.PENDING,
                0,
                null
        );

        Registry reg1 = LocateRegistry.getRegistry("127.0.0.1", 12099);
        SynchronizationService s1Sync = (SynchronizationService) reg1.lookup("SynchronizationService");

        // Send event 1st time
        boolean res1 = s1Sync.receiveEvents(List.of(event));
        assertTrue(res1);

        // Send same event 2nd and 3rd time
        boolean res2 = s1Sync.receiveEvents(List.of(event));
        boolean res3 = s1Sync.receiveEvents(List.of(event));
        assertTrue(res2);
        assertTrue(res3);

        // Verify product count and data integrity
        List<ProductDTO> matches = clientToServer1.searchProducts("IDEMPOTENT-01");
        assertEquals(1, matches.size(), "Replaying same sync event multiple times must not produce duplicate products");
        System.out.println(">>> TEST 7 PASSED: Idempotency verified. Repeated events are acknowledged safely without duplicate effects.");
    }

    @Test
    @Order(8)
    public void test8_ServerRestartAndDataPersistence() throws Exception {
        System.out.println("\n--- RUNNING TEST 8: Server Restart & Persistence ---");

        ProductDTO p = new ProductDTO();
        p.setProductCode("PERSIST-99");
        p.setProductName("Permanent Database Record");
        p.setCategory("Storage");
        p.setQuantity(500);
        p.setPrice(99.99);

        ProductDTO created = clientToServer1.addProduct(p);
        assertNotNull(created);
        Thread.sleep(1500);

        // Shutdown both servers completely
        server1.stop();
        server2.stop();
        Thread.sleep(1500);

        // Restart Server 1 and verify data persists from SQLite
        System.setProperty("server.id", "SERVER_1");
        System.setProperty("server.host", "127.0.0.1");
        System.setProperty("server.rmi.registry.port", "12099");
        System.setProperty("server.rmi.export.port", "12091");
        System.setProperty("server.db.path", S1_DB);
        System.setProperty("peer.server.id", "SERVER_2");
        System.setProperty("peer.server.host", "127.0.0.1");
        System.setProperty("peer.server.rmi.registry.port", "12100");
        System.setProperty("server.sync.interval.ms", "500");

        ServerConfig cfg1 = ServerConfig.load("server1.properties");
        server1 = new ServerApplication(cfg1);
        server1.start();

        Thread.sleep(1500);
        Registry reg1 = LocateRegistry.getRegistry("127.0.0.1", 12099);
        InventoryService s1Service = (InventoryService) reg1.lookup("InventoryService");

        ProductDTO persisted = s1Service.getProductById(created.getId());
        assertNotNull(persisted, "Product must be persistently loaded from SQLite after server restart");
        assertEquals("PERSIST-99", persisted.getProductCode());
        assertEquals(500, persisted.getQuantity());
        System.out.println(">>> TEST 8 PASSED: All records persist reliably across complete server restarts.");
    }
}
