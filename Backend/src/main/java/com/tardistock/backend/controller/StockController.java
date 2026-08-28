package com.tardistock.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    // 🚨 application-api.yml에 적어두신 핀허브 토큰 변수명을 맞춰주세요! (예: finnhub.api.token)
    @Value("${finnhub.api.key}") 
    private String finnhubToken;

    // 📈 실시간 주가 가져오기
    @GetMapping("/quote")
    public ResponseEntity<?> getStockQuote(@RequestParam(defaultValue = "AAPL") String symbol) {
        try {
            String url = "https://finnhub.io/api/v1/quote?symbol=" + symbol + "&token=" + finnhubToken;
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"error\": \"실시간 주가 로딩 실패\"}");
        }
    }
   // 📊 과거 주가 캔들 데이터 가져오기 (야후 파이낸스로 우회!)
    @GetMapping("/candles")
    public ResponseEntity<?> getStockCandles(
            @RequestParam(defaultValue = "AAPL") String symbol,
            @RequestParam(defaultValue = "D") String resolution) {
        try {
            // 사용자가 누른 버튼(일/주/월)에 맞춰 야후 파이낸스 규격(1d, 1wk, 1mo)으로 변환
            String interval = "1d";
            String range = "6mo"; // 일봉은 최근 6개월치
            
            if ("W".equals(resolution)) { 
                interval = "1wk"; 
                range = "2y";     // 주봉은 최근 2년치
            } else if ("M".equals(resolution)) { 
                interval = "1mo"; 
                range = "5y";     // 월봉은 최근 5년치
            }

            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol 
                       + "?interval=" + interval + "&range=" + range;
            
            RestTemplate restTemplate = new RestTemplate();
            
            // 🚨 야후 서버가 차단하지 못하도록 브라우저인 척 위장(User-Agent)합니다.
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            System.out.println("====== 야후 차트 API 에러 ======");
            System.out.println(e.getMessage());
            return ResponseEntity.status(500).body("{\"error\": \"차트 데이터 로딩 실패\"}");
        }
    }
// 🌐 핀허브 지원 미국 전체 주식 심볼 리스트 가져오기
    @GetMapping("/symbols")
    public ResponseEntity<?> getAllSymbols() {
        try {
            String url = "https://finnhub.io/api/v1/stock/symbol?exchange=US&token=" + finnhubToken;
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"error\": \"전체 종목 로딩 실패\"}");
        }
    }
}