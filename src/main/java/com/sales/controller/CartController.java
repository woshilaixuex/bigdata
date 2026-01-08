package com.sales.controller;

import com.sales.entity.CartItem;
import com.sales.service.CartService;
import com.sales.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车模块 - 用户购物车（Redis）
 */
@Slf4j
@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartService cartService;
    
    @Autowired
    private OrderService orderService;

    /**
     * 添加商品到购物车（Redis）
     */
    @PostMapping("/add")
    public ResponseEntity<Void> addToCart(@RequestBody CartItem cartItem) {
        try {
            cartService.addToCart(cartItem.getUserId(), cartItem);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to add to cart", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取用户购物车（Redis）
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<CartItem>> getCart(@PathVariable String userId) {
        try {
            List<CartItem> cartItems = cartService.getCart(userId);
            return ResponseEntity.ok(cartItems);
        } catch (Exception e) {
            log.error("Failed to get cart: userId={}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新购物车商品数量（Redis）
     */
    @PutMapping("/{userId}/items/{productId}")
    public ResponseEntity<Void> updateQuantity(
            @PathVariable String userId,
            @PathVariable String productId,
            @RequestParam Integer quantity) {
        try {
            cartService.updateQuantity(userId, productId, quantity);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to update cart quantity: userId={}, productId={}, quantity={}", 
                    userId, productId, quantity, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除购物车商品（Redis）
     */
    @DeleteMapping("/{userId}/items/{productId}")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable String userId,
            @PathVariable String productId) {
        try {
            cartService.removeFromCart(userId, productId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to remove from cart: userId={}, productId={}", userId, productId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清空购物车（Redis）
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> clearCart(@PathVariable String userId) {
        try {
            cartService.clearCart(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to clear cart: userId={}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 购物车结算
     */
    @PostMapping("/{userId}/checkout")
    public ResponseEntity<com.sales.entity.Order> checkout(@PathVariable String userId) {
        try {
            // 从购物车创建订单项
            List<com.sales.entity.Order.OrderItem> orderItems = cartService.createOrderItems(userId);
            if (orderItems.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            // 创建订单
            com.sales.entity.Order order = new com.sales.entity.Order();
            order.setUserId(userId);
            order.setItems(orderItems);
            order.setRemark("购物车结算");
            
            com.sales.entity.Order createdOrder = orderService.createOrder(order);
            
            // 注意：不再立即清空购物车，购物车在支付成功后清空
            // cartService.clearCart(userId);
            
            return ResponseEntity.ok(createdOrder);
        } catch (Exception e) {
            log.error("Failed to checkout cart: userId={}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取购物车商品数量
     */
    @GetMapping("/{userId}/count")
    public ResponseEntity<Integer> getCartCount(@PathVariable String userId) {
        try {
            int count = cartService.getCartItemCount(userId);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            log.error("Failed to get cart count: userId={}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 更新购物车商品选中状态
     */
    @PutMapping("/{userId}/items/{productId}/select")
    public ResponseEntity<Void> updateSelected(
            @PathVariable String userId,
            @PathVariable String productId,
            @RequestParam Boolean selected) {
        try {
            cartService.updateSelected(userId, productId, selected);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to update cart selected status: userId={}, productId={}, selected={}", 
                    userId, productId, selected, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
