package com.sales.controller;

import com.sales.entity.CartItem;
import com.sales.service.CartService;
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
}
