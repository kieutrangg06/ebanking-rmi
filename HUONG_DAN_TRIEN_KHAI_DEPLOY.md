# HƯỚNG DẪN TRIỂN KHAI HỆ THỐNG e-BANKING RMI (MULTI-NODE DEPLOYMENT)
> **Dự Án Lập Trình Mạng Phân Tán (RMI Banking System)**  
> **Áp dụng chuẩn gợi ý & Slide Bài 8: Triển khai 2 máy tính thật trên mạng LAN/VPN (KHÔNG localhost)**

---

## I. GIẢI THÍCH CHI TIẾT CƠ CHẾ RMI THEO GỢI Ý CỦA THẦY

### 1. "Địa chỉ IP trong giao thức RMI đại diện cho cái gì?" (`java.rmi.server.hostname`)
* **Bản chất của RMI Stub:**  
  Khi máy chủ Server export một đối tượng từ xa (`UnicastRemoteObject`), Java RMI sẽ tạo ra một đối tượng đại diện (**Stub**) chứa thông tin kết nối mạng gồm: **Địa chỉ IP** và **Số hiệu Cổng (Port)** của đối tượng đó. Đối tượng Stub này được đăng ký lên RMI Registry (cổng 1099).
* **Vấn đề khi không cấu hình IP thật:**  
  Nếu không thiết lập tường minh, Java RMI sẽ mặc định lấy địa chỉ hostname nội bộ hoặc `127.0.0.1` (localhost).  
  Khi máy khách (Client) ở máy tính khác kết nối vào Registry để tra cứu (`lookup`), Registry sẽ trả về Stub chứa IP `127.0.0.1`. Lúc này, máy Client sẽ cố gọi các hàm nghiệp vụ về chính địa chỉ `127.0.0.1` trên máy Client $\rightarrow$ Gặp lỗi nghiêm trọng:  
  `java.rmi.ConnectException: Connection refused to host: 127.0.0.1`.
* **Cơ chế 2 chiều đối với RMI Callback:**  
  Khi Client đăng ký nhận thông báo biến động số dư (`IClientCallback`), Client gửi đối tượng Callback lên Server. Lúc này **bản thân Client đóng vai trò là một RMI Server con**! Nếu máy Client không khai báo `java.rmi.server.hostname` là IP của máy Client, Server khi gọi Callback thông báo sẽ kết nối về `127.0.0.1` của máy Server và thất bại.
* **Giải pháp trong dự án:**  
  Cả Server và Client đều sử dụng lớp tiện ích `RmiConfig.setRmiHostname(ip)` để tự động dò tìm IP card mạng thật (Wi-Fi, Ethernet, Radmin VPN) và gán vào hệ thống trước khi export đối tượng.

---

### 2. "Cổng server là cổng động, cuối kỳ cấp phát cổng cố định & mở tường lửa"
* **Tại sao mặc định là cổng động (Dynamic/Ephemeral Port)?**  
  Trong slide Bài 8, lệnh khởi tạo thường dùng `UnicastRemoteObject.exportObject(obj, 0)` hoặc `super()`.  
  Tham số `0` chỉ thị cho hệ điều hành tự động cấp phát một cổng ngẫu nhiên trong dải **49152 – 65535** (ví dụ lúc thì 1100, lúc thì 54123, lúc thì 61002).
* **Hậu quả khi Deploy mạng thật:**  
  Tường lửa (Windows Firewall) không thể mở cổng nếu mỗi lần chạy Server lại đổi sang một cổng khác nhau. Kết nối giữa 2 máy tính sẽ bị tường lửa chặn đứng.
* **Quy hoạch 3 Cổng CỐ ĐỊNH (Fixed Ports) trong đồ án:**  
  Để cấu hình tường lửa 1 lần duy nhất và thông suốt vĩnh viễn, hệ thống đã cấp phát cố định 3 cổng:
  1. **Cổng 1099 (TCP)**: `DEFAULT_REGISTRY_PORT` - Cổng RMI Registry lắng nghe tìm kiếm dịch vụ.
  2. **Cổng 1098 (TCP)**: `DEFAULT_SERVER_PORT` - Cổng phục vụ đối tượng dịch vụ lõi `BankServiceImpl` (thay vì số 0 ngẫu nhiên).
  3. **Cổng 1097 (TCP)**: `DEFAULT_CALLBACK_PORT` - Cổng tiếp nhận Callback trên máy khách `ClientCallbackImpl`.

---

## II. BẢNG TỔNG HỢP CỔNG MẠNG VÀ TƯỜNG LỬA (FIREWALL RULES)

| Máy tính | Cổng TCP | Chiều kết nối | Chức năng | Lệnh mở tường lửa tự động |
| :--- | :---: | :---: | :--- | :--- |
| **Máy Chủ (Server)** | **1099** | Inbound | Tra cứu RMI Registry | Chạy `setup_firewall_server.bat` (Admin) |
| **Máy Chủ (Server)** | **1098** | Inbound | Thực thi gọi hàm `BankService` | Chạy `setup_firewall_server.bat` (Admin) |
| **Máy Khách (Client)**| **1097** | Inbound | Nhận Callback thông báo từ Server | Chạy `setup_firewall_client.bat` (Admin) |

*(Dự án đã tích hợp sẵn 2 file script `.bat` để mở tường lửa chỉ với 1 click chuột phải chọn "Run as administrator").*

---

## III. QUY TRÌNH TRIỂN KHAI VÀ CHẠY THỰC TẾ (DEMO 2 MÁY TÍNH)

### Bước 1: Chuẩn bị môi trường kết nối
* **Cách 1 (Cùng mạng Wi-Fi/LAN):** Cả 2 laptop kết nối chung một mạng Wi-Fi hoặc cắm dây LAN qua Switch/Router.
* **Cách 2 (Mạng Internet qua VPN):** Cả 2 máy cài đặt **Radmin VPN** (hoặc Hamachi) và cùng tham gia vào 1 Room mạng ảo (IP dải `26.x.x.x`).

### Bước 2: Thiết lập trên MÁY CHỦ (SERVER PC)
1. Bật **XAMPP** (Start module **MySQL**). Hệ thống sẽ tự động đồng bộ CSDL `ebanking_db` và nạp tài khoản mẫu.
2. Click chuột phải vào file `setup_firewall_server.bat` $\rightarrow$ Chọn **Run as administrator** (để mở cổng 1098 & 1099).
3. Chạy Server bằng cách click đúp file `run_server.bat` (hoặc chạy trong IntelliJ file `ServerMain.java`).
4. Quan sát cửa sổ Console: Server sẽ liệt kê các IP mạng và thông báo IP LAN được chọn (ví dụ: `192.168.1.15`). Hãy ghi nhớ IP này để nhập bên máy Client.

### Bước 3: Thiết lập trên MÁY KHÁCH (CLIENT PC)
1. Click chuột phải vào file `setup_firewall_client.bat` $\rightarrow$ Chọn **Run as administrator** (để mở cổng Callback 1097).
2. Chạy Client bằng cách click đúp file `run_client.bat` (hoặc chạy trong IntelliJ file `ClientMain.java`).
3. Màn hình Đăng Nhập Fintech hiện đại xuất hiện:
   * **Địa chỉ máy chủ:** Nhập IP của máy Server (ví dụ: `192.168.1.15`).
   * **Cổng:** Giữ mặc định `1099`.
   * Bấm nút **"⚡ Ping"**: Nếu hiện `ONLINE (độ trễ xx ms)` tức là mạng đã thông suốt!
   * Click vào nút **"⚙️ Mạng LAN/VPN"**: Kiểm tra IP máy khách đã tự động nhận đúng IP máy này chưa.

---

## IV. TÀI KHOẢN VÀ KỊCH BẢN THỬ NGHIỆM ĐÁNH GIÁ (TEST SCENARIOS)

| Tài khoản | Mật khẩu | Số tài khoản (STK) | Số dư ban đầu | Vai trò / Trạng thái |
| :--- | :---: | :---: | :---: | :--- |
| `usera` | `123456` | `1001` | 3.500.000 VNĐ | Khách hàng (ACTIVE) |
| `userb` | `123456` | `1002` | 1.650.000 VNĐ | Khách hàng (Đang bị KHÓA - LOCKED) |
| `userc` | `123456` | `1003` | 2.000.000 VNĐ | Khách hàng (ACTIVE) |
| `admin` | `admin123`| `9999` | 0 VNĐ | Quản trị viên hệ thống (Admin Portal) |

### Kịch bản 1: Chuyển tiền & Callback Realtime (Slide 36)
1. Máy 1 đăng nhập `usera` (STK: `1001`).
2. Máy 2 đăng nhập `userc` (STK: `1003`).
3. Trên máy 1, vào tab **"💸 Chuyển Khoản 24/7"**, nhập STK nhận `1003`, số tiền `500.000 VNĐ`. Bấm **Xác Nhận Chuyển Tiền**.
4. Máy 1: Hiện **Biên Lai Giao Dịch Điện Tử** sang trọng, trừ tiền số dư ngay lập tức.
5. Máy 2: Nhận ngay một hộp thoại thông báo **🔔 BIẾN ĐỘNG SỐ DƯ REALTIME** do Server đẩy qua Callback: *"Tài khoản nhận +500.000 VNĐ từ 1001"*. Số dư trên giao diện máy 2 tự động nhảy lên.

### Kịch bản 2: Nạp tiền & Rút tiền tức thì (Slide 36)
1. Tại màn hình Dashboard, bấm nút **"➕ Nạp Tiền"** hoặc **"➖ Rút Tiền"** phía góc phải trên cùng.
2. Chọn mệnh giá nhanh (Chips: 200k, 500k, 1M, 2M) và bấm xác nhận.
3. Hệ thống ghi nhận Transaction Type `NAP_TIEN` / `RUT_TIEN`, cập nhật số dư và hiển thị biên lai chi tiết.

### Kịch bản 3: Thanh toán hóa đơn (Dev 2 - ACID Transaction)
1. Vào tab **"🧾 Hóa Đơn & Tiện Ích"**.
2. Chọn hóa đơn mẫu (Ví dụ: Tiền điện EVN `EVN_HANOI_01` - 350.000 VNĐ).
3. Bấm **Thanh Toán**. Hệ thống kích hoạt JDBC Transaction (`SELECT ... FOR UPDATE`), trừ tiền, đổi trạng thái hóa đơn sang `PAID` và ghi nhận lịch sử.
4. Bấm thử thanh toán lại cùng hóa đơn đó $\rightarrow$ Hệ thống lập tức từ chối và cảnh báo hóa đơn đã được thanh toán (chống thanh toán trùng lặp).

### Kịch bản 4: Tiết kiệm có kỳ hạn & Tự động trả lãi (Dev 3)
1. Vào tab **"💰 Tiết Kiệm Tích Lũy"**, gửi sổ tiết kiệm mới (Ví dụ: 500.000 VNĐ, kỳ hạn 15 giây, lãi suất 7%).
2. Server kích hoạt tiến trình ngầm `InterestCalculatorTask`. Sau mỗi chu kỳ hạn, lãi suất tự động sinh và bắn thông báo Callback về Client.
3. Bấm **"Tất toán"** $\rightarrow$ Tiền gốc + toàn bộ lãi tích lũy hoàn trả thẳng về số dư tài khoản chính.

### Kịch bản 5: Quản trị an ninh Admin & Remote Revocation (Dev 3)
1. Đăng nhập tài khoản `admin` / `admin123`. Giao diện chuyển sang **Trung Tâm Giám Sát & Quản Trị Hệ Thống (SOC)**.
2. Xem danh sách toàn bộ người dùng đang Online theo thời gian thực.
3. Thử chọn `usera` và bấm nút **"Kick Phiên"** hoặc **"Khóa Tài Khoản"**.
4. Ngay tức thì trên máy của `usera`, Callback `forceLogout` được kích hoạt, hiện cảnh báo đỏ và tự động cưỡng chế đóng ứng dụng an toàn!
