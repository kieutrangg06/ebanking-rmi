package server.service;

import common.models.Account;
import common.models.Bill;
import common.models.Transaction;
import common.rmi.IBankService;
import common.rmi.IClientCallback;
import server.dao.AccountDAO;
import server.dao.BillDAO;
import server.dao.TransactionDAO;
import server.db.DatabaseConnection;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BankServiceImpl extends UnicastRemoteObject implements IBankService {
    private static final long serialVersionUID = 1L;

    // DAO quản lý tài khoản & giao dịch lõi (Kiến trúc 3-Tier chuẩn)
    private final AccountDAO accountDAO = new AccountDAO();
    private final BillDAO billDAO = new BillDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    // Quản lý phiên kết nối tập trung trên Server bằng ConcurrentHashMap
    // accountNumber -> Callback
    private final ConcurrentHashMap<String, IClientCallback> onlineClients = new ConcurrentHashMap<>();

    // Map phụ lưu username -> accountNumber để tiện tra cứu phiên
    private final ConcurrentHashMap<String, String> userSessionMap = new ConcurrentHashMap<>();

    public BankServiceImpl() throws RemoteException {
        super();
    }

    // =========================================================================
    // NGƯỜI 1: AUTHENTICATION & QUẢN LÝ PHIÊN (LOGIN, LOGOUT, REGISTER)
    // =========================================================================

    @Override
    public synchronized Account login(String username, String password, IClientCallback callback) throws RemoteException {
        if (username == null || password == null) return null;
        final String uName = username.trim();
        final String pwd = password.trim();

        try {
            Account acc = accountDAO.authenticate(uName, pwd);
            if (acc != null) {
                if ("LOCKED".equalsIgnoreCase(acc.getStatus())) {
                    System.out.println(">> [LOGIN REJECTED] Tài khoản đang bị khóa: " + uName);
                    return null;
                }

                // Xử lý login trùng: nếu user hoặc tài khoản này đã có phiên online trước đó -> kick phiên cũ
                String oldAcc = userSessionMap.get(uName);
                if (oldAcc != null) {
                    IClientCallback oldCb = onlineClients.remove(oldAcc);
                    if (oldCb != null) {
                        try {
                            oldCb.forceLogout("Tài khoản của bạn đã được đăng nhập từ một phiên làm việc khác.");
                        } catch (RemoteException ignored) {
                            // Client cũ đã ngắt kết nối
                        }
                    }
                    userSessionMap.remove(uName);
                }

                // Nếu có callback đăng ký cùng accountNumber
                IClientCallback existingAccCb = onlineClients.remove(acc.getAccountNumber());
                if (existingAccCb != null && existingAccCb != callback) {
                    try {
                        existingAccCb.forceLogout("Phiên làm việc của bạn đã hết hạn do tài khoản được đăng nhập ở nơi khác.");
                    } catch (RemoteException ignored) {}
                }

                // Đăng ký Callback lắng nghe biến động số dư
                if (callback != null) {
                    onlineClients.put(acc.getAccountNumber(), callback);
                }
                userSessionMap.put(uName, acc.getAccountNumber());
                System.out.println(">> [ONLINE] User: " + uName + " (STK: " + acc.getAccountNumber() + ")");
                return acc;
            }
        } catch (SQLException e) {
            System.err.println("Lỗi xác thực người dùng trong Database: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean register(String username, String password, String fullName, String accountNumber) throws RemoteException {
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty() ||
            fullName == null || fullName.trim().isEmpty() ||
            accountNumber == null || accountNumber.trim().isEmpty()) {
            return false;
        }

        try {
            return accountDAO.createAccount(username.trim(), password.trim(), fullName.trim(), accountNumber.trim());
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký tài khoản mới: " + e.getMessage());
            return false;
        }
    }

    @Override
    public synchronized void logout(String username) throws RemoteException {
        if (username == null) return;
        final String uName = username.trim();

        String accNum = userSessionMap.remove(uName);
        if (accNum != null) {
            onlineClients.remove(accNum);
            System.out.println("<< [OFFLINE] User: " + uName + " (STK: " + accNum + ")");
        } else {
            // Trường hợp truyền vào STK thay vì username
            onlineClients.remove(uName);
            userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(uName));
            System.out.println("<< [OFFLINE] Session cleanup for: " + uName);
        }
    }

    @Override
    public double getBalance(String accountNumber) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return -1;
        try {
            return accountDAO.getBalance(accountNumber.trim());
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn số dư tài khoản: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public Account getAccountByNumber(String accountNumber) throws RemoteException {
        if (accountNumber == null || accountNumber.trim().isEmpty()) return null;
        try {
            return accountDAO.findByAccountNumber(accountNumber.trim());
        } catch (SQLException e) {
            System.err.println("Lỗi tra cứu thông tin tài khoản: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<String> getOnlineUsers() throws RemoteException {
        return new ArrayList<>(onlineClients.keySet());
    }

    // =========================================================================
    // NGƯỜI 1: CHUYỂN TIỀN ACID & RMI CALLBACK PATTERN
    // =========================================================================

    @Override
    public synchronized boolean transfer(String fromAcc, String toAcc, double amount, String description) throws RemoteException {
        if (amount <= 0 || fromAcc == null || toAcc == null) return false;
        fromAcc = fromAcc.trim();
        toAcc = toAcc.trim();
        if (fromAcc.isEmpty() || toAcc.isEmpty() || fromAcc.equals(toAcc)) return false;

        try {
            boolean success = accountDAO.executeTransfer(fromAcc, toAcc, amount, description);
            if (success) {
                // Thực hiện Callback cho người nhận tiền (Event-driven Notification)
                String cbMsg = "Tài khoản nhận +" + String.format("%,.0f", amount) + " VNĐ từ " + fromAcc
                        + (description != null && !description.isEmpty() ? " (ND: " + description + ")" : "");
                triggerCallback(toAcc, cbMsg);
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Lỗi giao dịch chuyển khoản: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // =========================================================================
    // NGƯỜI 2: THANH TOÁN HÓA ĐƠN & XUẤT SAO KÊ GIAO DỊCH (3-TIER + ACID)
    // =========================================================================

    @Override
    public Bill queryBill(String billCode) throws RemoteException {
        return billDAO.queryBill(billCode);
    }

    @Override
    public synchronized boolean payBill(String param1, String param2) throws RemoteException {
        // Tự động phân định tham số (hỗ trợ cả payBill(acc, billCode) và payBill(billCode, acc)):
        String accountNumber;
        String billCode;

        Bill checkBillParam2 = billDAO.queryBill(param2);
        if (checkBillParam2 != null) {
            accountNumber = param1;
            billCode = param2;
        } else {
            Bill checkBillParam1 = billDAO.queryBill(param1);
            if (checkBillParam1 != null) {
                billCode = param1;
                accountNumber = param2;
            } else {
                accountNumber = param1;
                billCode = param2;
            }
        }

        if (accountNumber == null || accountNumber.trim().isEmpty() ||
            billCode == null || billCode.trim().isEmpty()) {
            throw new RemoteException("Thông tin số tài khoản hoặc mã hóa đơn không hợp lệ!");
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // BẮT ĐẦU JDBC TRANSACTION (ACID)

            // BƯỚC 1: Khóa dòng hóa đơn chống Double Payment (Dùng SELECT ... FOR UPDATE)
            Bill bill = billDAO.queryBillForUpdate(conn, billCode);
            if (bill == null) {
                conn.rollback();
                throw new RemoteException("Không tìm thấy hóa đơn có mã: " + billCode);
            }

            if ("PAID".equalsIgnoreCase(bill.getStatus())) {
                conn.rollback();
                throw new RemoteException("Hóa đơn đã được thanh toán trước đó (Mã HĐ: " + billCode + ")!");
            }

            // BƯỚC 2: Kiểm tra tài khoản người thanh toán (tồn tại, không bị khóa, số dư >= số tiền bill)
            Account acc = accountDAO.getAccountByNumberForUpdate(conn, accountNumber);
            if (acc == null) {
                conn.rollback();
                throw new RemoteException("Tài khoản người thanh toán không tồn tại: " + accountNumber);
            }

            if ("LOCKED".equalsIgnoreCase(acc.getStatus())) {
                conn.rollback();
                throw new RemoteException("Tài khoản đang bị khóa, không thể thực hiện giao dịch thanh toán!");
            }

            if (acc.getBalance() < bill.getAmount()) {
                conn.rollback();
                throw new RemoteException(String.format("Số dư tài khoản không đủ để thanh toán (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ)!",
                        acc.getBalance(), bill.getAmount()));
            }

            // BƯỚC 3: Trừ tiền tài khoản người thanh toán (ACID deduct với balance >= amount)
            boolean deducted = accountDAO.deductBalance(conn, accountNumber, bill.getAmount());
            if (!deducted) {
                conn.rollback();
                throw new RemoteException("Trừ tiền tài khoản thất bại!");
            }

            // BƯỚC 4: Cập nhật trạng thái hóa đơn: "UNPAID" -> "PAID"
            boolean billUpdated = billDAO.updateBillStatus(conn, billCode, "PAID");
            if (!billUpdated) {
                conn.rollback();
                throw new RemoteException("Cập nhật trạng thái hóa đơn thất bại!");
            }

            // BƯỚC 5: Thêm bản ghi vào bảng transactions
            String desc = "Thanh toan hoa don: " + bill.getServiceType() + " (" + bill.getCustomerName() + ") - Ma HD: " + billCode;
            Transaction tx = new Transaction(
                    0,
                    "THANH_TOAN_HOA_DON",
                    accountNumber,
                    billCode,
                    bill.getAmount(),
                    desc,
                    new Timestamp(System.currentTimeMillis())
            );
            boolean txSaved = transactionDAO.insertTransaction(conn, tx);
            if (!txSaved) {
                conn.rollback();
                throw new RemoteException("Ghi nhận lịch sử giao dịch thất bại!");
            }

            // CHỐT GIAO DỊCH THÀNH CÔNG (ACID COMMIT)
            conn.commit();
            conn.setAutoCommit(true);

            // BẮN CALLBACK THÔNG BÁO BIẾN ĐỘNG SỐ DƯ (NẾU CLIENT ĐANG ONLINE)
            triggerCallback(accountNumber, "Thanh toán thành công hóa đơn " + billCode + " (" + bill.getServiceType() + ") -" + String.format("%,.0f VNĐ", bill.getAmount()));

            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new RemoteException("Lỗi CSDL trong quá trình thanh toán: " + e.getMessage(), e);
        } catch (RemoteException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw e;
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new RemoteException("Thanh toán thất bại: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    @Override
    public List<Transaction> getTransactionHistory(String accountNumber) throws RemoteException {
        return transactionDAO.getTransactionHistory(accountNumber);
    }

    @Override
    public List<Transaction> getTransactionHistoryFiltered(String accountNumber, Date fromDate, Date toDate) throws RemoteException {
        return transactionDAO.getTransactionHistoryFiltered(accountNumber, fromDate, toDate);
    }

    /**
     * Bắn Callback an toàn: Bắt lỗi RemoteException và dọn dẹp các máy khách bị rớt mạng đột ngột (Dead Reference)
     * Đảm bảo Server KHÔNG bị treo luồng (hang thread) hay crash Server.
     */
    private void triggerCallback(String accountNumber, String message) {
        IClientCallback cb = onlineClients.get(accountNumber);
        if (cb != null) {
            try {
                double latestBal = getBalance(accountNumber);
                cb.notifyBalanceChange(message, latestBal);
            } catch (RemoteException e) {
                // Client đã ngắt kết nối bất thường (rút dây mạng, tắt app ngang)
                System.err.println("Gặp Dead Callback Reference tại STK: " + accountNumber + ". Đang dọn dẹp session...");
                onlineClients.remove(accountNumber);
                userSessionMap.entrySet().removeIf(entry -> entry.getValue().equals(accountNumber));
            }
        }
    }
}
