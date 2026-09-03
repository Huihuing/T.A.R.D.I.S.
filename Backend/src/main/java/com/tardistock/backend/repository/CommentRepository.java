package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
} 