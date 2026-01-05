package com.sales.repository;

import com.sales.config.HBaseConfig;
import com.sales.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class UserRepository extends BaseHBaseRepository {

    private static final TableName TABLE_NAME = HBaseConfig.TableNames.USER_PROFILE;

    public void save(User user) throws IOException {
        Put put = createPut(user.getUserId());
        
        // 基本信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_USERNAME, user.getUsername());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_NICKNAME, user.getNickname());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_PHONE, user.getPhone());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_EMAIL, user.getEmail());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_GENDER, user.getGender());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_BIRTHDAY, formatDate(user.getBirthday()));
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_REGISTER_TIME, formatDateTime(user.getRegisterTime()));
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_STATUS, user.getStatus());
        
        // 账户信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_LEVEL, user.getLevel());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_POINTS, user.getPoints());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_BALANCE, 
                 user.getBalance() != null ? user.getBalance().doubleValue() : null);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_GROWTH_VALUE, user.getGrowthValue());
        
        // 地址信息（多版本存储）
        if (user.getAddresses() != null) {
            for (int i = 0; i < user.getAddresses().size(); i++) {
                User.UserAddress address = user.getAddresses().get(i);
                String qualifier = "address_" + (i == 0 ? "default" : i);
                addJsonColumn(put, HBaseConfig.ColumnFamilies.CF_ADDRESS, qualifier, address);
            }
        }
        
        // 行为信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LAST_LOGIN, formatDateTime(user.getLastLogin()));
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LAST_LOGIN_IP, user.getLastLoginIp());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LOGIN_COUNT, user.getLoginCount());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_TOTAL_ORDER_AMOUNT, 
                 user.getTotalOrderAmount() != null ? user.getTotalOrderAmount().doubleValue() : null);
        
        putData(TABLE_NAME, put);
        log.info("User saved: {}", user.getUserId());
    }

    public User findById(String userId) throws IOException {
        Get get = createGet(userId);
        Result result = getData(TABLE_NAME, get);
        
        if (result.isEmpty()) {
            return null;
        }
        
        return mapToUser(result);
    }

    public User findByUsername(String username) throws IOException {
        Scan scan = createScan();
        
        // 添加用户名过滤器
        SingleColumnValueFilter usernameFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.USER_USERNAME),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(username)
        );
        
        scan.setFilter(usernameFilter);
        scan.setLimit(1);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        if (results.isEmpty()) {
            return null;
        }
        
        return mapToUser(results.get(0));
    }

    public User findByPhone(String phone) throws IOException {
        Scan scan = createScan();
        
        // 添加手机号过滤器
        SingleColumnValueFilter phoneFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.USER_PHONE),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(phone)
        );
        
        scan.setFilter(phoneFilter);
        scan.setLimit(1);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        if (results.isEmpty()) {
            return null;
        }
        
        return mapToUser(results.get(0));
    }

    public User findByEmail(String email) throws IOException {
        Scan scan = createScan();
        
        // 添加邮箱过滤器
        SingleColumnValueFilter emailFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.USER_EMAIL),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(email)
        );
        
        scan.setFilter(emailFilter);
        scan.setLimit(1);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        if (results.isEmpty()) {
            return null;
        }
        
        return mapToUser(results.get(0));
    }

    public List<User> findByStatus(Integer status, int limit) throws IOException {
        Scan scan = createScan();
        
        // 添加状态过滤器
        SingleColumnValueFilter statusFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.USER_STATUS),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(status)
        );
        
        scan.setFilter(statusFilter);
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<User> users = new ArrayList<>();
        
        for (Result result : results) {
            users.add(mapToUser(result));
        }
        
        return users;
    }

    public List<User> findByLevel(Integer level, int limit) throws IOException {
        Scan scan = createScan();
        
        // 添加等级过滤器
        SingleColumnValueFilter levelFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_ACCOUNT),
                Bytes.toBytes(HBaseConfig.Columns.USER_LEVEL),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(level)
        );
        
        scan.setFilter(levelFilter);
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<User> users = new ArrayList<>();
        
        for (Result result : results) {
            users.add(mapToUser(result));
        }
        
        return users;
    }

    public void updateLoginInfo(String userId, String loginIp) throws IOException {
        Put put = createPut(userId);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LAST_LOGIN, formatDateTime(LocalDateTime.now()));
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LAST_LOGIN_IP, loginIp);
        
        // 增加登录次数
        incrementColumnValue(TABLE_NAME, userId, 
                            HBaseConfig.ColumnFamilies.CF_BEHAVIOR, 
                            HBaseConfig.Columns.USER_LOGIN_COUNT, 1L);
        
        putData(TABLE_NAME, put);
        log.info("User login info updated: {}", userId);
    }

    public void updatePoints(String userId, Integer points) throws IOException {
        Put put = createPut(userId);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_POINTS, points);
        
        putData(TABLE_NAME, put);
        log.info("User points updated: {} -> {}", userId, points);
    }

    public void updateBalance(String userId, BigDecimal balance) throws IOException {
        Put put = createPut(userId);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_BALANCE, 
                 balance != null ? balance.doubleValue() : null);
        
        putData(TABLE_NAME, put);
        log.info("User balance updated: {} -> {}", userId, balance);
    }

    public void updateGrowthValue(String userId, Integer growthValue) throws IOException {
        Put put = createPut(userId);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_GROWTH_VALUE, growthValue);
        
        putData(TABLE_NAME, put);
        log.info("User growth value updated: {} -> {}", userId, growthValue);
    }

    public void addOrderAmount(String userId, BigDecimal amount) throws IOException {
        incrementColumnValue(TABLE_NAME, userId, 
                            HBaseConfig.ColumnFamilies.CF_BEHAVIOR, 
                            HBaseConfig.Columns.USER_TOTAL_ORDER_AMOUNT, 
                            amount != null ? amount.longValue() : 0L);
    }

    public boolean existsById(String userId) throws IOException {
        Get get = createGet(userId);
        return exists(TABLE_NAME, get);
    }

    public boolean existsByUsername(String username) throws IOException {
        return findByUsername(username) != null;
    }

    public boolean existsByPhone(String phone) throws IOException {
        return findByPhone(phone) != null;
    }

    public boolean existsByEmail(String email) throws IOException {
        return findByEmail(email) != null;
    }

    private User mapToUser(Result result) {
        User.UserBuilder builder = User.builder();
        
        String userId = Bytes.toString(result.getRow());
        builder.userId(userId);
        
        // 基本信息
        builder.username(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_USERNAME));
        builder.nickname(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_NICKNAME));
        builder.phone(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_PHONE));
        builder.email(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_EMAIL));
        builder.gender(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_GENDER));
        builder.birthday(parseDate(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_BIRTHDAY)));
        builder.registerTime(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_REGISTER_TIME)));
        builder.status(getInteger(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.USER_STATUS));
        
        // 账户信息
        builder.level(getInteger(result, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_LEVEL));
        builder.points(getInteger(result, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_POINTS));
        
        Double balance = getDouble(result, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_BALANCE);
        if (balance != null) {
            builder.balance(BigDecimal.valueOf(balance));
        }
        
        builder.growthValue(getInteger(result, HBaseConfig.ColumnFamilies.CF_ACCOUNT, HBaseConfig.Columns.USER_GROWTH_VALUE));
        
        // 地址信息
        List<User.UserAddress> addresses = new ArrayList<>();
        // 动态读取地址列
        for (int i = 0; i <= 10; i++) { // 假设最多10个地址
            String qualifier = i == 0 ? "address_default" : "address_" + i;
            User.UserAddress address = getJson(result, HBaseConfig.ColumnFamilies.CF_ADDRESS, qualifier, User.UserAddress.class);
            if (address != null) {
                addresses.add(address);
            }
        }
        builder.addresses(addresses);
        
        // 行为信息
        builder.lastLogin(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LAST_LOGIN)));
        builder.lastLoginIp(getString(result, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LAST_LOGIN_IP));
        builder.loginCount(getInteger(result, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_LOGIN_COUNT));
        
        Double totalOrderAmount = getDouble(result, HBaseConfig.ColumnFamilies.CF_BEHAVIOR, HBaseConfig.Columns.USER_TOTAL_ORDER_AMOUNT);
        if (totalOrderAmount != null) {
            builder.totalOrderAmount(BigDecimal.valueOf(totalOrderAmount));
        }
        
        return builder.build();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            // ignore
        }

        try {
            return LocalDate.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (Exception ignored) {
            // ignore
        }

        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            // ignore
        }

        log.error("Failed to parse date time: {}", dateTimeStr);
        return null;
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.error("Failed to parse date: {}", dateStr, e);
            return null;
        }
    }
}
