package com.sales.controller;

import com.sales.service.SalesAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售分析模块 - 实时销售看板（Redis）、历史销售数据分析（HBase）
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
public class SalesAnalysisController {

    @Autowired
    private SalesAnalysisService salesAnalysisService;

    /**
     * 获取实时销售看板数据（Redis）
     */
    @GetMapping("/dashboard")
    public ResponseEntity<SalesAnalysisService.DashboardData> getDashboardData() {
        try {
            SalesAnalysisService.DashboardData dashboardData = salesAnalysisService.getDashboardData();
            return ResponseEntity.ok(dashboardData);
        } catch (IOException e) {
            log.error("Failed to get dashboard data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取日销售数据（HBase）
     */
    @GetMapping("/daily/{date}")
    public ResponseEntity<com.sales.entity.SalesData> getDailySalesData(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        try {
            com.sales.entity.SalesData salesData = salesAnalysisService.getDailySalesData(date);
            if (salesData != null) {
                return ResponseEntity.ok(salesData);
            } else {
                return ResponseEntity.ok(com.sales.entity.SalesData.builder()
                        .date(date)
                        .saleCount(0L)
                        .saleAmount(BigDecimal.ZERO)
                        .refundCount(0L)
                        .refundAmount(BigDecimal.ZERO)
                        .build());
            }
        } catch (IOException e) {
            log.error("Failed to get daily sales data: date={}", date, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取日期范围内的销售数据（HBase）
     */
    @GetMapping("/range")
    public ResponseEntity<List<com.sales.entity.SalesData>> getSalesDataByDateRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        try {
            List<com.sales.entity.SalesData> salesDataList = salesAnalysisService.getSalesDataByDateRange(startDate, endDate);
            return ResponseEntity.ok(salesDataList);
        } catch (IOException e) {
            log.error("Failed to get sales data by date range: {} to {}", startDate, endDate, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 记录销售数据（内部接口，HBase）
     */
    @PostMapping("/record-sales")
    public ResponseEntity<Void> recordSales(
            @RequestParam String productId,
            @RequestParam String categoryId,
            @RequestParam Long quantity,
            @RequestParam java.math.BigDecimal amount) {
        try {
            salesAnalysisService.recordSales(productId, categoryId, quantity, amount);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to record sales: productId={}, quantity={}, amount={}", productId, quantity, amount, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
