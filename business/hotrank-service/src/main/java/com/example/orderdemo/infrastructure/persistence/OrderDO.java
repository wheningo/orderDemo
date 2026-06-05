package com.example.orderdemo.infrastructure.persistence;

/**
 * Database-mapped data object for the orders table.
 * Intentionally kept as a plain mutable POJO for MyBatis compatibility.
 */
public class OrderDO {

    private Long id;
    private String productName;
    private int quantity;
    private String state;
    private long version;
    private String idempotencyKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}