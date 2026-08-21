package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.TradeHistory;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.WalletRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/trade")
@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:5174" })
public class TradeController {

    private final TradeHistoryRepository historyRepository;
    private final PortfolioRepository portfolioRepository;
    private final WalletRepository walletRepository;

    public TradeController(TradeHistoryRepository historyRepository,
            PortfolioRepository portfolioRepository,
            WalletRepository walletRepository) {
        this.historyRepository = historyRepository;
        this.portfolioRepository = portfolioRepository;
        this.walletRepository = walletRepository;
    }

    private Wallet getMyWallet() {
        return walletRepository.findById(1L).orElseGet(() -> {
            Wallet newWallet = new Wallet(1L, 10000.00);
            return walletRepository.save(newWallet);
        });
    }

    @GetMapping("/balance")
    public double getBalance() {
        return getMyWallet().getBalance();
    }

    @GetMapping("/history")
    public List<TradeHistory> getTradeHistory() {
        return historyRepository.findAll(
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
    }

    // ==========================================
    // 📜 새로 추가된 부분: 내 보유 주식(포트폴리오) 가져오기
    // ==========================================
    @GetMapping("/portfolio")
    public List<Portfolio> getPortfolio() {
        return portfolioRepository.findAll();
    }
    // ==========================================

    @PostMapping("/buy")
    public TradeResponse buyStock(@RequestBody TradeRequest request) {
        Wallet myWallet = getMyWallet();
        double totalCost = request.amount() * request.price();

        if (myWallet.getBalance() < totalCost) {
            return new TradeResponse("FAIL", "잔고가 부족합니다!", myWallet.getBalance());
        }

        myWallet.setBalance(myWallet.getBalance() - totalCost);
        walletRepository.save(myWallet);

        Optional<Portfolio> optionalPortfolio = portfolioRepository.findBySymbol(request.symbol());

        if (optionalPortfolio.isPresent()) {
            Portfolio portfolio = optionalPortfolio.get();
            double newTotalCost = (portfolio.getAmount() * portfolio.getAveragePrice()) + totalCost;
            int newTotalAmount = portfolio.getAmount() + request.amount();

            portfolio.setAveragePrice(newTotalCost / newTotalAmount);
            portfolio.setAmount(newTotalAmount);
            portfolioRepository.save(portfolio);
        } else {
            Portfolio newPortfolio = new Portfolio(request.symbol(), request.amount(), request.price());
            portfolioRepository.save(newPortfolio);
        }

        TradeHistory history = new TradeHistory("BUY", request.symbol(), request.amount(), request.price());
        historyRepository.save(history);

        return new TradeResponse("SUCCESS", request.symbol() + " 매수 체결 완료!", myWallet.getBalance());
    }

    @PostMapping("/sell")
    public TradeResponse sellStock(@RequestBody TradeRequest request) {
        Optional<Portfolio> optionalPortfolio = portfolioRepository.findBySymbol(request.symbol());
        Wallet myWallet = getMyWallet();

        if (optionalPortfolio.isEmpty() || optionalPortfolio.get().getAmount() < request.amount()) {
            return new TradeResponse("FAIL", "보유 주식이 없거나 수량이 부족합니다!", myWallet.getBalance());
        }

        Portfolio portfolio = optionalPortfolio.get();

        double totalRevenue = request.amount() * request.price();
        myWallet.setBalance(myWallet.getBalance() + totalRevenue);
        walletRepository.save(myWallet);

        portfolio.setAmount(portfolio.getAmount() - request.amount());

        if (portfolio.getAmount() == 0) {
            portfolioRepository.delete(portfolio);
        } else {
            portfolioRepository.save(portfolio);
        }

        TradeHistory history = new TradeHistory("SELL", request.symbol(), request.amount(), request.price());
        historyRepository.save(history);

        return new TradeResponse("SUCCESS", request.symbol() + " 매도 체결 완료!", myWallet.getBalance());
    }
}

record TradeRequest(String symbol, int amount, double price) {
}

record TradeResponse(String status, String message, double newBalance) {
}