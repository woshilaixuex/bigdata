package com.sales.controller;

import com.sales.service.RankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 排行榜模块 - 实时销售排行榜（Redis）
 */
@Slf4j
@RestController
@RequestMapping("/api/ranking")
public class RankingController {

    @Autowired
    private RankingService rankingService;

    /**
     * 增加商品销售分数（Redis）
     */
    @PostMapping("/sales/add")
    public ResponseEntity<Void> addSalesScore(
            @RequestParam String productId,
            @RequestParam double score) {
        try {
            rankingService.addSalesScore(productId, score);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to add sales score: productId={}, score={}", productId, score, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取日销售排行榜（Redis）
     */
    @GetMapping("/daily")
    public ResponseEntity<Set<Object>> getDailySalesRanking(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            Set<Object> ranking = rankingService.getDailySalesRanking(limit);
            return ResponseEntity.ok(ranking);
        } catch (Exception e) {
            log.error("Failed to get daily sales ranking", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取周销售排行榜（Redis）
     */
    @GetMapping("/weekly")
    public ResponseEntity<Set<Object>> getWeeklySalesRanking(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            Set<Object> ranking = rankingService.getWeeklySalesRanking(limit);
            return ResponseEntity.ok(ranking);
        } catch (Exception e) {
            log.error("Failed to get weekly sales ranking", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取热门商品排行榜（Redis）
     */
    @GetMapping("/hot")
    public ResponseEntity<Set<Object>> getHotProducts(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            Set<Object> hotProducts = rankingService.getHotProducts(limit);
            return ResponseEntity.ok(hotProducts);
        } catch (Exception e) {
            log.error("Failed to get hot products", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
