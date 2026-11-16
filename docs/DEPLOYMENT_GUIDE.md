# HƯỚNG DẪN TRIỂN KHAI HỆ THỐNG PHÂN TÁN (DEPLOYMENT GUIDE)

Tài liệu này cung cấp hướng dẫn chi tiết từng bước để chạy hệ thống trên **1 máy tính cá nhân** (kiểm thử nhanh) và **2 máy tính vật lý + nhiều máy ảo** trong mạng LAN thực tế.

---

## 1. MÔI TRƯỜNG YÊU CẦU

- **Hệ điều hành**: Windows 10/11, Ubuntu Linux 20.04+, hoặc macOS.
- **Java JDK**: JDK 17, 21 hoặc 22 (đã cài đặt và có trong biến môi trường `PATH`).
- **Apache Maven**: Phiên bản 3.8+ (nếu muốn build lại mã nguồn).
- **Phần mềm máy ảo (tùy chọn)**: VMware Workstation / Player hoặc Oracle VirtualBox (để tạo các máy ảo Client).

Kiểm tra trên Terminal / PowerShell:
```bash
java -version
mvn -v
```

---

## 2. CHẾ ĐỘ 1: CHẠY TRÊN 1 MÁY TÍNH CÁ NHÂN (DEMO NHANH)

Hệ thống được thiết kế sẵn cấu hình để chạy 2 tiến trình Server độc lập với 2 cơ sở dữ liệu SQLite riêng biệt và mở nhiều cửa sổ Client cùng lúc trên 1 PC:

- **Server 1**: RMI Registry port `1099`, Export port `10991`, Database `data/inventory_server1.db`
- **Server 2**: RMI Registry port `1100`, Export port `11001`, Database `data/inventory_server2.db`
- **Client**: Kết nối mặc định tới Server 1 (port 1099), dự phòng Server 2 (port 1100).

### Các bước thực hiện:

#### Bước 1: Build toàn bộ dự án
Mở Terminal/PowerShell tại thư mục gốc của dự án:
```powershell
mvn clean package -DskipTests
```
Hoặc nhấp đúp vào script `scripts\build.bat` (Windows) / chạy `bash scripts/build.sh` (Linux).

#### Bước 2: Khởi động Server 1
Mở một cửa sổ Terminal/PowerShell thứ nhất:
```powershell
.\scripts\run-server1.bat
```
Hoặc bằng lệnh trực tiếp:
```powershell
java -jar server/target/server-1.0.0.jar config/server1.properties
```

#### Bước 3: Khởi động Server 2
Mở một cửa sổ Terminal/PowerShell thứ hai:
```powershell
.\scripts\run-server2.bat
```
Hoặc bằng lệnh trực tiếp:
```powershell
java -jar server/target/server-1.0.0.jar config/server2.properties
```
*Quan sát log trên cả 2 cửa sổ: Server 1 và Server 2 sẽ tự động kết nối và báo `Successfully connected to peer server!`*

#### Bước 4: Khởi động các Client
Mở thêm 2 hoặc nhiều cửa sổ Terminal/PowerShell khác để mở nhiều Client:
```powershell
.\scripts\run-client.bat
```
Hoặc bằng lệnh trực tiếp:
```powershell
java -jar client/target/client-1.0.0.jar config/client.properties
```

---

## 3. CHẾ ĐỘ 2: TRIỂN KHAI TRÊN 2 MÁY VẬT LÝ QUA MẠNG TAILSCALE VPN

Hệ thống đã được thiết lập sẵn cấu hình kết nối trực tiếp qua **Tailscale Mesh VPN** giữa 2 máy tính vật lý:
- **Máy 1 (Server 1)**: `100.115.205.94`
- **Máy 2 (Server 2)**: `100.99.68.41`
- **Các máy Client (JavaFX)**: Có thể cài Tailscale để trỏ tới 2 IP trên, hoặc chạy trực tiếp trên Máy 1/Máy 2.

### Ưu điểm vượt trội khi dùng Tailscale:
1. **Bỏ qua NAT / Router phức tạp**: Không cần mở port (Port Forwarding) trên Modem Wi-Fi của nhà trọ hay trường học.
2. **IP cố định ảo**: Dù 2 máy dùng 2 mạng Wi-Fi khác nhau hay dùng 4G, IP Tailscale `100.x.y.z` luôn không đổi.
3. **Mã hóa an toàn**: Toàn bộ lưu lượng Java RMI giữa 2 máy được mã hóa đầu-cuối qua WireGuard.

### Các bước kiểm tra và khởi chạy qua Tailscale:

#### Bước 1: Kiểm tra kết nối Tailscale giữa 2 máy
- Trên **Máy 1**: Mở Terminal/CMD gõ `ping 100.99.68.41` (Kiểm tra ping sang Máy 2).
- Trên **Máy 2**: Mở Terminal/CMD gõ `ping 100.115.205.94` (Kiểm tra ping sang Máy 1).
- Nếu ping thành công, 2 máy đã sẵn sàng giao tiếp RMI.

#### Bước 2: Khởi động Server 1 trên Máy 1
Tại thư mục dự án trên Máy 1:
```powershell
.\scripts\run-server1.bat
```
Server 1 sẽ tự động gán `java.rmi.server.hostname=100.115.205.94` và mở RMI Registry tại cổng `1099`.

#### Bước 3: Khởi động Server 2 trên Máy 2
Tại thư mục dự án trên Máy 2:
```powershell
.\scripts\run-server2.bat
```
Server 2 sẽ tự động gán `java.rmi.server.hostname=100.99.68.41` và mở RMI Registry tại cổng `1099`.

*Hai Server sẽ lập tức phát hiện nhau qua mạng Tailscale, trao đổi bắt tay và báo `Successfully connected to peer server!`*

#### Bước 4: Khởi động Client
Chạy trên bất kỳ máy nào có kết nối Tailscale:
```powershell
.\scripts\run-client.bat
```

---

## 4. CHẾ ĐỘ 3: TRIỂN KHAI TRÊN MẠNG LAN TRUYỀN THỐNG (KHÔNG DÙNG VPN)

Đây là kịch bản triển khai đồ án thực tế được yêu cầu trong đề tài môn học.

### 3.1. Mô hình phân bổ địa chỉ IP mẫu

Giả sử mạng LAN của bạn (hoặc mạng phát từ Router/Wi-Fi/Hotspot):
- **Máy vật lý A (Server 1)**: `192.168.1.10`
- **Máy vật lý B (Server 2)**: `192.168.1.11`
- **Máy ảo 1 (Client 1)**: `192.168.1.51`
- **Máy ảo 2 (Client 2)**: `192.168.1.52`

> **Lưu ý quan trọng về RMI**: Java RMI truyền stub chứa địa chỉ IP của máy chủ về phía Client. Do đó, nếu máy chủ không thông báo rõ ràng địa chỉ IP LAN của nó (`java.rmi.server.hostname`), Client từ máy khác gọi về sẽ bị lỗi `Connection refused` hoặc trỏ nhầm về `127.0.0.1`. Hệ thống của chúng tôi đã tự động cấu hình thuộc tính này dựa vào thông số trong file `.properties`.

### 3.2. Cấu hình Card mạng máy ảo (VMware / VirtualBox)
Để các máy ảo Client có thể giao tiếp trực tiếp với 2 máy vật lý:
1. Mở cài đặt máy ảo (VM Settings) -> **Network Adapter**.
2. Chuyển chế độ mạng từ **NAT** sang **Bridged (Bridge mode)** (Replicate physical network connection state).
3. Đảm bảo máy ảo nhận dải IP cùng subnet với 2 máy vật lý (ví dụ: `192.168.1.x`).
4. Kiểm tra thông mạng bằng lệnh `ping`:
   ```bash
   ping 192.168.1.10
   ping 192.168.1.11
   ```

### 3.3. Mở cổng trên Tường lửa (Firewall)

Để các gói tin Java RMI không bị Windows Firewall / Linux iptables chặn:

#### Trên Windows (Chạy PowerShell với quyền Administrator):
```powershell
# Mở cổng cho Server 1 (Máy A)
New-NetFirewallRule -DisplayName "Java RMI Registry S1" -Direction Inbound -LocalPort 1099 -Protocol TCP -Action Allow
New-NetFirewallRule -DisplayName "Java RMI Export S1" -Direction Inbound -LocalPort 10991 -Protocol TCP -Action Allow

# Mở cổng cho Server 2 (Máy B)
New-NetFirewallRule -DisplayName "Java RMI Registry S2" -Direction Inbound -LocalPort 1099 -Protocol TCP -Action Allow
New-NetFirewallRule -DisplayName "Java RMI Export S2" -Direction Inbound -LocalPort 10991 -Protocol TCP -Action Allow
```

#### Trên Linux (Ubuntu / Debian):
```bash
sudo ufw allow 1099/tcp
sudo ufw allow 10991/tcp
sudo ufw reload
```

### 3.4. Cấu hình file `config/` cho 2 máy vật lý

#### Cấu hình tại Máy vật lý A (`config/server1.properties`):
```properties
server.id=SERVER_1
server.host=192.168.1.10
server.rmi.registry.port=1099
server.rmi.export.port=10991
server.db.path=data/inventory_server1.db

peer.server.id=SERVER_2
peer.server.host=192.168.1.11
peer.server.rmi.registry.port=1099
server.sync.interval.ms=2000
```
*(Lưu ý: Vì chạy trên 2 máy riêng, Server 2 trên Máy B có thể dùng cổng tiêu chuẩn `1099`)*

#### Cấu hình tại Máy vật lý B (`config/server2.properties`):
```properties
server.id=SERVER_2
server.host=192.168.1.11
server.rmi.registry.port=1099
server.rmi.export.port=10991
server.db.path=data/inventory_server2.db

peer.server.id=SERVER_1
peer.server.host=192.168.1.10
peer.server.rmi.registry.port=1099
server.sync.interval.ms=2000
```

#### Cấu hình tại các Máy ảo Client (`config/client.properties`):
Chép thư mục dự án hoặc gói `client-1.0.0.jar` sang các máy ảo:
```properties
server1.host=192.168.1.10
server1.rmi.port=1099

server2.host=192.168.1.11
server2.rmi.port=1099

client.preferred.server=SERVER_1
client.connection.timeout.ms=3000
```

### 3.5. Trình tự khởi chạy hệ thống:
1. Tại **Máy A**: Khởi chạy Server 1:
   ```bash
   java -jar server/target/server-1.0.0.jar config/server1.properties
   ```
2. Tại **Máy B**: Khởi chạy Server 2:
   ```bash
   java -jar server/target/server-1.0.0.jar config/server2.properties
   ```
   *Quan sát màn hình terminal: 2 server sẽ ngay lập tức bắt tay và báo trạng thái kết nối thành công.*
3. Tại **các Máy ảo Client**: Khởi chạy giao diện quản lý:
   ```bash
   java -jar client/target/client-1.0.0.jar config/client.properties
   ```
4. Trên giao diện Client:
   - Client sẽ hiển thị trạng thái `KẾT NỐI HIỆN TẠI: SERVER_1 (ONLINE)`.
   - Trong Tab "Giám Sát Cụm Server", cả 2 Server đều hiển thị nhãn màu xanh lá `ONLINE`.
