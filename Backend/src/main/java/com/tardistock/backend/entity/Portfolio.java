package com.tardistock.backend.entity;

import jakarta.persistence.*;

@Entity
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol; // 주식 이름 (AAPL 등)
    private int amount; // 보유 수량
    private double averagePrice; // 평균 매수 단가

    public Portfolio() {
    }

    public Portfolio(String symbol, int amount, double averagePrice) {
        this.symbol = symbol;
        this.amount = amount;
        this.averagePrice = averagePrice;
    }

    // 데이터를 읽고 쓰기 위한 Getter와 Setter
    public String getSymbol() {
        return symbol;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(double averagePrice) {
        this.averagePrice = averagePrice;
    }
}