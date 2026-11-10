# TÀI LIỆU BÁO CÁO TOÀN DIỆN HỆ THỐNG e-BANKING RMI PHÂN TÁN
**Môn học:** Lập trình mạng (Network Programming)  
**Đồ án cuối kỳ:** Hệ thống Ngân hàng điện tử phân tán qua giao thức Java RMI (Multi-Node Production Deployment)  
**Giảng viên hướng dẫn:** Bộ môn Khoa học Máy tính - VKU  

---

## MỤC LỤC
1. [TỔNG QUAN HỆ THỐNG & ĐẶT VẤN ĐỀ](#1-tổng-quan-hệ-thống--đặt-vấn-đề)
2. [CÔNG NGHỆ & BẢN CHẤT GIAO THỨC RMI](#2-công-nghệ--bản-chất-giao-thức-rmi)
   - *Bản chất của RMI Stub & Skeleton*
   - *Cơ chế `java.rmi.server.hostname` (Giải quyết triệt để lỗi localhost)*
   - *Cơ chế Cổng Động vs. Cổng Cố Định (Fixed Ports & Windows Firewall)*
   - *Cơ chế RMI Callback 2 chiều (Event-Driven Notification)*
3. [KIẾN TRÚC MÔ HÌNH 3-TIER VÀ BẢO TOÀN DỮ LIỆU ACID](#3-kiến-trúc-mô-hình-3-tier-và-bảo-toàn-dữ-liệu-acid)
   - *Mô hình 3 tầng (Presentation - Business Logic - Data Persistence)*
   - *Chiến lược khóa chống Deadlock & Race Condition (`SELECT ... FOR UPDATE`)*
   - *Tiến trình tính lãi nền (Background Scheduled Threading & Chống Thread Leak)*
   - *Cơ chế điều khiển từ xa của Quản trị viên (Admin Remote Revocation)*
4. [BẢN ĐỒ KIẾN TRÚC THƯ MỤC DỰ ÁN](#4-bản-đồ-kiến-trúc-thư-mục-dự-án)
5. [HƯỚNG DẪN TRIỂN KHAI THỰC TẾ TRÊN 3 MÁY TÍNH (SERVER + 2 CLIENTS)](#5-hướng-dẫn-triển-khai-thực-tế-trên-3-máy-tính-server--2-clients)
6. [KỊCH BẢN THỬ NGHIỆM ĐÁNH GIÁ (DEMO TEST CASES)](#6-kịch-bản-thử-nghiệm-đánh-giá-demo-test-cases)

---

## 1. TỔNG QUAN HỆ THỐNG & ĐẶT VẤN ĐỀ
Hệ thống **e-Banking RMI** là giải pháp ngân hàng số hiện đại, mô phỏng đầy đủ chu trình nghiệp vụ tài chính thực tế giữa các thực thể phân tán trên mạng. Ứng dụng đáp ứng trọn vẹn yêu cầu từ Slide Bài giảng (Bài 8) và giải quyết các bài toán khắt khe nhất của hệ phân tán:
- Triển khai **Multi-Node thật qua mạng LAN/Wi-Fi hoặc VPN** (tuyệt đối không phụ thuộc vào `localhost`).
- Giao diện người dùng đồ họa hiện đại chuẩn **Fintech Desktop App** (Java Swing kết hợp Flat Design, Dark/Light palettes, hiệu ứng đổ bóng, thẻ kỹ thuật số debits gradient, biên lai giao dịch điện tử).
- Cơ chế phản hồi **Realtime Callback** khi có biến động số dư mà phía Client không cần phải gửi request thăm dò liên tục (No Polling).

---

## 2. CÔNG NGHỆ & BẢN CHẤT GIAO THỨC RMI

### 2.1. Bản chất của RMI Stub & Skeleton
- **Java RMI (Remote Method Invocation)** cho phép một đối tượng chạy trên máy ảo JVM này gọi phương thức của một đối tượng đang chạy trên một máy ảo JVM khác.
- **Client Stub (Proxy):** Nằm tại máy khách, đóng vai trò đại diện cục bộ cho đối tượng từ xa. Stub nhận lời gọi phương thức từ Client, đóng gói các tham số (Marshalling) và chuyển qua tầng TCP Transport đến Server.
- **Server Skeleton / Dispatcher:** Tiếp nhận dữ liệu mạng từ tầng Transport, giải nén tham số (Unmarshalling), kích hoạt phương thức thực thi trên Server, nhận kết quả trả về rồi tuần tự hóa ngược lại gửi về cho Client Stub.

### 2.2. Bản chất `java.rmi.server.hostname` (Giải quyết triệt để lỗi `localhost`)
- **Vấn đề:** Khi Server export đối tượng `UnicastRemoteObject`, RMI sẽ nhúng địa chỉ IP của máy Server vào trong Remote Stub trước khi đẩy lên `rmiregistry`. Nếu không cấu hình, RMI sẽ tự lấy `127.0.0.1` (localhost). Khi một Client từ máy khác tra cứu `registry.lookup()`, Client nhận về Stub chứa IP `127.0.0.1` $\rightarrow$ Client cố kết nối vào chính máy nó $\rightarrow$ Gặp lỗi `Connection refused`.
- **Giải pháp:** Hệ thống sử dụng lớp `common.rmi.RmiConfig`:
  ```java
  System.setProperty("java.rmi.server.hostname", actualLanIp);
  ```
  Tự động dò tìm IP card mạng thật (Wi-Fi, Ethernet hoặc Radmin/Hamachi VPN dải `192.168.x.x`, `10.x.x.x`, `26.x.x.x`) và nhúng IP chuẩn vào Stub.

### 2.3. Cổng Động vs. Cổng Cố Định (Fixed Ports & Windows Firewall)
- **Vấn đề cổng động:** Nếu gọi `super()` hoặc `exportObject(obj, 0)`, hệ điều hành sẽ cấp một cổng ngẫu nhiên trong dải `49152 - 65535` mỗi lần Server chạy. Tường lửa (Firewall) không thể cấu hình mở cổng khi cổng thay đổi liên tục.
- **Quy hoạch 3 Cổng CỐ ĐỊNH trong đồ án:**
  1. **TCP `1099` (RMI Registry Port):** Tiếp nhận tra cứu danh bạ dịch vụ.
  2. **TCP `1098` (RMI Server Service Port):** Thực thi các lời gọi hàm nghiệp vụ `IBankService`.
  3. **TCP `1097` (RMI Client Callback Port):** Máy khách mở cổng này để lắng nghe Server gọi ngược lại.
- **Tường lửa Windows:** Chỉ cần mở các rule TCP 1098, 1099 trên Server và TCP 1097 trên Client bằng các file `.bat` 1 chạm.

### 2.4. Cơ chế RMI Callback 2 chiều (Event-Driven Notification)
- Thay vì Client phải tạo timer liên tục gửi yêu cầu hỏi Server "Tôi có tiền mới không?", Client implement giao tiếp `IClientCallback` và truyền tham chiếu đối tượng này lên Server trong hàm `login(..., callback)`.
- Server lưu giữ tham chiếu vào `ConcurrentHashMap<String, IClientCallback> onlineClients`.
- Khi có sự kiện (Ai đó chuyển tiền tới, trả lãi tiết kiệm, hoặc Admin khóa tài khoản), Server chủ động gọi:
  ```java
  callback.notifyBalanceChange("Tài khoản nhận +500.000 VNĐ từ 1001", newBalance);
  ```
  Nhờ đó Client nhận thông báo biến động số dư **ngay trong 1 phần nghìn giây**!

---

## 3. KIẾN TRÚC MÔ HÌNH 3-TIER VÀ BẢO TOÀN DỮ LIỆU ACID

### 3.1. Mô hình 3 tầng (3-Tier Architecture)
```
+-------------------------------------------------------------------------+
|                  TẦNG TRÌNH DIỄN (PRESENTATION TIER)                    |
|  Swing GUI: LoginForm, DashboardForm, TransferForm, AdminDashboardForm  |
|  - RMI Client Stub, Event Dispatch Thread (EDT), SwingWorker Async     |
+------------------------------------+------------------------------------+
                                     | (RMI over TCP 1098/1099)
                                     v
+-------------------------------------------------------------------------+
|                   TẦNG NGHIỆP VỤ (BUSINESS LOGIC TIER)                  |
|  BankServiceImpl, InterestCalculatorTask, RmiConfig                     |
|  - Quản lý phiên online (ConcurrentHashMap), Dead Reference Cleanup     |
|  - Kiểm soát giao dịch tài chính, Tính lãi suất định kỳ                 |
+------------------------------------+------------------------------------+
                                     | (JDBC Connection Pooling)
                                     v
+-------------------------------------------------------------------------+
|                   TẦNG DỮ LIỆU (DATA PERSISTENCE TIER)                  |
|  AccountDAO, BillDAO, SavingDAO, TransactionDAO                         |
|  - MariaDB / MySQL (InnoDB Engine, ACID Transactions, Row-level Lock)   |
+-------------------------------------------------------------------------+
```

### 3.2. Chiến lược Chống Deadlock & Race Condition trong Giao dịch ACID
- **Chống Deadlock đa luồng:** Khi User A chuyển tiền cho User B đồng thời User B chuyển tiền cho User A, nếu không sắp xếp thứ tự lock sẽ dẫn đến Deadlock. Dự án sử dụng kỹ thuật:
  ```java
  String firstLock = fromAcc.compareTo(toAcc) < 0 ? fromAcc : toAcc;
  String secondLock = fromAcc.compareTo(toAcc) < 0 ? toAcc : fromAcc;
  ```
  Luôn luôn khóa 2 tài khoản theo thứ tự sắp xếp cố định trong bảng mã ASCII.
- **Khóa dòng dữ liệu (Row Lock):** Dùng câu lệnh `SELECT ... FOR UPDATE` để ngăn các thread khác sửa đổi số dư hoặc thanh toán cùng một mã hóa đơn tại cùng một thời điểm.
- **Bảo vệ kép chống số dư âm:** Câu lệnh cập nhật tài khoản người gửi có điều kiện:
  ```sql
  UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?
  ```

### 3.3. Xử lý sự cố Callback Chết (Dead Callback Cleanup)
- Nếu một Client tắt máy tính đột ngột hoặc rớt mạng mà chưa kịp gọi `logout()`, tham chiếu Callback trên Server trở thành "Dead Reference".
- Hàm `triggerCallback` trên Server luôn bọc trong khối `try - catch (RemoteException e)`. Khi phát hiện Client đã ngắt kết nối, Server tự động gỡ tài khoản khỏi `onlineClients` và `userSessionMap`, **tuyệt đối không làm treo luồng (hang thread) hay crash Server**.

---

## 4. BẢN ĐỒ KIẾN TRÚC THƯ MỤC DỰ ÁN
```
ebanking-rmi/
├── pom.xml                                  # Cấu hình Maven, MySQL Connector/J, Assembly Plugin
├── ebanking_db.sql                          # Cấu trúc CSDL và dữ liệu khởi tạo mẫu
├── setup_firewall_server.bat                # Mở cổng TCP 1098, 1099 trên máy Server (Admin)
├── setup_firewall_client.bat                # Mở cổng TCP 1097 trên máy Client (Admin)
├── kiem_tra_va_dong_cong.bat                # Công cụ kiểm tra và xóa đóng cổng tường lửa
├── run_server.bat                           # Khởi động Server nhanh bằng 1 click
├── run_client.bat                           # Khởi động Client nhanh bằng 1 click
└── src/
    ├── main/java/
    │   ├── common/                          # Giao diện & Models dùng chung cho cả Client và Server
    │   │   ├── models/
    │   │   │   ├── Account.java             # Đối tượng tài khoản (Serializable)
    │   │   │   ├── Bill.java                # Đối tượng hóa đơn điện, nước, internet
    │   │   │   ├── Saving.java              # Đối tượng sổ tiết kiệm
    │   │   │   └── Transaction.java         # Đối tượng lịch sử biến động số dư
    │   │   └── rmi/
    │   │       ├── IBankService.java        # Remote Interface dịch vụ ngân hàng
    │   │       ├── IClientCallback.java     # Remote Interface callback thông báo realtime
    │   │       └── RmiConfig.java           # Quản lý dải cổng cố định, dò tìm IP card mạng, Ping test
    │   │
    │   ├── server/                          # Mã nguồn tầng Máy Chủ
    │   │   ├── ServerMain.java              # Điểm khởi chạy RMI Registry & Bind dịch vụ
    │   │   ├── db/
    │   │   │   └── DatabaseConnection.java  # Quản lý kết nối JDBC MySQL & Auto-Init Schema
    │   │   ├── dao/
    │   │   │   ├── AccountDAO.java          # Nghiệp vụ tài khoản, xác thực, chuyển khoản ACID
    │   │   │   ├── BillDAO.java             # Nghiệp vụ khóa hóa đơn chống thanh toán kép
    │   │   │   ├── SavingDAO.java           # Nghiệp vụ mở sổ, tất toán hoàn gốc + lãi
    │   │   │   └── TransactionDAO.java      # Ghi nhận và truy vấn sao kê giao dịch
    │   │   └── service/
    │   │       ├── BankServiceImpl.java     # Cài đặt Remote Object IBankService (Cổng 1098)
    │   │       └── InterestCalculatorTask.java # Worker thread tính lãi ngầm định kỳ
    │   │
    │   └── client/                          # Mã nguồn tầng Máy Khách
    │       ├── ClientMain.java              # Điểm khởi chạy giao diện Desktop
    │       ├── callback/
    │       │   └── ClientCallbackImpl.java  # Triển khai nhận Callback từ Server (Cổng 1097)
    │       └── view/
    │           ├── DashboardForm.java       # Không gian giao dịch chính cho khách hàng
    │           ├── auth/
    │           │   ├── LoginForm.java       # Màn hình đăng nhập & cấu hình mạng, Ping test
    │           │   └── RegisterForm.java    # Đăng ký tài khoản mới trực tuyến
    │           ├── transfer/
    │           │   └── TransferForm.java    # Chuyển khoản 24/7, thẻ Debit Sapphire, tra cứu thụ hưởng
    │           ├── bill/
    │           │   ├── BillPayForm.java     # Thanh toán hóa đơn cước phí
    │           │   └── TransactionHistoryForm.java # Lọc lịch sử, thống kê KPI, xuất CSV/Excel
    │           ├── saving/
    │           │   └── SavingForm.java      # Mở và quản lý sổ tiết kiệm tích lũy
    │           ├── admin/
    │           │   └── AdminDashboardForm.java # Trung tâm điều hành SOC, Kick/Khóa tài khoản
    │           └── ui/
    │               ├── UITheme.java         # Bảng màu Fintech, Font chữ, chuyển tiền thành chữ
    │               ├── ModernButton.java    # Nút bấm bo tròn phẳng chuẩn phong cách
    │               ├── ModernTextField.java # Ô nhập liệu viền đổi màu khi focus
    │               ├── ModernPasswordField.java # Ô mật khẩu hỗ trợ ẩn/hiện
    │               ├── RoundedPanel.java    # Khối panel bo tròn đổ bóng (Drop Shadow)
    │               └── TransactionReceiptDialog.java # Biên lai giao dịch điện tử chuẩn ngân hàng
    │
    └── test/java/                           # Bộ kiểm thử tự động (Unit & Integration Tests)
        ├── client/ValidationAndCsvTest.java
        ├── common/SerializationTest.java
        ├── core/CoreAuthTransferTest.java
        ├── core/DepositWithdrawAndNetworkTest.java
        ├── saving/SavingAdminTest.java
        ├── server/AcidTransactionTest.java
        └── server/DatabaseIntegrationTest.java
```

---

## 5. HƯỚNG DẪN TRIỂN KHAI THỰC TẾ TRÊN 3 MÁY TÍNH (SERVER + 2 CLIENTS)

### 5.1. Thiết lập Mô hình Mạng
- **Cách 1 (Khuyên dùng khi trên giảng đường):** Cả 3 máy tính (Máy A, Máy B, Máy C) kết nối vào cùng 1 mạng Wi-Fi (hoặc 1 máy phát Wi-Fi Hotspot cho 2 máy kia kết nối vào).
- **Cách 2 (Khi ở nhà khác mạng):** Cả 3 máy cài **Radmin VPN** và cùng join vào 1 phòng mạng ảo chung (dải IP `26.x.x.x`).

---

### 5.2. Các bước vận hành trên từng máy

#### BƯỚC 1: TRÊN MÁY 1 (MÁY CHỦ - RMI SERVER)
1. Mở phần mềm **XAMPP** $\rightarrow$ Nhấn **Start** tại module **MySQL** (cổng 3306).  
   *(Database `ebanking_db` và toàn bộ bảng mẫu sẽ tự động khởi tạo khi chạy Server).*
2. Chuột phải vào file `setup_firewall_server.bat` $\rightarrow$ Chọn **"Run as administrator"** (để mở tường lửa cổng TCP `1098` và `1099`).
3. Chạy Server bằng cách click đúp file `run_server.bat` (hoặc chạy file `server.ServerMain` trong IntelliJ).
4. Quan sát màn hình Console:
   - Server in ra danh sách card mạng và hiển thị IP LAN máy chủ được chọn (Ví dụ: `192.168.1.15`).
   - Hãy lưu lại địa chỉ IP này để nhập trên 2 máy Client.

---

#### BƯỚC 2: TRÊN MÁY 2 (MÁY KHÁCH 1 - CLIENT A)
1. Chuột phải vào file `setup_firewall_client.bat` $\rightarrow$ Chọn **"Run as administrator"** (để mở tường lửa cổng Callback TCP `1097`).
2. Khởi động ứng dụng bằng cách click đúp file `run_client.bat` (hoặc chạy `client.ClientMain` trong IntelliJ).
3. Tại màn hình Đăng Nhập:
   - **Địa chỉ máy chủ RMI:** Nhập IP của Máy 1 (Ví dụ: `192.168.1.15`).
   - Nhấn nút **"⚡ Ping"** $\rightarrow$ Thấy thông báo màu xanh `ONLINE (độ trễ xx ms)` tức là mạng đã thông suốt!
   - Nhập tài khoản: `usera` / Mật khẩu: `123456`.
   - Bấm **"ĐĂNG NHẬP HỆ THỐNG"** $\rightarrow$ Giao diện `DashboardForm` của Nguyễn Văn A (STK: `1001`) xuất hiện.

---

#### BƯỚC 3: TRÊN MÁY 3 (MÁY KHÁCH 2 - CLIENT B)
1. Chuột phải vào file `setup_firewall_client.bat` $\rightarrow$ Chọn **"Run as administrator"**.
2. Khởi động ứng dụng qua file `run_client.bat`.
3. Nhập IP của Máy 1 (`192.168.1.15`), nhấn **"⚡ Ping"** kiểm tra kết nối.
4. Đăng nhập tài khoản: `userc` / Mật khẩu: `123456` (STK: `1003` - Lê Văn C) hoặc đăng nhập tài khoản quản trị `admin` / `admin123`.

---

## 6. KỊCH BẢN THỬ NGHIỆM ĐÁNH GIÁ (DEMO TEST CASES)

### Test Case 1: Chuyển khoản liên máy & Callback Realtime (Slide Bài 8)
- **Hành động:** Trên Máy 2 (`usera`), chuyển `500.000 VNĐ` tới số tài khoản `1003` của Máy 3 (`userc`).
- **Kết quả kiểm chứng:**
  - Máy 2 lập tức hiển thị **Biên lai chuyển tiền thành công**, số dư giảm trừ tương ứng.
  - Ngay trong tích tắc, Máy 3 nhận được hộp thoại Popup **`🔔 BIẾN ĐỘNG SỐ DƯ REALTIME: Tài khoản nhận +500.000 VNĐ từ 1001`**. Số dư trên màn hình Máy 3 tự động nhảy tăng mà không cần bấm F5 hay tải lại!

### Test Case 2: Nạp tiền & Rút tiền tức thì (Slide 36)
- **Hành động:** Trên Máy 2, bấm nút **"➕ Nạp Tiền"**, chọn mệnh giá `1.000.000 VNĐ`. Sau đó bấm **"➖ Rút Tiền"** `200.000 VNĐ`.
- **Kết quả:** Số dư cập nhật ngay lập tức, hiển thị Biên lai điện tử, ghi nhật ký giao dịch loại `NAP_TIEN` và `RUT_TIEN`.

### Test Case 3: Chống Race Condition & Deadlock khi giao dịch đồng thời
- **Hành động:** Cả Máy 2 và Máy 3 cùng bấm chuyển tiền chéo cho nhau tại cùng một giây, hoặc cùng bấm thanh toán 1 mã hóa đơn (`EVN_HANOI_01`).
- **Kết quả:** Cơ chế `SELECT ... FOR UPDATE` và sắp xếp thứ tự lock tài khoản đảm bảo CSDL không bao giờ bị Deadlock, chỉ duy nhất 1 giao dịch hóa đơn được thanh toán thành công, người thứ hai bị từ chối với thông báo "Hóa đơn đã được thanh toán trước đó".

### Test Case 4: Tiết kiệm tích lũy tự động sinh lãi qua Thread ngầm
- **Hành động:** Khách hàng mở sổ tiết kiệm `500.000 VNĐ` kỳ hạn 15 giây.
- **Kết quả:** Cứ sau mỗi chu kỳ hạn, Server kích hoạt worker thread tự động cộng tiền lãi tích lũy và bắn Callback về Client. Khi khách hàng bấm **"Tất toán"**, toàn bộ tiền gốc + lãi tích lũy hoàn trả thẳng về số dư khả dụng.

### Test Case 5: Quản trị an ninh Admin & Remote Revocation
- **Hành động:** Máy 3 đăng nhập tài khoản `admin` / `admin123`. Vào tab **"🌐 Giám Sát Phiên Online"**, tìm `usera` (Máy 2) và bấm nút **"Kick Phiên"** hoặc **"Khóa Tài Khoản"**.
- **Kết quả:** Ngay lập tức trên Máy 2, Callback `forceLogout` được Server kích hoạt, hiển thị cảnh báo đỏ và tự động đóng màn hình giao dịch, trả về trang Đăng Nhập an toàn!
