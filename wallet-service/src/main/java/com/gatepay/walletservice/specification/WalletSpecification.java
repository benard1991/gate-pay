package com.gatepay.walletservice.specification;

import com.gatepay.walletservice.enums.WalletStatus;
import com.gatepay.walletservice.model.Wallet;
import org.springframework.data.jpa.domain.Specification;

public class WalletSpecification {

    private WalletSpecification() {}

    public static Specification<Wallet> hasUserId(Long userId) {
        return (root, query, cb) ->
                userId == null ? null : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Wallet> hasStatus(WalletStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Wallet> hasCurrency(String currency) {
        return (root, query, cb) ->
                (currency == null || currency.isBlank()) ? null
                        : cb.equal(root.get("currency"), currency.toUpperCase());
    }

    public static Specification<Wallet> buildFilter(Long userId, WalletStatus status, String currency) {
        return Specification.where(hasUserId(userId))
                .and(hasStatus(status))
                .and(hasCurrency(currency));
    }
}