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
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private SessionService sessionService;

    /**
     * 用户注册
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
     * 获取在线用户ID列表（Redis online:users）
     */
    @GetMapping("/online")
    public ResponseEntity<Set<Object>> onlineUsers() {
        try {
            return ResponseEntity.ok(sessionService.getOnlineUsers());
        } catch (Exception e) {
            log.error("Failed to get online users", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取在线用户详情列表（根据 online:users -> HBase user_profile）
     */
    @GetMapping("/online/details")
    public ResponseEntity<List<User>> onlineUserDetails(@RequestParam(defaultValue = "20") int limit) {
        try {
            Set<Object> onlineUsers = sessionService.getOnlineUsers();
            if (onlineUsers == null || onlineUsers.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            List<User> users = onlineUsers.stream()
                    .limit(Math.max(0, limit))
                    .map(Object::toString)
                    .map(userId -> {
                        try {
                            return userService.getUserById(userId);
                        } catch (IOException e) {
                            log.error("Failed to get online user by id: {}", userId, e);
                            return null;
                        }
                    })
                    .filter(u -> u != null)
                    .toList();

            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("Failed to get online user details", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<String> login(
            @RequestParam String username,
            @RequestParam(required = false) String loginIp) {
        try {
            String sessionId = userService.login(username, loginIp);
            return ResponseEntity.ok(sessionId);
        } catch (IOException e) {
            log.error("Failed to login user: {}", username, e);
            return ResponseEntity.internalServerError().build();
        } catch (RuntimeException e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 用户登出
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
     * 获取用户详情
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
     * 根据用户名获取用户
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
        try {
            User user = userService.getUserByUsername(username);
            if (user != null) {
                return ResponseEntity.ok(user);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to get user by username: {}", username, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据手机号获取用户
     */
    @GetMapping("/phone/{phone}")
    public ResponseEntity<User> getUserByPhone(@PathVariable String phone) {
        try {
            User user = userService.getUserByPhone(phone);
            if (user != null) {
                return ResponseEntity.ok(user);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to get user by phone: {}", phone, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据邮箱获取用户
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        try {
            User user = userService.getUserByEmail(email);
            if (user != null) {
                return ResponseEntity.ok(user);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to get user by email: {}", email, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新用户信息
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

    /**
     * 更新用户积分
     */
    @PutMapping("/{userId}/points")
    public ResponseEntity<Void> updatePoints(
            @PathVariable String userId,
            @RequestParam Integer points) {
        try {
            userService.updatePoints(userId, points);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to update user points: userId={}, points={}", userId, points, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 增加用户积分
     */
    @PostMapping("/{userId}/points/add")
    public ResponseEntity<Void> addPoints(
            @PathVariable String userId,
            @RequestParam Integer deltaPoints) {
        try {
            userService.addPoints(userId, deltaPoints);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to add user points: userId={}, delta={}", userId, deltaPoints, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新用户余额
     */
    @PutMapping("/{userId}/balance")
    public ResponseEntity<Void> updateBalance(
            @PathVariable String userId,
            @RequestParam java.math.BigDecimal balance) {
        try {
            userService.updateBalance(userId, balance);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to update user balance: userId={}, balance={}", userId, balance, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 增加用户余额
     */
    @PostMapping("/{userId}/balance/add")
    public ResponseEntity<Void> addBalance(
            @PathVariable String userId,
            @RequestParam java.math.BigDecimal deltaBalance) {
        try {
            userService.addBalance(userId, deltaBalance);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to add user balance: userId={}, delta={}", userId, deltaBalance, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 扣减用户余额
     */
    @PostMapping("/{userId}/balance/deduct")
    public ResponseEntity<Boolean> deductBalance(
            @PathVariable String userId,
            @RequestParam java.math.BigDecimal amount) {
        try {
            boolean success = userService.deductBalance(userId, amount);
            return ResponseEntity.ok(success);
        } catch (IOException e) {
            log.error("Failed to deduct user balance: userId={}, amount={}", userId, amount, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新用户成长值
     */
    @PutMapping("/{userId}/growth")
    public ResponseEntity<Void> updateGrowthValue(
            @PathVariable String userId,
            @RequestParam Integer growthValue) {
        try {
            userService.updateGrowthValue(userId, growthValue);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to update user growth value: userId={}, growthValue={}", userId, growthValue, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 增加用户成长值
     */
    @PostMapping("/{userId}/growth/add")
    public ResponseEntity<Void> addGrowthValue(
            @PathVariable String userId,
            @RequestParam Integer deltaGrowthValue) {
        try {
            userService.addGrowthValue(userId, deltaGrowthValue);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to add user growth value: userId={}, delta={}", userId, deltaGrowthValue, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取用户列表（按状态）
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<User>> getUsersByStatus(
            @PathVariable Integer status,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<User> users = userService.getUsersByStatus(status, limit);
            return ResponseEntity.ok(users);
        } catch (IOException e) {
            log.error("Failed to get users by status: {}", status, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取用户列表（按等级）
     */
    @GetMapping("/level/{level}")
    public ResponseEntity<List<User>> getUsersByLevel(
            @PathVariable Integer level,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<User> users = userService.getUsersByLevel(level, limit);
            return ResponseEntity.ok(users);
        } catch (IOException e) {
            log.error("Failed to get users by level: {}", level, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 禁用/启用用户
     */
    @PutMapping("/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable String userId,
            @RequestParam Integer status) {
        try {
            userService.updateUserStatus(userId, status);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to update user status: userId={}, status={}", userId, status, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 检查用户是否存在
     */
    @GetMapping("/{userId}/exists")
    public ResponseEntity<Boolean> checkUserExists(@PathVariable String userId) {
        try {
            boolean exists = userService.userExists(userId);
            return ResponseEntity.ok(exists);
        } catch (IOException e) {
            log.error("Failed to check user existence: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 检查用户名是否存在
     */
    @GetMapping("/username/{username}/exists")
    public ResponseEntity<Boolean> checkUsernameExists(@PathVariable String username) {
        try {
            boolean exists = userService.usernameExists(username);
            return ResponseEntity.ok(exists);
        } catch (IOException e) {
            log.error("Failed to check username existence: {}", username, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 检查手机号是否存在
     */
    @GetMapping("/phone/{phone}/exists")
    public ResponseEntity<Boolean> checkPhoneExists(@PathVariable String phone) {
        try {
            boolean exists = userService.phoneExists(phone);
            return ResponseEntity.ok(exists);
        } catch (IOException e) {
            log.error("Failed to check phone existence: {}", phone, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 检查邮箱是否存在
     */
    @GetMapping("/email/{email}/exists")
    public ResponseEntity<Boolean> checkEmailExists(@PathVariable String email) {
        try {
            boolean exists = userService.emailExists(email);
            return ResponseEntity.ok(exists);
        } catch (IOException e) {
            log.error("Failed to check email existence: {}", email, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<UserService.UserStats> getUserStats() {
        try {
            UserService.UserStats stats = userService.getUserStats();
            return ResponseEntity.ok(stats);
        } catch (IOException e) {
            log.error("Failed to get user stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
