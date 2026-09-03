package com.tardistock.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "http://localhost:5173")
public class NewsController {

    @Value("${naver.api.client-id}")
    private String naverClientId;

    @Value("${naver.api.client-secret}")
    private String naverClientSecret;

    // 🚨 여기에 핀허브 토큰을 가져오는 코드를 추가해 줍니다!
    // 괄호 안의 이름은 application-api.yml에 적어두신 핀허브 키의 경로와 똑같이 맞춰주세요! 
    @Value("${finnhub.api.key}") 
    private String finnhubToken;

// 🇺🇸 1. 해외 뉴스 (Finnhub - 특정 종목 뉴스)
    @GetMapping("/global")
    public ResponseEntity<?> getGlobalNews(@RequestParam(defaultValue = "AAPL") String symbol) {
        try {
            // 오늘 날짜와 3일 전 날짜를 "YYYY-MM-DD" 포맷으로 자동 계산
            LocalDate today = LocalDate.now();
            LocalDate threeDaysAgo = today.minusDays(3);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            String toDate = today.format(formatter);
            String fromDate = threeDaysAgo.format(formatter);

            // 종목 기호(symbol)와 날짜를 넣어 고퀄리티 종목 뉴스를 호출!
            String url = "https://finnhub.io/api/v1/company-news?symbol=" + symbol + "&from=" + fromDate + "&to=" + toDate + "&token=" + finnhubToken;
            
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"error\": \"해외 뉴스 로딩 실패: " + e.getMessage() + "\"}");
        }
    }

// 🇰🇷 2. 국내 뉴스 (NAVER API HUB - NCP 최종 완벽 버전)
    @GetMapping("/korea")
    public ResponseEntity<?> getKoreanNews(@RequestParam(defaultValue = "증시 시황 특징주 -연예 -정치") String query) {
        try {
            // 🚨 1. 골칫덩어리 URLEncoder 완전히 삭제! (이중 인코딩 방지)
            
            // 🚨 2. 주소는 무조건 NCP API HUB 주소! + {query} 빈칸 뚫어두기
            // 🚨 맨 끝부분 sort=sim 을 sort=date 로 변경!
            // ✅ 수정 후 (display=4 삭제)
            String url = "https://naverapihub.apigw.ntruss.com/search/v1/news?query={query}&display=100&sort=date";
            
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            
            // 🚨 3. 헤더도 무조건 NCP 전용 헤더! (회원님의 10글자/40글자 키 사용)
            headers.set("X-NCP-APIGW-API-KEY-ID", naverClientId);
            headers.set("X-NCP-APIGW-API-KEY", naverClientSecret);
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // 🚨 4. exchange 마지막 파라미터로 query를 던져서 안전하게 변환!
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class, query);
            
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("{\"error\": \"NCP 뉴스 로딩 실패: " + e.getMessage() + "\"}");
        }
    }
}