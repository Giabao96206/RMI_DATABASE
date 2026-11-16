package com.inventory.server.database;

import com.inventory.common.dto.ProductDTO;
import com.inventory.common.dto.SyncEventDTO;
import com.inventory.common.model.EventType;
import com.inventory.common.model.SyncStatus;
import com.inventory.common.util.JsonUtils;
import com.inventory.server.config.ServerConfig;
import com.inventory.server.repository.ProductRepository;
import com.inventory.server.repository.SyncRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataSeeder {
    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    public static void seedIfEmpty(DatabaseManager dbManager, ProductRepository productRepo,
                                  SyncRepository syncRepo, ServerConfig config) {
        List<ProductDTO> existing = productRepo.findAll(true);
        if (existing.isEmpty()) {
            logger.info("Database is empty. Seeding 20 initial warehouse products for {}...", config.getServerId());
            seedProducts(dbManager, productRepo, syncRepo, config);
        } else {
            logger.info("Database already contains {} products. Skipping initial seeding.", existing.size());
        }
    }

    public static void seedProducts(DatabaseManager dbManager, ProductRepository productRepo,
                                   SyncRepository syncRepo, ServerConfig config) {
        List<ProductDTO> sampleProducts = getSampleProducts(config.getServerId());
        long now = System.currentTimeMillis();

        try {
            dbManager.executeInTransaction(conn -> {
                for (ProductDTO p : sampleProducts) {
                    productRepo.insert(conn, p);

                    // Queue sync events for peer server
                    SyncEventDTO event = new SyncEventDTO(
                            UUID.randomUUID().toString(),
                            "PRODUCT",
                            p.getId(),
                            EventType.CREATE,
                            JsonUtils.toJson(p),
                            config.getServerId(),
                            config.getPeerServerId(),
                            now,
                            1,
                            SyncStatus.PENDING,
                            0,
                            null
                    );
                    syncRepo.insertEvent(conn, event);
                }
                return null;
            });
            logger.info(">>> Successfully seeded {} sample products into warehouse database!", sampleProducts.size());
        } catch (Exception e) {
            logger.error("Failed to seed initial products: {}", e.getMessage(), e);
        }
    }

    public static List<ProductDTO> getSampleProducts(String serverId) {
        List<ProductDTO> list = new ArrayList<>();
        long now = System.currentTimeMillis();

        list.add(create("SP-LT-01", "Laptop Dell XPS 15 9530", "Điện Tử", 25, 32500000.0, now, serverId));
        list.add(create("SP-LT-02", "Laptop Apple MacBook Pro M3 14\"", "Điện Tử", 15, 45900000.0, now, serverId));
        list.add(create("SP-LT-03", "Laptop Lenovo ThinkPad X1 Carbon Gen 11", "Điện Tử", 20, 38000000.0, now, serverId));
        list.add(create("SP-LT-04", "Laptop Asus ROG Zephyrus G14", "Điện Tử", 12, 39500000.0, now, serverId));
        list.add(create("SP-MN-01", "Màn hình Dell UltraSharp U2723QE 4K IPS", "Điện Tử", 30, 13800000.0, now, serverId));
        list.add(create("SP-MN-02", "Màn hình LG UltraGear 27GP850 2K 165Hz", "Điện Tử", 40, 7990000.0, now, serverId));
        list.add(create("SP-KB-01", "Bàn phím cơ không dây Keychron Q1 Pro", "Phụ Kiện", 60, 4250000.0, now, serverId));
        list.add(create("SP-KB-02", "Bàn phím văn phòng Logitech MX Keys S", "Phụ Kiện", 55, 2650000.0, now, serverId));
        list.add(create("SP-MS-01", "Chuột không dây công thái học Logitech MX Master 3S", "Phụ Kiện", 80, 2450000.0, now, serverId));
        list.add(create("SP-MS-02", "Chuột Gaming Razer DeathAdder V3 Pro Wireless", "Phụ Kiện", 35, 3190000.0, now, serverId));
        list.add(create("SP-HP-01", "Tai nghe chụp tai Sony WH-1000XM5 Hi-Res", "Phụ Kiện", 25, 8490000.0, now, serverId));
        list.add(create("SP-HP-02", "Tai nghe true wireless Apple AirPods Pro 2 USB-C", "Phụ Kiện", 45, 5690000.0, now, serverId));
        list.add(create("SP-SSD-01", "Ổ cứng SSD NVMe Samsung 990 Pro 2TB PCIe 4.0", "Linh Kiện", 70, 4850000.0, now, serverId));
        list.add(create("SP-SSD-02", "Ổ cứng SSD Kingston NV2 1TB PCIe 4.0", "Linh Kiện", 90, 1650000.0, now, serverId));
        list.add(create("SP-RAM-01", "RAM Corsair Vengeance RGB DDR5 32GB (2x16GB) 6000MHz", "Linh Kiện", 50, 3200000.0, now, serverId));
        list.add(create("SP-CPU-01", "Bộ vi xử lý Intel Core i7-14700K 20 nhân 28 luồng", "Linh Kiện", 20, 10890000.0, now, serverId));
        list.add(create("SP-VGA-01", "Card màn hình ASUS TUF Gaming GeForce RTX 4070 Ti SUPER 16GB", "Linh Kiện", 8, 24500000.0, now, serverId));
        list.add(create("SP-PWR-01", "Nguồn máy tính Corsair RM850x 850W 80 Plus Gold Modular", "Linh Kiện", 30, 3650000.0, now, serverId));
        list.add(create("SP-NET-01", "Router Wi-Fi 6 ASUS RT-AX88U Pro Dual-Band 6000Mbps", "Thiết Bị Mạng", 18, 6290000.0, now, serverId));
        list.add(create("SP-NET-02", "Switch mạng TP-Link TL-SG108E 8-Port Gigabit Easy Smart", "Thiết Bị Mạng", 65, 750000.0, now, serverId));

        return list;
    }

    private static ProductDTO create(String code, String name, String category, int qty, double price, long now, String serverId) {
        String id = UUID.nameUUIDFromBytes(("PRODUCT-" + code).getBytes(StandardCharsets.UTF_8)).toString();
        return new ProductDTO(
                id,
                code,
                name,
                category,
                qty,
                price,
                now,
                now,
                1,
                false,
                serverId
        );
    }

    public static void main(String[] args) {
        String configFile = (args.length > 0) ? args[0] : "config/server1.properties";
        ServerConfig config = ServerConfig.load(configFile);
        DatabaseManager dbManager = new DatabaseManager(config.getDbPath());
        ProductRepository productRepo = new ProductRepository(dbManager);
        SyncRepository syncRepo = new SyncRepository(dbManager);

        seedProducts(dbManager, productRepo, syncRepo, config);
    }
}
