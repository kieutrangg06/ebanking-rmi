package server;

import common.rmi.IBankService;
import server.service.BankServiceImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerMain {
    public static final int RMI_PORT = 1099;
    public static final String SERVICE_NAME = "BankService";

    public static void main(String[] args) {
        try {
            // 1. Tạo hoặc kết nối RMI Registry trên cổng chuẩn 1099
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(RMI_PORT);
                System.out.println(">> RMI Registry khoi tao thanh cong tai cong " + RMI_PORT + ".");
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(RMI_PORT);
                System.out.println(">> Ket noi RMI Registry co san tai cong " + RMI_PORT + ".");
            }

            // 2. Khởi tạo đối tượng xử lý nghiệp vụ ngân hàng
            IBankService bankService = new BankServiceImpl();

            // 3. Đăng ký dịch vụ với tên định danh "BankService"
            registry.rebind(SERVICE_NAME, bankService);
            System.out.println(">> Dịch vụ '" + SERVICE_NAME + "' da san sang tiep nhan ket noi tu Client...");

        } catch (Exception e) {
            System.err.println("Loi khoi dong Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
