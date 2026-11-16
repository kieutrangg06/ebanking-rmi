package common.rmi;

import common.models.Account;     // Model thông tin tài khoản ngân hàng (truyền qua mạng nhờ Serializable)
import common.models.Bill;        // Model hóa đơn tiền điện/nước/internet (Serializable)
import common.models.Saving;      // Model sổ tiết kiệm tích lũy lãi (Serializable)
import common.models.Transaction; // Model lịch sử giao dịch sao kê (Serializable)

import java.rmi.Remote;           // Marker Interface bắt buộc của RMI để đánh dấu đối tượng có thể gọi từ xa
import java.rmi.RemoteException;  // Ngoại lệ bắt buộc ném ra ở mọi hàm RMI khi xảy ra lỗi mạng / ngắt kết nối
import java.util.Date;            // Kiểu dữ liệu ngày tháng dùng lọc sao kê giao dịch
import java.util.List;            // Danh sách dữ liệu trả về cho Client (danh sách tài khoản, hóa đơn, sổ tiết kiệm)

/**
 * Interface từ xa (Remote Interface) của máy chủ Ngân hàng RMI.
 * Kế thừa java.rmi.Remote để RMI Registry quản lý và tạo Stub cho Client gọi hàm qua mạng.
 * Tất cả các phương thức đều phải ném RemoteException theo đặc tả của Java RMI.
 */
public interface IBankService extends Remote {

    // =========================================================================
    // 1. NHÓM XÁC THỰC, PHIÊN LÀM VIỆC & QUẢN LÝ TÀI KHOẢN
    // =========================================================================

    /**
     * Đăng nhập hệ thống, lưu callback để Server chủ động đẩy thông báo biến động số dư về Client
     */
    Account login(String username, String password, IClientCallback callback) throws RemoteException;

    /**
     * Đăng ký tài khoản khách hàng mới vào cơ sở dữ liệu
     */
    boolean register(String username, String password, String fullName, String accountNumber) throws RemoteException;

    /**
     * Đăng xuất khỏi hệ thống và hủy đăng ký nhận callback trên Server
     */
    void logout(String username) throws RemoteException;

    /**
     * Lấy số dư hiện tại của tài khoản thanh toán
     */
    double getBalance(String accountNumber) throws RemoteException;

    /**
     * Lấy đầy đủ đối tượng Account theo số tài khoản
     */
    Account getAccountByNumber(String accountNumber) throws RemoteException;

    /**
     * Lấy danh sách username các tài khoản đang trực tuyến (Online) trên hệ thống
     */
    List<String> getOnlineUsers() throws RemoteException;

    // =========================================================================
    // 2. NHÓM GIAO DỊCH TÀI CHÍNH (NẠP / RÚT / CHUYỂN TIỀN)
    // =========================================================================

    /**
     * Chuyển khoản nội bộ giữa 2 tài khoản (sử dụng Transaction Lock Ordering chống Deadlock)
     */
    boolean transfer(String fromAccountNumber, String toAccountNumber, double amount, String description) throws RemoteException;

    /**
     * Nạp tiền vào tài khoản chỉ định
     */
    boolean deposit(String accountNumber, double amount, String description) throws RemoteException;

    /**
     * Rút tiền từ tài khoản chỉ định (kiểm tra điều kiện số dư đủ trên Database)
     */
    boolean withdraw(String accountNumber, double amount, String description) throws RemoteException;

    // =========================================================================
    // 3. NHÓM THANH TOÁN HÓA ĐƠN & SAO KÊ GIAO DỊCH
    // =========================================================================

    /**
     * Tra cứu thông tin hóa đơn điện/nước/viễn thông theo mã hóa đơn
     */
    Bill queryBill(String billCode) throws RemoteException;

    /**
     * Thanh toán hóa đơn bằng tiền trong tài khoản (khóa dòng FOR UPDATE chống thanh toán trùng)
     */
    boolean payBill(String accountNumber, String billCode) throws RemoteException;

    /**
     * Lấy toàn bộ lịch sử biến động số dư và giao dịch của tài khoản
     */
    List<Transaction> getTransactionHistory(String accountNumber) throws RemoteException;

    /**
     * Lọc sao kê giao dịch tài khoản theo khoảng thời gian từ ngày -> đến ngày
     */
    List<Transaction> getTransactionHistoryFiltered(String accountNumber, Date fromDate, Date toDate) throws RemoteException;

    // =========================================================================
    // 4. NHÓM TIẾT KIỆM TÍCH LŨY (SAVINGS)
    // =========================================================================

    /**
     * Mở sổ tiết kiệm mới, trích tiền từ tài khoản thanh toán sang sổ tiết kiệm
     */
    boolean openSaving(String accountNumber, double amount, double interestRate, int termSeconds) throws RemoteException;

    /**
     * Tất toán sổ tiết kiệm: hoàn trả cả tiền gốc lẫn lãi tích lũy về tài khoản thanh toán
     */
    boolean settleSaving(int savingId) throws RemoteException;

    /**
     * Lấy danh sách tất cả các sổ tiết kiệm thuộc về một số tài khoản
     */
    List<Saving> getSavingsByAccount(String accountNumber) throws RemoteException;

    // =========================================================================
    // 5. NHÓM QUẢN TRỊ VIÊN (ADMIN DASHBOARD & CONTROL)
    // =========================================================================

    /**
     * Admin khóa tài khoản người dùng và cưỡng chế đá văng khỏi hệ thống từ xa
     */
    boolean lockAccount(String accountNumber, String reason) throws RemoteException;

    /**
     * Admin cưỡng chế đăng xuất người dùng đang online (Force Logout qua Callback)
     */
    boolean kickUser(String accountNumber, String reason) throws RemoteException;

    /**
     * Admin mở khóa tài khoản người dùng
     */
    boolean unlockAccount(String accountNumber) throws RemoteException;

    /**
     * Admin lấy danh sách toàn bộ tài khoản trong hệ thống ngân hàng
     */
    List<Account> getAllAccounts() throws RemoteException;

    /**
     * Admin thống kê tổng số dư tiền gửi của toàn bộ hệ thống
     */
    double getTotalSystemBalance() throws RemoteException;
}
