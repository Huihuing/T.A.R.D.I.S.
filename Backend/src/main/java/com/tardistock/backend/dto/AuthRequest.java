package com.tardistock.backend.dto;

public class AuthRequest {
    private String username;
    private String password;
    private String nickname;

    // 데이터를 꺼내고 넣기 위한 Getter & Setter
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}