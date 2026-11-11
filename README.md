# HỆ THỐNG NGÂN HÀNG ĐIỆN TỬ PHÂN TÁN (e-BANKING RMI)

> Tài liệu duy nhất tổng hợp đầy đủ: **Kết cấu dự án, Mô hình kiến trúc, Công nghệ & Bản chất Java RMI, Phân tích toàn bộ mã nguồn, Hướng dẫn chạy tất cả trường hợp (1 máy & 3 máy), Lệnh kiểm tra/đóng cổng Windows, Kịch bản tự kiểm thử (Test Cases) và Kiến thức ôn thi vấn đáp.**

---

## MỤC LỤC
1. [Mục đích & Tổng quan hệ thống](#1-mục-đích--tổng-quan-hệ-thống)
2. [Công nghệ sử dụng & Cơ chế Java RMI](#2-công-nghệ-sử-dụng--cơ-chế-java-rmi)
3. [Mô hình & Kiến trúc hệ thống (3-Tier Architecture)](#3-mô-hình--kiến-trúc-hệ-thống-3-tier-architecture)
4. [Thiết kế Cơ sở dữ liệu & Bảo toàn giao dịch ACID](#4-thiết-kế-cơ-sở-dữ-liệu--bảo-toàn-giao-dịch-acid)
5. [Đa luồng & Xử lý bất đồng bộ (Multithreading & EDT)](#5-đa-luồng--xử-lý-bất-đồng-bộ-multithreading--edt)
6. [Cấu trúc thư mục & Phân tích chi tiết mã nguồn (31 files)](#6-cấu-trúc-thư-mục--phân-tích-chi-tiết-mã-nguồn-31-files)
7. [Hướng dẫn tuần tự chạy dự án (Tất cả trường hợp & 3 Máy)](#7-hướng-dẫn-tuần-tự-chạy-dự-án-tất-cả-trường-hợp--3-máy)
8. [Lệnh kiểm tra cổng đang mở & Lệnh đóng cổng trên Windows](#8-lệnh-kiểm-tra-cổng-đang-mở--lệnh-đóng-cổng-trên-windows)
9. [Kịch bản tự test đầy đủ (8 Test Cases)](#9-kịch-bản-tự-test-đầy-đủ-8-test-cases)
10. [Bộ câu hỏi & Đáp án thi vấn đáp trọng tâm](#10-bộ-câu-hỏi--đáp-án-thi-vấn-đáp-trọng-tâm)

---

## 1. Mục đích & Tổng quan hệ thống

### 1.1. Mục đích xây dựng
Dự án **e-Banking RMI** mô phỏng một hệ thống ngân hàng điện tử phân tán hoạt động qua mạng TCP/IP bằng giao thức **Java RMI (Remote Method Invocation)**, giải quyết các bài toán thực tế của lập trình mạng phân tán:
- **Phân tán đa nút (Multi-Node):** Tách biệt hoàn toàn Máy chủ nghiệp vụ/CSDL (**Server**) và các Máy khách giao dịch (**Client**), cho phép triển khai trên **2 đến 3 máy tính vật lý khác nhau** trong mạng LAN/Wi-Fi hoặc VPN (Radmin VPN, Tailscale...).
- **Toàn vẹn dữ liệu tài chính (ACID):** Chống tranh chấp đồng thời (Race Condition), chống khóa chết (Deadlock), chống số dư âm và chống thanh toán đúp hóa đơn.
- **Thông báo thời gian thực 2 chiều (RMI Callback - Event-driven):** Khi có tiền chuyển đến, nạp/rút tiền, cộng lãi tiết kiệm hoặc bị Admin khóa/kick từ xa, Server chủ động gọi ngược về Client để cập nhật ngay lập tức mà **không dùng Polling**.
- **Giao diện Fintech hiện đại:** Xây dựng bằng Java Swing theo phong cách Flat Design (bo góc, đổ bóng, thẻ ATM gradient, đọc số tiền thành chữ tiếng Việt, biên lai điện tử, xuất sao kê CSV UTF-8).

### 1.2. Các phân hệ chức năng chính
1. **Xác thực & Quản lý phiên:** Đăng nhập, đăng ký tài khoản mới, kiểm tra kết nối Ping & độ trễ RMI, chống đăng nhập trùng (kick phiên cũ).
2. **Giao dịch lõi (Core Banking):** Xem số dư, nạp tiền, rút tiền, tra cứu tên người nhận tự động, chuyển khoản nội bộ 24/7, xuất biên lai điện tử.
3. **Thanh toán hóa đơn (Bill Payment):** Tra cứu và thanh toán hóa đơn Điện / Nước / Internet với cơ chế khóa dòng chống thanh toán đúp.
4. **Sao kê & Lịch sử giao dịch:** Xem biến động số dư (+/-), thống kê tổng thu/chi, lọc theo khoảng ngày (`yyyy-MM-dd`), xuất sao kê ra file `.csv` chuẩn UTF-8 BOM mở được trực tiếp trên Excel.
5. **Tiết kiệm tích lũy & Tự động tính lãi:** Mở sổ tiết kiệm theo các gói kỳ hạn (15s - 5%, 30s - 7%, 60s - 10%), tiến trình nền Server tự động cộng lãi định kỳ và bắn Callback, hỗ trợ tất toán hoàn gốc + lãi về tài khoản chính.
6. **Trung tâm Giám sát & An ninh Admin (SOC):** Theo dõi tổng tài sản toàn hệ thống, xem danh sách phiên đang online, tìm kiếm tài khoản, xem sổ tiết kiệm của khách hàng, thực hiện **Kick phiên** hoặc **Đóng băng/Khóa tài khoản (Lock)** và **Mở khóa (Unlock)** từ xa.

---

## 2. Công nghệ sử dụng & Cơ chế Java RMI

### 2.1. Ngăn xếp công nghệ (Tech Stack)
- **Ngôn ngữ & Runtime:** Java **JDK 17** (`maven.compiler.source/target = 17`).
- **Giao thức truyền thông phân tán:** **Java RMI** (`java.rmi.*`, `UnicastRemoteObject`, `LocateRegistry`).
- **Giao diện người dùng (GUI):** **Java Swing** + Custom Graphics2D (`UITheme`, `ModernButton`, `RoundedPanel`, `ModernTextField`, `ModernPasswordField`, `TransactionReceiptDialog`).
- **Cơ sở dữ liệu:** **MySQL / MariaDB** (InnoDB Engine hỗ trợ Transaction & Row-level Lock) kết nối qua **JDBC** (`mysql-connector-j` v8.3.0).
- **Quản lý build & đóng gói:** **Apache Maven** (`pom.xml` + `maven-assembly-plugin` v3.6.0 xuất `jar-with-dependencies`).

### 2.2. Bản chất hoạt động của Java RMI
- **Remote Interface (`IBankService`, `IClientCallback`):** Kế thừa `java.rmi.Remote`, mọi phương thức đều khai báo `throws RemoteException`.
- **Client Stub (Proxy) & Server Dispatcher (Skeleton):**
  - Client gọi `LocateRegistry.getRegistry(host, 1099).lookup("BankService")` để nhận về **Stub** (đại diện cục bộ của đối tượng từ xa).
  - Khi Client gọi `bankService.transfer(...)`, Stub thực hiện **Marshalling** (tuần tự hóa tên hàm và tham số thành luồng byte qua giao diện `Serializable`), gửi qua socket TCP đến Server.
  - Server nhận luồng byte, **Unmarshalling** thành đối tượng Java, thực thi trong `BankServiceImpl`, rồi đóng gói kết quả trả về Client.
- **Tại sao các model phải `implements Serializable`?** Vì Client và Server nằm trên các JVM khác nhau (không dùng chung bộ nhớ RAM), toàn bộ đối tượng truyền qua mạng (`Account`, `Bill`, `Saving`, `Transaction`) phải được tuần tự hóa thành byte stream (`serialVersionUID = 1L`).

### 2.3. Giải quyết lỗi mạng thực tế khi chạy nhiều máy (`RmiConfig.java`)
1. **Lỗi `Connection refused to host: 127.0.0.1` hoặc card mạng ảo:**
   - Mặc định JVM có thể nhúng `127.0.0.1` hoặc IP card ảo (`192.168.56.x` của VirtualBox/VMware) vào Stub. Khi máy khác nhận Stub sẽ gọi nhầm vào chính nó.
   - **Cách dự án xử lý:** `RmiConfig.getAvailableNetworkInterfaces()` quét các card mạng vật lý đang bật, loại bỏ loopback (`127.0.0.1`), loại bỏ adapter ảo (`virtualbox`, `host-only`, `vmware`, `vethernet`, dải `192.168.56.*`), ưu tiên Wi-Fi/Ethernet thật hoặc Radmin/Hamachi/Tailscale VPN, sau đó thiết lập:
     ```java
     System.setProperty("java.rmi.server.hostname", selectedIp);
     ```
   - Phía Client khi đăng nhập cũng thiết lập `RmiConfig.setRmiHostname(finalClientIp)` **trước khi** khởi tạo `ClientCallbackImpl` để Server gọi ngược Callback đúng về IP của máy Client.
2. **Quy hoạch 3 cổng cố định (Fixed Ports) chống Windows Firewall chặn cổng động:**
   - Mặc định `UnicastRemoteObject(0)` mở cổng ngẫu nhiên (`49152–65535`), dễ bị Windows Firewall chặn khi demo đa máy.
   - Dự án cố định 3 cổng trong `RmiConfig`:
     - **TCP `1099`** (`DEFAULT_REGISTRY_PORT`): Cổng **RMI Registry** trên Server.
     - **TCP `1098`** (`DEFAULT_SERVER_PORT`): Cổng **BankServiceImpl** trên Server.
     - **TCP `1097`** (`DEFAULT_CALLBACK_PORT`): Cổng **ClientCallbackImpl** trên từng máy Client (kèm `allocateSafePort()` tự động fallback sang cổng động `0` nếu chạy nhiều Client trên cùng 1 máy tính).

### 2.4. Cơ chế RMI Callback 2 chiều & Xử lý Dead Callback
- Khi đăng nhập, Client tạo `ClientCallbackImpl` (export trên cổng `1097`) và gửi stub `callback` lên Server qua `bankService.login(username, password, callback)`. Lúc này **Client cũng đóng vai trò một RMI Server nhỏ** để đón cuộc gọi từ Server.
- Server lưu danh sách phiên online trong `ConcurrentHashMap<String, IClientCallback> onlineClients` (key là `accountNumber`) và `ConcurrentHashMap<String, String> userSessionMap` (`username -> accountNumber`).
- Khi có biến động số dư hoặc lệnh từ Admin, Server gọi `callback.notifyBalanceChange(...)` hoặc `callback.forceLogout(...)`.
- **Xử lý Dead Callback:** Nếu Client tắt đột ngột (rút dây mạng, tắt ngang tiến trình), `triggerCallback()` bắt `RemoteException`, tự động xóa `accountNumber` khỏi `onlineClients` và `userSessionMap` để không làm treo luồng hay rò rỉ bộ nhớ trên Server.

---

## 3. Mô hình & Kiến trúc hệ thống (3-Tier Architecture)

Hệ thống được thiết kế theo mô hình **3 tầng (3-Tier Architecture)** rõ ràng:

```
+------------------------------------------------------------------------------+
|                  TẦNG 1: TRÌNH DIỄN (PRESENTATION TIER - CLIENT)             |
|  - Công nghệ: Java Swing (Flat Design, SwingWorker, EDT)                     |
|  - Thành phần: LoginForm, RegisterForm, DashboardForm, TransferForm,         |
|                BillPayForm, TransactionHistoryForm, SavingForm,              |
|                AdminDashboardForm, ClientCallbackImpl                        |
+--------------------------------------+---------------------------------------+
                                       | Java RMI (TCP 1099, 1098, 1097)
                                       v
+------------------------------------------------------------------------------+
|                  TẦNG 2: NGHIỆP VỤ (BUSINESS LOGIC TIER - SERVER)            |
|  - Công nghệ: Java RMI (UnicastRemoteObject), ConcurrentHashMap,             |
|               ScheduledExecutorService                                       |
|  - Thành phần: ServerMain, BankServiceImpl, InterestCalculatorTask, RmiConfig|
+--------------------------------------+---------------------------------------+
                                       | JDBC (MySQL Connector/J 8.3.0)
                                       v
+------------------------------------------------------------------------------+
|                  TẦNG 3: DỮ LIỆU (DATA ACCESS & PERSISTENCE - DB)            |
|  - Công nghệ: MySQL / MariaDB (InnoDB Engine - ACID + Row-level Locking)     |
|  - Thành phần: DatabaseConnection, AccountDAO, BillDAO, SavingDAO,           |
|                TransactionDAO, bảng accounts/transactions/bills/savings      |
+------------------------------------------------------------------------------+
```

**Luồng gọi chuẩn:** `UI Form (SwingWorker)` ➔ `IBankService (RMI Stub)` ➔ `BankServiceImpl` ➔ `DAO` ➔ `DatabaseConnection` ➔ `MySQL InnoDB`, và chiều ngược lại `BankServiceImpl` / `InterestCalculatorTask` ➔ `IClientCallback` ➔ `ClientCallbackImpl` ➔ `SwingUtilities.invokeLater()` cập nhật UI.

---

## 4. Thiết kế Cơ sở dữ liệu & Bảo toàn giao dịch ACID

### 4.1. Cấu trúc 4 bảng trong CSDL `ebanking_db` (`ebanking_db.sql`)
1. **`accounts`**: `id` (PK), `account_number` (UNIQUE), `username` (UNIQUE), `password`, `full_name`, `balance`, `status` (`ACTIVE` / `LOCKED`).
2. **`transactions`**: `id` (PK), `transaction_type` (`CHUYEN_TIEN`, `NAP_TIEN`, `RUT_TIEN`, `THANH_TOAN_HOA_DON`, `MO_SO_TIET_KIEM`, `TAT_TOAN_TIET_KIEM`), `from_account`, `to_account`, `amount`, `description`, `created_at`.
3. **`bills`**: `id` (PK), `bill_code` (UNIQUE), `service_type`, `customer_name`, `amount`, `status` (`UNPAID` / `PAID`).
4. **`savings`**: `id` (PK), `account_number`, `deposit_amount`, `interest_rate`, `term_period`, `accumulated_interest`, `status` (`ACTIVE` / `CLOSED`), `created_at`.

> **Lưu ý:** `DatabaseConnection.initializeDatabaseIfPossible()` tự động tạo CSDL `ebanking_db`, tạo đủ 4 bảng và nạp dữ liệu mẫu ngay khi `ServerMain` khởi động nếu các bảng đang trống.

### 4.2. Dữ liệu mẫu mặc định
**Tài khoản (`accounts`):**
| STT | Số TK | Username | Mật khẩu | Họ tên | Số dư mặc định | Trạng thái |
|---|---|---|---|---|---:|---|
| 1 | `1001` | `usera` | `123456` | Nguyen Van A | `3,500,000 VNĐ` | `ACTIVE` |
| 2 | `1002` | `userb` | `123456` | Tran Thi B | `1,650,000 VNĐ` | `LOCKED` |
| 3 | `1003` | `userc` | `123456` | Le Van C | `2,000,000 VNĐ` | `ACTIVE` |
| 4 | `9999` | `admin` | `admin123` | System Administrator | `0 VNĐ` | `ACTIVE` |

**Hóa đơn (`bills`):**
- `EVN_HANOI_01` | `Tien Dien` | `Nguyen Van A` | `350,000 VNĐ` | `UNPAID`
- `WA_DANANG_02` | `Tien Nuoc` | `Tran Thi B` | `120,000 VNĐ` | `UNPAID`
- `FPT_NET_03` | `Internet FPT` | `Le Van C` | `250,000 VNĐ` | `UNPAID`

**Sổ tiết kiệm (`savings`):**
- ID `#1` | STK `1001` | Gốc `500,000 VNĐ` | Lãi suất `6.5%` | Chu kỳ `30s` | Lãi đã tích lũy `2,500 VNĐ` | `ACTIVE`
- ID `#2` | STK `1003` | Gốc `1,000,000 VNĐ` | Lãi suất `7.0%` | Chu kỳ `60s` | Lãi đã tích lũy `15,000 VNĐ` | `ACTIVE`

### 4.3. Cơ chế bảo toàn ACID trong mã nguồn
1. **Giao dịch nguyên tử (`conn.setAutoCommit(false)` / `commit()` / `rollback()`):** Toàn bộ `executeTransfer`, `deposit`, `withdraw`, `payBill`, `openSaving`, `settleSaving` đều bọc trong Transaction JDBC. Bất kỳ bước nào lỗi đều gọi `rollback()`.
2. **Chống Deadlock bằng Global Lock Ordering (`AccountDAO.executeTransfer`):**
   - Sắp xếp thứ tự khóa 2 tài khoản theo thứ tự từ điển trước khi khóa hàng:
     ```java
     String firstLock = fromAcc.compareTo(toAcc) < 0 ? fromAcc : toAcc;
     String secondLock = fromAcc.compareTo(toAcc) < 0 ? toAcc : fromAcc;
     ```
   - Triệt tiêu vòng chờ (Circular Wait) khi A chuyển cho B và B chuyển cho A tại cùng thời điểm.
3. **Khóa bi quan cấp dòng (Pessimistic Row-level Locking):** Dùng `SELECT ... FOR UPDATE` trong `AccountDAO`, `BillDAO`, `SavingDAO` để cô lập hoàn toàn các giao dịch đụng cùng 1 tài khoản, 1 hóa đơn hoặc 1 sổ tiết kiệm.
4. **Bảo vệ kép chống số dư âm:** Ngoài kiểm tra `if (currentBal < amount)` trong Java, câu SQL trừ tiền kiểm tra nguyên tử trực tiếp tại DB:
   ```sql
   UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?
   ```
5. **Chống thanh toán đúp hóa đơn (`BankServiceImpl.payBill` + `BillDAO.queryBillForUpdate`):** Khóa dòng hóa đơn bằng `FOR UPDATE`, kiểm tra `PAID` trước khi trừ tiền.

---

## 5. Đa luồng & Xử lý bất đồng bộ (Multithreading & EDT)

### 5.1. Tiến trình nền tính lãi tiết kiệm (`InterestCalculatorTask.java`)
- Sử dụng `Executors.newSingleThreadScheduledExecutor(...)` với thread đặt `setDaemon(true)` để không cản JVM thoát khi tắt Server.
- Trong `BankServiceImpl`, worker được khởi chạy với `start(10, 15)` (chờ 10 giây đầu, sau đó lặp lại mỗi **15 giây**).
- Mỗi chu kỳ, `calculateInterest()` quét các sổ `status = 'ACTIVE'`, tính lãi theo công thức trong mã nguồn:
  $$\text{Lãi phát sinh} = \text{Tiền gốc (deposit\_amount)} \times \left(\frac{\text{interest\_rate}}{100.0}\right)$$
- Câu lệnh cộng lãi dùng điều kiện `WHERE id = ? AND status = 'ACTIVE'` để tránh cộng nhầm vào sổ vừa tất toán (Race Condition), sau đó bắn Callback báo về Client nếu chủ sổ đang online.
- Có phương thức `stop()` gọi `shutdown()` / `awaitTermination()` chống Thread Leak.

### 5.2. Chống treo giao diện Client (Swing EDT & `SwingWorker`)
- Tất cả lời gọi RMI qua mạng trong `LoginForm`, `RegisterForm`, `DashboardForm`, `TransferForm`, `BillPayForm`, `TransactionHistoryForm` đều chạy trên luồng nền `SwingWorker.doInBackground()`, cập nhật giao diện trong `done()` trên Event Dispatch Thread (EDT).
- Trong `ClientCallbackImpl`, cả `notifyBalanceChange()` và `forceLogout()` đều bọc trong `SwingUtilities.invokeLater(...)` để đảm bảo thread-safe cho Swing.

---

## 6. Cấu trúc thư mục & Phân tích chi tiết mã nguồn (31 files)

```
ebanking-rmi/
├── pom.xml                                  # Cấu hình Maven, Java 17, mysql-connector-j, assembly JAR
├── ebanking_db.sql                          # Script tạo CSDL ebanking_db và dữ liệu mẫu
├── README.md                                # Tài liệu dự án duy nhất
└── src/main/java/
    ├── common/                              # Tầng dùng chung (Server & Client đều cần)
    │   ├── models/
    │   │   ├── Account.java                 # Model tài khoản (id, accountNumber, username, password, fullName, balance, status)
    │   │   ├── Bill.java                    # Model hóa đơn (id, billCode, serviceType, customerName, amount, status)
    │   │   ├── Saving.java                  # Model sổ tiết kiệm (id, accountNumber, depositAmount, interestRate, termPeriod, accumulatedInterest, status, createdAt)
    │   │   └── Transaction.java             # Model lịch sử giao dịch (id, transactionType, fromAccount, toAccount, amount, description, createdAt, status)
    │   └── rmi/
    │       ├── IBankService.java            # Remote Interface định nghĩa 19 phương thức nghiệp vụ ngân hàng
    │       ├── IClientCallback.java         # Remote Interface callback 2 phương thức: notifyBalanceChange, forceLogout
    │       └── RmiConfig.java               # Cố định port 1099/1098/1097, dò card mạng thật, ping TCP & test RMI service
    ├── server/                              # Tầng Server (chạy trên Máy 1)
    │   ├── ServerMain.java                  # Entry-point Server: nhận -ip/-regPort/-srvPort, init DB, tạo Registry 1099, bind BankService 1098
    │   ├── db/
    │   │   └── DatabaseConnection.java      # Kết nối MySQL JDBC (hỗ trợ -Ddb.url/user/password hoặc env DB_*), auto-init bảng & seed
    │   ├── dao/
    │   │   ├── AccountDAO.java              # authenticate, createAccount, getBalance, findByAccountNumber, executeTransfer (ACID + chống Deadlock), deposit, withdraw
    │   │   ├── BillDAO.java                 # queryBill, queryBillForUpdate (FOR UPDATE), updateBillStatus
    │   │   ├── SavingDAO.java               # openSaving, settleSaving, getSavingsByAccount, getAllActiveSavings, addInterest, lockAccount, unlockAccount, getAllAccounts, getTotalSystemBalance
    │   │   └── TransactionDAO.java          # insertTransaction, getTransactionHistory, getTransactionHistoryFiltered (lọc từ 00:00 đến 23:59:59.999)
    │   └── service/
    │       ├── BankServiceImpl.java         # Triển khai IBankService (synchronized các hàm ghi), quản lý onlineClients & userSessionMap, triggerCallback & dọn Dead Callback
    │       └── InterestCalculatorTask.java  # ScheduledExecutorService tính lãi định kỳ mỗi 15s và gọi Callback
    └── client/                              # Tầng Client (chạy trên Máy 2, Máy 3)
        ├── ClientMain.java                  # Entry-point Client: thiết lập System LookAndFeel và mở LoginForm trên EDT
        ├── callback/
        │   └── ClientCallbackImpl.java      # Triển khai IClientCallback (export cổng 1097), bật popup biến động số dư & cưỡng chế forceLogout về LoginForm
        └── view/
            ├── DashboardForm.java           # Portal chính cho khách hàng: Topbar số dư + Ẩn/Hiện, Nạp/Rút tiền Modal, 4 Tab nghiệp vụ, Đăng xuất an toàn
            ├── auth/
            │   ├── LoginForm.java           # Đăng nhập, Ping Server đo ms, mở rộng cấu hình Client IP/Callback Port, điều hướng Admin/User
            │   └── RegisterForm.java        # Đăng ký tài khoản mới trực tuyến (mặc định số dư 0 VNĐ)
            ├── transfer/
            │   └── TransferForm.java        # Thẻ ghi nợ ảo, tra cứu tên người nhận, nhập tiền + chip chọn nhanh + đọc chữ tiếng Việt, chuyển khoản + biên lai
            ├── bill/
            │   ├── BillPayForm.java         # Tra cứu mã hóa đơn, nút gợi ý mã mẫu, kiểm tra PAID/UNPAID, thanh toán + biên lai
            │   └── TransactionHistoryForm.java # KPI tổng GD/Thu/Chi, lọc ngày (Tất cả/Hôm nay/7 ngày), tô màu +/-, xuất CSV UTF-8 BOM
            ├── saving/
            │   └── SavingForm.java          # KPI gốc/lãi tiết kiệm, chọn gói kỳ hạn (15s/30s/60s), dự tính lãi realtime, mở sổ & tất toán
            ├── admin/
            │   └── AdminDashboardForm.java  # SOC Admin: 4 KPI, auto-refresh 5s, tab Phiên Online (Kick / Khóa khẩn cấp), tab Tài khoản (Tìm kiếm, Xem sổ, Lock/Unlock)
            └── ui/
                ├── UITheme.java             # Bảng màu Fintech, font, style bảng, MONEY_FORMAT, đọc số tiền tiếng Việt
                ├── ModernButton.java        # Nút bấm bo góc với hiệu ứng hover (PRIMARY, SECONDARY, SUCCESS, DANGER, OUTLINE)
                ├── ModernTextField.java     # Ô nhập liệu tùy biến viền focus
                ├── ModernPasswordField.java # Ô mật khẩu bo góc tùy biến
                ├── RoundedPanel.java        # Panel bo góc hỗ trợ gradient và đổ bóng
                └── TransactionReceiptDialog.java # Hộp thoại biên lai điện tử kèm mã tham chiếu EB... và tiền bằng chữ
```

---

## 7. Hướng dẫn tuần tự chạy dự án (Tất cả trường hợp & 3 Máy)

### 7.1. Điều kiện tiên quyết trên máy
1. **Java JDK 17+** (có thể dùng JDK của hệ thống hoặc JBR đi kèm IntelliJ IDEA).
2. **MySQL / MariaDB** chạy trên cổng **`3306`** (ví dụ cài **XAMPP**, bật module **MySQL**).
   - Tài khoản DB mặc định trong `DatabaseConnection.java`: `user = root`, `password = ""` (rỗng), database `ebanking_db`.
   - Nếu MySQL của bạn có mật khẩu (ví dụ `123456`), có thể truyền tham số JVM khi chạy Server: `-Ddb.user=root -Ddb.password=123456`.

---

### 7.2. Trường hợp 1: Chạy nhanh trên 1 máy bằng IntelliJ IDEA (Localhost / Test đơn máy)
1. Mở **XAMPP Control Panel** ➔ nhấn **Start** ở dòng **MySQL** (chờ chuyển xanh, port `3306`).
2. Mở dự án `ebanking-rmi` trong **IntelliJ IDEA**.
3. Chạy **`ServerMain`** (đã có sẵn Run Configuration `ServerMain`):
   - Quan sát Console: thấy `ebanking_db` đồng bộ thành công, RMI Registry tạo trên cổng `1099`, `BankService` bind thành công trên cổng `1098`, và in ra địa chỉ IP máy chủ.
4. Chạy **`ClientMain`** (Run Configuration `ClientMain`):
   - Tại `LoginForm`, kiểm tra badge trên cùng hiện **ONLINE** (nhấn `⚡ Ping` nếu cần). Nếu chạy cùng máy có thể giữ IP LAN hiển thị sẵn hoặc gõ `localhost` / `127.0.0.1`.
   - Đăng nhập thử `usera` / `123456`.
5. **Mở thêm Client thứ 2 trên cùng máy** (để test chuyển khoản hoặc Admin):
   - Trong IntelliJ, bật *Allow multiple instances* cho `ClientMain` (Run ➔ Edit Configurations ➔ Modify options ➔ Allow multiple instances), rồi chạy `ClientMain` lần 2.
   - `ClientCallbackImpl` sẽ tự thấy cổng `1097` đang có Client 1 dùng và tự động fallback sang cổng rảnh (`allocateSafePort`) để Client 2 vẫn nhận được Callback bình thường!

---

### 7.3. Trường hợp 2: Đóng gói & Chạy bằng dòng lệnh (Maven + JAR)
Nếu muốn chạy ngoài IDE hoặc copy sang máy khác không cần cài IntelliJ:

1. **Build file JAR chứa toàn bộ thư viện (`jar-with-dependencies`)** tại thư mục gốc dự án:
   ```powershell
   mvn clean package
   ```
   *(Nếu chưa cài Maven toàn cục nhưng có IntelliJ IDEA, trỏ `JAVA_HOME` tới JDK 17 rồi gọi `mvn.cmd` của IntelliJ).*
   File tạo ra sẽ nằm tại: `target/ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar`.

2. **Chạy Server bằng lệnh:**
   ```powershell
   java -cp target/ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar server.ServerMain
   ```
   *(Tuỳ chọn chỉ định IP cụ thể hoặc đổi mật khẩu DB):*
   ```powershell
   java -Ddb.user=root -Ddb.password=YOUR_PASS -cp target/ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar server.ServerMain -ip 192.168.1.15
   ```

3. **Chạy Client bằng lệnh:**
   ```powershell
   java -cp target/ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar client.ClientMain
   ```

---

### 7.4. Trường hợp 3: Triển khai phân tán thực tế trên 3 MÁY (LAN / Wi-Fi / Radmin VPN)

Mô hình 3 máy:
- **MÁY 1 (Server + Database):** Chạy MySQL (`3306`) + `ServerMain` (`1099`, `1098`).
- **MÁY 2 (Client A):** Chạy `ClientMain` (`1097`), đăng nhập khách hàng A (ví dụ `usera`).
- **MÁY 3 (Client B / Admin):** Chạy `ClientMain` (`1097`), đăng nhập khách hàng C (`userc`) hoặc quản trị viên (`admin`).

#### Bước 0: Kết nối chung mạng
- **Cách A (Cùng Wi-Fi / LAN / Hotspot):** Kết nối cả 3 máy vào cùng một mạng Wi-Fi (hoặc phát Hotspot từ điện thoại/Máy 1).
- **Cách B (Khác mạng / Từ xa):** Cài **Radmin VPN** trên cả 3 máy, tạo 1 Network chung, lấy IP dải `26.x.x.x` của Máy 1.

#### Bước 1: Cấu hình & Khởi động trên MÁY 1 (RMI Server)
1. Bật **MySQL** trong XAMPP (port `3306`).
2. Mở **PowerShell hoặc CMD với quyền Administrator** trên Máy 1 để mở tường lửa cho 2 cổng Server (`1098`, `1099`):
   ```powershell
   netsh advfirewall firewall add rule name="eBanking RMI Registry (1099)" dir=in action=allow protocol=TCP localport=1099 profile=any
   netsh advfirewall firewall add rule name="eBanking RMI Service (1098)" dir=in action=allow protocol=TCP localport=1098 profile=any
   ```
3. Khởi động `ServerMain` (qua IntelliJ hoặc lệnh `java -cp ... server.ServerMain`).
4. Xem console của `ServerMain`:
   - Kiểm tra dòng `>> [CẤU HÌNH IP MÁY CHỦ] java.rmi.server.hostname = ...` xem đã chọn đúng IP Wi-Fi/LAN hoặc Radmin VPN chưa (ví dụ `192.168.1.15` hoặc `26.10.20.30`).
   - Nếu máy có nhiều card mạng và chọn chưa đúng IP mong muốn, chạy lại kèm tham số `-ip`:
     ```powershell
     java -cp target/ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar server.ServerMain -ip 192.168.1.15
     ```

#### Bước 2: Cấu hình & Khởi động trên MÁY 2 (Client A)
1. Mở **PowerShell/CMD quyền Administrator** trên Máy 2 để mở tường lửa cho cổng Callback Client (`1097`) — **bắt buộc để Máy 1 gọi Callback về Máy 2**:
   ```powershell
   netsh advfirewall firewall add rule name="eBanking RMI Client Callback (1097)" dir=in action=allow protocol=TCP localport=1097 profile=any
   ```
2. Chạy `ClientMain` trên Máy 2.
3. Trên màn hình `LoginForm`:
   - Ô **Địa chỉ Máy Chủ RMI Server**: nhập IP của **Máy 1** (ví dụ `192.168.1.15`), cổng giữ `1099`.
   - Nhấn **`⚡ Ping`**: phải thấy badge chuyển màu xanh `ONLINE (... ms)`.
   - Nhấn **`⚙️ Mạng LAN/VPN`** để kiểm tra ô **IP máy khách này** đã đúng IP LAN/VPN của **Máy 2** chưa, cổng Callback là `1097`. (Nếu sai IP, sửa lại cho đúng IP của Máy 2).
   - Đăng nhập: `usera` / `123456`.

#### Bước 3: Cấu hình & Khởi động trên MÁY 3 (Client B / Admin)
1. Mở **PowerShell/CMD quyền Administrator** trên Máy 3, mở cổng Callback `1097`:
   ```powershell
   netsh advfirewall firewall add rule name="eBanking RMI Client Callback (1097)" dir=in action=allow protocol=TCP localport=1097 profile=any
   ```
2. Chạy `ClientMain` trên Máy 3.
3. Nhập IP **Máy 1** ➔ nhấn **`⚡ Ping`** kiểm tra `ONLINE` ➔ kiểm tra mục `⚙️ Mạng LAN/VPN` đúng IP của **Máy 3**.
4. Đăng nhập `userc` / `123456` (để test nhận tiền realtime) hoặc `admin` / `admin123` (để test trung tâm SOC Admin).

---

## 8. Lệnh kiểm tra cổng đang mở & Lệnh đóng cổng trên Windows

> Áp dụng cho các cổng của dự án: **`1097`** (Callback), **`1098`** (Service), **`1099`** (Registry), **`3306`** (MySQL).  
> Các lệnh xóa Firewall Rule hoặc kill tiến trình hệ thống cần mở PowerShell/CMD bằng **Run as administrator**.

### 8.1. Lệnh XEM các cổng nào đang mở / đang lắng nghe (Listening) trên máy

**Cách 1: Dùng PowerShell (xem chi tiết cổng + tên tiến trình + PID):**
```powershell
Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in 1097,1098,1099,3306 } | Select-Object LocalAddress, LocalPort, OwningProcess, @{Name="ProcessName";Expression={(Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName}} | Format-Table -AutoSize
```
*Hoặc xem **tất cả** cổng TCP đang mở (Listening) trên máy tính:*
```powershell
Get-NetTCPConnection -State Listen | Sort-Object LocalPort | Select-Object LocalAddress, LocalPort, OwningProcess, @{Name="ProcessName";Expression={(Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue).ProcessName}} | Format-Table -AutoSize
```

**Cách 2: Dùng CMD (`netstat`):**
```cmd
netstat -ano | findstr "LISTENING"
```
*Lọc riêng 4 cổng dự án (`1097`, `1098`, `1099`, `3306`):*
```cmd
netstat -ano | findstr /R ":1097 :1098 :1099 :3306"
```

**Xem các Rule Tường lửa Windows đang mở cho e-Banking:**
```powershell
netsh advfirewall firewall show rule name=all | findstr /I "eBanking 1097 1098 1099"
```

---

### 8.2. Lệnh ĐÓNG cổng đang bị tiến trình chiếm giữ (Kill Process theo Port/PID)

Khi bị lỗi `Address already in use` hoặc muốn tắt hẳn Server/Client đang chiếm cổng:

**Cách 1: Dùng PowerShell (Đóng trực tiếp theo số cổng — nhanh nhất):**
```powershell
# Đóng tiến trình đang mở cổng 1099 (Registry)
Get-NetTCPConnection -LocalPort 1099 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }

# Đóng tiến trình đang mở cổng 1098 (Server Service)
Get-NetTCPConnection -LocalPort 1098 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }

# Đóng tiến trình đang mở cổng 1097 (Client Callback)
Get-NetTCPConnection -LocalPort 1097 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```
*Đóng cả 3 cổng RMI (`1097`, `1098`, `1099`) bằng một dòng lệnh PowerShell:*
```powershell
1097,1098,1099 | ForEach-Object { Get-NetTCPConnection -LocalPort $_ -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force } }
```

**Cách 2: Dùng CMD (`taskkill` theo PID tìm được từ `netstat -ano`):**
```cmd
REM Bước 1: Tìm PID ở cột cuối cùng
netstat -ano | findstr :1099

REM Bước 2: Thay <PID> bằng số thực tế (ví dụ 12345)
taskkill /PID <PID> /F
```

### 8.3. Lệnh ĐÓNG cổng trên Tường lửa Windows (Xóa Firewall Rules sau khi thi xong)
Chạy trong **PowerShell/CMD với quyền Administrator**:
```cmd
netsh advfirewall firewall delete rule name="eBanking RMI Registry (1099)"
netsh advfirewall firewall delete rule name="eBanking RMI Service (1098)"
netsh advfirewall firewall delete rule name="eBanking RMI Client Callback (1097)"
```
*Hoặc xóa mọi rule liên quan đến cổng `1097`, `1098`, `1099`:*
```cmd
netsh advfirewall firewall delete rule name=all protocol=tcp localport=1097
netsh advfirewall firewall delete rule name=all protocol=tcp localport=1098
netsh advfirewall firewall delete rule name=all protocol=tcp localport=1099
```

---

## 9. Kịch bản tự test đầy đủ (8 Test Cases)

Hãy thực hiện lần lượt 8 bài kiểm thử thủ công dưới đây để kiểm chứng 100% tính năng trước khi bảo vệ đồ án:

### Test Case 1: Ping kết nối & Đăng ký tài khoản mới
1. Bật `ServerMain` trên Máy 1.
2. Mở `ClientMain` trên Máy 2, nhập IP Máy 1, bấm **`⚡ Ping`** ➔ Xác nhận hiện `ONLINE (...ms)`.
3. Bấm **Mở Tài Khoản Mới (Đăng Ký)** ➔ nhập STK `1004`, Họ tên `Pham Van D`, Username `userd`, Password `123456` ➔ Bấm **ĐĂNG KÝ NGAY**.
4. **Kỳ vọng:** Thông báo tạo thành công với số dư `0 VNĐ`, tự điền `userd` về `LoginForm`. Đăng nhập lại với `userd`/`123456` thành công. Nếu đăng ký trùng `1004` hoặc `userd` lần 2 ➔ báo trùng dữ liệu.

### Test Case 2: Chuyển khoản 24/7 & RMI Callback Realtime (3 máy)
1. Máy 2 đăng nhập `usera` (STK `1001`, số dư `3,500,000 VNĐ`).
2. Máy 3 đăng nhập `userc` (STK `1003`, số dư `2,000,000 VNĐ`).
3. Trên Máy 2, vào tab **Chuyển Khoản 24/7**:
   - Nhập STK nhận `1003`, bấm **Tra Cứu** ➔ hiện xanh `✓ Người thụ hưởng: LE VAN C (Đang hoạt động)`.
   - Nhập số tiền `500000` (hoặc bấm chip `500.000`) ➔ kiểm tra dòng đọc chữ `Năm trăm nghìn đồng`.
   - Nhập nội dung `Thanh toan tien hoc`, bấm **XÁC NHẬN CHUYỂN TIỀN**.
4. **Kỳ vọng:**
   - Máy 2 hiện `TransactionReceiptDialog` (Biên lai điện tử), số dư giảm còn `3,000,000 VNĐ`.
   - Máy 3 **tự động bật popup** `🔔 [BIẾN ĐỘNG SỐ DƯ REALTIME] Tài khoản nhận +500,000 VNĐ từ 1001...` và số dư trên Topbar tự nhảy lên `2,500,000 VNĐ` mà không cần bấm Làm Mới.
5. **Test ngoại lệ chuyển khoản:**
   - Nhập STK nhận `1001` (tự chuyển cho mình) ➔ bị chặn.
   - Nhập STK nhận `1002` (`userb` đang `LOCKED`) ➔ hiện đỏ `✕ Tài khoản này đang bị KHÓA bởi Quản trị viên!`.
   - Nhập số tiền lớn hơn số dư hiện tại ➔ báo không đủ số dư.

### Test Case 3: Nạp tiền & Rút tiền nhanh
1. Trên Máy 2 (`usera`), bấm **`➕ Nạp Tiền`** trên Topbar ➔ chọn `1,000,000 đ` ➔ xác nhận.
2. **Kỳ vọng:** Hiện Popup Callback nạp tiền + Biên lai nạp tiền + số dư tăng `+1,000,000 VNĐ`.
3. Bấm **`➖ Rút Tiền`** ➔ nhập `200,000` ➔ xác nhận.
4. **Kỳ vọng:** Hiện Popup Callback rút tiền + Biên lai rút tiền + số dư giảm `-200,000 VNĐ`. Nếu thử rút số tiền lớn hơn số dư ➔ bị chặn.

### Test Case 4: Thanh toán hóa đơn & Chống thanh toán đúp (ACID)
1. Vào tab **Hóa Đơn & Tiện Ích**, bấm gợi ý **`⚡ Tiền Điện (EVN_HANOI_01)`** ➔ hiện thông tin `Tien Dien`, `Nguyen Van A`, `350,000 VNĐ`, trạng thái `⚠ CHƯA THANH TOÁN (UNPAID)`.
2. Bấm **`✔ Xác Nhận Thanh Toán`**.
3. **Kỳ vọng:** Hiện Callback + Biên lai thành công, số dư bị trừ `350,000 VNĐ`, trạng thái hóa đơn đổi thành `✔ ĐÃ THANH TOÁN (PAID)` và nút Thanh toán bị vô hiệu hóa.
4. Trên Máy 3 (`userc`), thử tra cứu chính mã `EVN_HANOI_01` và kiểm tra:
   - **Kỳ vọng:** Trạng thái đã là `PAID`, không thể thanh toán lại lần thứ 2. (Có thể test tiếp `WA_DANANG_02` - `120,000 VNĐ` và `FPT_NET_03` - `250,000 VNĐ`).

### Test Case 5: Tiết kiệm tích lũy, tự động cộng lãi & Tất toán
1. Trên Máy 2 (`usera`), vào tab **Tiết Kiệm Tích Lũy**.
2. Nhập `1000000`, chọn gói **`⚡ Siêu ngắn: 15 giây (Lãi 5.0% / chu kỳ)`**, bấm **`✨ Mở Sổ Tiết Kiệm`**.
3. **Kỳ vọng:** Số dư chính trừ `1,000,000 VNĐ`, bảng hiển thị sổ mới trạng thái `ACTIVE`.
4. Chờ **15–20 giây** (khi `InterestCalculatorTask` chạy chu kỳ):
   - **Kỳ vọng:** Máy 2 nhận Popup Callback thông báo tiền lãi phát sinh `+50,000 VNĐ` (và sổ #1 có sẵn `500,000` lãi `6.5%` cũng phát sinh `+32,500 VNĐ`). Bấm **Làm Mới Danh Sách** sẽ thấy cột `Lãi Tích Lũy` tăng lên.
5. Chọn sổ vừa mở trong bảng, bấm **`💎 Tất Toán Sổ (Rút Gốc + Lãi)`** ➔ xác nhận.
6. **Kỳ vọng:** Sổ chuyển sang `CLOSED`, tiền gốc + lãi được hoàn về tài khoản chính, các chu kỳ 15s tiếp theo **không còn cộng lãi** cho sổ đã đóng này nữa.

### Test Case 6: Sao kê, lọc ngày & Xuất CSV
1. Vào tab **Sao Kê & Lịch Sử**.
2. **Kỳ vọng:** Hiển thị đủ các giao dịch `NAP_TIEN`, `RUT_TIEN`, `CHUYEN_TIEN`, `THANH_TOAN_HOA_DON`, `MO_SO_TIET_KIEM`, `TAT_TOAN_TIET_KIEM` với màu xanh (`+`) và đỏ (`-`), cùng 3 thẻ KPI (Tổng GD, Tổng Tiền Vào, Tổng Tiền Ra).
3. Bấm **`Hôm nay`** hoặc nhập tay `Từ ngày` / `Đến ngày` dạng `yyyy-MM-dd` ➔ bấm **`🔍 Lọc`**.
4. Bấm **`📥 Xuất File CSV`** ➔ lưu file `.csv` và mở bằng Excel/Notepad kiểm tra tiếng Việt có dấu hiển thị chuẩn (nhờ UTF-8 BOM).

### Test Case 7: Admin SOC (Giám sát, Kick phiên, Khóa & Mở khóa từ xa)
1. Trên Máy 3, đăng xuất `userc` và đăng nhập tài khoản **`admin` / `admin123`**.
2. Quan sát `AdminDashboardForm`:
   - Tab **Giám Sát Phiên Online** hiển thị `1001` (`usera`) đang `🟢 CONNECTED` (nếu Máy 2 đang đăng nhập).
3. Thử **Kick phiên**: Chọn `1001`, bấm **`🚪 Kick Phiên`**, nhập lý do.
   - **Kỳ vọng:** Máy 2 lập tức hiện hộp thoại đỏ *"⚠️ BẠN ĐÃ BỊ ĐĂNG XUẤT CƯỠNG CHẾ BỞI QUẢN TRỊ VIÊN!"*, đóng `DashboardForm` và văng về `LoginForm`. `usera` vẫn đăng nhập lại được vì chưa bị `LOCKED`.
4. Cho `usera` đăng nhập lại trên Máy 2, sau đó trên Máy 3 bấm **`🔒 Khóa Khẩn Cấp & Kick Ngay Lập Tức`** (hoặc vào Tab 2 bấm **`❄️ Đóng Băng / Khóa (Lock)`**).
   - **Kỳ vọng:** Máy 2 văng về `LoginForm`. Thử đăng nhập lại `usera` ➔ bị từ chối.
5. Trên Máy 3, vào Tab **Quản Trị Danh Sách Tài Khoản**, tìm `usera` (hoặc `userb`), bấm **`🔓 Mở Khóa Tài Khoản (Unlock)`** ➔ `usera` đăng nhập lại bình thường.

### Test Case 8: Kiểm thử độ bền phiên & Dead Callback
1. **Login trùng:** Máy 2 đang đăng nhập `usera`. Mở thêm Client trên Máy 3 đăng nhập `usera` ➔ Máy 2 lập tức nhận `forceLogout` *"Tài khoản của bạn đã được đăng nhập từ một phiên làm việc khác"*.
2. **Dead Callback:** Máy 2 đăng nhập `usera`, sau đó đóng ngang tiến trình Client bằng Task Manager / nút Stop IDE (không bấm Đăng xuất). Từ Máy 3 chuyển khoản vào STK `1001`:
   - **Kỳ vọng:** Giao dịch vẫn thành công 100%, Server log thông báo dọn dẹp Dead Callback tại STK `1001` mà **không bị treo hay crash**.

---

## 10. Bộ câu hỏi & Đáp án thi vấn đáp trọng tâm

1. **Java RMI hoạt động như thế nào?**
   - Dựa trên mô hình **Stub (Client Proxy) – Skeleton/Dispatcher (Server)**. Client gọi phương thức trên Stub ➔ Stub tuần tự hóa tham số (**Marshalling**) gửi qua TCP tới Server ➔ Server giải mã (**Unmarshalling**), thực thi hàm trong `BankServiceImpl` rồi gửi kết quả về.
2. **Tại sao các lớp Model (`Account`, `Transaction`, `Bill`, `Saving`) phải `implements Serializable`?**
   - Vì Client và Server chạy ở 2 JVM khác nhau (không chia sẻ RAM). JVM cần chuyển đối tượng thành chuỗi byte để truyền qua Socket TCP và tái tạo lại ở đầu kia.
3. **Tại sao phải cấu hình `java.rmi.server.hostname`? Không cấu hình sẽ bị lỗi gì?**
   - Để ép IP thật của máy chủ (Wi-Fi/LAN/VPN) vào bên trong Stub. Nếu không set, Stub có thể mang IP `127.0.0.1` hoặc IP card mạng ảo VirtualBox (`192.168.56.1`), khiến Client ở máy khác gọi nhầm về chính nó và gặp lỗi `Connection refused to host: 127.0.0.1`.
4. **Tại sao phải cố định 3 cổng `1099`, `1098`, `1097` thay vì để cổng động `0`?**
   - Mặc định `UnicastRemoteObject(0)` mở cổng ngẫu nhiên (`49152–65535`), khiến Windows Firewall chặn khi chạy khác máy. Cố định 3 cổng giúp dễ dàng mở đúng Inbound Rule trên tường lửa Server (`1098`, `1099`) và Client (`1097`).
5. **RMI Callback khác gì với Polling? Phía Client cần gì để nhận Callback?**
   - Polling phải hỏi Server liên tục gây tốn băng thông/CPU và có độ trễ. Callback là kiến trúc hướng sự kiện (Event-driven): Client export `ClientCallbackImpl` (đóng vai trò RMI Server nhỏ trên cổng `1097`), gửi Stub lên Server lưu trong `ConcurrentHashMap`. Khi có sự kiện, Server chủ động đẩy thông báo về Client tức thời.
6. **Client tắt ngang / mất mạng đột ngột khi đang giữ Callback thì Server có bị treo không?**
   - Không. Hàm `triggerCallback()` trong `BankServiceImpl` bọc lời gọi callback trong `try-catch (RemoteException)`. Khi gọi thất bại, Server tự động xóa tham chiếu chết khỏi `onlineClients` và `userSessionMap`.
7. **Hệ thống chống Deadlock và số dư âm khi chuyển khoản đồng thời như thế nào?**
   - **Chống Deadlock:** Khóa 2 tài khoản theo thứ tự từ điển tăng dần (`firstLock` trước `secondLock` dựa trên `fromAcc.compareTo(toAcc)`), phá vỡ điều kiện chờ đợi vòng tròn (Circular Wait).
   - **Chống Race Condition & Số dư âm:** Dùng JDBC Transaction (`setAutoCommit(false)`), khóa hàng bi quan `SELECT ... FOR UPDATE` trên InnoDB, và câu lệnh cập nhật nguyên tử `UPDATE accounts SET balance = balance - ? WHERE account_number = ? AND balance >= ?`.
8. **Chống Thread Leak trong chức năng tiết kiệm tự động như thế nào?**
   - `InterestCalculatorTask` dùng `ScheduledExecutorService` với daemon thread (`setDaemon(true)`), có phương thức `stop()` gọi `shutdown()`/`awaitTermination()`, và SQL cộng lãi kiểm tra `WHERE id = ? AND status = 'ACTIVE'` nên sổ đã tất toán (`CLOSED`) không bao giờ bị cộng lãi nhầm.
9. **Làm thế nào giao diện Swing không bị đơ ("Not Responding") khi gọi mạng RMI?**
   - Toàn bộ tác vụ mạng từ Client được chạy trong luồng nền `SwingWorker` (`doInBackground()`), còn cập nhật giao diện luôn quay về Event Dispatch Thread (EDT) qua `done()` hoặc `SwingUtilities.invokeLater()`.
