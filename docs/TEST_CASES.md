# HƯỚNG DẪN TRÌNH DIỄN CÁC BÀI KIỂM THỬ (TEST CASES DEMO GUIDE)

Tài liệu này hướng dẫn chi tiết cách thực hiện và trình diễn **8 bài kiểm thử bắt buộc** trước Giảng viên hướng dẫn và Hội đồng chấm đồ án.

---

## TỔNG HỢP KẾT QUẢ KIỂM THỬ TỰ ĐỘNG

Dự án đã tích hợp bộ kiểm thử tự động toàn diện trong class `com.inventory.server.DistributedSystemIntegrationTest` với kết quả:

| Test Case | Mô tả bài kiểm thử | Trạng thái |
|---|---|---|
| **Test 1** | Đồng bộ dữ liệu Server 1 ➔ Server 2 | **PASSED (100%)** |
| **Test 2** | Đồng bộ dữ liệu Server 2 ➔ Server 1 | **PASSED (100%)** |
| **Test 3** | 10 Client đồng thời ghi dữ liệu xen kẽ 2 Server | **PASSED (100%)** |
| **Test 4** | Server 2 mất kết nối ➔ Server 1 tích lũy hàng đợi ➔ Khởi động lại S2 ➔ Tự động đồng bộ bù | **PASSED (100%)** |
| **Test 5** | Server 1 tắt ➔ Client tự động chuyển đổi sang Server 2 (Failover) | **PASSED (100%)** |
| **Test 6** | Phát hiện xung đột sửa đổi đồng thời ➔ Không mất dữ liệu ➔ Giải quyết xung đột | **PASSED (100%)** |
| **Test 7** | Chống gửi lặp vô hạn và tính lũy đẳng (Idempotency) | **PASSED (100%)** |
| **Test 8** | Khởi động lại Server ➔ Dữ liệu lưu bền vững trong SQLite | **PASSED (100%)** |

Để chạy kiểm thử tự động bất cứ lúc nào:
```bash
mvn test
```

---

## HƯỚNG DẪN TRÌNH DIỄN TRỰC QUAN TRÊN GIAO DIỆN (DEMO)

Chuẩn bị trước khi demo: Khởi động Server 1, Server 2 và mở ít nhất 2 cửa sổ Client (Client A kết nối Server 1, Client B kết nối Server 2).

---

### Test 1: Đồng bộ dữ liệu Server 1 ➔ Server 2
- **Mục tiêu**: Chứng minh sản phẩm tạo trên Server 1 ngay lập tức xuất hiện tại Server 2.
- **Thực hiện**:
  1. Trên **Client A** (đang trỏ vào Server 1), bấm nút **"+ Thêm Mới"**.
  2. Nhập: Mã SP: `SP-DEMO-01`, Tên: `Chuột Logitech MX Master 3`, Danh mục: `Phụ Kiện`, Số lượng: `50`, Giá: `2400000`. Bấm **"Tạo Mới"**.
  3. Quan sát log trên cửa sổ Terminal của Server 1: `[SYNC-OUT] Pushing 1 pending events to peer SERVER_2`.
  4. Quan sát log trên cửa sổ Terminal của Server 2: `[SYNC-IN] Received 1 events ... [SYNC-APPLY] Creating new product from peer`.
  5. Trên **Client B** (kết nối Server 2), bấm **"🔄 Làm Mới"** (hoặc xem bảng tự cập nhật): Sản phẩm `SP-DEMO-01` hiển thị đầy đủ với Server sửa cuối là `SERVER_1`.

---

### Test 2: Đồng bộ dữ liệu Server 2 ➔ Server 1
- **Mục tiêu**: Chứng minh hệ thống đồng bộ 2 chiều đối xứng (Active-Active).
- **Thực hiện**:
  1. Trên **Client B** (đang kết nối Server 2), chọn sản phẩm `SP-DEMO-01` vừa tạo.
  2. Bấm nút **"✏ Chỉnh Sửa"**. Thay đổi số lượng từ `50` thành `45`, giá từ `2400000` thành `2300000`. Bấm **"Lưu Thay Đổi"**.
  3. Trên **Client A** (Server 1), bấm **"🔄 Làm Mới"**.
  4. Xác minh: Số lượng đã đổi thành `45`, giá thành `2,300,000 đ`, phiên bản tăng lên `v2`, và Server sửa cuối đổi thành `SERVER_2`.

---

### Test 3: Nhiều Client truy cập đồng thời
- **Mục tiêu**: Chứng minh khả năng xử lý đồng thời, không xung đột khóa SQLite hay trùng UUID.
- **Thực hiện**:
  1. Mở 3–5 cửa sổ Client khác nhau.
  2. Cùng lúc thực hiện thêm mới các sản phẩm khác nhau trên các Client.
  3. Xác minh: Tất cả sản phẩm đều được lưu thành công, số lượng tồn kho và tổng giá trị tính toán chính xác trên thanh thống kê của mọi Client.

---

### Test 4: Xử lý Server 2 mất kết nối & Tự động đồng bộ bù (Catch-Up Sync)
- **Mục tiêu**: Chứng minh tính chịu lỗi phân vùng mạng và khả năng phục hồi dữ liệu.
- **Thực hiện**:
  1. Trên cửa sổ Terminal của **Server 2**, nhấn `Ctrl + C` để tắt Server 2 (giả lập sự cố mất điện / đứt mạng).
  2. Trên **Client A** (Server 1):
     - Mở Tab **"Giám Sát Cụm Server"**: Thấy Server 2 chuyển sang màu đỏ `OFFLINE`.
     - Quay lại Tab **"Quản Lý Kho Hàng"**, thêm mới sản phẩm: `SP-OFFLINE-01`, Tên: `Bàn Phím Cơ Keychron K2`.
     - Thêm thành công vào Server 1.
     - Kiểm tra Tab **"Giám Sát"**: Số lượng `Event chờ đồng bộ` của Server 1 tăng lên `1`.
  3. Khởi động lại **Server 2**: Chạy lại lệnh mở Server 2.
  4. Quan sát log: Sau 1–2 giây, Server 1 và Server 2 bắt tay lại. Log Server 1 hiện: `>>> PEER SERVER_2 RECONNECTED! Starting catch-up synchronization... [SYNC-OUT] Successfully synchronized`.
  5. Mở **Client B** kết nối Server 2: Sản phẩm `SP-OFFLINE-01` đã tự động xuất hiện nguyên vẹn trong cơ sở dữ liệu của Server 2!

---

### Test 5: Server 1 mất kết nối & Client tự động Failover
- **Mục tiêu**: Chứng minh tính sẵn sàng cao (High Availability), Client không bị treo khi máy chủ chính gặp sự cố.
- **Thực hiện**:
  1. Đang mở Client đang kết nối tới **Server 1**.
  2. Tắt cửa sổ Terminal của **Server 1** (`Ctrl + C`).
  3. Trên giao diện Client, bấm thêm hoặc sửa một sản phẩm bất kỳ.
  4. Quan sát: Client phát hiện `RemoteException` từ Server 1, ngay lập tức kích hoạt cơ chế Failover, chuyển kết nối sang Server 2 và thực hiện tiếp thao tác thành công.
  5. Huy hiệu trên Header của Client tự động đổi thành: `KẾT NỐI HIỆN TẠI: SERVER_2 (ONLINE)`.
  6. Mở Tab **"Giám Sát Cụm Server"**, khung nhật ký bên dưới ghi rõ: `[HH:mm:ss] Switched to SERVER_2: Automated failover: Server 1 is offline`.

---

### Test 6: Phát hiện xung đột dữ liệu & Xử lý xung đột trực quan
- **Mục tiêu**: Chứng minh chính sách bảo vệ dữ liệu, không âm thầm ghi đè khi có sửa đổi đồng thời.
- **Thực hiện**:
  1. Ngắt kết nối mạng giữa 2 Server (hoặc mở Tab **"Lịch Sử Đồng Bộ & Xung Đột"**).
  2. Khi có xung đột đồng thời, hệ thống không ghi đè mất dữ liệu mà ghi nhận vào bảng **"Danh Sách Xung Đột Dữ Liệu"**.
  3. Chọn hàng xung đột trong bảng, bấm nút **"⚙ Giải Quyết Xung Đột Được Chọn"**.
  4. Hộp thoại **ConflictDialog** hiện lên, trình bày cột đối chiếu song song:
     - Giá trị của Server hiện tại (Local)
     - Giá trị của Server đối tác (Remote)
     - Làm nổi bật các trường bị lệch giá trị (màu cam/đỏ).
  5. Người quản trị có thể bấm chọn *"Ưu tiên lấy Local"*, *"Ưu tiên lấy Remote"*, hoặc chỉnh sửa trực tiếp giá trị chốt cuối cùng.
  6. Bấm **OK**: Xung đột được đánh dấu đã giải quyết, dữ liệu chốt được ghi nhận và gửi đồng bộ sang Server còn lại.

---

### Test 7: Chống đồng bộ lặp (Idempotency)
- **Mục tiêu**: Kiểm tra cơ chế bảng `processed_events`.
- **Thực hiện**:
  1. Khi một sự kiện đã được xử lý (Event ID đã nằm trong bảng `processed_events`), nếu mạng bị chập chờn khiến sự kiện đó bị gửi lại lần 2 hoặc lần 3, Server nhận sẽ kiểm tra `isEventProcessed()` và trả về kết quả thành công mà không chèn trùng sản phẩm hay tăng số lượng sai lệch.
  2. Điều này được kiểm chứng qua `test7_IdempotencyAndNoDuplicateReplay` đạt 100% PASS.

---

### Test 8: Khởi động lại Server & Lưu trữ bền vững
- **Mục tiêu**: Chứng minh dữ liệu thực sự được lưu vào SQLite trên đĩa cứng, không phụ thuộc bộ nhớ RAM.
- **Thực hiện**:
  1. Tắt hoàn toàn cả 2 Server và tất cả Client.
  2. Kiểm tra thư mục `data/`: thấy các file `inventory_server1.db` và `inventory_server2.db` có dung lượng và chứa dữ liệu thực.
  3. Khởi động lại cả 2 Server và Client.
  4. Toàn bộ danh sách sản phẩm, các chỉ số tồn kho và lịch sử đồng bộ đều được khôi phục nguyên vẹn 100%.
