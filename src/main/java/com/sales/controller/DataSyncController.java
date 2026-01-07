package com.sales.controller;

import com.sales.service.DataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * 数据同步模块 - Redis和HBase数据同步
 */
@Slf4j
@RestController
@RequestMapping("/api/sync")
public class DataSyncController {

    @Autowired
    private DataSyncService dataSyncService;

    /**
     * 手动触发库存同步（Redis -> HBase）
     */
    @PostMapping("/stock/{productId}")
    public ResponseEntity<String> syncStock(@PathVariable String productId, @RequestParam int delta) {
        try {
            CompletableFuture<Void> future = dataSyncService.syncStockToHBase(productId, delta);
            future.get(); // 等待完成
            return ResponseEntity.ok("Stock sync completed");
        } catch (Exception e) {
            log.error("Failed to sync stock: productId={}", productId, e);
            return ResponseEntity.internalServerError().body("Stock sync failed");
        }
    }

    /**
     * 手动触发商品缓存同步（HBase -> Redis）
     */
    @PostMapping("/product/{productId}")
    public ResponseEntity<String> syncProduct(@PathVariable String productId) {
        try {
            CompletableFuture<Void> future = dataSyncService.rebuildProductCache(productId);
            future.get(); // 等待完成
            return ResponseEntity.ok("Product cache sync completed");
        } catch (Exception e) {
            log.error("Failed to sync product: productId={}", productId, e);
            return ResponseEntity.internalServerError().body("Product sync failed");
        }
    }
}
