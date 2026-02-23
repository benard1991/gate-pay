package com.gatepay.userservice.service;

import com.gatepay.userservice.dto.ChangePasswordRequest;
import com.gatepay.userservice.dto.UpdateUserDto;
import com.gatepay.userservice.dto.UserDto;
import com.gatepay.userservice.enums.RoleEnum;
import com.gatepay.userservice.exception.AccountDisabledException;
import com.gatepay.userservice.exception.InvalidPasswordException;
import com.gatepay.userservice.exception.UserAlreadyExistsException;
import com.gatepay.userservice.exception.UserNotFoundException;
import com.gatepay.userservice.mapper.UpdateUserMapper;
import com.gatepay.userservice.validation.AccountStatusValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.gatepay.userservice.mapper.UserMapper;
import com.gatepay.userservice.model.Role;
import com.gatepay.userservice.model.User;
import com.gatepay.userservice.repository.RoleRepository;
import com.gatepay.userservice.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.gatepay.userservice.mapper.UserMapper.toDto;

@Service
@Slf4j
@AllArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    private PasswordEncoder passwordEncoder;


    @Override
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findWithRolesByEmail(email)
                .orElseThrow(()->new UserNotFoundException());

        return toDto(user);
    }

    @Override
    @Transactional
    public UserDto createUser(UserDto userDto) {
        if (userDto == null) {
            throw new IllegalArgumentException("User data must not be null");
        }

        if (userRepository.findWithRolesByEmail(userDto.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("User with email " + userDto.getEmail() + " already exists");
        }

        if (userRepository.existsByPhoneNumber(userDto.getPhoneNumber())) {
            throw new UserAlreadyExistsException("User with phone number " + userDto.getPhoneNumber() + " already exists");
        }

        User user = new User();
        user.setFirstName(userDto.getFirstName());
        user.setLastName(userDto.getLastName());
        user.setEmail(userDto.getEmail());
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setPhoneNumber(userDto.getPhoneNumber());
        user.setAddress(userDto.getAddress());
        user.setCountry(userDto.getCountry());
        user.setStatus(userDto.getStatus());
        user.setKycVerified(userDto.isKycVerified());

        if (userDto.getRoles() != null && !userDto.getRoles().isEmpty()) {
            Set<Role> roles = userDto.getRoles()
                    .stream()
                    .map(roleName -> {
                        RoleEnum roleEnum = RoleEnum.valueOf(roleName);
                        return roleRepository.findByName(roleEnum)
                                .orElseGet(() -> {
                                    Role newRole = Role.builder()
                                            .name(roleEnum)
                                            .build();
                                    return roleRepository.save(newRole);
                                });
                    })
                    .collect(Collectors.toSet());

            user.setRoles(roles);
        }

        User savedUser = userRepository.save(user);

        return UserMapper.toDto(savedUser);
    }


    @Override
    @Cacheable(value = "userProfiles", key = "#a0", unless="#result == null")
    public UserDto getUserProfile(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        AccountStatusValidator.validateProfileAccess(user);

        return UserMapper.toDto(user);
    }

    @CacheEvict(value = "userProfiles", key = "#userId")
    public void evictUserProfileCache(Long userId) {
        log.info("Evicting cache for user id: {}", userId);
    }


    @Override
    @Transactional
    public boolean updatePassword(String email, String newPassword) {
        User user = userRepository.findWithRolesByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        AccountStatusValidator.validatePasswordReset(user);

        String encodedPassword = passwordEncoder.encode(newPassword);

        int updatedRows = userRepository.updatePassword(email, encodedPassword);
        if (updatedRows <= 0) {
            log.error("Failed to update password for user: {}", email);
            return false;
        }

        log.info("Password successfully updated for user: {}", email);
        return true;
    }

    @Override
    @Transactional
    public boolean changePassword( ChangePasswordRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + request.getEmail()));

        AccountStatusValidator.validateProfileAccess(user);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Old password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidPasswordException("New password and confirm password do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed successfully for email={}", request.getEmail());
        return true;
    }

    @Override
    @Transactional
    @CacheEvict(value = "userProfiles", key = "#a0")
    public UserDto updateUserProfile(Long userId,  UpdateUserDto userDto) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        AccountStatusValidator.validateProfileAccess(user);


        Optional.ofNullable(userDto.getFirstName()).ifPresent(user::setFirstName);
        Optional.ofNullable(userDto.getLastName()).ifPresent(user::setLastName);
        Optional.ofNullable(userDto.getEmail()).ifPresent(user::setEmail);

        if (userDto.getPhoneNumber() != null && !userDto.getPhoneNumber().equals(user.getPhoneNumber())) {
            if (userRepository.existsByPhoneNumber(userDto.getPhoneNumber())) {
                throw new UserAlreadyExistsException("Phone number already in use");
            }
            user.setPhoneNumber(userDto.getPhoneNumber());
        }

        Optional.ofNullable(userDto.getAddress()).ifPresent(user::setAddress);
        Optional.ofNullable(userDto.getCountry()).ifPresent(user::setCountry);
        Optional.ofNullable(userDto.getStatus()).ifPresent(user::setStatus);
        Optional.ofNullable(userDto.getKycVerified()).ifPresent(user::setKycVerified);

        User updatedUser = userRepository.save(user);

        log.info("User profile updated successfully for userId={}", userId);

        return UpdateUserMapper.toDto(updatedUser);
    }


}
