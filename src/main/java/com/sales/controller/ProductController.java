package com.sales.controller;

import com.sales.entity.Product;
import com.sales.service.ProductService;
import com.sales.service.StockService;
import com.sales.service.CartService;
import com.sales.entity.CartItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品管理模块 - 商品信息存储（HBase）
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;
    
    @Autowired
    private StockService stockService;
    
    @Autowired
    private CartService cartService;

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
            // 同时删除库存
            stockService.deleteStock(productId);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to delete product: {}", productId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 更新商品库存
     */
    @PutMapping("/{productId}/stock")
    public ResponseEntity<Void> updateStock(
            @PathVariable String productId,
            @RequestParam Integer stock) {
        try {
            stockService.setStock(productId, stock);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to update stock: productId={}, stock={}", productId, stock, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取商品库存
     */
    @GetMapping("/{productId}/stock")
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
     * 扣减商品库存
     */
    @PostMapping("/{productId}/stock/deduct")
    public ResponseEntity<Boolean> deductStock(
            @PathVariable String productId,
            @RequestParam Integer quantity) {
        try {
            boolean success = stockService.deductStock(productId, quantity);
            return ResponseEntity.ok(success);
        } catch (Exception e) {
            log.error("Failed to deduct stock: productId={}, quantity={}", productId, quantity, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 添加商品到购物车
     */
    @PostMapping("/{productId}/add-to-cart")
    public ResponseEntity<Void> addToCart(
            @PathVariable String productId,
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") Integer quantity) {
        try {
            // 检查商品是否存在
            Product product = productService.getProductById(productId);
            if (product == null || product.getStatus() != 1) {
                return ResponseEntity.badRequest().build();
            }
            
            // 检查库存
            int currentStock = stockService.getStock(productId);
            if (currentStock < quantity) {
                return ResponseEntity.badRequest().build();
            }
            
            // 创建购物车项
            CartItem cartItem = CartItem.builder()
                    .userId(userId)
                    .productId(productId)
                    .quantity(quantity)
                    .selected(true)
                    .build();
            
            cartService.addToCart(userId, cartItem);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to add to cart: productId={}, userId={}, quantity={}", productId, userId, quantity, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 批量初始化商品库存（用于测试）
     */
    @PostMapping("/init-stock")
    public ResponseEntity<Map<String, String>> initStock() {
        Map<String, String> result = new HashMap<>();
        
        try {
            // 为一些常见商品设置库存
            String[] productIds = {"P2001", "P2002", "P2003", "P2004", "P2005"};
            
            for (String productId : productIds) {
                // 检查商品是否存在
                Product product = productService.getProductById(productId);
                if (product != null) {
                    // 设置库存为100
                    stockService.setStock(productId, 100);
                    result.put(productId, "库存已设置为100");
                } else {
                    result.put(productId, "商品不存在");
                }
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to init stock", e);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
