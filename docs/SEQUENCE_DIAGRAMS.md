# SƠ ĐỒ TUẦN TỰ (SEQUENCE DIAGRAMS)

Tài liệu mô tả chi tiết các luồng giao tiếp mạng và đồng bộ dữ liệu giữa Client và 2 Server sử dụng Mermaid Sequence Diagrams.

---

## 1. LUỒNG ĐỒNG BỘ 2 CHIỀU THỜI GIAN THỰC (SERVER 1 ➔ SERVER 2)

Kịch bản: Client kết nối tới Server 1 và thêm mới hoặc sửa đổi một sản phẩm trong kho.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant S1 as Server 1 (RMI Service)
    participant DB1 as SQLite 1
    participant Worker1 as S1 SyncWorker
    participant S2 as Server 2 (RMI Sync)
    participant DB2 as SQLite 2

    Client->>S1: addProduct(ProductDTO) qua Java RMI
    activate S1
    S1->>S1: Validate dữ liệu & sinh UUID
    Note over S1,DB1: Mở SQLite Transaction (ACID)
    S1->>DB1: INSERT INTO products (id, code, ver=1, last_mod='SERVER_1'...)
    S1->>DB1: INSERT INTO synchronization_events (event_id, payload, status='PENDING'...)
    Note over S1,DB1: Commit SQLite Transaction
    S1-->>Client: Trả về ProductDTO hoàn chỉnh
    deactivate S1

    S1->>Worker1: triggerImmediateSync()
    activate Worker1
    Worker1->>DB1: SELECT * FROM synchronization_events WHERE status='PENDING'
    DB1-->>Worker1: Danh sách events
    Worker1->>S2: receiveEvents(List<SyncEventDTO>) qua Java RMI
    activate S2
    S2->>DB2: SELECT 1 FROM processed_events WHERE event_id = ?
    alt Chưa xử lý (Không trùng lặp)
        Note over S2,DB2: Mở SQLite Transaction
        S2->>DB2: forceUpsert(ProductDTO từ payload)
        S2->>DB2: INSERT INTO processed_events (event_id, status='SUCCESS')
        Note over S2,DB2: Commit Transaction
        S2-->>Worker1: return true (Thành công)
    else Đã từng xử lý trước đó (Idempotent)
        S2-->>Worker1: return true (Bỏ qua an toàn)
    end
    deactivate S2

    Worker1->>DB1: UPDATE synchronization_events SET status='SUCCESS' WHERE event_id IN (...)
    deactivate Worker1
```

---

## 2. LUỒNG XỬ LÝ SERVER MẤT KẾT NỐI VÀ ĐỒNG BỘ BÙ (CATCH-UP SYNC)

Kịch bản: Server 2 bị tắt hoặc đứt mạng LAN. Server 1 vẫn tiếp tục phục vụ Client. Khi Server 2 khởi động lại, hai Server tự động trao đổi các sự kiện còn thiếu.

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant S1 as Server 1
    participant DB1 as SQLite 1
    participant Worker1 as S1 SyncWorker
    participant S2 as Server 2
    participant DB2 as SQLite 2

    Note over S2: [SỰ CỐ] Server 2 bị tắt / Mất nguồn / Đứt cáp LAN
    Client->>S1: updateProduct(ProductDTO)
    activate S1
    S1->>DB1: Cập nhật sản phẩm & Lưu Event (status='PENDING')
    S1-->>Client: Trả về kết quả thành công
    deactivate S1

    Worker1->>S2: ping() hoặc receiveEvents(...)
    Note over Worker1,S2: RemoteException / ConnectionRefusedException
    Worker1->>Worker1: Đánh dấu Peer = OFFLINE
    Worker1->>DB1: Cập nhật retry_count, giữ nguyên status='PENDING'

    Note over S2: [PHỤC HỒI] Server 2 được khởi động lại
    S2->>S2: Khởi tạo SQLite 2 & Start RMI Registry
    
    Worker1->>S2: ping() chu kỳ kế tiếp (Định kỳ 2s)
    S2-->>Worker1: pong (true)
    Note over Worker1: Phát hiện Peer vừa ONLINE trở lại!

    rect rgb(230, 245, 230)
    Note over Worker1,S2: QUÁ TRÌNH ĐỒNG BỘ BÙ (CATCH-UP SYNC)
    Worker1->>DB1: Lấy các sự kiện còn tồn đọng (status='PENDING')
    DB1-->>Worker1: Pending events tích lũy trong thời gian S2 offline
    Worker1->>S2: receiveEvents(pendingEvents)
    S2->>DB2: Áp dụng toàn bộ sự kiện vào SQLite 2
    S2-->>Worker1: Xác nhận đã nhận đủ
    Worker1->>DB1: Đánh dấu tất cả events thành 'SUCCESS'

    Worker1->>S2: getPendingEvents(target='SERVER_1')
    S2-->>Worker1: Các sự kiện S2 sinh ra lúc cô lập (nếu có)
    Worker1->>DB1: Áp dụng vào SQLite 1
    end
```

---

## 3. LUỒNG CHUYỂN ĐỔI DỰ PHÒNG TỰ ĐỘNG (CLIENT TRANSPARENT FAILOVER)

Kịch bản: Client đang kết nối với Server 1, Server 1 đột ngột ngừng hoạt động. Client tự động phát hiện lỗi và chuyển đổi cuộc gọi sang Server 2 mà không làm gián đoạn người dùng.

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng JavaFX
    participant UI as JavaFX View
    participant ClientService as RmiClientService
    participant S1 as Server 1 (Chính)
    participant S2 as Server 2 (Dự phòng)

    User->>UI: Nhấn nút "+ Thêm Mới" hoặc "Xóa"
    UI->>ClientService: addProduct(ProductDTO)
    activate ClientService
    ClientService->>S1: addProduct(ProductDTO) qua Java RMI
    Note over ClientService,S1: [SỰ CỐ] Server 1 chết đột ngột -> Ném RemoteException!

    rect rgb(255, 235, 235)
    Note over ClientService: TỰ ĐỘNG KÍCH HOẠT FAILOVER
    ClientService->>ClientService: switchServer("SERVER_2", "Lỗi kết nối Server 1")
    ClientService->>UI: Gửi thông báo: "Đã tự động chuyển sang Server 2"
    ClientService->>S2: Thử lại addProduct(ProductDTO) trên Server 2
    S2-->>ClientService: Trả về kết quả thành công!
    end

    ClientService-->>UI: Trả về dữ liệu cho giao diện
    deactivate ClientService
    UI->>User: Cập nhật TableView và đổi Badge "KẾT NỐI: SERVER 2 (ONLINE)"
```

---

## 4. LUỒNG PHÁT HIỆN VÀ GIẢI QUYẾT XUNG ĐỘT DỮ LIỆU ĐỒNG THỜI

Kịch bản: Trong lúc bị phân rã mạng, Client A sửa giá sản phẩm X trên Server 1, Client B đồng thời sửa tồn kho sản phẩm X trên Server 2. Khi tái kết nối, xung đột được phát hiện và xử lý.

```mermaid
sequenceDiagram
    autonumber
    participant S1 as Server 1
    participant DB1 as SQLite 1
    participant S2 as Server 2
    participant DB2 as SQLite 2
    actor Admin as Người Quản Trị (Client)

    Note over S1,S2: Cả 2 Server cùng sửa đổi sản phẩm X lúc mạng bị ngắt
    S1->>DB1: Sản phẩm X: version=2, modified_by='SERVER_1'
    S2->>DB2: Sản phẩm X: version=2, modified_by='SERVER_2'

    Note over S1,S2: Mạng kết nối trở lại, SyncWorker đẩy sự kiện
    S2->>S1: receiveEvents(Event update SP X từ S2, version=2)
    activate S1
    S1->>DB1: Lấy thông tin hiện tại của SP X
    DB1-->>S1: SP X (Local version=2, modified_by='SERVER_1')
    
    S1->>S1: Kiểm tra: local.version == remote.version AND local != remote!
    Note over S1: [PHÁT HIỆN XUNG ĐỘT!] Không ghi đè làm mất dữ liệu!
    
    S1->>DB1: INSERT INTO sync_conflicts (conflict_id, local_json, remote_json, status=0)
    S1->>DB1: INSERT INTO processed_events (event_id, status='CONFLICT')
    S1-->>S2: Đã tiếp nhận và ghi nhận xung đột
    deactivate S1

    Admin->>S1: getConflicts()
    S1-->>Admin: Trả về danh sách xung đột
    Admin->>Admin: Mở hộp thoại so sánh đối chiếu Local vs Remote
    Admin->>S1: resolveConflict(conflictId, resolvedProduct)
    activate S1
    S1->>DB1: Cập nhật SP X theo phương án Admin chọn (version tăng tiếp)
    S1->>DB1: UPDATE sync_conflicts SET resolved=1
    S1->>DB1: Tạo SyncEvent mới gửi sang Server 2
    S1-->>Admin: Báo giải quyết thành công
    deactivate S1
```
