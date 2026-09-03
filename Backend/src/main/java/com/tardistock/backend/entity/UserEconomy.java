package com.tardistock.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class UserEconomy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    private LocalDate lastCheckInDate;
    private int attendanceStreak;

    private LocalDateTime lastBankruptcyClaim;

    private LocalDate tradeQuestClaimedDate;
    private LocalDate postQuestClaimedDate;

    public UserEconomy() {}

    public UserEconomy(Member member) {
        this.member = member;
        this.attendanceStreak = 0;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public void setMember(Member member) { this.member = member; }

    public LocalDate getLastCheckInDate() { return lastCheckInDate; }
    public void setLastCheckInDate(LocalDate lastCheckInDate) { this.lastCheckInDate = lastCheckInDate; }

    public int getAttendanceStreak() { return attendanceStreak; }
    public void setAttendanceStreak(int attendanceStreak) { this.attendanceStreak = attendanceStreak; }

    public LocalDateTime getLastBankruptcyClaim() { return lastBankruptcyClaim; }
    public void setLastBankruptcyClaim(LocalDateTime lastBankruptcyClaim) { this.lastBankruptcyClaim = lastBankruptcyClaim; }

    public LocalDate getTradeQuestClaimedDate() { return tradeQuestClaimedDate; }
    public void setTradeQuestClaimedDate(LocalDate tradeQuestClaimedDate) { this.tradeQuestClaimedDate = tradeQuestClaimedDate; }

    public LocalDate getPostQuestClaimedDate() { return postQuestClaimedDate; }
    public void setPostQuestClaimedDate(LocalDate postQuestClaimedDate) { this.postQuestClaimedDate = postQuestClaimedDate; }
}
