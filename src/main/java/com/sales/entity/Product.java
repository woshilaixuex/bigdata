package com.sales.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String productId;          // 商品ID (rowkey)
    
    // 基本信息
    private String name;              // 商品名称
    private String category;          // 品类编号
    private String brand;             // 品牌
    private BigDecimal price;         // 价格
    private BigDecimal cost;          // 成本价
    private Integer status;           // 状态(1-上架, 0-下架)
    private LocalDateTime createTime; // 创建时间
    
    // 详细信息
    private String description;       // 描述
    private String spec;              // 规格参数(JSON格式)
    private List<String> images;      // 图片URL(JSON数组)
    private String tags;              // 标签(逗号分隔)
    
    // 库存信息
    private Integer totalStock;      // 总库存
    private Map<String, Integer> warehouseStock; // 各仓库库存(JSON: {仓库ID:数量})
    private Integer safeStock;        // 安全库存
    private Integer lockStock;        // 锁定库存
    
    // 统计信息
    private Long viewCount;           // 浏览数
    private Long saleCount;           // 销量
    private Long collectCount;        // 收藏数
    private LocalDateTime updateTime; // 最后更新时间
    
    // 实时库存（从Redis获取）
    private Integer realTimeStock;
    
    // 商品状态枚举
    public enum Status {
        OFF_SHELF(0, "下架"),
        ON_SHELF(1, "上架");
        
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
            return OFF_SHELF;
        }
    }
    
    // 获取库存状态
    @JsonIgnore
    public String getStockStatus() {
        Integer stock = realTimeStock != null ? realTimeStock : totalStock;
        if (stock == null) {
            stock = 0;
        }

        if (stock <= 0) {
            return "缺货";
        }

        if (safeStock != null && stock <= safeStock) {
            return "库存不足";
        }

        return "库存充足";
    }
    
    // 检查是否可以购买
    @JsonIgnore
    public boolean isAvailable() {
        return Status.ON_SHELF.getCode().equals(status) &&
               (realTimeStock == null || realTimeStock > 0);
    }
    
    // 计算毛利率
    @JsonIgnore
    public BigDecimal getGrossMargin() {
        if (price == null || cost == null || cost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return price.subtract(cost).divide(cost, 4, BigDecimal.ROUND_HALF_UP)
                     .multiply(new BigDecimal("100"));
    }
}
