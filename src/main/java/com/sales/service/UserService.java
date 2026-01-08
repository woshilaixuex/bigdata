package com.sales.service;

import com.sales.entity.User;
import com.sales.repository.UserRepository;
import com.sales.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionService sessionService;

    /**
     * 注册用户
     */
    public User registerUser(User user) throws IOException {
        // 生成用户ID
        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            user.setUserId(generateUserId());
        }
        System.out.println(user.toString());
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查手机号是否已存在
        if (user.getPhone() != null && userRepository.existsByPhone(user.getPhone())) {
            throw new RuntimeException("手机号已存在");
        }

        // 检查邮箱是否已存在
        if (user.getEmail() != null && userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("邮箱已存在");
        }

        // 设置默认值
        if (user.getRegisterTime() == null) {
            user.setRegisterTime(LocalDateTime.now());
        }
        if (user.getStatus() == null) {
            user.setStatus(User.Status.NORMAL.getCode());
        }
        if (user.getLevel() == null) {
            user.setLevel(User.Level.BRONZE.getCode());
        }
        if (user.getPoints() == null) {
            user.setPoints(0);
        }
        if (user.getBalance() == null) {
            user.setBalance(BigDecimal.ZERO);
        }
        if (user.getGrowthValue() == null) {
            user.setGrowthValue(0);
        }
        if (user.getLoginCount() == null) {
            user.setLoginCount(0);
        }
        if (user.getTotalOrderAmount() == null) {
            user.setTotalOrderAmount(BigDecimal.ZERO);
        }

        // 保存用户
        userRepository.save(user);

        log.info("User registered: {}", user.getUserId());
        return user;
    }

    /**
     * 用户登录
     */
    public String login(String username, String loginIp) throws IOException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!user.isActive()) {
            throw new RuntimeException("用户已被禁用");
        }
        // 生成会话ID
        String sessionId = generateSessionId();

        // 创建会话
        sessionService.createSession(sessionId, user.getUserId(), user.getUsername(), loginIp);

        // 更新登录信息
        userRepository.updateLoginInfo(user.getUserId(), loginIp);

        log.info("User logged in: userId={}, username={}, ip={}", user.getUserId(), username, loginIp);
        return sessionId;
    }

    /**
     * 用户登出
     */
    public void logout(String sessionId) {
        sessionService.destroySession(sessionId);
        log.info("User logged out: sessionId={}", sessionId);
    }

    /**
     * 根据ID获取用户
     */
    @Cacheable(value = "user", key = "#userId")
    public User getUserById(String userId) throws IOException {
        return userRepository.findById(userId);
    }

    /**
     * 根据用户名获取用户
     */
    public User getUserByUsername(String username) throws IOException {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据手机号获取用户
     */
    public User getUserByPhone(String phone) throws IOException {
        return userRepository.findByPhone(phone);
    }

    /**
     * 根据邮箱获取用户
     */
    public User getUserByEmail(String email) throws IOException {
        return userRepository.findByEmail(email);
    }

    /**
     * 更新用户信息
     */
    @CachePut(value = "user", key = "#user.userId")
    public User updateUser(User user) throws IOException {
        userRepository.save(user);
        log.info("User updated: {}", user.getUserId());
        return user;
    }

    /**
     * 更新用户积分
     */
    public void updatePoints(String userId, Integer points) throws IOException {
        userRepository.updatePoints(userId, points);
        log.info("User points updated: userId={}, points={}", userId, points);
    }

    /**
     * 增加用户积分
     */
    public void addPoints(String userId, Integer deltaPoints) throws IOException {
        User user = userRepository.findById(userId);
        if (user != null && user.getPoints() != null) {
            userRepository.updatePoints(userId, user.getPoints() + deltaPoints);
            log.info("User points added: userId={}, delta={}", userId, deltaPoints);
        }
    }

    /**
     * 更新用户余额
     */
    public void updateBalance(String userId, BigDecimal balance) throws IOException {
        userRepository.updateBalance(userId, balance);
        log.info("User balance updated: userId={}, balance={}", userId, balance);
    }

    /**
     * 增加用户余额
     */
    public void addBalance(String userId, BigDecimal deltaBalance) throws IOException {
        User user = userRepository.findById(userId);
        if (user != null && user.getBalance() != null) {
            BigDecimal newBalance = user.getBalance().add(deltaBalance);
            userRepository.updateBalance(userId, newBalance);
            log.info("User balance added: userId={}, delta={}", userId, deltaBalance);
        }
    }

    /**
     * 扣减用户余额
     */
    public boolean deductBalance(String userId, BigDecimal amount) throws IOException {
        User user = userRepository.findById(userId);
        if (user != null && user.getBalance() != null) {
            if (user.getBalance().compareTo(amount) >= 0) {
                BigDecimal newBalance = user.getBalance().subtract(amount);
                userRepository.updateBalance(userId, newBalance);
                log.info("User balance deducted: userId={}, amount={}", userId, amount);
                return true;
            } else {
                log.warn("Insufficient balance: userId={}, required={}, available={}", 
                        userId, amount, user.getBalance());
                return false;
            }
        }
        return false;
    }

    /**
     * 更新用户成长值
     */
    public void updateGrowthValue(String userId, Integer growthValue) throws IOException {
        userRepository.updateGrowthValue(userId, growthValue);
        
        // 重新计算会员等级
        User user = userRepository.findById(userId);
        if (user != null) {
            user.updateLevel();
            userRepository.save(user);
        }
        
        log.info("User growth value updated: userId={}, growthValue={}", userId, growthValue);
    }

    /**
     * 增加用户成长值
     */
    public void addGrowthValue(String userId, Integer deltaGrowthValue) throws IOException {
        User user = userRepository.findById(userId);
        if (user != null && user.getGrowthValue() != null) {
            int newGrowthValue = user.getGrowthValue() + deltaGrowthValue;
            userRepository.updateGrowthValue(userId, newGrowthValue);
            
            // 重新计算会员等级
            user.setGrowthValue(newGrowthValue);
            user.updateLevel();
            userRepository.save(user);
            
            log.info("User growth value added: userId={}, delta={}", userId, deltaGrowthValue);
        }
    }

    /**
     * 增加用户订单金额
     */
    public void addOrderAmount(String userId, BigDecimal amount) throws IOException {
        userRepository.addOrderAmount(userId, amount);
        
        // 根据消费金额增加成长值
        int growthValue = amount.divide(BigDecimal.valueOf(10), 0, BigDecimal.ROUND_DOWN).intValue(); // 每10元1成长值
        addGrowthValue(userId, growthValue);
        
        log.info("User order amount added: userId={}, amount={}", userId, amount);
    }

    /**
     * 获取用户列表（按状态）
     */
    public List<User> getUsersByStatus(Integer status, int limit) throws IOException {
        return userRepository.findByStatus(status, limit);
    }

    /**
     * 获取用户列表（按等级）
     */
    public List<User> getUsersByLevel(Integer level, int limit) throws IOException {
        return userRepository.findByLevel(level, limit);
    }

    /**
     * 禁用/启用用户
     */
    public void updateUserStatus(String userId, Integer status) throws IOException {
        User user = userRepository.findById(userId);
        if (user != null) {
            user.setStatus(status);
            userRepository.save(user);
            
            // 如果禁用用户，强制下线
            if (User.Status.DISABLED.getCode().equals(status)) {
                sessionService.forceLogout(userId);
            }
            
            log.info("User status updated: userId={}, status={}", userId, status);
        }
    }

    /**
     * 检查用户是否存在
     */
    public boolean userExists(String userId) throws IOException {
        return userRepository.existsById(userId);
    }

    /**
     * 检查用户名是否存在
     */
    public boolean usernameExists(String username) throws IOException {
        return userRepository.existsByUsername(username);
    }

    /**
     * 检查手机号是否存在
     */
    public boolean phoneExists(String phone) throws IOException {
        return userRepository.existsByPhone(phone);
    }

    /**
     * 检查邮箱是否存在
     */
    public boolean emailExists(String email) throws IOException {
        return userRepository.existsByEmail(email);
    }

    /**
     * 获取用户统计信息
     */
    public UserStats getUserStats() throws IOException {
        long totalUsers = 0;
        long activeUsers = 0;
        long bronzeUsers = 0;
        long silverUsers = 0;
        long goldUsers = 0;
        long platinumUsers = 0;
        long diamondUsers = 0;

        // 这里简化处理，实际项目中可以使用更高效的统计方法
        List<User> allUsers = userRepository.findByStatus(User.Status.NORMAL.getCode(), 10000);
        totalUsers = allUsers.size();
        activeUsers = sessionService.getOnlineUserCount();

        for (User user : allUsers) {
            switch (User.Level.fromCode(user.getLevel())) {
                case BRONZE -> bronzeUsers++;
                case SILVER -> silverUsers++;
                case GOLD -> goldUsers++;
                case PLATINUM -> platinumUsers++;
                case DIAMOND -> diamondUsers++;
            }
        }

        return UserStats.builder()
                .totalUsers((int) totalUsers)
                .activeUsers((int) activeUsers)
                .bronzeUsers((int) bronzeUsers)
                .silverUsers((int) silverUsers)
                .goldUsers((int) goldUsers)
                .platinumUsers((int) platinumUsers)
                .diamondUsers((int) diamondUsers)
                .build();
    }

    /**
     * 生成用户ID
     */
    private String generateUserId() {
        return "U" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 用户统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class UserStats {
        private int totalUsers;
        private int activeUsers;
        private int bronzeUsers;
        private int silverUsers;
        private int goldUsers;
        private int platinumUsers;
        private int diamondUsers;
    }
}
