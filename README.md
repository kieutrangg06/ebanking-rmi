# KẾ HOẠCH TRIỂN KHAI DỰ ÁN RMI E-BANKING

## 1. Timeline & Mốc thời gian
- Ngày 1 (Bắt đầu): Chốt bộ giao diện RMI (`common/`), tạo skeleton và đẩy lên `main`.
- Ngày 2 - 5 (Làm việc song song): Mỗi dev làm độc lập trên feature branch riêng, không chạm vào `common/`.
- Ngày 6 (Kiểm thử & Tích hợp): Tạo Pull Request, merge từng nhánh vào `main`, test liên thông 3 luồng.
- Ngày 7 (Hoàn thiện & Nộp bài): Đóng băng code, xuất file build/jar, test kịch bản demo và nộp bài.

## 2. Kiến trúc thư mục tách biệt (Tránh Conflict)
ebanking-rmi/
├── src/
│   ├── common/                # [CHỐT DAY 1 - KHÔNG ĐƯỢC ĐỔI NỮA]
│   │   ├── models/            # Account, Bill, Transaction, Saving (Serializable)
│   │   └── rmi/               # IBankService.java, IClientCallback.java
│   │
│   ├── server/                
│   │   ├── db/                # DatabaseConnection.java (Dev 1 dựng khung)
│   │   ├── dao/               # Tách file DAO theo từng người
│   │   │   ├── AccountDAO.java        # Dev 1
│   │   │   ├── BillDAO.java           # Dev 2
│   │   │   ├── TransactionDAO.java    # Dev 2
│   │   │   └── SavingDAO.java         # Dev 3
│   │   ├── service/           # Chia nhỏ Service Implementation
│   │   │   ├── CoreBankServiceImpl.java   # Dev 1 (Login, Transfer, Callback pool)
│   │   │   ├── BillServiceImpl.java       # Dev 2 (Pay bill, Transaction history)
│   │   │   └── SavingServiceImpl.java     # Dev 3 (Interest calculator, Admin revoke)
│   │   └── ServerMain.java    # Khởi tạo RMI Registry (1099), bind dịch vụ
│   │
│   └── client/                
│       ├── callback/          # ClientCallbackImpl.java (Dev 1 dựng cơ chế)
│       └── view/              # Chia folder view riêng cho từng dev
│           ├── auth/          # LoginForm.java, RegisterForm.java (Dev 1)
│           ├── transfer/      # TransferForm.java (Dev 1)
│           ├── bill/          # BillPayForm.java, HistoryForm.java (Dev 2)
│           └── admin/         # SavingForm.java, AdminDashboardForm.java (Dev 3)

## 3. Phân công & Lỗ hổng cần Test (Test Cases)
- Dev 1 (Core & Transfer):
  + Test Double-click transfer (Race Condition -> số dư không được âm).
  + Test Dead Callback (Client B tắt đột ngột -> Server bắt RemoteException, gỡ khỏi session pool, không treo thread).
- Dev 2 (Bill & Transaction):
  + Test ACID Transaction: Ngắt DB khi vừa trừ tiền -> Rollback, không để tình trạng mất tiền nhưng hóa đơn còn nợ.
  + Test Concurrency: 2 client thanh toán cùng 1 mã hóa đơn -> Chỉ 1 máy thành công.
- Dev 3 (Saving & Admin):
  + Test Thread Leak: Tất toán sổ tiết kiệm -> Dừng ScheduledExecutorService tính lãi.
  + Test Remote Revocation: Admin bấm Kick/Lock -> Callback gọi client tự hủy session về Login.
