package com.gatepay.paymentservice.security;

import com.gatepay.paymentservice.exception.ErrorCode;
import com.gatepay.paymentservice.exception.PaymentServiceException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Who is calling, as established by the gateway from the verified JWT.
// The gateway strips any client-sent copies of these headers, so they can be
// trusted here. A request without them didn't come through the gateway, so
// user-scoped endpoints refuse it (fail closed).
public final class CallerIdentity {

    public static final String USER_ID_HEADER  = "X-User-Id";
    public static final String USERNAME_HEADER = "X-Username";

    private CallerIdentity() { }

    public static Long currentUserId() {
        try {
            return Long.valueOf(header(USER_ID_HEADER));
        } catch (NumberFormatException e) {
            throw denied("Missing or invalid caller identity");
        }
    }

    public static String currentUsername() {
        return header(USERNAME_HEADER);
    }

    // Throws 403 unless the caller is the owner of the given user id
    public static void requireSelf(Long userId) {
        if (userId == null || !userId.equals(currentUserId())) {
            throw denied("You can only access your own account");
        }
    }

    private static String header(String name) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String value = attrs == null ? null : attrs.getRequest().getHeader(name);
        if (value == null || value.isBlank()) {
            throw denied("Missing caller identity");
        }
        return value.trim();
    }

    private static PaymentServiceException denied(String message) {
        return new PaymentServiceException(ErrorCode.ACCESS_DENIED, message);
    }
}
