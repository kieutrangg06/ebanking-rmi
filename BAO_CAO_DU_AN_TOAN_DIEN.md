# TÀI LIỆU TOÀN TẬP BÁO CÁO & ÔN THI VẤN ĐÁP HỆ THỐNG e-BANKING RMI PHÂN TÁN
**Môn học:** Lập trình mạng (Network Programming)  
**Đồ án cuối kỳ:** Hệ thống Ngân hàng điện tử phân tán qua giao thức Java RMI (Multi-Node Production Deployment)  
**Sinh viên thực hiện:** Đồ án chuyên đề Hệ thống mạng phân tán  
**Giảng viên hướng dẫn:** Bộ môn Mạng máy tính & Truyền thông - VKU  

---

## MỤC LỤC
1. [TỔNG QUAN HỆ THỐNG & ĐẶT VẤN ĐỀ](#1-tổng-quan-hệ-thống--đặt-vấn-đề)
2. [CÔNG NGHỆ & BẢN CHẤT GIAO THỨC JAVA RMI](#2-công-nghệ--bản-chất-giao-thức-java-rmi)
   - *Bản chất Stub & Skeleton / Remote Dispatcher*
   - *Cơ chế `java.rmi.server.hostname` (Giải quyết triệt để lỗi localhost 127.0.0.1)*
   - *Quy hoạch Cổng Cố Định (Fixed Ports) vs. Tường lửa Windows (Firewall)*
   - *Cơ chế RMI Callback 2 chiều (Bi-directional Realtime Notification)*
   - *Xử lý sự cố "Dead Callback" (Ngắt kết nối đột ngột)*
3. [KIẾN TRÚC MÔ HÌNH 3 TẦNG (3-TIER ARCHITECTURE)](#3-kiến-trúc-mô-hình-3-tầng-3-tier-architecture)
4. [BẢO TOÀN DỮ LIỆU TÀI CHÍNH & NGUYÊN LÝ ACID](#4-bảo-toàn-dữ-liệu-tài-chính--nguyên-lý-acid)
   - *Thuật toán chống Deadlock khi chuyển khoản đồng thời*
   - *Khóa bi quan (Pessimistic Row-level Locking - SELECT FOR UPDATE)*
   - *Bảo vệ kép chống số dư âm (Double Negative Balance Prevention)*
   - *Chống thanh toán đúp hóa đơn (Double Payment Protection)*
5. [ĐA LUỒNG & TIẾN TRÌNH NỀN (MULTITHREADING & ASYNC)](#5-đa-luồng--tiến-trình-nền-multithreading--async)
   - *Tính lãi tiết kiệm tự động (Scheduled Background Worker)*
   - *Event Dispatch Thread (EDT) & Chống treo giao diện (UI Freezing)*
6. [PHÂN TÍCH CHI TIẾT MÃ NGUỒN DỰ ÁN (SOURCE CODE WALKTHROUGH)](#6-phân-tích-chi-tiết-mã-nguồn-dự-án-source-code-walkthrough)
7. [HƯỚNG DẪN VẬN HÀNH THỰC TẾ TRÊN 2 HOẶC 3 MÁY (LAN / WI-FI / VPN)](#7-hướng-dẫn-vận-hành-thực-tế-trên-2-hoặc-3-máy-lan--wi-fi--vpn)
8. [KỊCH BẢN THỬ NGHIỆM ĐÁNH GIÁ (DEMO TEST CASES ĐẦY ĐỦ)](#8-kịch-bản-thử-nghiệm-đánh-giá-demo-test-cases-đầy-đủ)
9. [BỘ CÂU HỎI & ĐÁP ÁN THI VẤN ĐÁP KINH ĐIỂN CỦA GIẢNG VIÊN (CHEAT-SHEET)](#9-bộ-câu-hỏi--đáp-án-thi-vấn-đáp-kinh-điển-của-giảng-viên-cheat-sheet)

---

## 1. TỔNG QUAN HỆ THỐNG & ĐẶT VẤN ĐỀ

### 1.1. Mục đích xây dựng hệ thống
Trong thực tế, các hệ thống lõi ngân hàng (Core Banking) và giao dịch điện tử phân tán đòi hỏi:
1. **Tính độc lập vị trí địa lý:** Client và Server chạy trên các máy vật lý khác nhau thông qua mạng nội bộ hoặc Internet.
2. **Tính toàn vẹn dữ liệu cực cao (ACID):** Tiền tệ không được phép tự sinh ra hoặc biến mất do lỗi tranh chấp dữ liệu (Race Condition) hay tắc nghẽn (Deadlock).
3. **Phản hồi tức thì theo hướng sự kiện (Event-driven):** Khi tài khoản A gửi tiền cho B, máy B phải nhận thông báo biến động số dư tức thời mà **không được dùng cơ chế Polling** (hỏi liên tục gây quá tải mạng và lãng phí CPU).
4. **Trải nghiệm ứng dụng hiện đại:** Giao diện đồ họa Fintech phẳng (Flat Design), tiện lợi, hỗ trợ nạp/rút tiền, chuyển khoản 24/7, thanh toán hóa đơn, gửi tiết kiệm tích lũy và điều hành an ninh từ xa (SOC Admin).

Hệ thống **e-Banking RMI** được thiết kế đáp ứng 100% các tiêu chuẩn kỹ thuật trên, giải quyết triệt để các rào cản mạng thực tế (như NAT, Windows Firewall, card mạng ảo VirtualBox) để vận hành trơn tru giữa các máy vật lý.

---

## 2. CÔNG NGHỆ & BẢN CHẤT GIAO THỨC JAVA RMI

### 2.1. Bản chất Stub & Skeleton / Remote Dispatcher
- **Java RMI (Remote Method Invocation)** là công nghệ cho phép một đối tượng chạy trên máy ảo Java (JVM) này có thể gọi trực tiếp phương thức của một đối tượng đang chạy trên một máy ảo Java (JVM) khác qua mạng TCP/IP.
- **Client Stub (Proxy):** Nằm tại máy khách, đóng vai trò là "đại sứ" của đối tượng từ xa. Khi mã nguồn Client gọi hàm `service.transfer(...)`, thực chất nó đang gọi vào Stub cục bộ.
  - **Marshalling (Tuần tự hóa & Đóng gói):** Stub lấy tên phương thức và tham số, chuyển đổi thành dòng byte (nhờ giao diện `Serializable`) và gửi qua socket TCP tới Server.
- **Server Dispatcher (Skeleton):** Tại máy chủ, tầng RMI nhận luồng byte, **Unmarshalling** (giải mã) ngược lại thành đối tượng Java, gọi hàm thực thi trong `BankServiceImpl`, sau đó đóng gói kết quả trả về cho Client.
- **Tính trong suốt về vị trí (Location Transparency):** Lập trình viên phía Client gọi hàm từ xa giống hệt như gọi một hàm nội bộ thông thường trong cùng bộ nhớ JVM.

### 2.2. Cơ chế `java.rmi.server.hostname` (Giải quyết triệt để lỗi 127.0.0.1)
- **Vấn đề cốt lõi:** Khi Server export đối tượng `UnicastRemoteObject`, JVM sẽ nhúng một địa chỉ IP vào bên trong Stub để chỉ dẫn cho Client biết phải kết nối về đâu khi nhận được Stub từ Registry. Nếu không can thiệp, JVM sẽ lấy IP mặc định là `127.0.0.1` (hoặc IP của card mạng ảo đầu tiên nó tìm thấy, ví dụ VirtualBox Host-Only `192.168.56.1`).
- **Hậu quả:** Client ở Máy 2 truy vấn Registry tại Máy 1 thành công, nhận về Stub. Nhưng bên trong Stub lại ghi IP là `127.0.0.1` (hoặc `192.168.56.1`). Khi Client 2 gọi phương thức, nó tự gửi gói tin vào chính máy nó $\rightarrow$ Gây lỗi `java.rmi.ConnectException: Connection refused to host: 127.0.0.1`.
- **Giải pháp xử lý trong dự án (`RmiConfig.java`):**
  - Hệ thống tự động quét toàn bộ danh sách card mạng vật lý (`NetworkInterface.getNetworkInterfaces()`).
  - Lọc bỏ loopback (`127.0.0.1`), lọc bỏ card mạng ảo VirtualBox/VMware (`isVirtualAdapter()` loại bỏ dải `192.168.56.x` hoặc tên chứa *Virtual/VMware/Host-only*).
  - Ưu tiên chọn card Wi-Fi hoặc Ethernet vật lý đang có kết nối Internet (Ví dụ: `172.20.10.3` hoặc `192.168.1.x`).
  - Thiết lập thuộc tính hệ thống trước khi mở cổng:
    ```java
    System.setProperty("java.rmi.server.hostname", actualLanIp);
    ```

### 2.3. Quy hoạch Cổng Cố Định (Fixed Ports) vs. Tường lửa Windows (Firewall)
- **Cơ chế cổng động (Mặc định của RMI):** Nếu gọi `super()` hoặc `UnicastRemoteObject.exportObject(obj, 0)`, tham số `0` chỉ thị cho hệ điều hành cấp phát một cổng ngẫu nhiên trong dải Anonymous `49152 - 65535`. Mỗi lần khởi động lại máy, cổng này lại thay đổi. Tường lửa Windows (Windows Firewall) sẽ chặn toàn bộ các cổng này khiến Client không thể gọi được vào Server.
- **Chiến lược 3 Cổng Cố Định của Dự án:**
  1. **TCP `1099` (RMI Registry Port):** Tiếp nhận tra cứu danh bạ dịch vụ (`LocateRegistry.createRegistry(1099)`).
  2. **TCP `1098` (RMI Server Service Port):** Cổng cố định phục vụ thực thi nghiệp vụ ngân hàng `UnicastRemoteObject.exportObject(this, 1098)`.
  3. **TCP `1097` (RMI Client Callback Port):** Cổng cố định mở trên từng máy Client để đón nhận lời gọi ngược từ Server `UnicastRemoteObject.exportObject(this, 1097)`.
- Nhờ quy hoạch cổng cố định, hệ thống đi kèm 2 file cấu hình tường lửa Windows 1 chạm:
  - `setup_firewall_server.bat`: Mở chiều Inbound cho cổng 1098, 1099.
  - `setup_firewall_client.bat`: Mở chiều Inbound cho cổng 1097.

### 2.4. Cơ chế RMI Callback 2 chiều (Bi-directional Realtime Notification)
- **Nguyên lý:** Trong mô hình Client-Server truyền thống, chỉ Client mới gửi request lên Server. Nhưng trong e-Banking, khi tài khoản B nhận tiền, Server cần chủ động đánh thức Client B mà không bắt Client B phải gửi HTTP Polling liên tục.
- **Cách thức triển khai:**
  1. Client khởi tạo đối tượng `ClientCallbackImpl` (kế thừa `IClientCallback`, export trên cổng 1097). Lúc này **Client đồng thời đóng vai trò là một RMI Server nhỏ**.
  2. Khi đăng nhập, Client gửi chính Remote Reference của Callback lên cho Server:
     ```java
     bankService.login(username, password, clientCallback);
     ```
  3. Server lưu Callback vào bảng quản lý luồng:
     ```java
     ConcurrentHashMap<String, IClientCallback> onlineClients;
     ```
  4. Bất cứ khi nào tài khoản có giao dịch (nhận tiền, rút tiền, tính lãi hoặc Admin cưỡng chế đăng xuất), Server trực tiếp thực hiện RMI gọi ngược lại phương thức trên máy Client:
     ```java
     callback.notifyBalanceChange("Nhận +500.000 VNĐ từ STK 1001", newBalance);
     ```

### 2.5. Xử lý sự cố "Dead Callback" (Client ngắt kết nối đột ngột)
- **Vấn đề:** Nếu máy Client bị sập nguồn, rút dây mạng hoặc tắt cửa sổ đột ngột mà không kịp gọi `logout()`, tham chiếu Callback trên Server sẽ trở thành một tham chiếu chết (Dead Reference).
- **Giải pháp:** Trong phương thức `triggerCallback()` của `BankServiceImpl`, mọi lời gọi đến Client đều được bảo vệ trong khối `try - catch (RemoteException e)`. Khi phát hiện kết nối gãy:
  - Server ghi log cảnh báo: `[RMI-CALLBACK] Client đã ngắt kết nối, tự động dọn dẹp tham chiếu chết`.
  - Tự động gỡ tài khoản khỏi `onlineClients` và `userSessionMap`.
  - Không làm gián đoạn hay treo luồng thực thi của các giao dịch khác.

---

## 3. KIẾN TRÚC MÔ HÌNH 3 TẦNG (3-TIER ARCHITECTURE)

Hệ thống được tổ chức phân tầng nghiêm ngặt nhằm tách biệt trách nhiệm:

```
+---------------------------------------------------------------------------------+
|                       TẦNG 1: TRÌNH DIỄN (PRESENTATION TIER)                    |
|  - Công nghệ: Java Swing (Flat Design, Modern Flat UI, Custom Card Renderers)   |
|  - Thành phần: LoginForm, DashboardForm, TransferForm, BillPayForm,             |
|                SavingForm, AdminDashboardForm, TransactionReceiptDialog         |
|  - Trách nhiệm: Thu nhận thao tác người dùng, hiển thị đồ họa, xử lý luồng EDT |
+---------------------------------------+-----------------------------------------+
                                        |  (Giao thức RMI qua TCP 1098, 1099, 1097)
                                        v
+---------------------------------------------------------------------------------+
|                       TẦNG 2: NGHIỆP VỤ (BUSINESS LOGIC TIER)                   |
|  - Công nghệ: Java RMI Engine, Concurrent Multi-threading, Scheduled Tasks      |
|  - Thành phần: BankServiceImpl, InterestCalculatorTask, RmiConfig               |
|  - Trách nhiệm: Điều phối phiên (Session Manager), kiểm tra logic nghiệp vụ,   |
|                quản lý RMI Callbacks, kích hoạt luồng trả lãi định kỳ          |
+---------------------------------------+-----------------------------------------+
                                        |  (JDBC Connection Pooling)
                                        v
+---------------------------------------------------------------------------------+
|                       TẦNG 3: DỮ LIỆU (DATA PERSISTENCE TIER)                   |
|  - Công nghệ: MySQL 8 / MariaDB (InnoDB Engine - Hỗ trợ đầy đủ ACID)            |
|  - Thành phần: DatabaseConnection, AccountDAO, BillDAO, SavingDAO,              |
|                TransactionDAO, ebanking_db.sql                                  |
|  - Trách nhiệm: Lưu trữ bền vững, thực thi Transaction, Khóa hàng (Row-Lock)   |
+---------------------------------------------------------------------------------+
```

---

## 4. BẢO TOÀN DỮ LIỆU TÀI CHÍNH & NGUYÊN LÝ ACID

Trong hệ thống tài chính phân tán, các giao dịch tài khoản phải thỏa mãn trọn vẹn 4 đặc tính **ACID**:
- **A (Atomicity - Nguyên tử):** Giao dịch chuyển tiền phải thành công cả 2 vế (trừ tiền A và cộng tiền B). Nếu 1 vế thất bại, toàn bộ phải `rollback()` về trạng thái ban đầu, tiền không được thất thoát.
- **C (Consistency - Nhất quán):** Tổng số tiền trong hệ thống trước và sau giao dịch luôn luôn cân bằng. Số dư tài khoản không được phép nhỏ hơn 0.
- **I (Isolation - Cô lập):** Hai giao dịch diễn ra đồng thời không được nhìn thấy dữ liệu trung gian của nhau.
- **D (Durability - Bền vững):** Một khi đã `commit()`, dữ liệu được ghi vĩnh viễn xuống đĩa cứng CSDL kể cả khi sập nguồn điện.

### 4.1. Thuật toán chống Deadlock khi chuyển khoản đồng thời
- **Kịch bản Deadlock kinh điển:**  
  - Luồng 1: User A chuyển tiền cho User B $\rightarrow$ Khóa tài khoản A trước, chờ khóa tiếp tài khoản B.  
  - Luồng 2 (cùng lúc): User B chuyển tiền cho User A $\rightarrow$ Khóa tài khoản B trước, chờ khóa tiếp tài khoản A.  
  $\rightarrow$ Hai luồng giữ khóa của nhau và chờ nhau vô tận (Deadlock), làm treo toàn bộ hệ thống CSDL!
- **Giải pháp trong `AccountDAO.java`:**
  - Áp dụng nguyên tắc **Thứ tự khóa toàn cục (Global Lock Ordering)** bằng cách sắp xếp theo mã tài khoản (ASCII Sorting):
    ```java
    String firstLock = fromAcc.compareTo(toAcc) < 0 ? fromAcc : toAcc;
    String secondLock = fromAcc.compareTo(toAcc) < 0 ? toAcc : fromAcc;
    ```
  - Dù A chuyển cho B hay B chuyển cho A, hệ thống **luôn luôn khóa tài khoản có số thứ tự nhỏ hơn trước, rồi mới khóa tài khoản lớn hơn**. Nhờ đó loại bỏ hoàn toàn điều kiện chờ đợi vòng tròn (Circular Wait) $\rightarrow$ Không bao giờ bị Deadlock!

### 4.2. Khóa bi quan (Pessimistic Row-level Locking - `SELECT ... FOR UPDATE`)
- Khi thực hiện kiểm tra số dư và trạng thái tài khoản, hệ thống không dùng lệnh `SELECT` thông thường mà dùng:
  ```sql
  SELECT balance, status FROM accounts WHERE account_number = ? FOR UPDATE
  ```
- Dòng dữ liệu của tài khoản đó lập tức bị khóa độc quyền (Exclusive Lock). Mọi thao tác rút tiền hay chuyển khoản khác từ bên ngoài phải xếp hàng chờ cho đến khi transaction hiện tại kết thúc (`commit` hoặc `rollback`).

### 4.3. Bảo vệ kép chống số dư âm (Double Check Negative Balance)
- Bên cạnh việc kiểm tra trong mã nguồn Java (`if (balance < amount)`), câu lệnh SQL trừ tiền trên Server được gia cố điều kiện nguyên tử:
  ```sql
  UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?
  ```
- Nếu có 2 luồng rút tiền gửi đến siêu nhanh cùng lúc, chỉ duy nhất 1 câu lệnh cập nhật thành công (trả về 1 dòng bị ảnh hưởng), câu lệnh thứ hai trả về 0 dòng $\rightarrow$ Hệ thống tự động `rollback()` và báo lỗi "Số dư không đủ". Tài khoản không bao giờ bị âm tiền!

### 4.4. Chống thanh toán đúp hóa đơn (Double Payment Protection)
- Đối với hóa đơn điện, nước, viễn thông trong `BillDAO.java`, câu lệnh kiểm tra trạng thái sử dụng:
  ```sql
  SELECT status, amount FROM bills WHERE bill_code = ? FOR UPDATE
  ```
- Nếu trạng thái đã là `PAID`, hệ thống lập tức hủy giao dịch và thông báo: `"Hóa đơn này đã được thanh toán trước đó"`. Hai người dùng cùng thanh toán 1 hóa đơn tại cùng 1 giây sẽ không bao giờ bị trừ tiền 2 lần.

---

## 5. ĐA LUỒNG & TIẾN TRÌNH NỀN (MULTITHREADING & ASYNC)

### 5.1. Tính lãi tiết kiệm tự động (Scheduled Background Worker)
- Trong `InterestCalculatorTask.java`, hệ thống dùng `ScheduledExecutorService` chạy nền độc lập với chu kỳ định kỳ (ví dụ mỗi 15 giây mô phỏng 1 tháng hoặc 1 năm thực tế).
- Tiến trình quét các sổ tiết kiệm đang hoạt động (`status = 'ACTIVE'`), tính toán lãi phát sinh theo công thức tài chính:
  $$\text{Tiền lãi kỳ này} = \text{Tiền gốc} \times \left(\frac{\text{Lãi suất năm}}{100}\right) \times \left(\frac{\text{Số ngày chu kỳ}}{365}\right)$$
- Tiền lãi tự động cộng dồn vào `accumulated_interest` của sổ trong CSDL.
- Nếu chủ sổ tiết kiệm đang online, Server sẽ tự động bắn RMI Callback về máy khách để thông báo: *"Sổ tiết kiệm SAV... vừa sinh lời +... VNĐ"*.
- **Chống Thread Leak:** Khi tắt Server, phương thức hook `InterestCalculatorTask.stop()` tự động giải phóng executor thread pool an toàn.

### 5.2. Event Dispatch Thread (EDT) & Chống treo giao diện (UI Freezing)
- Trong Java Swing, mọi thao tác vẽ đồ họa và bắt sự kiện chuột/phím đều chạy trên 1 luồng duy nhất gọi là **EDT (Event Dispatch Thread)**.
- Nếu gửi một lệnh RMI qua mạng (mất từ vài mili-giây đến vài giây) trực tiếp trên EDT, toàn bộ giao diện sẽ bị đơ, không cuộn được chuột, xuất hiện trạng thái *"Not Responding"*.
- **Cách xử lý trong dự án:**
  - Các thao tác mạng nặng (như kiểm tra Ping, đăng nhập, nạp rút tiền) đều được bọc trong `SwingWorker` hoặc luồng con `new Thread(() -> { ... }).start()`.
  - Sau khi nhận được kết quả từ Server qua RMI, mã nguồn dùng `SwingUtilities.invokeLater(() -> { ... })` để đưa việc cập nhật số dư, hiển thị thông báo quay trở lại EDT an toàn.

---

## 6. PHÂN TÍCH CHI TIẾT MÃ NGUỒN DỰ ÁN (SOURCE CODE WALKTHROUGH)

Cấu trúc dự án được phân tách rõ ràng theo chuẩn Maven Enterprise:

```
ebanking-rmi/
├── src/main/java/
│   ├── common/                               # CHỨA CÁC ĐỐI TƯỢNG VÀ INTERFACE DÙNG CHUNG
│   │   ├── models/
│   │   │   ├── Account.java                  # Model tài khoản, số dư, họ tên, vai trò (Serializable)
│   │   │   ├── Transaction.java              # Model biến động số dư (CHUYEN_TIEN, NAP_TIEN, RUT_TIEN...)
│   │   │   ├── Bill.java                     # Model hóa đơn tiền điện, nước, internet
│   │   │   └── Saving.java                   # Model sổ tiết kiệm tích lũy, lãi suất, tiền lãi
│   │   └── rmi/
│   │       ├── IBankService.java             # Remote Interface chính (định nghĩa các hàm từ xa)
│   │       ├── IClientCallback.java          # Remote Interface nhận callback biến động số dư
│   │       └── RmiConfig.java                # Cấu hình 3 cổng cố định (1099, 1098, 1097), tự dò tìm IP
│   │
│   ├── server/                               # MÃ NGUỒN MÁY CHỦ (CHẠY TRÊN MÁY 1)
│   │   ├── ServerMain.java                   # Khởi động RMI Registry 1099, export BankService 1098, init DB
│   │   ├── db/
│   │   │   └── DatabaseConnection.java       # Kết nối JDBC MySQL, tự động tạo CSDL và seed dữ liệu mẫu
│   │   ├── dao/
│   │   │   ├── AccountDAO.java               # Xử lý đăng nhập, nạp/rút tiền, chuyển tiền ACID chống Deadlock
│   │   │   ├── TransactionDAO.java           # Ghi nhật ký giao dịch, sao kê tài khoản
│   │   │   ├── BillDAO.java                  # Truy vấn hóa đơn, thanh toán có khóa hàng FOR UPDATE
│   │   │   └── SavingDAO.java                # Mở sổ, cộng lãi định kỳ, tất toán sổ tiết kiệm
│   │   └── service/
│   │       ├── BankServiceImpl.java          # Cài đặt IBankService, quản lý session online, bắn Callback
│   │       └── InterestCalculatorTask.java   # Worker thread ngầm chạy định kỳ tính và trả lãi
│   │
│   └── client/                               # MÃ NGUỒN MÁY KHÁCH (CHẠY TRÊN MÁY 2 VÀ MÁY 3)
│       ├── ClientMain.java                   # Điểm khởi chạy màn hình đăng nhập Client
│       ├── callback/
│       │   └── ClientCallbackImpl.java       # Triển khai IClientCallback trên cổng 1097, kích hoạt popup
│       └── view/
│           ├── auth/
│           │   ├── LoginForm.java            # Đăng nhập, cấu hình IP/Port máy chủ, nút Ping đo độ trễ
│           │   └── RegisterForm.java         # Màn hình đăng ký tài khoản khách hàng mới
│           ├── DashboardForm.java            # Màn hình chính chứa Thẻ ATM, Nạp tiền, Rút tiền, Menu chức năng
│           ├── transfer/TransferForm.java     # Chuyển khoản nội bộ 24/7, tự tra cứu tên người nhận
│           ├── bill/BillPayForm.java         # Tra cứu mã hóa đơn và thanh toán
│           ├── bill/TransactionHistoryForm.java # Xem lịch sử sao kê, thống kê thu/chi, xuất CSV
│           ├── saving/SavingForm.java        # Danh sách sổ tiết kiệm, nút Mở sổ, nút Tất toán
│           ├── admin/AdminDashboardForm.java # Màn hình quản trị SOC, xem phiên online, Kick/Khóa tài khoản
│           └── ui/                           # Thư viện giao diện Flat UI hiện đại
│               ├── UITheme.java              # Mã màu Fintech (Navy, Emerald, Coral), đọc tiền thành chữ
│               ├── ModernButton.java         # Nút bấm hiệu ứng hover và bo góc mượt mà
│               ├── ModernTextField.java      # Ô nhập liệu có viền đổi màu focus
│               ├── ModernPasswordField.java  # Ô mật khẩu có nút bấm con mắt Ẩn/Hiện
│               ├── RoundedPanel.java         # Khối Panel đổ bóng (Shadow) phong cách thẻ ngân hàng
│               └── TransactionReceiptDialog.java # Hộp thoại Biên lai điện tử xuất sắc có mã tham chiếu
```

---

## 7. HƯỚNG DẪN VẬN HÀNH THỰC TẾ TRÊN 2 HOẶC 3 MÁY (LAN / WI-FI / VPN)

### 7.1. Chuẩn bị mạng
- **Trường hợp 1 (Trên phòng máy / Ở lớp):** Kết nối tất cả các máy vào cùng một mạng Wi-Fi (hoặc phát Wi-Fi Hotspot từ điện thoại hoặc Máy 1 cho các máy khác kết nối vào).
- **Trường hợp 2 (Khác mạng Internet):** Cài phần mềm **Radmin VPN**, tạo 1 phòng chung và copy địa chỉ IP ảo dải `26.x.x.x` của Máy 1.

### 7.2. Các bước triển khai chi tiết

#### BƯỚC 1: TRÊN MÁY 1 (MÁY CHỦ - RMI SERVER)
1. Mở phần mềm **XAMPP** $\rightarrow$ Nhấn **Start** tại module **MySQL** (cổng 3306).
2. Chuột phải vào file `setup_firewall_server.bat` $\rightarrow$ Chọn **"Run as administrator"** (để Windows mở thông cổng TCP 1098 và 1099).
3. Khởi động Server: Click đúp vào file `run_server.bat` (hoặc nhấn nút Run `ServerMain` trong IntelliJ IDEA).
4. Quan sát cửa sổ dòng lệnh (Console):
   - Server sẽ tự kiểm tra CSDL, nếu chưa có bảng sẽ tự tạo và nạp dữ liệu mẫu ban đầu.
   - Server in ra địa chỉ IP LAN máy chủ đang lắng nghe (Ví dụ: `192.168.1.15` hoặc `172.20.10.3`).
   - Hãy ghi nhớ địa chỉ IP này để cung cấp cho các máy Client.

#### BƯỚC 2: TRÊN MÁY 2 (MÁY KHÁCH 1 - CLIENT A)
1. Chuột phải vào file `setup_firewall_client.bat` $\rightarrow$ Chọn **"Run as administrator"** (để mở thông cổng Callback TCP 1097).
2. Khởi động ứng dụng Client: Click đúp vào file `run_client.bat` (hoặc chạy `ClientMain` trong IntelliJ).
3. Tại giao diện Đăng Nhập:
   - Nhập **Địa chỉ máy chủ RMI:** Nhập IP của Máy 1 (Ví dụ: `192.168.1.15`).
   - Nhấn nút **"⚡ Ping"**: Màn hình hiện chữ màu xanh `ONLINE (độ trễ ... ms)` báo hiệu kết nối mạng đã thông suốt.
   - Nhập tài khoản: `usera` / Mật khẩu: `123456`.
   - Bấm **"ĐĂNG NHẬP HỆ THỐNG"** $\rightarrow$ Màn hình Dashboard của người dùng `usera` xuất hiện.

#### BƯỚC 3: TRÊN MÁY 3 (MÁY KHÁCH 2 - CLIENT B)
*(Nếu chỉ có 2 máy, bạn có thể mở thêm 1 cửa sổ `run_client.bat` thứ hai ngay trên Máy 1 hoặc Máy 2).*
1. Chạy file `setup_firewall_client.bat` bằng quyền Administrator.
2. Mở Client bằng `run_client.bat`.
3. Nhập IP Máy 1, nhấn **"⚡ Ping"** kiểm tra.
4. Đăng nhập tài khoản: `userc` / Mật khẩu: `123456` (hoặc tài khoản `admin` / `admin123`).

---

## 8. KỊCH BẢN THỬ NGHIỆM ĐÁNH GIÁ (DEMO TEST CASES ĐẦY ĐỦ)

Để đạt điểm tối đa khi demo đồ án, hãy thực hiện theo đúng 6 kịch bản sau:

### Case 1: Chuyển khoản liên máy & Callback Realtime (Điểm sáng Slide 8)
- **Thao tác:** Trên Máy 2 (`usera`), bấm vào tab **"Chuyển Khoản"**, nhập số tài khoản đích `1003` (hệ thống tự động hiển thị tên người nhận là *Lê Văn C*), nhập số tiền `500.000 VNĐ`, nội dung: *"Thanh toán tiền học"*, bấm **"Xác Nhận Chuyển Tiền"**.
- **Hiện tượng quan sát được:**
  - Trên Máy 2: Hiện hộp thoại **Biên lai chuyển tiền điện tử** sang trọng, có dấu xác thực thành công, mã giao dịch ngẫu nhiên, số dư giảm đi 500.000 VNĐ.
  - Trên Máy 3 (`userc`): **Ngay trong tích tắc mà không cần bấm F5 hay tải lại trang**, một Popup hiện lên giữa màn hình:  
    `🔔 BIẾN ĐỘNG SỐ DƯ REALTIME: Tài khoản nhận +500.000 VNĐ từ 1001 (usera) - ND: Thanh toán tiền học`. Đồng thời số dư trên giao diện Máy 3 tự động nhảy tăng thêm 500.000 VNĐ!

### Case 2: Nạp tiền & Rút tiền nhanh qua Modal Dialog (Slide 36)
- **Thao tác:** Trên thanh Topbar của Dashboard, bấm nút **"➕ Nạp Tiền"**, chọn chip mệnh giá nhanh `1.000.000 VNĐ`, bấm Xác nhận $\rightarrow$ Số dư tăng tức thì và in Biên lai nạp tiền. Sau đó bấm nút **"➖ Rút Tiền"**, nhập `200.000 VNĐ` $\rightarrow$ Số dư trừ 200.000 VNĐ và in Biên lai rút tiền.

### Case 3: Chống thanh toán đúp hóa đơn điện nước (ACID Row-Lock)
- **Thao tác:** Mở tab **"Hóa Đơn"**. Nhập mã hóa đơn `EVN_HANOI_01` (Tiền điện 450.000 VNĐ). Bấm thanh toán thành công.
- Tiếp tục bấm thanh toán lại lần thứ 2 với chính mã đó $\rightarrow$ Hệ thống lập tức từ chối: *"Hóa đơn EVN_HANOI_01 đã được thanh toán trước đó!"*, ngăn chặn mất mát tài chính.

### Case 4: Tiết kiệm sinh lời tự động qua luồng ngầm (Background Worker Thread)
- **Thao tác:** Vào tab **"Tiết Kiệm"**, bấm **"Mở Sổ Mới"**, gửi số tiền `500.000 VNĐ`, chọn gói kỳ hạn 15 giây lãi suất 6.5%/năm.
- **Hiện tượng:** Sau mỗi 15 giây, luồng `InterestCalculatorTask` trên Server quét và tính lãi. Server kích hoạt Callback báo về Client: *"Sổ tiết kiệm SAV... vừa nhận lãi tích lũy"*. Khách hàng bấm nút **"Tất Toán"** $\rightarrow$ Toàn bộ gốc 500.000 VNĐ cộng toàn bộ tiền lãi tích lũy hoàn trả thẳng vào số dư tài khoản chính.

### Case 5: Trung tâm An ninh Quản trị viên (Admin SOC & Remote Revocation)
- **Thao tác:** Máy 3 đăng nhập tài khoản `admin` / `admin123`. Vào tab **"🌐 Giám Sát Phiên Online"**. Danh sách sẽ liệt kê tài khoản `usera` đang kết nối từ IP Máy 2. Admin bấm nút **"Kick Phiên"** (hoặc **"Khóa Tài Khoản"**).
- **Hiện tượng:** Trên Máy 2, màn hình lập tức hiện thông báo đỏ: *"Phiên làm việc của bạn đã bị Quản trị viên chấm dứt từ xa!"*. Ứng dụng tự động đóng Dashboard và đưa người dùng về lại trang Đăng Nhập.

---

## 9. BỘ CÂU HỎI & ĐÁP ÁN THI VẤN ĐÁP KINH ĐIỂN CỦA GIẢNG VIÊN (CHEAT-SHEET)

Dưới đây là tập hợp những câu hỏi giảng viên hay hỏi nhất khi bảo vệ đồ án RMI và câu trả lời chuẩn xác nhất:

### Câu 1: Em hãy giải thích cơ chế hoạt động của Java RMI? Làm thế nào Client gọi được hàm trên Server?
> **Trả lời:**  
> RMI hoạt động dựa trên cơ chế Client Proxy (Stub) và Server Dispatcher (Skeleton):
> 1. Phía Server định nghĩa một **Remote Interface** (kế thừa `java.rmi.Remote`, các hàm ném `RemoteException`) và triển khai nó trong lớp Service kế thừa `UnicastRemoteObject`.
> 2. Server đăng ký đối tượng này với bộ danh bạ `LocateRegistry` (cổng 1099) bằng một cái tên định danh (ví dụ `"BankService"`).
> 3. Phía Client dùng `Naming.lookup()` hoặc `registry.lookup()` để tìm dịch vụ. Client sẽ nhận về một đối tượng **Stub**.
> 4. Khi Client gọi phương thức trên Stub, Stub sẽ thực hiện **Marshalling** (đóng gói tên hàm và tham số thành dòng byte), gửi qua socket TCP tới Server.
> 5. Server nhận dữ liệu, **Unmarshalling** (giải mã), thực thi hàm thực tế trên JVM của Server rồi đóng gói kết quả gửi trả lại cho Client Stub.

### Câu 2: Tại sao các tham số và đối tượng truyền qua RMI (như `Account`, `Transaction`) phải `implements Serializable`?
> **Trả lời:**  
> Vì Client và Server chạy trên 2 máy ảo JVM khác nhau, thậm chí ở 2 máy tính vật lý khác nhau, chúng không dùng chung vùng nhớ RAM. Để truyền một đối tượng Java qua môi trường mạng, JVM bắt buộc phải chuyển đối tượng đó thành chuỗi byte nhị phân (gọi là Serialization - Tuần tự hóa) để gửi qua socket TCP, và máy bên kia tái tạo lại đối tượng từ chuỗi byte đó (Deserialization). Giao diện `Serializable` là một "đánh dấu" (marker interface) cho phép JVM thực hiện tiến trình này.

### Câu 3: Thuộc tính `java.rmi.server.hostname` có tác dụng gì? Nếu không thiết lập thì sẽ bị lỗi gì khi chạy trên 2 máy khác nhau?
> **Trả lời:**  
> - Khi Server export một Remote Object, RMI sẽ nhúng một địa chỉ IP vào bên trong Stub để Client biết cách liên lạc lại với Server.
> - Nếu không cấu hình, RMI sẽ tự lấy địa chỉ cục bộ mặc định là `127.0.0.1` (localhost) hoặc lấy IP của card mạng ảo như VirtualBox.
> - Khi Client ở máy khác lookup lấy Stub về, Client sẽ đọc thấy IP `127.0.0.1` bên trong Stub và nó cố gắng kết nối vào chính nó $\rightarrow$ Gặp lỗi `Connection refused to host: 127.0.0.1`.
> - Do đó, bắt buộc phải set `System.setProperty("java.rmi.server.hostname", actualLanIp)` bằng chính IP mạng thật của Server để Stub mang đúng IP mà Client ngoài mạng có thể gọi tới được.

### Câu 4: Tại sao trong đồ án nhóm em phải quy hoạch các Cổng Cố Định (1099, 1098, 1097) mà không để mặc định?
> **Trả lời:**  
> Mặc định nếu không chỉ định cổng, RMI chỉ dùng cổng 1099 cho Registry, còn đối tượng Service và Callback sẽ được gán ngẫu nhiên vào cổng động trong dải 49152 - 65535. Khi chạy trên các máy vật lý thật, Tường lửa Windows (Windows Firewall) sẽ chặn toàn bộ các kết nối từ ngoài vào những cổng lạ này. Bằng cách cố định cổng:
> - Registry: `1099`
> - Server Service: `1098`
> - Client Callback: `1097`  
> Nhóm em có thể mở các Rule tường lửa cố định chính xác qua các file Script `.bat`, giúp hệ thống vận hành trơn tru trên mọi mạng LAN hoặc Wi-Fi.

### Câu 5: RMI Callback hoạt động như thế nào? Phía Client có cần phải mở cổng hay làm Server không?
> **Trả lời:**  
> Có. RMI Callback là cơ chế giao tiếp 2 chiều:
> - Phía Client tạo một đối tượng Callback kế thừa `IClientCallback` và export nó trên cổng cố định 1097. Như vậy, máy Client lúc này đóng vai trò như một Remote Server mini.
> - Khi đăng nhập, Client gửi tham chiếu Stub của Callback này lên Server lưu giữ trong `ConcurrentHashMap`.
> - Khi có sự kiện (nhận tiền, biến động số dư, trả lãi), Server chủ động gọi hàm `callback.notifyBalanceChange(...)` để đẩy dữ liệu về máy khách theo thời gian thực mà Client không cần tốn tài nguyên gửi request thăm dò (No Polling).

### Câu 6: Nếu một Client bị mất kết nối mạng đột ngột hoặc tắt máy tính ngang, Server gọi Callback có bị treo hoặc sập không?
> **Trả lời:**  
> Không. Trong phương thức `triggerCallback()` của Server, mọi lời gọi đến Client đều được bọc trong khối `try - catch (RemoteException e)`. Khi Client đã ngắt kết nối, lời gọi RMI sẽ bắn ra ngoại lệ `RemoteException` hoặc `ConnectException`. Lúc này Server lập tức bắt ngoại lệ, định danh đây là một "Dead Callback" (tham chiếu chết) và tự động xóa nó khỏi danh sách `onlineClients` để giải phóng bộ nhớ, đảm bảo Server tiếp tục hoạt động ổn định 100%.

### Câu 7: Làm sao hệ thống giải quyết được vấn đề tranh chấp dữ liệu (Race Condition) và tắc nghẽn (Deadlock) khi 2 người dùng chuyển tiền cho nhau cùng một lúc?
> **Trả lời:**  
> - **Chống Deadlock:** Nhóm em áp dụng kỹ thuật sắp xếp thứ tự khóa tài khoản cố định (Global Lock Ordering). Dù A chuyển cho B hay B chuyển cho A, hệ thống luôn luôn so sánh mã tài khoản (`fromAcc.compareTo(toAcc)`) và khóa tài khoản có giá trị nhỏ hơn trước, tài khoản lớn hơn sau. Nhờ vậy triệt tiêu vòng lặp chờ đợi tài nguyên (Circular Wait), không bao giờ bị Deadlock.
> - **Chống Race Condition & Số dư âm:** Nhóm em sử dụng cơ chế Khóa bi quan (Pessimistic Locking) với câu lệnh `SELECT ... FOR UPDATE` trong transaction của MySQL/MariaDB InnoDB, đồng thời câu lệnh `UPDATE` có điều kiện kiểm tra nguyên tử:  
>   `UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?`  
>   Đảm bảo kể cả khi có nhiều luồng gửi giao dịch rút tiền cùng 1 mili-giây thì số dư tài khoản không bao giờ bị âm.

### Câu 8: Tại sao không dùng Timer hoặc vòng lặp `while(true)` trên Client để kiểm tra số dư mà lại dùng RMI Callback?
> **Trả lời:**  
> Cơ chế dùng Timer liên tục hỏi Server gọi là **Polling**:
> 1. **Lãng phí băng thông & CPU:** Nếu có 1000 Client, mỗi giây gửi 1000 request kiểm tra dù tài khoản không có tiền mới, gây nghẽn mạng và quá tải CSDL.
> 2. **Có độ trễ (Latency):** Nếu đặt timer 5 giây, người nhận có thể phải chờ tới 5 giây sau mới biết có tiền tới.
> Ngược lại, **RMI Callback** là kiến trúc hướng sự kiện (Event-Driven): Khi nào thực sự có tiền vào, Server mới đẩy dữ liệu về trong 1 mili-giây. Không có giao dịch thì không tốn bất kỳ gói tin mạng nào, tối ưu tài nguyên tuyệt đối.

---

*Tài liệu được biên soạn đồng bộ với mã nguồn dự án ebanking-rmi. Chúc bạn hoàn thành xuất sắc buổi thi vấn đáp!*
