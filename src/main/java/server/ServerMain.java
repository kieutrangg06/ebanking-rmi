package server;

import common.rmi.IBankService;
import server.service.BankServiceImpl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerMain {
    public static void main(String[] args) {
        try {
            // 1. Tạo RMI Registry trên cổng chuẩn 1099
            Registry registry = LocateRegistry.createRegistry(1099);
            System.out.println(">> RMI Registry khoi tao thanh cong tai cong 1099.");

            // 2. Khởi tạo đối tượng xử lý nghiệp vụ
            IBankService bankService = new BankServiceImpl();

            // 3. Đăng ký dịch vụ với tên định danh "BankService"
            registry.rebind("BankService", bankService);
            System.out.println(">> Dịch vụ 'BankService' da san sang tiep nhan ket noi...");

        } catch (Exception e) {
            System.err.println("Loi khoi dong Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}