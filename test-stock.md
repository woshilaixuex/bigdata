# 库存测试步骤

## 1. 初始化商品库存
```bash
curl -X POST "http://localhost:8080/api/products/init-stock"
```

## 2. 检查库存是否设置成功
```bash
# 检查P2001的库存
curl "http://localhost:8080/api/products/P2001/stock"

# 检查P2002的库存
curl "http://localhost:8080/api/products/P2002/stock"
```

## 3. 添加商品到购物车
```bash
curl -X POST "http://localhost:8080/api/products/P2001/add-to-cart?userId=U001&quantity=1"
```

## 4. 查看购物车
```bash
curl "http://localhost:8080/api/cart/U001"
```

## 5. 购物车结算
```bash
curl -X POST "http://localhost:8080/api/cart/U001/checkout"
```

## 6. 查看订单
```bash
curl "http://localhost:8080/api/orders/user/U001?limit=10"
```

## 7. 更新订单状态为已完成
```bash
curl -X PUT "http://localhost:8080/api/orders/{orderId}/status?status=4"
```

## 8. 检查仪表板数据
```bash
curl "http://localhost:8080/api/dashboard/realtime"
```
