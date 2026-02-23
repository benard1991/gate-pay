package com.gatepay.userservice.controller;

import com.gatepay.userservice.dto.*;
import com.gatepay.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<UserDto> getUserByEmail(@RequestParam("email") String email) {
        UserDto userDto = userService.getUserByEmail(email);
        return new ApiResponse<>(HttpStatus.OK.value(), "User fetched successfully", userDto);
    }

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserDto>> getUserProfile(@PathVariable("userId") Long userId) {
        UserDto userDto = userService.getUserProfile(userId);
        ApiResponse<UserDto> response = new ApiResponse<>(HttpStatus.OK.value(), "User fetched successfully", userDto);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/register")
    public ApiResponse<UserDto> createUser(@Valid @RequestBody UserDto userDto) {
        UserDto savedUser = userService.createUser(userDto);
        return new ApiResponse<>(HttpStatus.CREATED.value(), "User created successfully", savedUser);
    }

    @PutMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody PasswordUpdateRequest request) {
        boolean success = userService.updatePassword(request.getEmail(), request.getNewPassword());
        if (success) {
            return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Password updated successfully", null));
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            "Failed to update password", null));
        }
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "Password changed successfully", null)
        );
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserDto>> updateUserProfile(@PathVariable("userId") Long userId, @Valid @RequestBody UpdateUserDto userDto) {
        UserDto updatedUser = userService.updateUserProfile(userId, userDto);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "User profile updated successfully", updatedUser));
    }

}