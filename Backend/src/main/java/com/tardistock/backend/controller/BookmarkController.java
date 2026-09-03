package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Bookmark;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.BookmarkRepository;
import com.tardistock.backend.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookmark")
@CrossOrigin(origins = "*")
public class BookmarkController {

    private final BookmarkRepository bookmarkRepository;
    private final MemberRepository memberRepository;

    public BookmarkController(BookmarkRepository bookmarkRepository, MemberRepository memberRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<?> getBookmarks(@RequestParam String username) {
        if (username == null || username.isEmpty() || "Guest".equalsIgnoreCase(username)) {
            return ResponseEntity.ok(List.of());
        }
        Optional<Member> memberOpt = memberRepository.findByUsername(username);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<String> symbols = bookmarkRepository.findByMember(memberOpt.get())
                .stream()
                .map(Bookmark::getSymbol)
                .collect(Collectors.toList());
        return ResponseEntity.ok(symbols);
    }

    @PostMapping("/toggle")
    @Transactional
    public ResponseEntity<?> toggleBookmark(@RequestBody Map<String, Object> request) {
        try {
            String username = (String) request.get("username");
            String symbol = (String) request.get("symbol");
            double price = request.containsKey("price") && request.get("price") != null ?
                    Double.parseDouble(request.get("price").toString()) : 0.0;

            if (username == null || symbol == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "잘못된 요청입니다."));
            }

            Optional<Member> memberOpt = memberRepository.findByUsername(username);
            if (memberOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "존재하지 않는 회원입니다."));
            }

            Member member = memberOpt.get();
            Optional<Bookmark> bookmarkOpt = bookmarkRepository.findByMemberAndSymbol(member, symbol);

            if (bookmarkOpt.isPresent()) {
                bookmarkRepository.delete(bookmarkOpt.get());
                return ResponseEntity.ok(Map.of("status", "REMOVED", "symbol", symbol));
            } else {
                Bookmark bookmark = new Bookmark(member, symbol, price);
                bookmarkRepository.save(bookmark);
                return ResponseEntity.ok(Map.of("status", "ADDED", "symbol", symbol));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "북마크 처리 중 오류 발생: " + e.getMessage()));
        }
    }
}