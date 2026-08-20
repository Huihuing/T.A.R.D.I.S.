package com.tardistock.backend.controller;

import com.tardistock.backend.entity.TradeHistory;
import com.tardistock.backend.repository.TradeHistoryRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class TradeController {

    private double myBalance = 10000.00;
    
    // DB와 통신하는 리포지토리를 불러옵니다.
    private final TradeHistoryRepository repository;

    // 생성자를 통해 스프링이 자동으로 리포지토리를 주입(연결)해 줍니다.
    public TradeController(TradeHistoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/balance")
    public double getBalance() {
        return myBalance;
    }

    @PostMapping("/buy")
    public TradeResponse buyStock(@RequestBody TradeRequest request) {
        double totalCost = request.amount() * request.price();
        
        if (myBalance < totalCost) {
            return new TradeResponse("FAIL", "잔고가 부족합니다!", myBalance);
        }
        
        myBalance -= totalCost;
        
        // 💾 매수 성공 시 DB에 기록 저장!
        TradeHistory history = new TradeHistory("BUY", request.symbol(), request.amount(), request.price());
        repository.save(history); // 이 한 줄로 SQL의 INSERT문이 실행됩니다.

        return new TradeResponse("SUCCESS", request.symbol() + " 매수 완료 및 DB 저장!", myBalance);
    }

    @PostMapping("/sell")
    public TradeResponse sellStock(@RequestBody TradeRequest request) {
        double totalRevenue = request.amount() * request.price();
        myBalance += totalRevenue;
        
        // 💾 매도 성공 시 DB에 기록 저장!
        TradeHistory history = new TradeHistory("SELL", request.symbol(), request.amount(), request.price());
        repository.save(history);

        return new TradeResponse("SUCCESS", request.symbol() + " 매도 완료 및 DB 저장!", myBalance);
    }
}

record TradeRequest(String symbol, int amount, double price) {}
record TradeResponse(String status, String message, double newBalance) {}