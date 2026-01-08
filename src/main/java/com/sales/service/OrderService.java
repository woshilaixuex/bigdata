package com.sales.service;

import com.sales.config.RedisConfig;
import com.sales.entity.Order;
import com.sales.entity.Product;
import com.sales.repository.OrderRepository;
import com.sales.repository.ProductRepository;
import com.sales.service.CartService;
import com.sales.service.RankingService;
import com.sales.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private StockService stockService;

    @Autowired
    private CartService cartService;
    
    @Autowired
    private RankingService rankingService;

    @Autowired
    private RedisService redisService;

    private static final long ORDER_STATUS_EXPIRE_DAYS = 7;

    /**
     * 创建订单
     */
    @Transactional
    public Order createOrder(Order order) throws IOException {
        if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
            order.setOrderId(generateOrderId());
        }

        if (order.getCreateTime() == null) {
            order.setCreateTime(LocalDateTime.now());
        }
        if (order.getStatus() == null) {
            order.setStatus(Order.Status.PENDING_PAYMENT.getCode());
        }
        if (order.getDiscountAmount() == null) {
            order.setDiscountAmount(BigDecimal.ZERO);
        }
        calculateOrderAmount(order);

        // 库存已在加入购物车时扣减，这里不再需要锁定库存

        orderRepository.save(order);

        cacheOrderStatus(order.getOrderId(), order.getStatus());

        // 注意：这里不再清空购物车，因为库存已扣减，购物车应在支付成功后清空
        // if (order.getUserId() != null) {
        //     cartService.clearCart(order.getUserId());
        // }
        
        // 设置订单来源为购物车
        if (order.getRemark() == null || order.getRemark().isEmpty()) {
            order.setRemark("购物车结算");
        }

        log.info("Order created: {}", order.getOrderId());
        return order;
    }

    /**
     * 根据ID获取订单
     */
    public Order getOrderById(String orderId) throws IOException {
        Order order = orderRepository.findById(orderId);
        applyRedisStatusIfPresent(order);
        return order;
    }

    /**
     * 获取用户订单列表
     */
    public List<Order> getUserOrders(String userId, int limit) throws IOException {
        List<Order> orders = orderRepository.findByUserId(userId, limit);
        applyRedisStatusIfPresent(orders);
        return orders;
    }

    /**
     * 获取订单列表（按状态）
     */
    public List<Order> getOrdersByStatus(Integer status, int limit) throws IOException {
        List<Order> orders = orderRepository.findByStatus(status, limit);
        applyRedisStatusIfPresent(orders);
        return orders;
    }

    /**
     * 获取最近订单
     */
    public List<Order> getRecentOrders(int limit) throws IOException {
        List<Order> orders = orderRepository.findRecentOrders(limit);
        applyRedisStatusIfPresent(orders);
        return orders;
    }

    /**
     * 支付订单
     */
    @Transactional
    public boolean payOrder(String orderId, String payMethod) throws IOException {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            log.error("Order not found: {}", orderId);
            return false;
        }

        if (!order.canPay()) {
            log.error("Order cannot be paid: {}, status={}", orderId, order.getStatus());
            return false;
        }

        // 更新订单状态
        order.setStatus(Order.Status.PENDING_DELIVERY.getCode());
        order.setPayMethod(payMethod);
        order.setPayTime(LocalDateTime.now());
        
        orderRepository.save(order);

        // 订单状态写入Redis（实时）
        cacheOrderStatus(order.getOrderId(), order.getStatus());

        // 库存已在加入购物车时扣减，这里不再需要扣减库存
        // deductOrderStock(order);

        // 实时销售看板/统计（Redis）
        updateRealtimeMetricsOnPaid(order);

        // 支付成功后清空购物车
        if (order.getUserId() != null) {
            cartService.clearCart(order.getUserId());
        }

        log.info("Order paid: {}", orderId);
        return true;
    }

    private void updateRealtimeMetricsOnPaid(Order order) {
        if (order == null) {
            return;
        }

        BigDecimal actualAmount = order.getActualAmount() != null ? order.getActualAmount() : BigDecimal.ZERO;

        // 检查是否已经统计过（避免重复统计）
        String orderStatsKey = RedisConfig.RedisKeys.ORDER_STATS_PREFIX + order.getOrderId();
        Boolean alreadyCounted = (Boolean) redisService.get(orderStatsKey);
        
        if (alreadyCounted != null && alreadyCounted) {
            log.info("Order already counted in stats: {}", order.getOrderId());
            return;
        }

        // 今日计数器
        redisService.incr(RedisConfig.RedisKeys.STAT_ORDERS_TODAY, 1);
        redisService.expire(RedisConfig.RedisKeys.STAT_ORDERS_TODAY, 3600, TimeUnit.SECONDS);

        redisService.incrByFloat(RedisConfig.RedisKeys.STAT_SALES_TODAY, actualAmount.doubleValue());
        redisService.expire(RedisConfig.RedisKeys.STAT_SALES_TODAY, 3600, TimeUnit.SECONDS);

        // 今日看板 Hash：dashboard:{yyyyMMdd}
        String dateKey = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String dashboardKey = RedisConfig.RedisKeys.DASHBOARD_PREFIX + dateKey;
        redisService.hincrByFloat(dashboardKey, "total_amount", actualAmount.doubleValue());
        redisService.hincrBy(dashboardKey, "order_count", 1);
        redisService.expire(dashboardKey, 3600, TimeUnit.SECONDS);

        // 热门商品：按订单金额/数量加权
        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                if (item == null || item.getProductId() == null) {
                    continue;
                }
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                if (qty > 0) {
                    rankingService.addSalesScore(item.getProductId(), qty);
                }
                BigDecimal itemAmount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                rankingService.addPurchaseScore(item.getProductId(), itemAmount.doubleValue());
            }
        }
        
        // 标记该订单已统计过
        redisService.set(orderStatsKey, true, 7, TimeUnit.DAYS);
        
        log.info("Order stats updated: orderId={}, amount={}", order.getOrderId(), actualAmount);
    }

    /**
     * 发货
     */
    public boolean deliverOrder(String orderId, String expressCompany, String expressNo) throws IOException {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            log.error("Order not found: {}", orderId);
            return false;
        }

        if (!order.canDeliver()) {
            log.error("Order cannot be delivered: {}, status={}", orderId, order.getStatus());
            return false;
        }

        // 更新订单状态和物流信息
        order.setStatus(Order.Status.SHIPPED.getCode());
        order.setExpressCompany(expressCompany);
        order.setExpressNo(expressNo);
        order.setDeliverTime(LocalDateTime.now());
        
        orderRepository.save(order);

        // 订单状态写入Redis（实时）
        cacheOrderStatus(order.getOrderId(), order.getStatus());

        log.info("Order delivered: {}, express: {} {}", orderId, expressCompany, expressNo);
        return true;
    }

    /**
     * 确认收货
     */
    public boolean completeOrder(String orderId) throws IOException {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            log.error("Order not found: {}", orderId);
            return false;
        }

        if (!order.canComplete()) {
            log.error("Order cannot be completed: {}, status={}", orderId, order.getStatus());
            return false;
        }

        // 更新订单状态
        order.setStatus(Order.Status.COMPLETED.getCode());
        order.setCompleteTime(LocalDateTime.now());
        
        orderRepository.save(order);

        // 订单状态写入Redis（实时）
        cacheOrderStatus(order.getOrderId(), order.getStatus());

        // 增加商品销量
        increaseProductSales(order);
        
        // 更新热销榜单
        updateHotRanking(order);

        log.info("Order completed: {}", orderId);
        return true;
    }

    /**
     * 取消订单
     */
    @Transactional
    public boolean cancelOrder(String orderId) throws IOException {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            log.error("Order not found: {}", orderId);
            return false;
        }

        if (!order.canCancel()) {
            log.error("Order cannot be cancelled: {}, status={}", orderId, order.getStatus());
            return false;
        }

        // 更新订单状态
        order.setStatus(Order.Status.CANCELLED.getCode());
        orderRepository.save(order);

        // 订单状态写入Redis（实时）
        cacheOrderStatus(order.getOrderId(), order.getStatus());

        // 注意：库存已在购物车中预扣，取消订单不需要额外处理库存
        // 用户可以通过购物车重新操作或清空购物车来归还库存
        // releaseOrderStock(order);

        log.info("Order cancelled: {}", orderId);
        return true;
    }
    
    /**
     * 删除订单（物理删除）
     */
    public void deleteOrder(String orderId) throws IOException {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            log.error("Order not found for deletion: {}", orderId);
            return;
        }
        
        // 如果订单已支付，需要释放库存
        if (order.getStatus() >= Order.Status.PENDING_DELIVERY.getCode()) {
            releaseOrderStock(order);
        }
        
        // 删除订单
        orderRepository.delete(orderId);
        
        // 清理Redis缓存
        String statusKey = RedisConfig.RedisKeys.ORDER_STATUS_PREFIX + orderId;
        redisService.del(statusKey);
        
        // 清理统计标记
        String statsKey = RedisConfig.RedisKeys.ORDER_STATS_PREFIX + orderId;
        redisService.del(statsKey);
        
        log.info("Order deleted: {}", orderId);
    }

    /**
     * 更新订单状态
     */
    public void updateOrderStatus(String orderId, Integer status) throws IOException {
        // 获取更新前的订单信息
        Order oldOrder = orderRepository.findById(orderId);
        Integer oldStatus = oldOrder != null ? oldOrder.getStatus() : null;
        
        // 更新订单状态
        orderRepository.updateStatus(orderId, status);

        // 订单状态写入Redis（实时）
        cacheOrderStatus(orderId, status);
        
        // 如果更新为已完成状态，直接更新仪表盘统计
        if (status.equals(Order.Status.COMPLETED.getCode())) {
            
            // 检查是否已经统计过（避免重复统计）
            String orderStatsKey = RedisConfig.RedisKeys.ORDER_STATS_PREFIX + orderId;
            Boolean alreadyCounted = (Boolean) redisService.get(orderStatsKey);
            
            if (alreadyCounted == null || !alreadyCounted) {
                // 获取完整订单信息进行统计
                Order order = orderRepository.findById(orderId);
                if (order != null && order.getTotalAmount() != null) {
                    // 直接使用订单金额进行统计
                    BigDecimal originalActualAmount = order.getActualAmount();
                    order.setActualAmount(order.getTotalAmount());
                    
                    // 更新仪表板统计
                    updateRealtimeMetricsOnPaid(order);
                    
                    // 恢复原来的实际金额
                    order.setActualAmount(originalActualAmount);
                    
                    // 标记该订单已统计过
                    redisService.set(orderStatsKey, true, 7, TimeUnit.DAYS);
                    
                    // 更新热销榜单
                    updateHotRanking(order);
                    
                    log.info("Order completion stats updated: {}, amount={}", orderId, order.getTotalAmount());
                }
            }
        }
        
        log.info("Order status updated: {} -> {}, old status: {}", orderId, status, oldStatus);
    }

    private void cacheOrderStatus(String orderId, Integer status) {
        if (orderId == null || orderId.isEmpty() || status == null) {
            return;
        }
        String key = RedisConfig.RedisKeys.ORDER_STATUS_PREFIX + orderId;
        redisService.set(key, String.valueOf(status), ORDER_STATUS_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    private Integer getCachedOrderStatus(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            return null;
        }
        String key = RedisConfig.RedisKeys.ORDER_STATUS_PREFIX + orderId;
        Object obj = redisService.get(key);
        if (obj == null) {
            return null;
        }
        try {
            if (obj instanceof Integer) {
                return (Integer) obj;
            }
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyRedisStatusIfPresent(Order order) {
        if (order == null) {
            return;
        }
        Integer cached = getCachedOrderStatus(order.getOrderId());
        if (cached != null) {
            order.setStatus(cached);
        }
    }

    private void applyRedisStatusIfPresent(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (Order order : orders) {
            applyRedisStatusIfPresent(order);
        }
    }

    /**
     * 更新物流信息
     */
    public void updateLogistics(String orderId, String expressCompany, String expressNo) throws IOException {
        orderRepository.updateLogistics(orderId, expressCompany, expressNo);
        log.info("Order logistics updated: {} -> {} {}", orderId, expressCompany, expressNo);
    }

    /**
     * 检查订单是否存在
     */
    public boolean orderExists(String orderId) throws IOException {
        return orderRepository.existsById(orderId);
    }

    /**
     * 获取订单统计信息
     */
    public OrderStats getOrderStats() throws IOException {
        long pendingPaymentCount = orderRepository.countByStatus(Order.Status.PENDING_PAYMENT.getCode());
        long pendingDeliveryCount = orderRepository.countByStatus(Order.Status.PENDING_DELIVERY.getCode());
        long shippedCount = orderRepository.countByStatus(Order.Status.SHIPPED.getCode());
        long completedCount = orderRepository.countByStatus(Order.Status.COMPLETED.getCode());

        return OrderStats.builder()
                .pendingPaymentCount((int) pendingPaymentCount)
                .pendingDeliveryCount((int) pendingDeliveryCount)
                .shippedCount((int) shippedCount)
                .completedCount((int) completedCount)
                .build();
    }

    /**
     * 计算订单金额
     */
    private void calculateOrderAmount(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            order.setTotalAmount(BigDecimal.ZERO);
            order.setActualAmount(BigDecimal.ZERO);
            return;
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Order.OrderItem item : order.getItems()) {
            if (item.getAmount() == null) {
                // 计算小计金额
                BigDecimal itemAmount = item.getPrice().multiply(new BigDecimal(item.getQuantity()));
                item.setAmount(itemAmount);
            }
            totalAmount = totalAmount.add(item.getAmount());
        }

        order.setTotalAmount(totalAmount);
        
        // 计算实付金额
        BigDecimal discountAmount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        order.setActualAmount(totalAmount.subtract(discountAmount));
    }

    /**
     * 锁定订单库存
     */
    private boolean lockOrderStock(Order order) throws IOException {
        if (order.getItems() == null) {
            return true;
        }

        for (Order.OrderItem item : order.getItems()) {
            boolean locked = stockService.lockStock(item.getProductId(), item.getQuantity());
            if (!locked) {
                // 回滚已锁定的库存
                rollbackLockedStock(order);
                return false;
            }
        }

        return true;
    }

    /**
     * 扣减订单库存
     */
    private void deductOrderStock(Order order) throws IOException {
        if (order.getItems() == null) {
            return;
        }

        for (Order.OrderItem item : order.getItems()) {
            productService.deductStock(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 释放订单库存
     */
    private void releaseOrderStock(Order order) throws IOException {
        if (order.getItems() == null) {
            return;
        }

        for (Order.OrderItem item : order.getItems()) {
            productService.increaseStock(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 回滚已锁定的库存
     */
    private void rollbackLockedStock(Order order) throws IOException {
        if (order.getItems() == null) {
            return;
        }

        for (Order.OrderItem item : order.getItems()) {
            stockService.releaseStock(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 更新热销榜单
     */
    private void updateHotRanking(Order order) throws IOException {
        if (order.getItems() == null) {
            return;
        }

        for (Order.OrderItem item : order.getItems()) {
            String productId = item.getProductId();
            double amount = item.getAmount() != null ? item.getAmount().doubleValue() : 0.0;
            
            // 增加日销售排行榜分数
            rankingService.addSalesScore(productId, amount);
            
            // 增加周销售排行榜分数
            rankingService.addWeeklySalesScore(productId, amount);
            
            // 增加月销售排行榜分数
            rankingService.addMonthlySalesScore(productId, amount);
            
            // 增加热门商品分数（基于购买金额）
            rankingService.addPurchaseScore(productId, amount);
            
            log.info("Updated hot ranking for completed order: productId={}, amount={}", productId, amount);
        }
    }

    /**
     * 增加商品销量
     */
    private void increaseProductSales(Order order) throws IOException {
        if (order.getItems() == null) {
            return;
        }

        for (Order.OrderItem item : order.getItems()) {
            productService.incrementSaleCount(item.getProductId(), (long) item.getQuantity());
        }
    }

    /**
     * 生成订单ID
     */
    private String generateOrderId() {
        return "ORD" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + 
               UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    /**
     * 订单统计信息
     */
    @lombok.Data
    @lombok.Builder
    public static class OrderStats {
        private int pendingPaymentCount;
        private int pendingDeliveryCount;
        private int shippedCount;
        private int completedCount;
    }
}
