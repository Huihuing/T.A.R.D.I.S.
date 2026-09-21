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
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_POST_LENGTH = 20_000;
    private static final int MAX_COMMENT_LENGTH = 3_000;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    @Value("${freeimage.api.key:}")
    private String freeimageApiKey;

    public BoardController(
            PostRepository postRepository,
            CommentRepository commentRepository,
            MemberRepository memberRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/posts")
    public ResponseEntity<?> getPosts() {
        List<Map<String, Object>> posts =
                postRepository.findAllByOrderByCreatedAtDesc().stream()
                        .map(post -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", post.getId());
                            map.put("title", post.getTitle());
                            map.put("author", authorName(
                                    post.getMember(), post.getGuestIp()));
                            map.put("createdAt", post.getCreatedAt().toString());
                            return map;
                        })
                        .collect(Collectors.toList());
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> getPostDetail(@PathVariable Long id) {
        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "게시글이 없습니다."));
        }

        Post post = postOpt.get();
        List<Map<String, Object>> comments =
                commentRepository.findByPostOrderByCreatedAtAsc(post).stream()
                        .map(comment -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", comment.getId());
                            map.put("author", authorName(
                                    comment.getMember(), comment.getGuestIp()));
                            map.put("content", comment.getContent());
                            map.put("createdAt", comment.getCreatedAt().toString());
                            return map;
                        })
                        .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("id", post.getId());
        response.put("title", post.getTitle());
        response.put("content", post.getContent());
        response.put("author", authorName(post.getMember(), post.getGuestIp()));
        response.put("createdAt", post.getCreatedAt().toString());
        response.put("comments", comments);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/posts")
    public ResponseEntity<?> createPost(
            @RequestBody Map<String, String> request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Authentication authentication) {

        String title = normalize(request.get("title"));
        String content = normalize(request.get("content"));
        ResponseEntity<?> validation = validatePost(title, content);
        if (validation != null) return validation;

        Optional<Member> memberOpt = getAuthenticatedMember(authentication);
        if (memberOpt.isPresent()) {
            postRepository.save(new Post(memberOpt.get(), title, content));
        } else {
            postRepository.save(new Post(
                    maskIp(getClientIp(httpRequest)),
                    title,
                    content
            ));
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PostMapping("/comments")
    public ResponseEntity<?> createComment(
            @RequestBody Map<String, Object> request,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            Authentication authentication) {

        Long postId = parsePostId(request.get("postId"));
        String content = normalize(
                request.get("content") == null
                        ? null
                        : request.get("content").toString()
        );

        if (postId == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "잘못된 게시글 번호입니다."));
        }
        if (content == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "댓글 내용을 입력해주세요."));
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "댓글은 " + MAX_COMMENT_LENGTH
                            + "자 이하로 작성해주세요."));
        }

        Optional<Post> postOpt = postRepository.findById(postId);
        if (postOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "게시글이 없습니다."));
        }

        Optional<Member> memberOpt = getAuthenticatedMember(authentication);
        if (memberOpt.isPresent()) {
            commentRepository.save(
                    new Comment(postOpt.get(), memberOpt.get(), content));
        } else {
            commentRepository.save(
                    new Comment(
                            postOpt.get(),
                            maskIp(getClientIp(httpRequest)),
                            content
                    )
            );
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<?> updatePost(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        if (!hasAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).body(
                    Map.of("message", "로그인이 필요합니다."));
        }

        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "게시글이 없습니다."));
        }

        Post post = postOpt.get();
        if (post.getMember() == null
                || !post.getMember().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).body(
                    Map.of("message", "수정 권한이 없습니다."));
        }

        String title = normalize(request.get("title"));
        String content = normalize(request.get("content"));
        ResponseEntity<?> validation = validatePost(title, content);
        if (validation != null) return validation;

        post.setTitle(title);
        post.setContent(content);
        postRepository.save(post);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> deletePost(
            @PathVariable Long id,
            Authentication authentication) {

        if (!hasAuthenticatedUser(authentication)) {
            return ResponseEntity.status(401).body(
                    Map.of("message", "로그인이 필요합니다."));
        }

        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "게시글이 없습니다."));
        }

        Post post = postOpt.get();
        if (post.getMember() == null
                || !post.getMember().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).body(
                    Map.of("message", "삭제 권한이 없습니다."));
        }

        commentRepository.deleteAll(
                commentRepository.findByPostOrderByCreatedAtAsc(post));
        postRepository.delete(post);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(
            @RequestParam("image") org.springframework.web.multipart.MultipartFile image) {
        try {
            if (freeimageApiKey == null || freeimageApiKey.isBlank()) {
                return ResponseEntity.status(503).body(
                        Map.of("message", "이미지 업로드 서비스가 설정되지 않았습니다."));
            }
            if (image.isEmpty() || image.getSize() > MAX_IMAGE_SIZE) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "이미지는 5MB 이하만 업로드할 수 있습니다."));
            }

            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "이미지 파일만 업로드할 수 있습니다."));
            }

            String url = "https://freeimage.host/api/1/upload";
            org.springframework.util.LinkedMultiValueMap<String, Object> map =
                    new org.springframework.util.LinkedMultiValueMap<>();
            map.add("key", freeimageApiKey);
            map.add("action", "upload");

            org.springframework.core.io.ByteArrayResource resource =
                    new org.springframework.core.io.ByteArrayResource(
                            image.getBytes()) {
                        @Override
                        public String getFilename() {
                            return image.getOriginalFilename() != null
                                    ? image.getOriginalFilename()
                                    : "image.jpg";
                        }
                    };
            map.add("source", resource);

            org.springframework.http.HttpHeaders headers =
                    new org.springframework.http.HttpHeaders();
            headers.setContentType(
                    org.springframework.http.MediaType.MULTIPART_FORM_DATA);

            org.springframework.http.HttpEntity<
                    org.springframework.util.LinkedMultiValueMap<String, Object>>
                    requestEntity =
                    new org.springframework.http.HttpEntity<>(map, headers);

            org.springframework.web.client.RestTemplate restTemplate =
                    new org.springframework.web.client.RestTemplate();

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, requestEntity, Map.class);

            Map<String, Object> body = response.getBody();
            if (body != null
                    && body.get("status_code") instanceof Number status
                    && status.intValue() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> imageMap =
                        (Map<String, Object>) body.get("image");
                if (imageMap != null && imageMap.get("url") != null) {
                    return ResponseEntity.ok(
                            Map.of("url", imageMap.get("url")));
                }
            }
            return ResponseEntity.status(502).body(
                    Map.of("message", "이미지 업로드에 실패했습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("message", "이미지 업로드 중 서버 오류가 발생했습니다."));
        }
    }

    private ResponseEntity<?> validatePost(String title, String content) {
        if (title == null || content == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "제목과 내용을 입력해주세요."));
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "제목은 " + MAX_TITLE_LENGTH
                            + "자 이하로 작성해주세요."));
        }
        if (content.length() > MAX_POST_LENGTH) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "본문은 " + MAX_POST_LENGTH
                            + "자 이하로 작성해주세요."));
        }
        return null;
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

    private String authorName(Member member, String guestIp) {
        return member != null
                ? member.getUsername()
                : "ㅇㅇ(" + guestIp + ")";
    }

    private String getClientIp(
            jakarta.servlet.http.HttpServletRequest request) {
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

    private Long parsePostId(Object value) {
        if (value == null) return null;
        try {
            long id = Long.parseLong(value.toString());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
