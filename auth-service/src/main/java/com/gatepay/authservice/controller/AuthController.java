package com.gatepay.authservice.controller;

import com.gatepay.authservice.dto.LoginOtpResponse;
import com.gatepay.authservice.dto.LoginRequest;
import com.gatepay.authservice.dto.LoginResponse;
import com.gatepay.authservice.dto.VerifyOtpRequest;
import com.gatepay.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/initiateLogin")
    public ResponseEntity<LoginOtpResponse> initiateLogin(@RequestBody @Valid LoginRequest request) {
        LoginOtpResponse response = authService.initiateLogin(request);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/verify-otp")
    public ResponseEntity<LoginResponse>completeLogin(@RequestBody @Valid VerifyOtpRequest request){
        LoginResponse response = authService.completeLogin(request);
        return  ResponseEntity.ok(response);

    }

    @GetMapping("/validate-token")
    public ResponseEntity<Boolean> validateToken(@RequestParam("token") String token) {
        boolean valid = authService.validateToken(token);
        return ResponseEntity.ok(valid);
    }

}
