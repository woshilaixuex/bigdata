package com.sales.controller;

import com.sales.entity.Product;
import com.sales.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 商品管理模块 - 商品信息存储（HBase）
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 创建商品（HBase）
     */
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        try {
            Product createdProduct = productService.createProduct(product);
            return ResponseEntity.ok(createdProduct);
        } catch (IOException e) {
            log.error("Failed to create product", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取商品详情（HBase）
     */
    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable String productId) {
        try {
            Product product = productService.getProductById(productId);
            if (product != null) {
                productService.incrementViewCount(productId);
                return ResponseEntity.ok(product);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            log.error("Failed to get product: {}", productId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有商品（HBase）
     */
    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts(
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Product> products = productService.getAllProducts(limit);
            return ResponseEntity.ok(products);
        } catch (IOException e) {
            log.error("Failed to get all products", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据分类获取商品（HBase）
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Product>> getProductsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Product> products = productService.getProductsByCategory(category, limit);
            return ResponseEntity.ok(products);
        } catch (IOException e) {
            log.error("Failed to get products by category: {}", category, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新商品信息（HBase）
     */
    @PutMapping("/{productId}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable String productId,
            @RequestBody Product product) {
        try {
            product.setProductId(productId);
            Product updatedProduct = productService.updateProduct(product);
            return ResponseEntity.ok(updatedProduct);
        } catch (IOException e) {
            log.error("Failed to update product: {}", productId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除商品（HBase）
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String productId) {
        try {
            productService.deleteProduct(productId);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to delete product: {}", productId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
