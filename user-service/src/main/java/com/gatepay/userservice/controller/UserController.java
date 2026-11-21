package com.gatepay.userservice.controller;

import com.gatepay.userservice.dto.*;
import com.gatepay.userservice.exception.ErrorCode;
import com.gatepay.userservice.exception.UserServiceException;
import com.gatepay.userservice.security.CallerIdentity;
import com.gatepay.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Public, user-scoped endpoints (reached through the gateway at /api/v1/users/**).
// Every endpoint acts only on the caller's own account.
@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserDto>> getUserProfile(@PathVariable("userId") Long userId) {
        CallerIdentity.requireSelf(userId);
        UserDto userDto = userService.getUserProfile(userId);
        ApiResponse<UserDto> response = new ApiResponse<>(HttpStatus.OK.value(), "User fetched successfully", userDto);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/register")
    public ApiResponse<UserDto> createUser(@Valid @RequestBody UserDto userDto) {
        UserDto savedUser = userService.createUser(userDto);
        return new ApiResponse<>(HttpStatus.CREATED.value(), "User created successfully", savedUser);
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        // you can only change your own password, whatever email is in the body
        if (!request.getEmail().equalsIgnoreCase(CallerIdentity.currentUsername())) {
            throw new UserServiceException(ErrorCode.ACCESS_DENIED, "You can only change your own password");
        }
        userService.changePassword(request);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Password changed successfully", null)
        );
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateUserProfile(@PathVariable("userId") Long userId, @Valid @RequestBody UpdateUserDto userDto) {
        CallerIdentity.requireSelf(userId);
        UserDto updatedUser = userService.updateUserProfile(userId, userDto);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "User profile updated successfully", updatedUser));
    }

}