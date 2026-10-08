package common.models;

import java.io.Serializable;
import java.sql.Timestamp;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String transactionType; // CHUYEN_TIEN, NHAN_TIEN, THANH_TOAN_HOA_DON, TIET_KIEM
    private String fromAccount;
    private String toAccount;
    private double amount;
    private String description;
    private Timestamp createdAt;

    public Transaction() {}

    public Transaction(int id, String transactionType, String fromAccount, String toAccount, double amount, String description, Timestamp createdAt) {
        this.id = id;
        this.transactionType = transactionType;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getFromAccount() { return fromAccount; }
    public void setFromAccount(String fromAccount) { this.fromAccount = fromAccount; }

    public String getToAccount() { return toAccount; }
    public void setToAccount(String toAccount) { this.toAccount = toAccount; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}