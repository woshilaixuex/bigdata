package com.sales.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sales.config.RedisConfig;
import com.sales.entity.CartItem;
import com.sales.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CartService {

    @Autowired
    private RedisService redisService;
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private StockService stockService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long CART_EXPIRE_TIME = 7; // 7天

    /**
     * 添加商品到购物车
     */
    public void addToCart(String userId, CartItem cartItem) throws IOException {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        
        // 检查商品是否存在
        Product product = productService.getProductById(cartItem.getProductId());
        if (product == null || product.getStatus() != 1) {
            throw new RuntimeException("商品不存在或已下架");
        }
        
        // 检查购物车中是否已存在该商品
        int currentQuantity = getProductQuantity(userId, cartItem.getProductId());
        int totalQuantity = currentQuantity + cartItem.getQuantity();
        
        // 立即扣减库存（新增部分）
        if (!stockService.deductStock(cartItem.getProductId(), cartItem.getQuantity())) {
            throw new RuntimeException("库存不足，无法添加到购物车");
        }
        
        try {
            // 构建购物车项JSON
            CartItemData cartItemData = CartItemData.builder()
                    .productId(cartItem.getProductId())
                    .quantity(totalQuantity)
                    .addTime(System.currentTimeMillis() / 1000)
                    .selected(cartItem.getSelected() != null ? cartItem.getSelected() : true)
                    .build();
            
            String cartItemJson = objectMapper.writeValueAsString(cartItemData);
            
            // 添加到Redis Hash
            redisService.hset(cartKey, cartItem.getProductId(), cartItemJson);
            
            // 设置过期时间
            redisService.expire(cartKey, CART_EXPIRE_TIME, TimeUnit.DAYS);
            
            log.info("Added to cart: userId={}, productId={}, quantity={}, totalQuantity={}", 
                    userId, cartItem.getProductId(), cartItem.getQuantity(), totalQuantity);
        } catch (Exception e) {
            // 回滚库存
            stockService.increaseStock(cartItem.getProductId(), cartItem.getQuantity());
            log.error("Failed to add to cart, rolled back stock", e);
            throw new RuntimeException("添加购物车失败", e);
        }
    }

    /**
     * 获取用户购物车
     */
    public List<CartItem> getCart(String userId) {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        Map<Object, Object> cartMap = redisService.hgetAll(cartKey);
        
        List<CartItem> cartItems = new ArrayList<>();
        if (cartMap != null) {
            for (Map.Entry<Object, Object> entry : cartMap.entrySet()) {
                String productId = (String) entry.getKey();
                String cartItemJson = (String) entry.getValue();
                
                try {
                    // 解析JSON
                    CartItemData cartItemData = objectMapper.readValue(cartItemJson, CartItemData.class);
                    
                    // 获取商品信息
                    Product product = productService.getProductById(productId);
                    if (product == null || product.getStatus() != 1) {
                        // 商品不存在或已下架，从购物车中移除
                        removeFromCart(userId, productId);
                        continue;
                    }
                    
                    // 检查库存
                    int currentStock = stockService.getStock(productId);
                    if (currentStock < cartItemData.getQuantity()) {
                        // 库存不足，调整数量
                        if (currentStock > 0) {
                            cartItemData.setQuantity(currentStock);
                            updateQuantity(userId, productId, currentStock);
                        } else {
                            // 库存为0，移除商品
                            removeFromCart(userId, productId);
                            continue;
                        }
                    }
                    
                    CartItem cartItem = CartItem.builder()
                            .userId(userId)
                            .productId(productId)
                            .quantity(cartItemData.getQuantity())
                            .selected(cartItemData.getSelected())
                            .productName(product.getName())
                            .price(product.getPrice())
                            .image(product.getImages() != null && !product.getImages().isEmpty() ? product.getImages().get(0) : null)
                            .build();
                    cartItems.add(cartItem);
                } catch (Exception e) {
                    log.error("Failed to parse cart item: {}", cartItemJson, e);
                    // 移除无效数据
                    removeFromCart(userId, productId);
                }
            }
        }
        
        return cartItems;
    }

    /**
     * 更新购物车商品数量
     */
    public void updateQuantity(String userId, String productId, Integer quantity) throws IOException {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        
        // 检查商品是否存在
        Product product = productService.getProductById(productId);
        if (product == null || product.getStatus() != 1) {
            throw new RuntimeException("商品不存在或已下架");
        }
        
        // 获取当前数量
        int currentQuantity = getProductQuantity(userId, productId);
        
        if (quantity <= 0) {
            // 数量小于等于0，删除商品并归还全部库存
            removeFromCart(userId, productId);
            return;
        } else {
            // 计算数量变化
            int quantityDiff = quantity - currentQuantity;
            
            if (quantityDiff > 0) {
                // 增加数量，需要扣减库存
                if (!stockService.deductStock(productId, quantityDiff)) {
                    throw new RuntimeException("库存不足，无法增加数量");
                }
            } else if (quantityDiff < 0) {
                // 减少数量，需要归还库存
                stockService.increaseStock(productId, -quantityDiff);
                log.info("Returned stock to inventory: productId={}, returnedQuantity={}", productId, -quantityDiff);
            }
            
            try {
                // 更新数量
                CartItemData cartItemData = CartItemData.builder()
                        .productId(productId)
                        .quantity(quantity)
                        .addTime(System.currentTimeMillis() / 1000)
                        .selected(true)
                        .build();
                
                String cartItemJson = objectMapper.writeValueAsString(cartItemData);
                redisService.hset(cartKey, productId, cartItemJson);
            } catch (Exception e) {
                // 回滚库存变化
                if (quantityDiff > 0) {
                    stockService.increaseStock(productId, quantityDiff);
                } else if (quantityDiff < 0) {
                    stockService.deductStock(productId, -quantityDiff);
                }
                log.error("Failed to update cart quantity, rolled back stock", e);
                throw new RuntimeException("更新购物车数量失败", e);
            }
        }
        
        log.info("Updated cart quantity: userId={}, productId={}, oldQuantity={}, newQuantity={}", 
                userId, productId, currentQuantity, quantity);
    }

    /**
     * 删除购物车商品
     */
    public void removeFromCart(String userId, String productId) {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        
        // 获取当前商品数量以便归还库存
        int currentQuantity = getProductQuantity(userId, productId);
        
        // 从购物车中移除商品
        redisService.hdel(cartKey, productId);
        
        // 归还库存
        if (currentQuantity > 0) {
            stockService.increaseStock(productId, currentQuantity);
            log.info("Returned stock to inventory: productId={}, quantity={}", productId, currentQuantity);
        }
        
        log.info("Removed from cart: userId={}, productId={}, returnedQuantity={}", userId, productId, currentQuantity);
    }

    /**
     * 清空购物车
     */
    public void clearCart(String userId) {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        
        // 获取购物车中所有商品以便归还库存
        List<CartItem> cartItems = getCart(userId);
        
        // 归还所有商品的库存
        for (CartItem item : cartItems) {
            if (item.getQuantity() > 0) {
                stockService.increaseStock(item.getProductId(), item.getQuantity());
                log.info("Returned stock to inventory: productId={}, quantity={}", 
                        item.getProductId(), item.getQuantity());
            }
        }
        
        // 清空购物车
        redisService.del(cartKey);
        
        log.info("Cleared cart: userId={}, returnedItemsCount={}", userId, cartItems.size());
    }

    /**
     * 获取购物车商品数量
     */
    public int getCartItemCount(String userId) {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        Map<Object, Object> cartMap = redisService.hgetAll(cartKey);
        
        if (cartMap == null) {
            return 0;
        }
        
        int totalCount = 0;
        for (Object value : cartMap.values()) {
            try {
                String cartItemJson = (String) value;
                CartItemData cartItemData = objectMapper.readValue(cartItemJson, CartItemData.class);
                totalCount += cartItemData.getQuantity();
            } catch (Exception e) {
                log.error("Failed to parse cart item quantity: {}", value, e);
            }
        }
        
        return totalCount;
    }

    /**
     * 检查购物车中是否存在商品
     */
    public boolean existsInCart(String userId, String productId) {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        return redisService.hexists(cartKey, productId);
    }

    /**
     * 获取购物车中商品数量
     */
    public int getProductQuantity(String userId, String productId) {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        String cartItemJson = (String) redisService.hget(cartKey, productId);
        
        if (cartItemJson == null) {
            return 0;
        }
        
        try {
            CartItemData cartItemData = objectMapper.readValue(cartItemJson, CartItemData.class);
            return cartItemData.getQuantity();
        } catch (Exception e) {
            log.error("Failed to parse cart item quantity: {}", cartItemJson, e);
            return 0;
        }
    }
    
    /**
     * 从购物车创建订单项
     */
    public List<com.sales.entity.Order.OrderItem> createOrderItems(String userId) throws IOException {
        List<CartItem> cartItems = getCart(userId);
        List<com.sales.entity.Order.OrderItem> orderItems = new ArrayList<>();
        
        for (CartItem cartItem : cartItems) {
            if (cartItem.getSelected() != null && cartItem.getSelected()) {
                Product product = productService.getProductById(cartItem.getProductId());
                if (product != null) {
                    com.sales.entity.Order.OrderItem orderItem = new com.sales.entity.Order.OrderItem();
                    orderItem.setProductId(cartItem.getProductId());
                    orderItem.setProductName(product.getName());
                    orderItem.setQuantity(cartItem.getQuantity());
                    orderItem.setPrice(product.getPrice());
                    orderItem.setAmount(product.getPrice().multiply(new java.math.BigDecimal(cartItem.getQuantity())));
                    orderItems.add(orderItem);
                }
            }
        }
        
        return orderItems;
    }
    
    /**
     * 更新购物车商品选中状态
     */
    public void updateSelected(String userId, String productId, Boolean selected) {
        String cartKey = RedisConfig.RedisKeys.CART_PREFIX + userId;
        String cartItemJson = (String) redisService.hget(cartKey, productId);
        
        if (cartItemJson == null) {
            throw new RuntimeException("购物车中不存在该商品");
        }
        
        try {
            CartItemData cartItemData = objectMapper.readValue(cartItemJson, CartItemData.class);
            cartItemData.setSelected(selected);
            
            String updatedJson = objectMapper.writeValueAsString(cartItemData);
            redisService.hset(cartKey, productId, updatedJson);
            
            log.info("Updated cart selected status: userId={}, productId={}, selected={}", 
                    userId, productId, selected);
        } catch (Exception e) {
            log.error("Failed to update cart selected status", e);
            throw new RuntimeException("更新选中状态失败", e);
        }
    }
    
    /**
     * 购物车数据内部类
     */
    public static class CartItemData {
        private String productId;
        private Integer quantity;
        private Long addTime;
        private Boolean selected;
        
        public static CartItemDataBuilder builder() {
            return new CartItemDataBuilder();
        }
        
        // Getters and Setters
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Long getAddTime() { return addTime; }
        public void setAddTime(Long addTime) { this.addTime = addTime; }
        public Boolean getSelected() { return selected; }
        public void setSelected(Boolean selected) { this.selected = selected; }
        
        public static class CartItemDataBuilder {
            private String productId;
            private Integer quantity;
            private Long addTime;
            private Boolean selected = true;
            
            public CartItemDataBuilder productId(String productId) { this.productId = productId; return this; }
            public CartItemDataBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
            public CartItemDataBuilder addTime(Long addTime) { this.addTime = addTime; return this; }
            public CartItemDataBuilder selected(Boolean selected) { this.selected = selected; return this; }
            public CartItemData build() {
                CartItemData data = new CartItemData();
                data.productId = this.productId;
                data.quantity = this.quantity;
                data.addTime = this.addTime;
                data.selected = this.selected;
                return data;
            }
        }
    }
}
