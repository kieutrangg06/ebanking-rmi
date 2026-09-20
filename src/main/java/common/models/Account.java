package common.models;

import java.io.Serializable;

public class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String accountNumber;
    private String username;
    private String password;
    private String fullName;
    private double balance;
    private String status; // ACTIVE, LOCKED

    public Account() {}

    public Account(int id, String accountNumber, String username, String password, String fullName, double balance, String status) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.balance = balance;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return fullName + " (" + accountNumber + ") - Số dư: " + balance + " VNĐ";
    }
}