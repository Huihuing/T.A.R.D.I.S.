package com.tardistock.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminAccessService {

    private final Set<String> adminUsernames;

    public AdminAccessService(
            @Value("${admin.usernames:}") String configuredUsernames) {
        this.adminUsernames = Arrays.stream(
                        configuredUsernames == null
                                ? new String[0]
                                : configuredUsernames.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && adminUsernames.contains(authentication.getName());
    }
}
