package com.sales.controller;

import com.sales.entity.User;
import com.sales.service.SessionService;
import com.sales.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理模块 - 用户信息存储（HBase）、用户会话管理（Redis）
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private SessionService sessionService;

    /**
     * 用户注册（HBase）
     */
    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody User user) {
        try {
            User registeredUser = userService.registerUser(user);
            return ResponseEntity.ok(registeredUser);
        } catch (IOException e) {
            log.error("Failed to register user", e);
            return ResponseEntity.internalServerError().build();
        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 用户登录（Redis会话）
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String username,
            @RequestParam(required = false) String loginIp) {
        try {
            String sessionId = userService.login(username, loginIp);
            return ResponseEntity.ok(Map.of(
                    "code", 0,
                    "message", "登录成功",
                    "data", Map.of(
                            "sessionId", sessionId,
                            "username", username
                    )
            ));
        } catch (IOException e) {
            log.error("Failed to login user: {}", username, e);
            return ResponseEntity.internalServerError().build();
        } catch (RuntimeException e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 用户登出（Redis会话）
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestParam String sessionId) {
        userService.logout(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 验证会话是否有效（Redis）
     */
    @GetMapping("/session/validate")
    public ResponseEntity<Boolean> validateSession(@RequestParam String sessionId) {
        boolean valid = sessionService.validateSession(sessionId);
        return ResponseEntity.ok(valid);
    }

    /**
     * 获取当前用户（Redis session -> HBase user_profile）
     */
    @GetMapping("/me")
    public ResponseEntity<User> me(@RequestParam String sessionId) {
        try {
            String sessionData = sessionService.getSession(sessionId);
            if (sessionData == null) {
                return ResponseEntity.status(401).build();
            }

            String userId = extractUserId(sessionData);
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.status(401).build();
            }

            User user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(user);
        } catch (IOException e) {
            log.error("Failed to get current user by sessionId", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private String extractUserId(String sessionData) {
        try {
            if (sessionData.contains("\"user_id\":")) {
                int start = sessionData.indexOf("\"user_id\":\"") + 11;
                int end = sessionData.indexOf("\"", start);
                if (end > start) {
                    return sessionData.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract user_id from session data: {}", sessionData, e);
        }
        return null;
    }

    /**
     * 获取用户详情（HBase）
     */
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable String userId) {
        try {
            User user = userService.getUserById(userId);
            if (user != null) {
                return ResponseEntity.ok(user);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to get user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新用户信息（HBase）
     */
    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUser(
            @PathVariable String userId,
            @RequestBody User user) {
        try {
            user.setUserId(userId);
            User updatedUser = userService.updateUser(user);
            return ResponseEntity.ok(updatedUser);
        } catch (IOException e) {
            log.error("Failed to update user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
