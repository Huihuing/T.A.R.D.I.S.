package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    @Value("${freeimage.api.key:}")
    private String freeimageApiKey;

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
                String authorName = post.getMember() != null ? post.getMember().getUsername() : "ㅇㅇ(" + post.getGuestIp() + ")";
                map.put("author", authorName);
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
                String cAuthorName = c.getMember() != null ? c.getMember().getUsername() : "ㅇㅇ(" + c.getGuestIp() + ")";
                map.put("author", cAuthorName);
                map.put("content", c.getContent());
                map.put("createdAt", c.getCreatedAt().toString());
                return map;
            }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("id", post.getId());
        response.put("title", post.getTitle());
        response.put("content", post.getContent());
        String pAuthorName = post.getMember() != null ? post.getMember().getUsername() : "ㅇㅇ(" + post.getGuestIp() + ")";
        response.put("author", pAuthorName);
        response.put("createdAt", post.getCreatedAt().toString());
        response.put("comments", comments);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(
            @RequestBody Map<String, String> request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Authentication authentication) {
        String title = request.get("title");
        String content = request.get("content");

        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "제목과 내용을 입력해주세요."));
        }

        Optional<Member> memberOpt = getAuthenticatedMember(authentication);
        if (memberOpt.isPresent()) {
            postRepository.save(new Post(memberOpt.get(), title, content));
        } else {
            postRepository.save(new Post(maskIp(getClientIp(httpRequest)), title, content));
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PostMapping("/comments")
    public ResponseEntity<?> createComment(
            @RequestBody Map<String, Object> request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Authentication authentication) {
        Long postId = Long.parseLong(request.get("postId").toString());
        String content = (String) request.get("content");

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "댓글 내용을 입력해주세요."));
        }

        Optional<Post> postOpt = postRepository.findById(postId);
        if (postOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "게시글이 없습니다."));

        Optional<Member> memberOpt = getAuthenticatedMember(authentication);
        if (memberOpt.isPresent()) {
            commentRepository.save(new Comment(postOpt.get(), memberOpt.get(), content));
        } else {
            commentRepository.save(new Comment(postOpt.get(), maskIp(getClientIp(httpRequest)), content));
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<?> updatePost(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        if (!hasAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "게시글이 없습니다."));

        Post post = postOpt.get();
        if (post.getMember() == null || !post.getMember().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("message", "수정 권한이 없습니다."));
        }

        String title = request.get("title");
        String content = request.get("content");
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "제목과 내용을 입력해주세요."));
        }

        post.setTitle(title);
        post.setContent(content);
        postRepository.save(post);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(@PathVariable Long id, Authentication authentication) {
        if (!hasAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) return ResponseEntity.status(404).body(Map.of("message", "게시글이 없습니다."));

        Post post = postOpt.get();
        if (post.getMember() == null || !post.getMember().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("message", "삭제 권한이 없습니다."));
        }

        commentRepository.deleteAll(commentRepository.findByPostOrderByCreatedAtAsc(post));
        postRepository.delete(post);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("image") org.springframework.web.multipart.MultipartFile image) {
        try {
            if (freeimageApiKey == null || freeimageApiKey.isBlank()) {
                return ResponseEntity.status(503).body(Map.of("message", "이미지 업로드 서비스가 설정되지 않았습니다."));
            }
            if (image.isEmpty() || image.getSize() > MAX_IMAGE_SIZE) {
                return ResponseEntity.badRequest().body(Map.of("message", "이미지는 5MB 이하만 업로드할 수 있습니다."));
            }
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("message", "이미지 파일만 업로드할 수 있습니다."));
            }

            String url = "https://freeimage.host/api/1/upload";
            org.springframework.util.LinkedMultiValueMap<String, Object> map = new org.springframework.util.LinkedMultiValueMap<>();
            map.add("key", freeimageApiKey);
            map.add("action", "upload");

            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename() != null ? image.getOriginalFilename() : "image.jpg";
                }
            };
            map.add("source", resource);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
            org.springframework.http.HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity =
                    new org.springframework.http.HttpEntity<>(map, headers);

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null && body.get("status_code") instanceof Number status && status.intValue() == 200) {
                Map<String, Object> imageMap = (Map<String, Object>) body.get("image");
                return ResponseEntity.ok(Map.of("url", imageMap.get("url")));
            }
            return ResponseEntity.status(502).body(Map.of("message", "이미지 업로드에 실패했습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "이미지 업로드 중 서버 오류가 발생했습니다."));
        }
    }

    private Optional<Member> getAuthenticatedMember(Authentication authentication) {
        if (!hasAuthenticatedUser(authentication)) {
            return Optional.empty();
        }
        return memberRepository.findByUsername(authentication.getName());
    }

    private boolean hasAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName());
    }

    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) return "unknown";
        if (ip.contains(",")) ip = ip.split(",")[0].trim();
        if (ip.equals("0:0:0:0:0:0:0:1")) return "127.0.*.*";
        String[] parts = ip.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return "masked";
    }
}
