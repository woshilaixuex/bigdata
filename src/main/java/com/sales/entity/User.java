package com.sales.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class User implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String userId;            // 用户ID (rowkey)
    
    // 基本信息
    private String username;          // 用户名
    private String nickname;          // 昵称
    private String phone;             // 手机号
    private String email;             // 邮箱
    private String gender;            // 性别
    private LocalDate birthday;       // 生日
    private LocalDateTime registerTime; // 注册时间
    private Integer status;           // 状态(1-正常, 0-禁用)
    
    // 账户信息
    private Integer level;            // 会员等级
    private Integer points;           // 积分
    private BigDecimal balance;       // 余额
    private Integer growthValue;      // 成长值
    
    // 地址信息
    private List<UserAddress> addresses; // 地址列表
    
    // 行为信息
    private LocalDateTime lastLogin;  // 最后登录时间
    private String lastLoginIp;       // 最后登录IP
    private Integer loginCount;       // 登录次数
    private BigDecimal totalOrderAmount; // 累计消费金额
    
    // 用户状态枚举
    public enum Status {
        DISABLED(0, "禁用"),
        NORMAL(1, "正常");
        
        private final Integer code;
        private final String desc;
        
        Status(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }
        
        public Integer getCode() {
            return code;
        }
        
        public String getDesc() {
            return desc;
        }
        
        public static Status fromCode(Integer code) {
            for (Status status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return DISABLED;
        }
    }
    
    // 会员等级枚举
    public enum Level {
        BRONZE(1, "青铜会员", 0),
        SILVER(2, "白银会员", 1000),
        GOLD(3, "黄金会员", 5000),
        PLATINUM(4, "铂金会员", 20000),
        DIAMOND(5, "钻石会员", 50000);
        
        private final Integer code;
        private final String desc;
        private final Integer minGrowthValue;
        
        Level(Integer code, String desc, Integer minGrowthValue) {
            this.code = code;
            this.desc = desc;
            this.minGrowthValue = minGrowthValue;
        }
        
        public Integer getCode() {
            return code;
        }
        
        public String getDesc() {
            return desc;
        }
        
        public Integer getMinGrowthValue() {
            return minGrowthValue;
        }
        
        public static Level fromCode(Integer code) {
            for (Level level : values()) {
                if (level.code.equals(code)) {
                    return level;
                }
            }
            return BRONZE;
        }
        
        public static Level fromGrowthValue(Integer growthValue) {
            if (growthValue == null) {
                return BRONZE;
            }
            
            for (int i = values().length - 1; i >= 0; i--) {
                Level level = values()[i];
                if (growthValue >= level.minGrowthValue) {
                    return level;
                }
            }
            return BRONZE;
        }
    }
    
    // 获取状态描述
    public String getStatusDesc() {
        return Status.fromCode(status).getDesc();
    }
    
    // 获取会员等级描述
    public String getLevelDesc() {
        return Level.fromCode(level).getDesc();
    }
    
    // 检查用户是否正常
    @JsonIgnore
    public boolean isActive() {
        return Status.NORMAL.getCode().equals(status);
    }
    
    // 获取默认地址
    public UserAddress getDefaultAddress() {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        
        return addresses.stream()
                .filter(address -> address.getIsDefault() != null && address.getIsDefault())
                .findFirst()
                .orElse(addresses.get(0));
    }
    
    // 更新会员等级
    public void updateLevel() {
        this.level = Level.fromGrowthValue(growthValue).getCode();
    }
    
    // 用户地址内部类
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAddress implements Serializable {
        
        private static final long serialVersionUID = 1L;
        private String addressId;       // 地址ID
        private String receiver;        // 收货人
        private String phone;           // 联系电话
        private String province;         // 省份
        private String city;            // 城市
        private String district;         // 区县
        private String detail;          // 详细地址
        private String postcode;        // 邮编
        @JsonProperty("is_default")
        private Boolean isDefault;
        private LocalDateTime createTime; // 创建时间
    }
}
