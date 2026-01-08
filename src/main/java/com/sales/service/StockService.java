package com.sales.service;

import com.sales.config.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class StockService {

    @Autowired
    private RedisService redisService;

    private static final long STOCK_EXPIRE_TIME = 3600; // 1小时

    /**
     * 设置商品库存
     */
    public void setStock(String productId, int stock) {
        String stockKey = RedisConfig.RedisKeys.STOCK_PREFIX + productId;
        redisService.set(stockKey, stock, STOCK_EXPIRE_TIME, java.util.concurrent.TimeUnit.SECONDS);
        
        log.info("Set stock: productId={}, stock={}", productId, stock);
    }

    /**
     * 获取商品库存
     */
    public int getStock(String productId) {
        String stockKey = RedisConfig.RedisKeys.STOCK_PREFIX + productId;
        Object stockObj = redisService.get(stockKey);
        
        if (stockObj == null) {
            log.warn("Stock not found in Redis: productId={}", productId);
            return 0;
        }
        
        try {
            if (stockObj instanceof Integer) {
                return (Integer) stockObj;
            } else if (stockObj instanceof String) {
                return Integer.parseInt((String) stockObj);
            } else {
                log.error("Invalid stock type: productId={}, type={}", productId, stockObj.getClass());
                return 0;
            }
        } catch (NumberFormatException e) {
            log.error("Failed to parse stock: productId={}, value={}", productId, stockObj, e);
            return 0;
        }
    }

    /**
     * 增加库存
     */
    public long increaseStock(String productId, int delta) {
        String stockKey = RedisConfig.RedisKeys.STOCK_PREFIX + productId;
        long newStock = redisService.incr(stockKey, delta);
        
        // 设置过期时间（如果不存在的话）
        redisService.expire(stockKey, STOCK_EXPIRE_TIME, java.util.concurrent.TimeUnit.SECONDS);
        
        log.info("Increased stock: productId={}, delta={}, newStock={}", productId, delta, newStock);
        return newStock;
    }

    /**
     * 减少库存
     */
    public long decreaseStock(String productId, int delta) {
        String stockKey = RedisConfig.RedisKeys.STOCK_PREFIX + productId;
        long newStock = redisService.decr(stockKey, delta);
        
        // 检查库存是否足够
        if (newStock < 0) {
            // 库存不足，回滚
            redisService.incr(stockKey, delta);
            log.warn("Insufficient stock: productId={}, delta={}, currentStock={}", productId, delta, newStock + delta);
            return -1;
        }
        
        // 设置过期时间
        redisService.expire(stockKey, STOCK_EXPIRE_TIME, java.util.concurrent.TimeUnit.SECONDS);
        
        log.info("Decreased stock: productId={}, delta={}, newStock={}", productId, delta, newStock);
        return newStock;
    }

    /**
     * 原子性扣减库存
     */
    public boolean deductStock(String productId, int quantity) {
        String stockKey = RedisConfig.RedisKeys.STOCK_PREFIX + productId;
        long currentStock = getStock(productId);
        
        if (currentStock < quantity) {
            log.warn("Insufficient stock for deduction: productId={}, required={}, available={}", 
                    productId, quantity, currentStock);
            return false;
        }
        
        long newStock = decreaseStock(productId, quantity);
        if (newStock >= 0) {
            log.info("Stock deducted successfully: productId={}, quantity={}, remaining={}", 
                    productId, quantity, newStock);
            return true;
        } else {
            log.error("Stock deduction failed: productId={}, quantity={}", productId, quantity);
            return false;
        }
    }

    /**
     * 设置秒杀库存
     */
    public void setSeckillStock(String seckillId, String productId, int stock) {
        String seckillStockKey = RedisConfig.RedisKeys.SECKILL_STOCK_PREFIX + seckillId + "_" + productId;
        redisService.set(seckillStockKey, stock, STOCK_EXPIRE_TIME, java.util.concurrent.TimeUnit.SECONDS);
        
        log.info("Set seckill stock: seckillId={}, productId={}, stock={}", seckillId, productId, stock);
    }

    /**
     * 获取秒杀库存
     */
    public int getSeckillStock(String seckillId, String productId) {
        String seckillStockKey = RedisConfig.RedisKeys.SECKILL_STOCK_PREFIX + seckillId + "_" + productId;
        Object stockObj = redisService.get(seckillStockKey);
        
        if (stockObj == null) {
            return 0;
        }
        
        try {
            if (stockObj instanceof Integer) {
                return (Integer) stockObj;
            } else if (stockObj instanceof String) {
                return Integer.parseInt((String) stockObj);
            }
        } catch (NumberFormatException e) {
            log.error("Failed to parse seckill stock: seckillId={}, productId={}, value={}", 
                    seckillId, productId, stockObj, e);
        }
        
        return 0;
    }

    /**
     * 扣减秒杀库存
     */
    public boolean deductSeckillStock(String seckillId, String productId, int quantity) {
        String seckillStockKey = RedisConfig.RedisKeys.SECKILL_STOCK_PREFIX + seckillId + "_" + productId;
        long currentStock = getSeckillStock(seckillId, productId);
        
        if (currentStock < quantity) {
            log.warn("Insufficient seckill stock: seckillId={}, productId={}, required={}, available={}", 
                    seckillId, productId, quantity, currentStock);
            return false;
        }
        
        long newStock = redisService.decr(seckillStockKey, quantity);
        if (newStock >= 0) {
            log.info("Seckill stock deducted: seckillId={}, productId={}, quantity={}, remaining={}", 
                    seckillId, productId, quantity, newStock);
            return true;
        } else {
            // 回滚
            redisService.incr(seckillStockKey, quantity);
            log.error("Seckill stock deduction failed: seckillId={}, productId={}, quantity={}", 
                    seckillId, productId, quantity);
            return false;
        }
    }

    /**
     * 检查库存是否存在
     */
    public boolean stockExists(String productId) {
        String stockKey = RedisConfig.RedisKeys.STOCK_PREFIX + productId;
        return redisService.exists(stockKey);
    }

    /**
     * 删除库存
     */
    public void deleteStock(String productId) {
        String stockKey = RedisConfig.RedisKeys.STOCK_PREFIX + productId;
        redisService.del(stockKey);
        
        log.info("Deleted stock: productId={}", productId);
    }

    /**
     * 批量获取库存
     */
    public List<Integer> batchGetStock(List<String> productIds) {
        List<Integer> stocks = new ArrayList<>();
        
        for (String productId : productIds) {
            stocks.add(getStock(productId));
        }
        
        return stocks;
    }

    /**
     * 预占库存（用于订单创建时锁定库存）
     */
    public boolean lockStock(String productId, int quantity) {
        String lockKey = RedisConfig.RedisKeys.LOCK_PREFIX + "stock:" + productId;
        
        // 尝试获取分布式锁
        boolean locked = redisService.setIfAbsent(lockKey, "locked", 30, java.util.concurrent.TimeUnit.SECONDS);
        
        if (locked) {
            try {
                // 检查库存是否充足
                long currentStock = getStock(productId);
                if (currentStock < quantity) {
                    log.warn("Insufficient stock for locking: productId={}, required={}, available={}", 
                            productId, quantity, currentStock);
                    return false;
                }
                
                // 扣减库存
                return deductStock(productId, quantity);
            } finally {
                // 释放锁
                redisService.del(lockKey);
            }
        }
        
        log.warn("Failed to acquire stock lock: productId={}", productId);
        return false;
    }
    
    /**
     * 批量锁定库存
     */
    public boolean batchLockStock(java.util.Map<String, Integer> productQuantities) {
        java.util.List<String> lockedProducts = new java.util.ArrayList<>();
        
        try {
            // 逐个锁定库存
            for (java.util.Map.Entry<String, Integer> entry : productQuantities.entrySet()) {
                String productId = entry.getKey();
                Integer quantity = entry.getValue();
                
                if (!lockStock(productId, quantity)) {
                    // 锁定失败，回滚已锁定的库存
                    log.error("Failed to lock stock for product: {}, rolling back locked products", productId);
                    
                    for (String lockedProductId : lockedProducts) {
                        // 需要获取原来的数量，这里简化处理
                        releaseStock(lockedProductId, productQuantities.get(lockedProductId));
                    }
                    
                    return false;
                }
                
                lockedProducts.add(productId);
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error during batch stock locking", e);
            
            // 回滚已锁定的库存
            for (String lockedProductId : lockedProducts) {
                releaseStock(lockedProductId, productQuantities.get(lockedProductId));
            }
            
            return false;
        }
    }

    /**
     * 释放预占库存
     */
    public void releaseStock(String productId, int quantity) {
        increaseStock(productId, quantity);
        log.info("Released stock: productId={}, quantity={}", productId, quantity);
    }
    
    /**
     * 检查库存是否充足
     */
    public boolean checkStock(String productId, int requiredQuantity) {
        long currentStock = getStock(productId);
        return currentStock >= requiredQuantity;
    }
    
    /**
     * 批量检查库存
     */
    public java.util.Map<String, Boolean> batchCheckStock(java.util.Map<String, Integer> productQuantities) {
        java.util.Map<String, Boolean> results = new java.util.HashMap<>();
        
        for (java.util.Map.Entry<String, Integer> entry : productQuantities.entrySet()) {
            String productId = entry.getKey();
            Integer requiredQuantity = entry.getValue();
            results.put(productId, checkStock(productId, requiredQuantity));
        }
        
        return results;
    }
    
    /**
     * 获取库存详细信息
     */
    public StockInfo getStockInfo(String productId) {
        long currentStock = getStock(productId);
        boolean exists = stockExists(productId);
        
        return StockInfo.builder()
                .productId(productId)
                .currentStock((int) currentStock)
                .exists(exists)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 库存信息内部类
     */
    public static class StockInfo {
        private String productId;
        private Integer currentStock;
        private Boolean exists;
        private Long timestamp;
        
        public static StockInfoBuilder builder() {
            return new StockInfoBuilder();
        }
        
        // Getters and Setters
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public Integer getCurrentStock() { return currentStock; }
        public void setCurrentStock(Integer currentStock) { this.currentStock = currentStock; }
        public Boolean getExists() { return exists; }
        public void setExists(Boolean exists) { this.exists = exists; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        
        public static class StockInfoBuilder {
            private String productId;
            private Integer currentStock;
            private Boolean exists;
            private Long timestamp;
            
            public StockInfoBuilder productId(String productId) { this.productId = productId; return this; }
            public StockInfoBuilder currentStock(Integer currentStock) { this.currentStock = currentStock; return this; }
            public StockInfoBuilder exists(Boolean exists) { this.exists = exists; return this; }
            public StockInfoBuilder timestamp(Long timestamp) { this.timestamp = timestamp; return this; }
            public StockInfo build() {
                StockInfo info = new StockInfo();
                info.productId = this.productId;
                info.currentStock = this.currentStock;
                info.exists = this.exists;
                info.timestamp = this.timestamp;
                return info;
            }
        }
    }
}
