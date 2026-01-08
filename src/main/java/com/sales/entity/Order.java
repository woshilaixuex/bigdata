package com.sales.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String orderId;           // 订单ID (rowkey)
    
    // 基本信息
    private String userId;            // 用户ID
    private BigDecimal totalAmount;   // 订单总金额
    private BigDecimal discountAmount; // 优惠金额
    private BigDecimal actualAmount;   // 实付金额
    private Integer status;           // 订单状态
    private String payMethod;         // 支付方式
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime payTime;    // 支付时间
    private LocalDateTime deliverTime;// 发货时间
    private LocalDateTime completeTime;// 完成时间
    
    // 收货信息
    private String receiver;          // 收货人
    private String phone;             // 联系电话
    private String address;           // 详细地址
    private String postcode;          // 邮编
    
    // 商品明细
    private List<OrderItem> items;    // 商品明细列表
    
    // 物流信息
    private String expressCompany;    // 快递公司
    private String expressNo;         // 快递单号
    private List<LogisticsInfo> logisticsInfo; // 物流轨迹
    
    // 备注信息
    private String remark;            // 订单备注
    
    // 订单状态枚举
    public enum Status {
        PENDING_PAYMENT(1, "待付款"),
        PENDING_DELIVERY(2, "待发货"),
        SHIPPED(3, "已发货"),
        COMPLETED(4, "已完成"),
        CANCELLED(5, "已取消");
        
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
            return PENDING_PAYMENT;
        }
    }
    
    // 支付方式枚举
    public enum PayMethod {
        ALIPAY("alipay", "支付宝"),
        WECHAT("wechat", "微信支付"),
        BANK_CARD("bank_card", "银行卡"),
        BALANCE("balance", "余额支付");
        
        private final String code;
        private final String desc;
        
        PayMethod(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDesc() {
            return desc;
        }
        
        public static PayMethod fromCode(String code) {
            for (PayMethod method : values()) {
                if (method.code.equals(code)) {
                    return method;
                }
            }
            return ALIPAY;
        }
    }
    
    // 获取状态描述
    public String getStatusDesc() {
        return Status.fromCode(status).getDesc();
    }
    
    // 获取支付方式描述
    public String getPayMethodDesc() {
        return PayMethod.fromCode(payMethod).getDesc();
    }
    
    // 检查是否可以取消
    public boolean canCancel() {
        return Status.PENDING_PAYMENT.getCode().equals(status);
    }
    
    // 检查是否可以支付
    public boolean canPay() {
        return Status.PENDING_PAYMENT.getCode().equals(status);
    }
    
    // 检查是否可以发货
    public boolean canDeliver() {
        return Status.PENDING_DELIVERY.getCode().equals(status);
    }
    
    // 检查是否可以确认收货
    public boolean canComplete() {
        return Status.SHIPPED.getCode().equals(status);
    }
    
    // 订单项内部类
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem implements Serializable {
        
        private static final long serialVersionUID = 1L;
        @JsonProperty("product_id")
        private String productId;       // 商品ID
        @JsonProperty("name")
        private String productName;     // 商品名称
        private BigDecimal price;       // 商品单价
        private Integer quantity;       // 数量
        private BigDecimal amount;      // 小计金额
        private String image;           // 商品图片
    }
    
    // 物流信息内部类
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogisticsInfo implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        private LocalDateTime time;     // 时间
        private String content;         // 物流内容
        private String location;        // 所在地
    }
}
