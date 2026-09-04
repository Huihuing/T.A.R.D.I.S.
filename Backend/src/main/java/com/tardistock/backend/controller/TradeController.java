package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Portfolio;
import com.tardistock.backend.entity.TradeHistory;
import com.tardistock.backend.entity.Wallet;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PortfolioRepository;
import com.tardistock.backend.repository.TradeHistoryRepository;
import com.tardistock.backend.repository.WalletRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/trade")
@CrossOrigin(origins = "*")
public class TradeController {

    private final MemberRepository memberRepository;
    private final WalletRepository walletRepository;
    private final PortfolioRepository portfolioRepository; // 👈 포트폴리오 창고 추가!
    private final TradeHistoryRepository tradeHistoryRepository; // 👈 영수증 창고 추가!

    public TradeController(MemberRepository memberRepository, WalletRepository walletRepository, 
                           PortfolioRepository portfolioRepository, TradeHistoryRepository tradeHistoryRepository) {
        this.memberRepository = memberRepository;
        this.walletRepository = walletRepository;
        this.portfolioRepository = portfolioRepository;
        this.tradeHistoryRepository = tradeHistoryRepository;
    }

    // 📜 1. 잔고 조회
    @GetMapping("/balance")
    public double getBalance(@RequestParam(required = false, defaultValue = "") String username) {
        if (username.isEmpty()) return 0.0;
        Optional<Member> member = memberRepository.findByUsername(username);
        if (member.isPresent()) {
            Optional<Wallet> wallet = walletRepository.findByMember(member.get());
            if (wallet.isPresent()) return wallet.get().getBalance();
        }
        return 0.0;
    }

    // 📜 2. 거래 내역(영수증) 조회
    @GetMapping("/history")
    public List<TradeHistory> getHistory(@RequestParam(required = false, defaultValue = "") String username) {
        Optional<Member> member = memberRepository.findByUsername(username);
        return member.map(tradeHistoryRepository::findByMember).orElseGet(List::of);
    }

    // 📜 3. 포트폴리오(내 주식) 조회
    @GetMapping("/portfolio")
    public List<Portfolio> getPortfolio(@RequestParam(required = false, defaultValue = "") String username) {
        Optional<Member> member = memberRepository.findByUsername(username);
        return member.map(portfolioRepository::findByMember).orElseGet(List::of);
    }

    // 🚀 4. 진짜 매수 (BUY) 로직
    @PostMapping("/buy")
    public Map<String, String> buyStock(@RequestBody Map<String, Object> payload, @RequestParam String username) {
        Map<String, String> response = new HashMap<>();
        Optional<Member> memberOpt = memberRepository.findByUsername(username);
        
        if (memberOpt.isEmpty()) {
            response.put("status", "FAIL"); response.put("message", "로그인이 필요합니다.");
            return response;
        }

        Member member = memberOpt.get();
        Wallet wallet = walletRepository.findByMember(member).orElse(null);
        
        String symbol = (String) payload.get("symbol");
        int amount = (int) payload.get("amount");
        double price = Double.parseDouble(payload.get("price").toString());
        double totalCost = price * amount;

        // 🛑 돈이 부족한지 체크
        if (wallet == null || wallet.getBalance() < totalCost) {
            response.put("status", "FAIL"); response.put("message", "잔액이 부족합니다!");
            return response;
        }

        // 🟢 1. 지갑에서 돈 빼기
        wallet.setBalance(wallet.getBalance() - totalCost);
        walletRepository.save(wallet);

        // 🟢 2. 포트폴리오에 주식 넣기 (기존에 있으면 합치기, 없으면 새로 생성)
        Portfolio portfolio = portfolioRepository.findByMemberAndSymbol(member, symbol)
                .orElse(new Portfolio(member, symbol, 0, 0.0));
        
        double newTotalValue = (portfolio.getAmount() * portfolio.getAveragePrice()) + totalCost;
        int newAmount = portfolio.getAmount() + amount;
        portfolio.setAmount(newAmount);
        portfolio.setAveragePrice(newTotalValue / newAmount); // 평단가 계산
        portfolioRepository.save(portfolio);

        // 🟢 3. 영수증(History) 기록하기
        TradeHistory history = new TradeHistory(member, "BUY", symbol, amount, price, LocalDateTime.now());
        tradeHistoryRepository.save(history);

        response.put("status", "SUCCESS"); response.put("message", symbol + " " + amount + "주 매수 완료!");
        return response;
    }

// 🚀 5. 진짜 매도 (SELL) 로직
    @PostMapping("/sell")
    public Map<String, String> sellStock(@RequestBody Map<String, Object> payload, @RequestParam String username) {
        Map<String, String> response = new HashMap<>();
        Optional<Member> memberOpt = memberRepository.findByUsername(username);

        if (memberOpt.isEmpty()) {
            response.put("status", "FAIL"); response.put("message", "로그인이 필요합니다.");
            return response;
        }

        Member member = memberOpt.get();
        Wallet wallet = walletRepository.findByMember(member).orElse(null);

        String symbol = (String) payload.get("symbol");
        int amount = (int) payload.get("amount");
        double price = Double.parseDouble(payload.get("price").toString());
        double totalRevenue = price * amount;

        // 🛑 보유 주식이 충분한지 체크 (주식이 아예 없거나, 팔려는 양보다 적으면 컷!)
        Optional<Portfolio> portfolioOpt = portfolioRepository.findByMemberAndSymbol(member, symbol);
        if (portfolioOpt.isEmpty() || portfolioOpt.get().getAmount() < amount) {
            response.put("status", "FAIL"); response.put("message", "보유 주식이 부족합니다!");
            return response;
        }

        Portfolio portfolio = portfolioOpt.get();

        // 🟢 1. 포트폴리오에서 주식 빼기
        int newAmount = portfolio.getAmount() - amount;
        if (newAmount == 0) {
            portfolioRepository.delete(portfolio); // 다 팔았으면 목록에서 아예 삭제
        } else {
            portfolio.setAmount(newAmount);
            portfolioRepository.save(portfolio);
        }

        // 🟢 2. 지갑에 돈(수익금) 더하기
        wallet.setBalance(wallet.getBalance() + totalRevenue);
        walletRepository.save(wallet);

        // 🟢 3. 영수증(History) 기록하기
        TradeHistory history = new TradeHistory(member, "SELL", symbol, amount, price, LocalDateTime.now());
        tradeHistoryRepository.save(history);

        response.put("status", "SUCCESS"); response.put("message", symbol + " " + amount + "주 매도 완료!");
        return response;
    }

    // 🆘 6. 파산 구제 시스템 (Relief)
    @PostMapping("/relief")
    public Map<String, Object> bankruptcyRelief(@RequestBody Map<String, Object> payload, @RequestParam String username) {
        Map<String, Object> response = new HashMap<>();
        Optional<Member> memberOpt = memberRepository.findByUsername(username);

        if (memberOpt.isEmpty()) {
            response.put("status", "FAIL"); response.put("message", "로그인이 필요합니다.");
            return response;
        }

        Member member = memberOpt.get();
        double totalAssets = Double.parseDouble(payload.get("totalAssets").toString());

        if (totalAssets >= 100.0) {
            response.put("status", "FAIL"); response.put("message", "총자산이 $100 이상이므로 파산 구제 대상이 아닙니다.");
            return response;
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        if (member.getLastReliefDate() != null && member.getLastReliefDate().equals(today)) {
            response.put("status", "FAIL"); response.put("message", "파산 구제금은 하루에 한 번만 받을 수 있습니다.");
            return response;
        }

        Wallet wallet = walletRepository.findByMember(member).orElse(null);
        if (wallet != null) {
            wallet.setBalance(wallet.getBalance() + 2000.0);
            walletRepository.save(wallet);

            member.setLastReliefDate(today);
            memberRepository.save(member);

            response.put("status", "SUCCESS");
            response.put("message", "파산 구제금 $2,000이 지급되었습니다! 다시 일어나세요!");
        } else {
            response.put("status", "FAIL"); response.put("message", "지갑을 찾을 수 없습니다.");
        }
        return response;
    }
}