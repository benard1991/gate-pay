package com.gatepay.userservice.service;

import com.gatepay.userservice.dto.ChangePasswordRequest;
import com.gatepay.userservice.dto.UpdateUserDto;
import com.gatepay.userservice.dto.UserDto;

public interface UserService {
    UserDto getUserByEmail(String email);

    public UserDto createUser(UserDto userDto);

    UserDto getUserProfile(Long userId);

    public boolean updatePassword(String email, String newPassword);

    public boolean changePassword(ChangePasswordRequest request);

    public UserDto updateUserProfile(Long userId, UpdateUserDto userDto);
}