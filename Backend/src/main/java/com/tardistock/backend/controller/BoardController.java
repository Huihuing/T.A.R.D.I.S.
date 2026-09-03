package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    public BoardController(PostRepository postRepository, CommentRepository commentRepository, MemberRepository memberRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/posts")
    public ResponseEntity<?> getPosts() {
        List<Map<String, Object>> posts = postRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(post -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", post.getId());
                map.put("title", post.getTitle());
                map.put("author", post.getMember().getUsername());
                map.put("createdAt", post.getCreatedAt().toString());
                return map;
            }).collect(Collectors.toList());
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> getPostDetail(@PathVariable Long id) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "게시글이 없습니다."));
        
        Post post = postOpt.get();
        List<Map<String, Object>> comments = commentRepository.findByPostOrderByCreatedAtAsc(post).stream()
            .map(c -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", c.getId());
                map.put("author", c.getMember().getUsername());
                map.put("content", c.getContent());
                map.put("createdAt", c.getCreatedAt().toString());
                return map;
            }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("id", post.getId());
        response.put("title", post.getTitle());
        response.put("content", post.getContent());
        response.put("author", post.getMember().getUsername());
        response.put("createdAt", post.getCreatedAt().toString());
        response.put("comments", comments);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        Optional<Member> memberOpt = memberRepository.findByUsername(username);
        if (memberOpt.isEmpty()) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Post post = new Post(memberOpt.get(), request.get("title"), request.get("content"));
        postRepository.save(post);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PostMapping("/comments")
    public ResponseEntity<?> createComment(@RequestBody Map<String, Object> request) {
        String username = (String) request.get("username");
        // 💡 오타 수정: "postId" 뒤에서 괄호를 닫아줍니다.
        Long postId = Long.parseLong(request.get("postId").toString());
        String content = (String) request.get("content");

        Optional<Member> memberOpt = memberRepository.findByUsername(username);
        Optional<Post> postOpt = postRepository.findById(postId);
        
        if (memberOpt.isEmpty() || postOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "오류가 발생했습니다."));

        Comment comment = new Comment(postOpt.get(), memberOpt.get(), content);
        commentRepository.save(comment);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }
}