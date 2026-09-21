package com.tardistock.backend.repository;

import com.tardistock.backend.entity.Member;
import com.tardistock.backend.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findAllByOrderByCreatedAtDesc();

    List<Post> findByMember(Member member);

    @Query("""
            select p
            from Post p
            left join p.member m
            where :q = ''
               or lower(p.title) like lower(concat('%', :q, '%'))
               or lower(p.content) like lower(concat('%', :q, '%'))
               or lower(coalesce(p.guestNickname, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(m.username, '')) like lower(concat('%', :q, '%'))
            """)
    Page<Post> search(
            @Param("q") String query,
            Pageable pageable
    );
}
