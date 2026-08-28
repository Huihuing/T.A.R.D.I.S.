package com.tardistock.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmark")
public class BookmarkController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<?> getBookmarks(@RequestParam String username) {
        try {
            String sql = "SELECT Bookmark_stockCode FROM Bookmark WHERE Member_id = ?";
            List<String> bookmarks = jdbcTemplate.queryForList(sql, String.class, username);
            return ResponseEntity.ok(bookmarks);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "북마크 조회 실패"));
        }
    }

    @PostMapping("/toggle")
    @Transactional
    public ResponseEntity<?> toggleBookmark(@RequestBody Map<String, Object> request) {
        try {
            String username = (String) request.get("username");
            String symbol = (String) request.get("symbol");
            double currentPrice = request.containsKey("price") ? Double.parseDouble(request.get("price").toString()) : 0.0;

            String checkSql = "SELECT COUNT(*) FROM Bookmark WHERE Member_id = ? AND Bookmark_stockCode = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, username, symbol);

            if (count != null && count > 0) {
                jdbcTemplate.update("DELETE FROM Bookmark WHERE Member_id = ? AND Bookmark_stockCode = ?", username, symbol);
                return ResponseEntity.ok(Map.of("status", "REMOVED", "symbol", symbol));
            } else {
                String insertSql = "INSERT INTO Bookmark (Member_id, Bookmark_stockCode, Bookmark_stockName, Bookmark_Price, Bookmark_tradingVolume, Bookmark_fluctuation) VALUES (?, ?, ?, ?, 0, '0%')";
                jdbcTemplate.update(insertSql, username, symbol, symbol, currentPrice);
                return ResponseEntity.ok(Map.of("status", "ADDED", "symbol", symbol));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "북마크 토글 실패"));
        }
    }
}