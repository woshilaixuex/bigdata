package com.sales.controller;

import com.sales.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 库存管理模块 - 商品库存实时更新（Redis）
 */
@Slf4j
@RestController
@RequestMapping("/api/stock")
public class StockController {

    @Autowired
    private StockService stockService;

    /**
     * 设置商品库存（Redis）
     */
    @PostMapping("/set")
    public ResponseEntity<Void> setStock(
            @RequestParam String productId,
            @RequestParam int stock) {
        try {
            stockService.setStock(productId, stock);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to set stock: productId={}, stock={}", productId, stock, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取商品库存（Redis）
     */
    @GetMapping("/{productId}")
    public ResponseEntity<Integer> getStock(@PathVariable String productId) {
        try {
            int stock = stockService.getStock(productId);
            return ResponseEntity.ok(stock);
        } catch (Exception e) {
            log.error("Failed to get stock: productId={}", productId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 增加库存（Redis）
     */
    @PostMapping("/{productId}/increase")
    public ResponseEntity<Long> increaseStock(
            @PathVariable String productId,
            @RequestParam int delta) {
        try {
            long newStock = stockService.increaseStock(productId, delta);
            return ResponseEntity.ok(newStock);
        } catch (Exception e) {
            log.error("Failed to increase stock: productId={}, delta={}", productId, delta, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 原子性扣减库存（Redis）
     */
    @PostMapping("/{productId}/deduct")
    public ResponseEntity<Boolean> deductStock(
            @PathVariable String productId,
            @RequestParam int quantity) {
        try {
            boolean success = stockService.deductStock(productId, quantity);
            return ResponseEntity.ok(success);
        } catch (Exception e) {
            log.error("Failed to deduct stock: productId={}, quantity={}", productId, quantity, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 设置秒杀库存（Redis）
     */
    @PostMapping("/seckill/set")
    public ResponseEntity<Void> setSeckillStock(
            @RequestParam String seckillId,
            @RequestParam String productId,
            @RequestParam int stock) {
        try {
            stockService.setSeckillStock(seckillId, productId, stock);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to set seckill stock: seckillId={}, productId={}, stock={}", 
                    seckillId, productId, stock, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 扣减秒杀库存（Redis）
     */
    @PostMapping("/seckill/{seckillId}/{productId}/deduct")
    public ResponseEntity<Boolean> deductSeckillStock(
            @PathVariable String seckillId,
            @PathVariable String productId,
            @RequestParam int quantity) {
        try {
            boolean success = stockService.deductSeckillStock(seckillId, productId, quantity);
            return ResponseEntity.ok(success);
        } catch (Exception e) {
            log.error("Failed to deduct seckill stock: seckillId={}, productId={}, quantity={}", 
                    seckillId, productId, quantity, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
