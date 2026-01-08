package com.sales.controller;

import com.sales.service.RankingService;
import com.sales.service.RedisService;
import com.sales.config.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 仪表板数据控制器 - 提供实时仪表板数据
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private RedisService redisService;

    @Autowired
    private RankingService rankingService;

    /**
     * 获取仪表板实时数据
     */
    @GetMapping("/realtime")
    public ResponseEntity<Map<String, Object>> getRealtimeDashboard() {
        try {
            Map<String, Object> dashboard = new HashMap<>();
            
            // 获取今日销售统计数据
            String dateKey = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String dashboardKey = RedisConfig.RedisKeys.DASHBOARD_PREFIX + dateKey;
            
            // 从Redis Hash获取今日数据
            Map<Object, Object> todayStats = redisService.hgetAll(dashboardKey);
            
            // 今日销售金额
            Object totalAmountObj = todayStats.get("total_amount");
            double totalAmount = totalAmountObj != null ? Double.parseDouble(String.valueOf(totalAmountObj)) : 0.0;
            
            // 今日订单数量
            Object orderCountObj = todayStats.get("order_count");
            int orderCount = orderCountObj != null ? Integer.parseInt(String.valueOf(orderCountObj)) : 0;
            
            // 从独立计数器获取数据（作为备选）
            if (totalAmount == 0.0) {
                String salesToday = (String) redisService.get(RedisConfig.RedisKeys.STAT_SALES_TODAY);
                totalAmount = salesToday != null ? Double.parseDouble(salesToday) : 0.0;
            }
            
            if (orderCount == 0) {
                String ordersToday = (String)  redisService.get(RedisConfig.RedisKeys.STAT_ORDERS_TODAY);
                orderCount = ordersToday != null ? Integer.parseInt(ordersToday) : 0;
            }
            
            // 计算平均客单价
            double avgPrice = orderCount > 0 ? totalAmount / orderCount : 0.0;
            
            // 模拟今日活跃用户数（可以从Redis统计中获取）
            int userCount = (int) (orderCount * 0.65 + Math.random() * 50);
            
            dashboard.put("totalAmount", totalAmount);
            dashboard.put("orderCount", orderCount);
            dashboard.put("userCount", userCount);
            dashboard.put("avgPrice", avgPrice);
            
            log.info("Dashboard data retrieved: totalAmount={}, orderCount={}, userCount={}, avgPrice={}", 
                    totalAmount, orderCount, userCount, avgPrice);
            
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            log.error("Failed to get dashboard data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取热门商品列表
     */
    @GetMapping("/hot-products")
    public ResponseEntity<Set<Object>> getHotProducts(@RequestParam(defaultValue = "4") int limit) {
        try {
            Set<Object> hotProducts = rankingService.getHotProducts(limit);
            return ResponseEntity.ok(hotProducts);
        } catch (Exception e) {
            log.error("Failed to get hot products", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
