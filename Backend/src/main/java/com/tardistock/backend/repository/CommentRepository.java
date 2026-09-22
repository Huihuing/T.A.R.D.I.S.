package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Comment;
import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CommentRepository
        extends JpaRepository<Comment, Long> {

    List<Comment> findByPostOrderByCreatedAtAsc(Post post);

    List<Comment> findByMember(Member member);

    long countByMember(Member member);

    boolean existsByMemberAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Member member,
            LocalDateTime start,
            LocalDateTime end
    );
}
