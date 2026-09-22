package com.tardistock.backend.controller;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Post;
import com.tardistock.backend.repository.CommentRepository;
import com.tardistock.backend.repository.MemberRepository;
import com.tardistock.backend.repository.PostRepository;
import com.tardistock.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES =
            java.util.Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/gif",
                    "image/webp"
            );
    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_POST_LENGTH = 20_000;
    private static final int MAX_COMMENT_LENGTH = 3_000;
    private static final int MIN_GUEST_NICKNAME_LENGTH = 2;
    private static final int MAX_GUEST_NICKNAME_LENGTH = 20;
    private static final int MIN_GUEST_PASSWORD_LENGTH = 4;
    private static final int MAX_GUEST_PASSWORD_LENGTH = 64;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Value("${freeimage.api.key:}")
    private String freeimageApiKey;

    public BoardController(
            PostRepository postRepository,
            CommentRepository commentRepository,
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @GetMapping("/posts")
    public ResponseEntity<?> getPosts(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String query = q == null ? "" : q.trim();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 30));

        Page<Post> result = postRepository.search(
                query,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );

        List<Map<String, Object>> posts = result.getContent().stream()
                .map(post -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", post.getId());
                    map.put("title", post.getTitle());
                    map.put("author", authorName(
                            post.getMember(),
                            post.getGuestNickname(),
                            post.getGuestIp()));
                    map.put("isGuest", post.getMember() == null);
                    map.put("createdAt", post.getCreatedAt().toString());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("items", posts);
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalPages", result.getTotalPages());
        response.put("totalElements", result.getTotalElements());
        response.put("query", query);

        return ResponseEntity.ok(response);
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
                                    comment.getMember(),
                                    comment.getGuestNickname(),
                                    comment.getGuestIp()));
                            map.put("isGuest", comment.getMember() == null);
                            map.put("content", comment.getContent());
                            map.put("createdAt", comment.getCreatedAt().toString());
                            return map;
                        })
                        .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("id", post.getId());
        response.put("title", post.getTitle());
        response.put("content", post.getContent());
        response.put("author", authorName(
                post.getMember(), post.getGuestNickname(), post.getGuestIp()));
        response.put("isGuest", post.getMember() == null);
        response.put("guestNickname", post.getGuestNickname());
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
            String guestNickname = normalize(request.get("guestNickname"));
            String guestPassword = request.get("guestPassword");
            ResponseEntity<?> guestValidation =
                    validateGuestCredentials(guestNickname, guestPassword);
            if (guestValidation != null) return guestValidation;

            postRepository.save(new Post(
                    maskIp(getClientIp(httpRequest)),
                    guestNickname,
                    passwordEncoder.encode(guestPassword),
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
        String commentAuthor;

        if (memberOpt.isPresent()) {
            commentAuthor = memberOpt.get().getUsername();
            commentRepository.save(
                    new Comment(postOpt.get(), memberOpt.get(), content));
        } else {
            String guestNickname = normalizeObject(request.get("guestNickname"));
            String guestPassword = rawObject(request.get("guestPassword"));
            ResponseEntity<?> guestValidation =
                    validateGuestCredentials(guestNickname, guestPassword);
            if (guestValidation != null) return guestValidation;

            commentAuthor = guestNickname;
            commentRepository.save(new Comment(
                    postOpt.get(),
                    maskIp(getClientIp(httpRequest)),
                    guestNickname,
                    passwordEncoder.encode(guestPassword),
                    content
            ));
        }

        Member postOwner = postOpt.get().getMember();
        if (postOwner != null
                && !postOwner.getUsername().equals(commentAuthor)) {
            notificationService.create(
                    postOwner,
                    "COMMENT",
                    commentAuthor + "님이 회원님의 게시글에 댓글을 남겼습니다."
            );
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<?> updatePost(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "게시글이 없습니다."));
        }

        Post post = postOpt.get();
        ResponseEntity<?> authorization =
                authorizePostMutation(post, request.get("guestPassword"), authentication);
        if (authorization != null) return authorization;

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
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication) {

        Optional<Post> postOpt = postRepository.findById(id);
        if (postOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "게시글이 없습니다."));
        }

        Post post = postOpt.get();
        String guestPassword = request == null ? null : request.get("guestPassword");
        ResponseEntity<?> authorization =
                authorizePostMutation(post, guestPassword, authentication);
        if (authorization != null) return authorization;

        commentRepository.deleteAll(
                commentRepository.findByPostOrderByCreatedAtAsc(post));
        postRepository.delete(post);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @PutMapping("/comments/{id}")
    public ResponseEntity<?> updateComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {

        Optional<Comment> commentOpt = commentRepository.findById(id);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "댓글이 없습니다."));
        }

        Comment comment = commentOpt.get();
        ResponseEntity<?> authorization =
                authorizeCommentMutation(
                        comment,
                        request.get("guestPassword"),
                        authentication
                );
        if (authorization != null) return authorization;

        String content = normalize(request.get("content"));
        if (content == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "댓글 내용을 입력해주세요."));
        }
        if (content.length() > MAX_COMMENT_LENGTH) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "댓글은 " + MAX_COMMENT_LENGTH
                                    + "자 이하로 작성해주세요."
                    ));
        }

        comment.setContent(content);
        commentRepository.save(comment);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<?> deleteComment(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication) {

        Optional<Comment> commentOpt = commentRepository.findById(id);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(
                    Map.of("message", "댓글이 없습니다."));
        }

        Comment comment = commentOpt.get();
        String guestPassword = request == null ? null : request.get("guestPassword");
        ResponseEntity<?> authorization =
                authorizeCommentMutation(comment, guestPassword, authentication);
        if (authorization != null) return authorization;

        commentRepository.delete(comment);
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
            if (contentType == null
                    || !ALLOWED_IMAGE_TYPES.contains(
                            contentType.toLowerCase(
                                    java.util.Locale.ROOT))) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "message",
                                "JPEG, PNG, GIF, WebP 이미지만 업로드할 수 있습니다."
                        ));
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

            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(
                            url,
                            org.springframework.http.HttpMethod.POST,
                            requestEntity,
                            new org.springframework.core
                                    .ParameterizedTypeReference<
                                            Map<String, Object>>() {}
                    );

            Map<String, Object> body = response.getBody();
            if (body != null
                    && body.get("status_code") instanceof Number status
                    && status.intValue() == 200
                    && body.get("image") instanceof Map<?, ?> imageMap
                    && imageMap.get("url") != null) {
                return ResponseEntity.ok(
                        Map.of("url", imageMap.get("url")));
            }
            return ResponseEntity.status(502).body(
                    Map.of("message", "이미지 업로드에 실패했습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("message", "이미지 업로드 중 서버 오류가 발생했습니다."));
        }
    }

    private ResponseEntity<?> authorizePostMutation(
            Post post,
            String guestPassword,
            Authentication authentication) {
        if (post.getMember() != null) {
            if (!hasAuthenticatedUser(authentication)) {
                return ResponseEntity.status(401).body(
                        Map.of("message", "로그인이 필요합니다."));
            }
            if (!post.getMember().getUsername().equals(authentication.getName())) {
                return ResponseEntity.status(403).body(
                        Map.of("message", "수정/삭제 권한이 없습니다."));
            }
            return null;
        }

        if (post.getGuestPasswordHash() == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "message",
                    "비밀번호 기능 도입 이전의 게스트 글은 수정/삭제할 수 없습니다."
            ));
        }
        if (guestPassword == null
                || !passwordEncoder.matches(
                        guestPassword, post.getGuestPasswordHash())) {
            return ResponseEntity.status(403).body(
                    Map.of("message", "게스트 작성 비밀번호가 일치하지 않습니다."));
        }
        return null;
    }

    private ResponseEntity<?> authorizeCommentMutation(
            Comment comment,
            String guestPassword,
            Authentication authentication) {
        if (comment.getMember() != null) {
            if (!hasAuthenticatedUser(authentication)
                    || !comment.getMember().getUsername()
                    .equals(authentication.getName())) {
                return ResponseEntity.status(403).body(
                        Map.of("message", "댓글 수정/삭제 권한이 없습니다."));
            }
            return null;
        }

        if (comment.getGuestPasswordHash() == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "message",
                    "비밀번호 기능 도입 이전의 게스트 댓글은 수정/삭제할 수 없습니다."
            ));
        }
        if (guestPassword == null
                || !passwordEncoder.matches(
                        guestPassword, comment.getGuestPasswordHash())) {
            return ResponseEntity.status(403).body(
                    Map.of("message", "게스트 작성 비밀번호가 일치하지 않습니다."));
        }
        return null;
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

    private ResponseEntity<?> validateGuestCredentials(
            String nickname,
            String password) {
        if (nickname == null
                || nickname.length() < MIN_GUEST_NICKNAME_LENGTH
                || nickname.length() > MAX_GUEST_NICKNAME_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "비회원 닉네임은 2~20자로 입력해주세요."
            ));
        }
        if (password == null
                || password.length() < MIN_GUEST_PASSWORD_LENGTH
                || password.length() > MAX_GUEST_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "비회원 작성 비밀번호는 4~64자로 입력해주세요."
            ));
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

    private String authorName(
            Member member,
            String guestNickname,
            String guestIp) {
        if (member != null) return member.getUsername();
        if (guestNickname != null && !guestNickname.isBlank()) {
            return guestNickname + "(" + guestIp + ")";
        }
        return "ㅇㅇ(" + guestIp + ")";
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

    private String normalizeObject(Object value) {
        return value == null ? null : normalize(value.toString());
    }

    private String rawObject(Object value) {
        return value == null ? null : value.toString();
    }
}
