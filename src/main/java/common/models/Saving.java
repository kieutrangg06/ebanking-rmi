package common.models;

import java.io.Serializable;
import java.sql.Timestamp;

public class Saving implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String accountNumber;
    private double depositAmount;
    private double interestRate; // % mỗi chu kỳ
    private int termPeriod;      // Chu kỳ tính lãi (tính bằng giây để dễ demo trực tiếp)
    private double accumulatedInterest;
    private String status;       // ACTIVE, CLOSED
    private Timestamp createdAt;

    public Saving() {}

    public Saving(int id, String accountNumber, double depositAmount, double interestRate, int termPeriod, double accumulatedInterest, String status, Timestamp createdAt) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.depositAmount = depositAmount;
        this.interestRate = interestRate;
        this.termPeriod = termPeriod;
        this.accumulatedInterest = accumulatedInterest;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public double getDepositAmount() { return depositAmount; }
    public void setDepositAmount(double depositAmount) { this.depositAmount = depositAmount; }

    public double getInterestRate() { return interestRate; }
    public void setInterestRate(double interestRate) { this.interestRate = interestRate; }

    public int getTermPeriod() { return termPeriod; }
    public void setTermPeriod(int termPeriod) { this.termPeriod = termPeriod; }

    public double getAccumulatedInterest() { return accumulatedInterest; }
    public void setAccumulatedInterest(double accumulatedInterest) { this.accumulatedInterest = accumulatedInterest; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}