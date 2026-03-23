package com.gatepay.walletservice.specification;


import com.gatepay.walletservice.enums.TransactionSource;
import com.gatepay.walletservice.enums.TransactionStatus;
import com.gatepay.walletservice.enums.TransactionType;
import com.gatepay.walletservice.model.WalletTransaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TransactionSpecification {

    private TransactionSpecification() {}

    public static Specification<WalletTransaction> hasWalletId(Long walletId) {
        return (root, query, cb) ->
                walletId == null ? null : cb.equal(root.get("wallet").get("id"), walletId);
    }

    public static Specification<WalletTransaction> hasUserId(Long userId) {
        return (root, query, cb) ->
                userId == null ? null : cb.equal(root.get("wallet").get("userId"), userId);
    }

    public static Specification<WalletTransaction> hasType(TransactionType type) {
        return (root, query, cb) ->
                type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<WalletTransaction> hasSource(TransactionSource source) {
        return (root, query, cb) ->
                source == null ? null : cb.equal(root.get("source"), source);
    }

    public static Specification<WalletTransaction> hasStatus(TransactionStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<WalletTransaction> createdAfter(LocalDateTime from) {
        return (root, query, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<WalletTransaction> createdBefore(LocalDateTime to) {
        return (root, query, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<WalletTransaction> buildFilter(
            Long walletId, Long userId, TransactionType type,
            TransactionSource source, TransactionStatus status,
            LocalDateTime from, LocalDateTime to) {

        return Specification.where(hasWalletId(walletId))
                .and(hasUserId(userId))
                .and(hasType(type))
                .and(hasSource(source))
                .and(hasStatus(status))
                .and(createdAfter(from))
                .and(createdBefore(to));
    }
}
