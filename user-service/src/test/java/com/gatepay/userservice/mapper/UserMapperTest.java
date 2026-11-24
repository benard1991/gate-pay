package com.gatepay.userservice.mapper;

import com.gatepay.userservice.dto.UserDto;
import com.gatepay.userservice.model.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private User userWithHash() {
        User user = new User();
        user.setId(1L);
        user.setEmail("john.doe@example.com");
        user.setPassword("$2a$10$hashhashhash");
        user.setRoles(Set.of());
        return user;
    }

    @Test
    void toDto_neverIncludesPasswordHash() {
        UserDto dto = UserMapper.toDto(userWithHash());

        assertThat(dto.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(dto.getPassword()).isNull();
    }

    @Test
    void toCredentialsDto_includesHashForInternalLoginCheck() {
        assertThat(UserMapper.toCredentialsDto(userWithHash()).getPassword())
                .isEqualTo("$2a$10$hashhashhash");
    }
}
