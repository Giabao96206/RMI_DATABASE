# KIẾN TRÚC HỆ THỐNG QUẢN LÝ KHO PHÂN TÁN (JAVA RMI)

## 1. TỔNG QUAN HỆ THỐNG

Hệ thống quản lý kho hàng phân tán được thiết kế theo mô hình **Active-Active 2-Server Cluster** với giao tiếp mạng độc quyền thông qua **Java RMI (Remote Method Invocation)**, sử dụng cơ sở dữ liệu nhúng **SQLite** độc lập trên mỗi máy chủ và giao diện người dùng hiện đại viết bằng **JavaFX**.

Hệ thống giải quyết các bài toán cốt lõi của môn học Lập trình mạng và Hệ thống phân tán:
- Giao tiếp RPC/RMI giữa Client–Server và Server–Server.
- Đồng bộ dữ liệu 2 chiều gần thời gian thực (Near Real-time Bidirectional Active Synchronization).
- Lưu trữ bền vững (Persistence) và toàn vẹn giao dịch (ACID Transactions với SQLite WAL mode).
- Khả năng chịu lỗi và tính sẵn sàng cao (High Availability & Partition Tolerance): Khi một Server sập hoặc mạng bị ngắt, Server còn lại vẫn phục vụ độc lập, Client tự động chuyển đổi dự phòng (Automatic Failover).
- Tự động đồng bộ bù khi tái kết nối (Catch-up / Recovery Synchronization).
- Chống lặp vòng vô hạn (Infinite Loop Prevention) và xử lý lặp sự kiện (Idempotency).
- Phát hiện xung đột dữ liệu đồng thời (Concurrent Conflict Detection) theo số phiên bản (Version Vector / Optimistic Concurrency).

---

## 2. SƠ ĐỒ KIẾN TRÚC TỔNG THỂ

```
+-----------------------------------------------------------------------------------+
|                                  CLIENT LAYER                                     |
|  (Các máy ảo VMware/VirtualBox hoặc các máy tính Client chạy JavaFX Application)  |
|                                                                                   |
|  +--------------------+    +--------------------+    +--------------------+       |
|  |     Client 1       |    |     Client 2       |    |     Client N       |       |
|  |  (JavaFX Client)   |    |  (JavaFX Client)   |    |  (JavaFX Client)   |       |
|  +---------+----------+    +---------+----------+    +---------+----------+       |
+------------|-------------------------|-------------------------|------------------+
             |                         |                         |
             | Java RMI                | Java RMI                | Java RMI
             | (Port 1099/1100)        | (Port 1099/1100)        | (Port 1099/1100)
             v                         v                         v
+-----------------------------------------------------------------------------------+
|                                  SERVER LAYER                                     |
|                                                                                   |
|   +------------------------------------+   +------------------------------------+ |
|   |         SERVER 1 (Máy A)           |   |         SERVER 2 (Máy B)           | |
|   |  - RMI Registry (Port 1099)        |   |  - RMI Registry (Port 1100)        | |
|   |  - InventoryService (Port 10991)   |   |  - InventoryService (Port 11001)   | |
|   |  - SynchronizationService          |   |  - SynchronizationService          | |
|   |  - Background SyncWorker           |   |  - Background SyncWorker           | |
|   |  - Outbox Pattern Engine           |   |  - Outbox Pattern Engine           | |
|   +-----------------+------------------+   +-----------------+------------------+ |
|                     |                                        |                    |
|                     |     Java RMI Server-to-Server Sync     |                    |
|                     +<======================================>+                    |
|                               (Hai chiều chủ động)                                |
+-----------------------------------------------------------------------------------+
|                                 DATABASE LAYER                                    |
|                                                                                   |
|           +--------------------+                  +--------------------+          |
|           | SQLite Database 1  |                  | SQLite Database 2  |          |
|           | (inventory_s1.db)  |                  | (inventory_s2.db)  |          |
|           +--------------------+                  +--------------------+          |
+-----------------------------------------------------------------------------------+
```

---

## 3. CÁC MODULE VÀ THÀNH PHẦN

Dự án được tổ chức theo mô hình Maven Multi-module:

### 3.1. Module `common`
Chứa các thành phần dùng chung giữa Client và Server:
- **Remote Interfaces**:
  - `InventoryService`: Giao diện từ xa cho Client gọi Server (CRUD sản phẩm, thống kê, kiểm tra trạng thái, xem lịch sử đồng bộ, giải quyết xung đột).
  - `SynchronizationService`: Giao diện từ xa cho Server gọi Server (nhận batch sự kiện `receiveEvents`, truy vấn sự kiện tồn đọng `getPendingEvents`, xác nhận đã xử lý `acknowledgeEvents`, kiểm tra trạng thái `getSyncStatus`, heartbeat `ping`).
- **DTOs (Data Transfer Objects)**:
  - `ProductDTO`: Chứa thông tin sản phẩm, UUID, mã, tên, danh mục, tồn kho, giá, version, thời gian, cờ xóa mềm (`isDeleted`), server sửa cuối cùng (`lastModifiedByServer`).
  - `SyncEventDTO`: Bản ghi sự kiện đồng bộ (Event ID UUID, Entity Type, Payload JSON, Origin Server ID, Target Server ID, Timestamp, Version, Status).
  - `ServerStatusDTO`: Thông tin giám sát máy chủ (IP, Port, Online, Uptime, Pending Syncs, Trạng thái kết nối Peer).
  - `ConflictDTO`: Bản ghi xung đột phát hiện được (ID, Local Data JSON, Remote Data JSON, thời điểm, trạng thái xử lý).
  - `InventoryStatsDTO`: Số liệu thống kê tồn kho (tổng mặt hàng, tổng số lượng, tổng giá trị kho).

### 3.2. Module `server`
- **`DatabaseManager`**: Quản trị kết nối SQLite JDBC, kích hoạt chế độ WAL (Write-Ahead Logging) cho hiệu năng truy xuất đồng thời, quản lý ACID Transactions với phương thức `executeInTransaction()`.
- **`ProductRepository`**: Thao tác CRUD bảng `products` với kiểm tra khóa lạc quan (Optimistic Concurrency Control) theo trường `version`.
- **`SyncRepository`**: Thao tác các bảng `synchronization_events`, `processed_events`, `sync_conflicts`.
- **`InventoryServiceImpl`**: Hiện thực `InventoryService`, cung cấp các nghiệp vụ kho cho Client, tự động ghi sự kiện Outbox vào SQLite trong cùng Transaction khi có thao tác ghi.
- **`SynchronizationServiceImpl`**: Hiện thực `SynchronizationService`, tiếp nhận sự kiện từ Server đối tác, kiểm tra Idempotency, phát hiện xung đột và ghi dữ liệu.
- **`SyncManager`**: Background worker định kỳ kiểm tra heartbeat của Peer, đẩy các sự kiện đang chờ (`status = 'PENDING'`) sang Peer, và thực hiện Catch-up sync khi kết nối mạng được phục hồi.
- **`ServerApplication`**: Điểm khởi động Server, nạp file cấu hình, thiết lập `java.rmi.server.hostname`, khởi tạo RMI Registry và bind các Remote Services.

### 3.3. Module `client`
- **`RmiClientService`**: Quản lý kết nối RMI tới cả 2 Server, giám sát trạng thái mạng nền (background health polling), tự động chuyển đổi dự phòng (**Transparent Failover**) khi gọi RMI bị lỗi `RemoteException`.
- **`ProductModel`**: Adapter bao bọc `ProductDTO` thành các JavaFX Observable Properties cho TableView phản ứng tức thời.
- **`MainView`**: Giao diện người dùng JavaFX gồm 3 Tab: Quản lý sản phẩm, Giám sát cụm Server & Failover, Lịch sử đồng bộ & Xử lý xung đột.
- **`ProductDialog` & `ConflictDialog`**: Hộp thoại thêm/sửa sản phẩm và hộp thoại so sánh trực quan đối chiếu dữ liệu xung đột để người quản trị lựa chọn.

---

## 4. THIẾT KẾ CƠ SỞ DỮ LIỆU SQLITE

Mỗi Server sở hữu một cơ sở dữ liệu SQLite riêng biệt hoàn toàn.

### 4.1. Bảng `products` (Kho hàng)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| `id` | VARCHAR(36) | PRIMARY KEY | UUID định danh duy nhất toàn cục |
| `product_code` | VARCHAR(50) | NOT NULL, UNIQUE | Mã sản phẩm người dùng quản lý |
| `product_name` | VARCHAR(255) | NOT NULL | Tên sản phẩm |
| `category` | VARCHAR(100) | NOT NULL | Danh mục hàng hóa |
| `quantity` | INTEGER | NOT NULL | Số lượng tồn kho (>= 0) |
| `price` | REAL | NOT NULL | Đơn giá sản phẩm (>= 0.0) |
| `created_at` | INTEGER | NOT NULL | Epoch timestamp lúc tạo |
| `updated_at` | INTEGER | NOT NULL | Epoch timestamp lúc sửa cuối |
| `version` | INTEGER | NOT NULL | Số phiên bản dữ liệu (Optimistic Lock) |
| `is_deleted` | INTEGER | NOT NULL, DEFAULT 0 | Cờ xóa mềm (0: Hoạt động, 1: Đã xóa) |
| `last_modified_by_server` | VARCHAR(50) | NOT NULL | Mã Server thực hiện sửa đổi cuối cùng |

### 4.2. Bảng `synchronization_events` (Hàng đợi sự kiện gửi đi - Outbox Pattern)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| `event_id` | VARCHAR(36) | PRIMARY KEY | UUID định danh sự kiện đồng bộ |
| `entity_type` | VARCHAR(50) | NOT NULL | Loại thực thể (`PRODUCT`) |
| `entity_id` | VARCHAR(36) | NOT NULL | ID của sản phẩm bị thay đổi |
| `event_type` | VARCHAR(20) | NOT NULL | `CREATE`, `UPDATE`, hoặc `DELETE` |
| `payload_json` | TEXT | NOT NULL | JSON toàn văn của ProductDTO tại thời điểm đó |
| `origin_server_id` | VARCHAR(50) | NOT NULL | Server sinh ra sự kiện (`SERVER_1` / `SERVER_2`) |
| `target_server_id` | VARCHAR(50) | NOT NULL | Server nhận (`SERVER_2` / `SERVER_1`) |
| `timestamp` | INTEGER | NOT NULL | Epoch timestamp của sự kiện |
| `version` | INTEGER | NOT NULL | Version của dữ liệu trong sự kiện |
| `status` | VARCHAR(20) | NOT NULL | `PENDING`, `SUCCESS`, `FAILED`, `CONFLICT` |
| `retry_count` | INTEGER | NOT NULL, DEFAULT 0 | Số lần đã thử gửi lại |
| `last_error` | TEXT | | Chi tiết lỗi mạng hoặc xử lý |

### 4.3. Bảng `processed_events` (Nhật ký chống trùng lặp - Idempotency Table)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| `event_id` | VARCHAR(36) | PRIMARY KEY | ID sự kiện nhận được từ Peer Server |
| `origin_server_id` | VARCHAR(50) | NOT NULL | Server gửi sự kiện |
| `processed_at` | INTEGER | NOT NULL | Thời điểm xử lý thành công |
| `status` | VARCHAR(20) | NOT NULL | Trạng thái ghi nhận (`SUCCESS`, `CONFLICT`) |

### 4.4. Bảng `sync_conflicts` (Nhật ký xung đột dữ liệu)
| Tên cột | Kiểu dữ liệu | Ràng buộc | Ý nghĩa |
|---|---|---|---|
| `conflict_id` | VARCHAR(36) | PRIMARY KEY | UUID định danh xung đột |
| `event_id` | VARCHAR(36) | NOT NULL | Sự kiện gây ra xung đột |
| `entity_id` | VARCHAR(36) | NOT NULL | ID sản phẩm bị xung đột |
| `local_data` | TEXT | NOT NULL | Bản chụp JSON của dữ liệu tại Server sở tại |
| `remote_data` | TEXT | NOT NULL | Bản chụp JSON của dữ liệu gửi từ Server đối tác |
| `detected_at` | INTEGER | NOT NULL | Thời điểm phát hiện xung đột |
| `resolved` | INTEGER | NOT NULL, DEFAULT 0 | 0: Chưa giải quyết, 1: Đã giải quyết |
| `resolution_note` | TEXT | | Ghi chú phương án giải quyết của người dùng |

---

## 5. GIỚI HẠN CỦA MÔ HÌNH EVENTUAL CONSISTENCY

Theo định lý **CAP (Consistency, Availability, Partition Tolerance)**:
- Hệ thống này ưu tiên **AP (Availability và Partition Tolerance)**: Khi 2 Server bị đứt kết nối mạng vật lý (Network Partition), cả 2 Server **vẫn tiếp tục phục vụ** ghi và đọc cho các Client cục bộ của mình.
- Dữ liệu đạt trạng thái **Nhất quán sau cùng (Eventual Consistency)**: Khi kết nối mạng phục hồi, dữ liệu sẽ được tự động đồng bộ bù.
- **Tình huống xung đột**: Nếu hai Client sửa cùng một sản phẩm trên 2 Server trong lúc mạng bị ngắt, không thể đảm bảo tự động nhất quán tức thời mà không làm mất dữ liệu của một bên. Hệ thống áp dụng chính sách: **Phát hiện xung đột -> Đóng băng thay đổi không đè bừa bãi -> Lưu lại cả 2 phiên bản vào bảng `sync_conflicts` -> Cung cấp giao diện trực quan cho Người Quản Trị lựa chọn phiên bản chính xác hoặc hợp nhất dữ liệu**.
