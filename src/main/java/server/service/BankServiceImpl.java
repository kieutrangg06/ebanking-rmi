package server.service;

import common.models.Account;
import common.rmi.IBankService;
import common.rmi.IClientCallback;
import server.dao.AccountDAO;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BankServiceImpl extends UnicastRemoteObject implements IBankService {
    private static final long serialVersionUID = 1L;

    // DAO quản lý tài khoản & giao dịch lõi (Kiến trúc 3-Tier chuẩn)
    private final AccountDAO accountDAO = new AccountDAO();

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
