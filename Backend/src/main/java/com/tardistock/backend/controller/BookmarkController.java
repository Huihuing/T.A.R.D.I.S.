package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Bookmark;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.repository.BookmarkRepository;
import com.tardistock.backend.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookmark")
public class BookmarkController {

    private final BookmarkRepository bookmarkRepository;
    private final MemberRepository memberRepository;

    public BookmarkController(BookmarkRepository bookmarkRepository, MemberRepository memberRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<?> getBookmarks(Authentication authentication) {
        Optional<Member> memberOpt = authenticatedMember(authentication);
        if (memberOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        List<String> symbols = bookmarkRepository.findByMember(memberOpt.get())
                .stream()
                .map(Bookmark::getSymbol)
                .collect(Collectors.toList());
        return ResponseEntity.ok(symbols);
    }

    @PostMapping("/toggle")
    @Transactional
    public ResponseEntity<?> toggleBookmark(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            Optional<Member> memberOpt = authenticatedMember(authentication);
            if (memberOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
            }

            String symbol = request.get("symbol") == null ? null : request.get("symbol").toString().trim().toUpperCase();
            double price = request.containsKey("price") && request.get("price") != null
                    ? Double.parseDouble(request.get("price").toString())
                    : 0.0;

            if (symbol == null || !symbol.matches("[A-Z0-9.\\-]{1,12}")) {
                return ResponseEntity.badRequest().body(Map.of("message", "잘못된 종목 코드입니다."));
            }

            Member member = memberOpt.get();
            Optional<Bookmark> bookmarkOpt = bookmarkRepository.findByMemberAndSymbol(member, symbol);

            if (bookmarkOpt.isPresent()) {
                bookmarkRepository.delete(bookmarkOpt.get());
                return ResponseEntity.ok(Map.of("status", "REMOVED", "symbol", symbol));
            }

            bookmarkRepository.save(new Bookmark(member, symbol, price));
            return ResponseEntity.ok(Map.of("status", "ADDED", "symbol", symbol));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "북마크 처리 중 오류가 발생했습니다."));
        }
    }

    private Optional<Member> authenticatedMember(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        return memberRepository.findByUsername(authentication.getName());
    }
}
