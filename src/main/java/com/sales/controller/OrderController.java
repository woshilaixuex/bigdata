package com.sales.controller;

import com.sales.entity.Order;
import com.sales.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 订单处理模块 - 订单创建与存储（HBase）、订单状态实时更新（Redis）
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 创建订单（HBase + Redis状态）
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        try {
            Order createdOrder = orderService.createOrder(order);
            return ResponseEntity.ok(createdOrder);
        } catch (IOException e) {
            log.error("Failed to create order", e);
            return ResponseEntity.internalServerError().build();
        } catch (RuntimeException e) {
            log.error("Failed to create order: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取订单详情（HBase）
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        try {
            Order order = orderService.getOrderById(orderId);
            if (order != null) {
                return ResponseEntity.ok(order);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to get order: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取用户订单列表（HBase）
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getUserOrders(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Order> orders = orderService.getUserOrders(userId, limit);
            return ResponseEntity.ok(orders);
        } catch (IOException e) {
            log.error("Failed to get user orders: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新订单状态（HBase + Redis）
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<Void> updateOrderStatus(
            @PathVariable String orderId,
            @RequestParam Integer status) {
        try {
            orderService.updateOrderStatus(orderId, status);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to update order status: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
