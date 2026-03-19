package com.walnut.agent.controller;

import com.walnut.agent.dto.WorkflowDtos;
import com.walnut.agent.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public WorkflowDtos.LoginResponse login(@Valid @RequestBody WorkflowDtos.LoginRequest req) {
        if (!"admin".equals(req.username()) || !"admin123".equals(req.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtService.generateToken(req.username());
        return new WorkflowDtos.LoginResponse(token, jwtService.expiresAtEpochMs());
    }
}
