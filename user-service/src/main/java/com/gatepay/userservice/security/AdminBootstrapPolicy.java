package com.gatepay.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Decides which emails get ADMIN on registration. The list comes from server
// config (BOOTSTRAP_ADMIN_EMAILS), never from the client request.
@Component
public class AdminBootstrapPolicy {

    private final Set<String> adminEmails;

    public AdminBootstrapPolicy(@Value("${gatepay.security.bootstrap-admin-emails:}") String adminEmails) {
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isBootstrapAdmin(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
