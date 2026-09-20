package common.models;

import java.io.Serializable;

public class Bill implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String billCode;
    private String serviceType;
    private String customerName;
    private double amount;
    private String status; // UNPAID, PAID

    public Bill() {}

    public Bill(int id, String billCode, String serviceType, String customerName, double amount, String status) {
        this.id = id;
        this.billCode = billCode;
        this.serviceType = serviceType;
        this.customerName = customerName;
        this.amount = amount;
        this.status = status;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBillCode() { return billCode; }
    public void setBillCode(String billCode) { this.billCode = billCode; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "[" + billCode + "] " + serviceType + " - " + customerName + ": " + amount + " VNĐ (" + status + ")";
    }
}