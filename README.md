# ĐỒ ÁN LẬP TRÌNH MẠNG: HỆ THỐNG QUẢN LÝ KHO PHÂN TÁN
## SỬ DỤNG JAVA RMI VÀ ĐỒNG BỘ HÓA DỮ LIỆU ACTIVE-ACTIVE GIỮA 2 SERVER

![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2022-orange.svg)
![Protocol](https://img.shields.io/badge/Protocol-Java%20RMI-blue.svg)
![Database](https://img.shields.io/badge/Database-SQLite%20(WAL%20Mode)-lightgrey.svg)
![GUI](https://img.shields.io/badge/GUI-JavaFX%2021-green.svg)
![Build](https://img.shields.io/badge/Build-Maven%20Multi--module-red.svg)
![Tests](https://img.shields.io/badge/Tests-8%2F8%20Passed%20(100%25)-brightgreen.svg)

---

## 1. GIỚI THIỆU ĐỀ TÀI

Đồ án xây dựng một hệ thống quản lý kho hàng phân tán (Distributed Warehouse Management System) hoàn chỉnh theo kiến trúc Client–Server phân tán, sử dụng hoàn toàn **Java RMI (Remote Method Invocation)** cho mọi giao tiếp từ xa (giữa Client–Server và giữa Server–Server).

Hệ thống triển khai thực tế trên **2 máy tính vật lý độc lập (Server 1 và Server 2)** và **nhiều máy tính/máy ảo Client (JavaFX)** kết nối qua mạng LAN.

### Điểm nổi bật về mặt kỹ thuật:
1. **Kiến trúc Active-Active 2-Server**: Cả 2 Server đều phục vụ yêu cầu Đọc/Ghi trực tiếp từ Client đồng thời.
2. **Đồng bộ dữ liệu hai chiều (Bidirectional Real-Time Sync)**: Dữ liệu thêm, sửa, xóa trên bất kỳ Server nào đều được nhân bản sang Server còn lại qua Java RMI.
3. **Mẫu thiết kế Outbox Pattern & Lưu trữ bền vững**: Dữ liệu và sự kiện đồng bộ được ghi nguyên tử (Atomic Transaction) vào cơ sở dữ liệu SQLite cục bộ ở chế độ WAL (Write-Ahead Logging), không lo mất mát dữ liệu khi sập nguồn.
4. **Chống lặp vòng vô hạn (Loop Prevention) & Tính lũy đẳng (Idempotency)**: Mã định danh UUID toàn cục cho mỗi sự kiện kết hợp bảng `processed_events` giúp loại bỏ hoàn toàn hiện tượng gửi vòng lặp vô hạn (A ➔ B ➔ A) và đảm bảo an toàn khi nhận trùng lặp sự kiện.
5. **Chịu lỗi phân vùng mạng (Partition Tolerance) & Đồng bộ bù (Catch-Up Sync)**: Khi một Server mất kết nối, Server còn lại vẫn phục vụ bình thường. Khi kết nối lại, hệ thống tự động đẩy các sự kiện còn tồn đọng để dữ liệu đạt trạng thái nhất quán sau cùng (Eventual Consistency).
6. **Chuyển đổi dự phòng trong suốt (Transparent Client Failover)**: Khi Server đang kết nối gặp sự cố, Client tự động chuyển sang Server dự phòng ngay trong cuộc gọi mà không làm gián đoạn người dùng.
7. **Phát hiện và xử lý xung đột trực quan (Conflict Detection & Resolution)**: Phát hiện xung đột dữ liệu đồng thời theo số phiên bản (`version`), không ghi đè âm thầm, hiển thị bảng so sánh đối chiếu trực quan để người quản trị giải quyết.

---

## 2. CẤU TRÚC THƯ MỤC DỰ ÁN

```
d:/LTM/RMI CSDL/
├── pom.xml                         # Root Maven POM (Quản lý đa module)
├── common/                         # Module thư viện chung (DTO, Remote Interface, Models)
│   ├── pom.xml
│   └── src/main/java/com/inventory/common/
│       ├── dto/                    # ProductDTO, SyncEventDTO, ServerStatusDTO, ConflictDTO...
│       ├── service/                # InventoryService.java, SynchronizationService.java
│       ├── model/                  # EventType, SyncStatus
│       ├── exception/              # InventoryException, ProductNotFoundException...
│       └── util/                   # JsonUtils.java
├── server/                         # Module máy chủ (RMI Server, SQLite, Sync Engine)
│   ├── pom.xml
│   └── src/main/java/com/inventory/server/
│       ├── config/                 # ServerConfig.java (nạp file cấu hình)
│       ├── database/               # DatabaseManager.java (SQLite WAL, Transaction manager)
│       ├── repository/             # ProductRepository.java, SyncRepository.java
│       ├── service/                # InventoryServiceImpl.java (Client RMI handler)
│       ├── synchronization/        # SynchronizationServiceImpl.java, SyncManager.java
│       └── ServerApplication.java  # Main Server Bootstrap & RMI Registry exporter
├── client/                         # Module giao diện người dùng (JavaFX)
│   ├── pom.xml
│   └── src/main/java/com/inventory/client/
│       ├── config/                 # ClientConfig.java
│       ├── model/                  # ProductModel.java (JavaFX reactive properties)
│       ├── service/                # RmiClientService.java (Failover manager, background monitor)
│       ├── view/                   # MainView.java, ProductDialog.java, ConflictDialog.java
│       ├── ClientApp.java          # JavaFX Application
│       └── ClientLauncher.java     # Entry point cho runnable JAR
├── config/                         # Các file cấu hình mẫu cho triển khai
│   ├── server1.properties          # Cấu hình Server 1 (IP, Ports, DB path, Peer target)
│   ├── server2.properties          # Cấu hình Server 2 (IP, Ports, DB path, Peer target)
│   └── client.properties           # Cấu hình Client (IP cả 2 server, server ưu tiên)
├── docs/                           # Tài liệu thiết kế và hướng dẫn chi tiết
│   ├── ARCHITECTURE.md             # Kiến trúc hệ thống, CAP theorem, Schema DB
│   ├── SEQUENCE_DIAGRAMS.md        # 4 sơ đồ tuần tự Mermaid chi tiết
│   ├── DEPLOYMENT_GUIDE.md         # Hướng dẫn chi tiết chạy 1 máy và 2 máy vật lý + LAN/VM
│   └── TEST_CASES.md               # Kịch bản 8 bài test demo trước hội đồng
└── scripts/                        # Script tiện ích biên dịch và khởi chạy
    ├── build.bat / build.sh        # Biên dịch toàn bộ project sang Fat JARs
    ├── run-server1.bat / .sh       # Chạy Server 1
    ├── run-server2.bat / .sh       # Chạy Server 2
    └── run-client.bat / .sh        # Chạy JavaFX Client
```

---

## 3. CÁC TÀI LIỆU CHI TIẾT TRONG DỰ ÁN

Để chuẩn bị tốt nhất cho báo cáo đồ án, mời bạn tham khảo các tài liệu chuyên sâu:
- [Tài Liệu Kiến Trúc Hệ Thống](file:///d:/LTM/RMI%20CSDL/docs/ARCHITECTURE.md)
- [Sơ Đồ Tuần Tự Đồng Bộ & Chịu Lỗi (Mermaid Sequence Diagrams)](file:///d:/LTM/RMI%20CSDL/docs/SEQUENCE_DIAGRAMS.md)
- [Hướng Dẫn Triển Khai Trên 2 Máy Vật Lý & Máy Ảo Mạng LAN](file:///d:/LTM/RMI%20CSDL/docs/DEPLOYMENT_GUIDE.md)
- [Kịch Bản 8 Bài Test Trình Diễn Trước Giảng Viên](file:///d:/LTM/RMI%20CSDL/docs/TEST_CASES.md)

---

## 4. HƯỚNG DẪN CHẠY NHANH (QUICK START TRÊN 1 MÁY TÍNH)

Dự án đã cấu hình sẵn cổng tách biệt (`1099` cho Server 1, `1100` cho Server 2) để kiểm thử và demo ngay trên một máy tính:

### Bước 1: Build mã nguồn
```bash
mvn clean package -DskipTests
```
*(Hoặc nhấp đúp file `scripts/build.bat`)*

### Bước 2: Khởi động Server 1
Mở một cửa sổ Terminal/CMD 1:
```bash
scripts\run-server1.bat
```

### Bước 3: Khởi động Server 2
Mở một cửa sổ Terminal/CMD 2:
```bash
scripts\run-server2.bat
```
*(Hai server sẽ tự động nhận diện và bắt tay kết nối đồng bộ)*

### Bước 4: Khởi động Client
Mở một hoặc nhiều cửa sổ Terminal/CMD 3, 4:
```bash
scripts\run-client.bat
```

---

## 5. HƯỚNG DẪN KẾT NỐI QUA MẠNG TAILSCALE (2 MÁY THỰC)

Dự án đã cấu hình sẵn IP Tailscale cho 2 máy tính thực tế:
- **Máy 1 (Server 1)**: `100.115.205.94` (RMI Registry Port: `1099`, Export Port: `10991`)
- **Máy 2 (Server 2)**: `100.99.68.41` (RMI Registry Port: `1099`, Export Port: `10991`)

### Các bước chạy:
1. **Trên Máy 1**: Chạy `scripts\run-server1.bat` (Server 1 khởi động với IP Tailscale `100.115.205.94`).
2. **Trên Máy 2**: Chạy `scripts\run-server2.bat` (Server 2 khởi động với IP Tailscale `100.99.68.41`).
3. Hai Server tự động phát hiện nhau qua mạng Tailscale và đồng bộ dữ liệu hai chiều.
4. **Khởi động Client**: Chạy `scripts\run-client.bat` trên Máy 1, Máy 2 hoặc bất kỳ thiết bị nào cùng mạng Tailscale.

*(Nếu muốn chạy cả 2 server trên cùng 1 PC để test nhanh, dùng cấu hình `config/local-server1.properties` và `config/local-server2.properties`)*

---

## 6. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG (AUTOMATED TEST SUITE)

Hệ thống có bộ bài kiểm thử tự động toàn diện mô phỏng toàn bộ các tình huống phân tán phức tạp:

```
[INFO] Running com.inventory.server.DistributedSystemIntegrationTest
--- RUNNING TEST 1: Sync Server 1 -> Server 2 ---
>>> TEST 1 PASSED: Product synchronized from Server 1 to Server 2 successfully.

--- RUNNING TEST 2: Sync Server 2 -> Server 1 ---
>>> TEST 2 PASSED: Update on Server 2 successfully synchronized back to Server 1.

--- RUNNING TEST 3: Multiple Concurrent Clients ---
>>> TEST 3 PASSED: 10 concurrent clients added products successfully without deadlocks or data loss.

--- RUNNING TEST 4: Server 2 Offline & Catch-Up Sync ---
>>> TEST 4 PASSED: Server 2 caught up with all missed events upon restart.

--- RUNNING TEST 5: Server 1 Offline & Client Failover ---
>>> TEST 5 PASSED: Client operates seamlessly on Server 2 when Server 1 goes offline.

--- RUNNING TEST 6: Concurrent Conflict Detection ---
Conflict successfully detected and logged: Concurrent edit: Local modified by SERVER_1 (v1) vs Remote modified by SERVER_2 (v1)
>>> TEST 6 PASSED: Concurrent conflict detected, logged, and resolved safely without silent data loss.

--- RUNNING TEST 7: Idempotency & Loop Prevention ---
>>> TEST 7 PASSED: Idempotency verified. Repeated events are acknowledged safely without duplicate effects.

--- RUNNING TEST 8: Server Restart & Persistence ---
>>> TEST 8 PASSED: All records persist reliably across complete server restarts.

[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 7. KẾT LUẬN

Hệ thống đã hoàn thiện 100% các yêu cầu của đồ án:
- Sử dụng chuẩn công nghệ Java RMI thuần túy, JDBC, SQLite, JavaFX.
- Không dùng mã giả hay dữ liệu giả lập.
- Đầy đủ mã nguồn, cấu hình, kiểm thử tự động và tài liệu đồ án đạt chuẩn xuất sắc.
