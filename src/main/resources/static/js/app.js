// API基础路径
const API_BASE = '/sales/api';

// 全局状态
let currentSessionId = localStorage.getItem('sessionId') || '';
let currentUserId = localStorage.getItem('userId') || '';

// 工具函数
function formatMoney(value) {
    const num = Number(value);
    if (isNaN(num)) return '¥ 0.00';
    return '¥ ' + num.toFixed(2);
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return dateStr;
        return d.toLocaleString('zh-CN');
    } catch (e) {
        return dateStr;
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// API请求封装
async function apiRequest(url, options = {}) {
    try {
        const response = await fetch(API_BASE + url, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        });

    if (!response.ok) {
            const text = await response.text();
            throw new Error(`HTTP ${response.status}: ${text.substring(0, 100)}`);
    }

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return await response.json();
        }
        return null;
    } catch (error) {
        console.error('API请求失败:', error);
        throw error;
    }
}

// Toast通知
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    const messageEl = document.getElementById('toastMessage');
    const icon = toast.querySelector('i');

    toast.className = `toast ${type}`;
    messageEl.textContent = message;

    const icons = {
        success: 'fa-check-circle',
        error: 'fa-exclamation-circle',
        warning: 'fa-info-circle'
    };
    icon.className = `fas ${icons[type] || icons.success}`;

    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 3000);
}

// 页面切换
function showPage(pageId) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
    
    document.getElementById(pageId)?.classList.add('active');
    event.target.closest('.nav-tab')?.classList.add('active');

    // 加载页面数据
    loadPageData(pageId);
}

function loadPageData(pageId) {
    switch(pageId) {
        case 'dashboard':
            loadDashboard();
            break;
        case 'products':
            loadProducts();
            updateCartCount(); // 更新购物车数量
            break;
        case 'orders':
            loadOrders();
            break;
        case 'cart':
            loadCart();
            break;
        case 'users':
            loadUsers();
            break;
        case 'ranking':
            loadRanking();
            break;
        case 'analysis':
            loadAnalysis();
            break;
    }
}

// 仪表板
async function loadDashboard() {
    try {
        // 获取实时仪表板数据
        const response = await apiRequest('/dashboard/realtime');
        const data = response;

        document.getElementById('todaySales').textContent = formatMoney(data.totalAmount);
        document.getElementById('todayOrders').textContent = data.orderCount;
        document.getElementById('todayUsers').textContent = data.userCount;
        document.getElementById('avgPrice').textContent = formatMoney(data.avgPrice);

        // 获取热门商品
        const hotProductsResponse = await apiRequest('/dashboard/hot-products?limit=4');
        const hotProductIds = Array.from(hotProductsResponse).slice(0, 4);
        
        // 获取商品详情
        const productPromises = hotProductIds.map(id => {
            const productId = String(id).replace('product:', '');
            return apiRequest(`/products/${productId}`).catch(() => null);
        });
        
        const productList = await Promise.all(productPromises);
        const validProducts = productList.filter(p => p !== null);
        
        renderHotProducts(validProducts);
    } catch (error) {
        showToast('加载仪表板数据失败', 'error');
        console.error(error);
        
        // 降级到模拟数据
        loadFallbackDashboard();
    }
}

function renderHotProducts(products) {
    const container = document.getElementById('hotProducts');
    if (!products || products.length === 0) {
        container.innerHTML = '<div class="empty-state"><i class="fas fa-box"></i><div>暂无热门商品</div></div>';
        return;
    }

    container.innerHTML = products.map(p => createProductCard(p)).join('');
}

// 降级仪表板数据（当API失败时使用）
function loadFallbackDashboard() {
    const data = {
        totalAmount: 158320.50,   // 今日销售金额
        orderCount: 320,          // 今日订单数
        userCount: 210,           // 今日活跃用户数
        avgPrice: 495.38          // 平均客单价
    };

    document.getElementById('todaySales').textContent = formatMoney(data.totalAmount);
    document.getElementById('todayOrders').textContent = data.orderCount;
    document.getElementById('todayUsers').textContent = data.userCount;
    document.getElementById('avgPrice').textContent = formatMoney(data.avgPrice);

    // 模拟热门商品
    const hotProducts = [
        { productId: '1001_20260107', name: 'iPhone 14', price: 6999, images: ['https://via.placeholder.com/300x200?text=iPhone+14'], realTimeStock: 50 },
        { productId: '1002_20260107', name: '小米12', price: 3999, images: ['https://via.placeholder.com/300x200?text=小米12'], realTimeStock: 120 },
        { productId: '1003_20260107', name: '华为Mate50', price: 5999, images: ['https://via.placeholder.com/300x200?text=Mate50'], realTimeStock: 80 },
        { productId: '1004_20260107', name: 'OPPO Reno9', price: 3299, images: ['https://via.placeholder.com/300x200?text=Reno9'], realTimeStock: 60 }
    ];

    renderHotProducts(hotProducts);
}

// 商品管理
async function loadProducts() {
    try {
        const products = await apiRequest('/products?limit=50');
        renderProducts(products);
    } catch (error) {
        showToast('加载商品列表失败', 'error');
        document.getElementById('productsList').innerHTML = '<div class="empty-state"><i class="fas fa-exclamation-circle"></i><div>加载失败</div></div>';
    }
}

function renderProducts(products) {
    const container = document.getElementById('productsList');
    if (!products || products.length === 0) {
        container.innerHTML = '<div class="empty-state"><i class="fas fa-box"></i><div>暂无商品</div></div>';
        return;
    }

    container.innerHTML = products.map(p => createProductCard(p, true)).join('');
}

function createProductCard(product, showActions = false) {
    const imageUrl = product.images && product.images.length > 0 
        ? product.images[0] 
        : 'https://via.placeholder.com/300x200?text=' + encodeURIComponent(product.name || '商品');

    const currentStock = product.realTimeStock || product.totalStock || 0;
    const isLoggedIn = currentUserId !== null;
    const isOutOfStock = currentStock <= 0;
    const stockStatusClass = isOutOfStock ? 'stock-out' : 'stock-available';
    const stockStatusText = isOutOfStock ? '库存不足' : `库存: ${currentStock}`;
    const stockStatusIcon = isOutOfStock ? 'fa-times-circle' : 'fa-box';

    return `
        <div class="product-card" onclick="viewProduct('${product.productId}')">
            <div class="product-image">
                <img src="${imageUrl}" alt="${escapeHtml(product.name || '商品')}" 
                     onerror="this.src='https://via.placeholder.com/300x200?text=商品图片'">
                ${isOutOfStock ? '<div class="out-of-stock-badge">缺货</div>' : ''}
            </div>
            <div class="product-info">
                <div class="product-name">${escapeHtml(product.name || '未命名商品')}</div>
                <div class="product-price">${formatMoney(product.price || 0)}</div>
                <div class="product-meta">
                    <span><i class="fas fa-tag"></i> ${escapeHtml(product.category || '-')}</span>
                    <span class="stock-info ${stockStatusClass}" data-product="${product.productId}">
                        <i class="fas ${stockStatusIcon}"></i> <span class="stock-count">${stockStatusText}</span>
                    </span>
                </div>
                ${showActions ? `
                    <div class="product-actions">
                        <button class="btn btn-primary btn-sm" onclick="event.stopPropagation(); editProduct('${product.productId}')" title="编辑">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="btn btn-warning btn-sm" onclick="event.stopPropagation(); showStockModal('${product.productId}', ${currentStock})" title="库存">
                            <i class="fas fa-warehouse"></i>
                        </button>
                        <button class="btn btn-danger btn-sm" onclick="event.stopPropagation(); deleteProduct('${product.productId}')" title="删除">
                            <i class="fas fa-trash"></i>
                        </button>
                        ${isLoggedIn && product.status === 1 && !isOutOfStock ? `
                            <button class="btn btn-success btn-sm" onclick="event.stopPropagation(); addToCartFromProduct('${product.productId}')" title="加入购物车">
                                <i class="fas fa-cart-plus"></i>
                            </button>
                        ` : isLoggedIn && product.status === 1 && isOutOfStock ? `
                            <button class="btn btn-outline btn-sm" onclick="event.stopPropagation(); showOutOfStockNotice('${product.productId}')" title="缺货通知">
                                <i class="fas fa-bell"></i>
                            </button>
                        ` : ''}
                    </div>
                ` : ''}
            </div>
        </div>
    `;
}

// 缺货通知功能
function showOutOfStockNotice(productId) {
    showToast('商品暂时缺货，请关注库存更新', 'warning');
    
    // 可以在这里添加到货提醒功能
    // 例如：保存到本地存储或发送到后端
    const notificationRequests = JSON.parse(localStorage.getItem('stockNotifications') || '[]');
    if (!notificationRequests.includes(productId)) {
        notificationRequests.push(productId);
        localStorage.setItem('stockNotifications', JSON.stringify(notificationRequests));
        showToast('已为您设置到货提醒', 'success');
    }
}

async function viewProduct(productId) {
    try {
        const product = await apiRequest(`/products/${productId}`);
        showProductModal('商品详情', product, false);
    } catch (error) {
        showToast('获取商品详情失败', 'error');
    }
}

function showAddProductModal() {
    showProductModal('添加商品', null, true);
}

function editProduct(productId) {
    apiRequest(`/products/${productId}`).then(product => {
        showProductModal('编辑商品', product, true);
    }).catch(() => {
        showToast('获取商品信息失败', 'error');
    });
}

function showProductModal(title, product, isEdit = false) {
    const currentStock = product?.realTimeStock || product?.totalStock || 0;
    const isOutOfStock = currentStock <= 0;
    const isLoggedIn = currentUserId !== null;
    
    const modal = document.createElement('div');
    modal.id = 'productModal';
    modal.className = 'modal active';
    
    let body = '';
    if (isEdit) {
        body = `
            <form onsubmit="saveProduct(event)">
                <div class="form-group">
                    <label class="form-label">商品ID</label>
                    <input type="text" id="productIdInput" class="form-input" value="${product?.productId || ''}" readonly>
                </div>
                <div class="form-group">
                    <label class="form-label">商品名称 *</label>
                    <input type="text" id="productName" class="form-input" value="${product?.name || ''}" required>
                </div>
                <div class="form-group">
                    <label class="form-label">分类</label>
                    <input type="text" id="productCategory" class="form-input" value="${product?.category || ''}">
                </div>
                <div class="form-group">
                    <label class="form-label">价格 *</label>
                    <input type="number" id="productPrice" class="form-input" value="${product?.price || ''}" step="0.01" required>
                </div>
                <div class="form-group">
                    <label class="form-label">描述</label>
                    <textarea id="productDescription" class="form-input" rows="3">${product?.description || ''}</textarea>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-outline" onclick="closeModal('productModal')">取消</button>
                    <button type="submit" class="btn btn-primary">保存</button>
                </div>
            </form>
        `;
    } else {
        const stockStatusClass = isOutOfStock ? 'text-danger' : 'text-success';
        const stockStatusText = isOutOfStock ? '库存不足' : `${currentStock}件`;
        
        body = `
            <div style="display: grid; grid-template-columns: 200px 1fr; gap: 16px;">
                <div><strong>商品ID:</strong></div><div>${escapeHtml(product?.productId || '-')}</div>
                <div><strong>商品名称:</strong></div><div>${escapeHtml(product?.name || '-')}</div>
                <div><strong>分类:</strong></div><div>${escapeHtml(product?.category || '-')}</div>
                <div><strong>价格:</strong></div><div>${formatMoney(product?.price || 0)}</div>
                <div><strong>库存:</strong></div><div class="${stockStatusClass}">${stockStatusText}</div>
                <div><strong>销量:</strong></div><div>${product?.saleCount || 0}</div>
                <div><strong>浏览量:</strong></div><div>${product?.viewCount || 0}</div>
                <div><strong>状态:</strong></div><div><span class="badge ${product?.status === 1 ? 'badge-success' : 'badge-warning'}">${product?.status === 1 ? '上架' : '下架'}</span></div>
                <div><strong>描述:</strong></div><div>${escapeHtml(product?.description || '-')}</div>
            </div>
            <div class="modal-footer">
                ${isLoggedIn && product?.status === 1 && !isOutOfStock ? `
                    <button class="btn btn-success" onclick="addToCartFromDetail('${product?.productId}')">
                        <i class="fas fa-cart-plus"></i> 加入购物车
                    </button>
                    <button class="btn btn-primary" onclick="buyNow('${product?.productId}')">
                        <i class="fas fa-bolt"></i> 立即购买
                    </button>
                ` : isLoggedIn && product?.status === 1 && isOutOfStock ? `
                    <button class="btn btn-outline" onclick="showOutOfStockNotice('${product?.productId}')">
                        <i class="fas fa-bell"></i> 到货提醒
                    </button>
                ` : ''}
                <button class="btn btn-primary" onclick="closeModal('productModal'); editProduct('${product?.productId}')">编辑</button>
                <button class="btn" onclick="closeModal('productModal')">关闭</button>
        </div>
    `;
    }
    
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3>${title}</h3>
                <button class="btn btn-outline" onclick="this.closest('.modal').remove()">关闭</button>
            </div>
            <div class="modal-body">
                ${body}
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);

    document.getElementById('productModal').classList.add('active');
}

async function saveProduct(event) {
    event.preventDefault();
    const productId = document.getElementById('productIdInput').value;
    const product = {
        productId: productId,
        name: document.getElementById('productName').value,
        category: document.getElementById('productCategory').value,
        price: parseFloat(document.getElementById('productPrice').value),
        description: document.getElementById('productDescription').value,
        status: 1
    };

    try {
        if (document.getElementById('productId').value) {
            // 更新
            await apiRequest(`/products/${productId}`, {
                method: 'PUT',
                body: JSON.stringify(product)
            });
            showToast('商品更新成功', 'success');
        } else {
            // 创建
            await apiRequest('/products', {
                method: 'POST',
                body: JSON.stringify(product)
            });
            showToast('商品创建成功', 'success');
        }
        closeModal('productModal');
        loadProducts();
    } catch (error) {
        showToast('保存失败', 'error');
    }
}

async function deleteProduct(productId) {
    if (!confirm(`确认删除商品 ${productId}?`)) return;
    try {
        await apiRequest(`/products/${productId}`, { method: 'DELETE' });
        showToast('删除成功', 'success');
        loadProducts();
    } catch (error) {
        showToast('删除失败', 'error');
    }
}

// 订单管理
async function loadOrders() {
    const userId = document.getElementById('orderUserId').value || currentUserId;
    if (!userId) {
        showToast('请输入用户ID', 'warning');
        return;
    }
    
    try {
        const orders = await apiRequest(`/orders/user/${userId}?limit=50`);
        renderOrders(orders);
    } catch (error) {
        showToast('加载订单列表失败', 'error');
        document.getElementById('ordersTable').innerHTML = '<tr><td colspan="6" class="empty-state"><i class="fas fa-exclamation-circle"></i><div>加载失败</div></td></tr>';
    }
}

function renderOrders(orders) {
    const tbody = document.getElementById('ordersTable');
    if (!orders || orders.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="empty-state"><i class="fas fa-shopping-bag"></i><div>暂无订单</div></td></tr>';
        return;
    }

    tbody.innerHTML = orders.map(order => {
        const statusMap = { 1: '待付款', 2: '待发货', 3: '已发货', 4: '已完成', 5: '已取消' };
        const statusClass = order.status === 4 ? 'badge-success' : order.status === 5 ? 'badge-danger' : 'badge-warning';
        return `
            <tr>
                <td>${escapeHtml(order.orderId || '-')}</td>
                <td>${escapeHtml(order.userId || '-')}</td>
                <td>${formatMoney(order.actualAmount || 0)}</td>
                <td><span class="badge ${statusClass}">${statusMap[order.status] || '未知'}</span></td>
                <td>${formatDate(order.createTime)}</td>
                <td>
                    <button class="btn btn-sm btn-primary" onclick="viewOrder('${order.orderId}')">
                        <i class="fas fa-eye"></i> 查看
                    </button>
                    ${order.status < 4 ? `
                        <button class="btn btn-sm btn-success" onclick="updateOrderStatus('${order.orderId}', ${order.status + 1})">
                            <i class="fas fa-arrow-right"></i> 更新状态
                        </button>
                    ` : ''}
                    <button class="btn btn-sm btn-danger" onclick="deleteOrder('${order.orderId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>
                </td>
                        </tr>
        `;
    }).join('');
}

async function viewOrder(orderId) {
    try {
        const order = await apiRequest(`/orders/${orderId}`);
        alert(`订单详情:\n订单号: ${order.orderId}\n用户ID: ${order.userId}\n金额: ${formatMoney(order.actualAmount)}\n状态: ${order.status}`);
    } catch (error) {
        showToast('获取订单详情失败', 'error');
    }
}

async function deleteOrder(orderId) {
    if (!confirm('确认删除订单？删除后将无法恢复，且会释放相关库存。')) {
        return;
    }
    
    try {
        await apiRequest(`/orders/${orderId}`, { method: 'DELETE' });
        showToast('订单删除成功', 'success');
        loadOrders(); // 重新加载订单列表
    } catch (error) {
        showToast('删除订单失败: ' + (error.message || '未知错误'), 'error');
    }
}

// 库存管理功能
function showStockModal(productId, currentStock) {
    const modal = document.createElement('div');
    modal.className = 'modal active';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3>库存管理</h3>
                <button class="btn btn-outline" onclick="this.closest('.modal').remove()">关闭</button>
            </div>
            <div class="modal-body">
                <div class="form-group">
                    <label class="form-label">当前库存</label>
                    <input type="number" id="currentStock" class="form-input" value="${currentStock}" readonly>
                </div>
                <div class="form-group">
                    <label class="form-label">新库存 *</label>
                    <input type="number" id="newStock" class="form-input" value="${currentStock}" min="0" required>
                </div>
                <div class="form-group">
                    <label class="form-label">扣减数量</label>
                    <input type="number" id="deductStock" class="form-input" value="1" min="1">
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-primary" onclick="updateStock('${productId}')">更新库存</button>
                <button type="button" class="btn btn-warning" onclick="deductStock('${productId}')">扣减库存</button>
                <button type="button" class="btn btn-outline" onclick="this.closest('.modal').remove()">取消</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
}

async function updateStock(productId) {
    const newStock = parseInt(document.getElementById('newStock').value);
    
    if (isNaN(newStock) || newStock < 0) {
        showToast('请输入有效的库存数量', 'warning');
        return;
    }
    
    try {
        await apiRequest(`/products/${productId}/stock?stock=${newStock}`, { method: 'PUT' });
        showToast('库存更新成功', 'success');
        
        // 更新页面显示
        const stockInfo = document.querySelector(`.stock-info[data-product="${productId}"] .stock-count`);
        if (stockInfo) {
            stockInfo.textContent = newStock;
        }
        
        // 关闭模态框
        document.querySelector('.modal').remove();
        
        // 刷新商品列表
        loadProducts();
    } catch (error) {
        showToast('库存更新失败: ' + (error.message || '未知错误'), 'error');
    }
}

async function deductStock(productId) {
    const quantity = parseInt(document.getElementById('deductStock').value);
    
    if (isNaN(quantity) || quantity <= 0) {
        showToast('请输入有效的扣减数量', 'warning');
        return;
    }
    
    try {
        const success = await apiRequest(`/products/${productId}/stock/deduct?quantity=${quantity}`, { method: 'POST' });
        
        if (success) {
            showToast('库存扣减成功', 'success');
            
            // 获取最新库存并更新显示
            const stock = await apiRequest(`/products/${productId}/stock`);
            const stockInfo = document.querySelector(`.stock-info[data-product="${productId}"] .stock-count`);
            if (stockInfo) {
                stockInfo.textContent = stock;
            }
            
            // 更新当前库存输入框
            document.getElementById('currentStock').value = stock;
            document.getElementById('newStock').value = stock;
            
            // 刷新商品列表
            loadProducts();
        } else {
            showToast('库存不足，扣减失败', 'error');
        }
    } catch (error) {
        showToast('库存扣减失败: ' + (error.message || '未知错误'), 'error');
    }
}

// 购物车功能
async function addToCartFromProduct(productId) {
    if (!currentUserId) {
        showToast('请先登录', 'warning');
        return;
    }
    
    try {
        await apiRequest(`/products/${productId}/add-to-cart?userId=${currentUserId}&quantity=1`, { method: 'POST' });
        showToast('已添加到购物车', 'success');
        
        // 更新购物车数量
        updateCartCount();
    } catch (error) {
        if (error.message && error.message.includes('库存不足')) {
            showToast('库存不足，无法添加到购物车', 'error');
        } else {
            showToast('添加购物车失败: ' + (error.message || '未知错误'), 'error');
        }
    }
}

async function updateCartCount() {
    if (!currentUserId) return;
    
    try {
        const count = await apiRequest(`/cart/${currentUserId}/count`);
        updateCartBadge(count);
    } catch (error) {
        console.error('Failed to update cart count:', error);
    }
}

async function updateOrderStatus(orderId, status) {
    try {
        await apiRequest(`/orders/${orderId}/status?status=${status}`, { method: 'PUT' });
        showToast('订单状态更新成功', 'success');
        loadOrders();
    } catch (error) {
        showToast('更新失败', 'error');
    }
}

// 购物车
async function loadCart() {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) {
        showToast('请输入用户ID', 'warning');
        return;
    }
    
    try {
        const cartItems = await apiRequest(`/cart/${userId}`);
        renderCart(cartItems);
        updateCartBadge(cartItems.length);
    } catch (error) {
        showToast('加载购物车失败', 'error');
        document.getElementById('cartTable').innerHTML = '<tr><td colspan="6" class="empty-state"><i class="fas fa-exclamation-circle"></i><div>加载失败</div></td></tr>';
    }
}

function renderCart(cartItems) {
    const tbody = document.getElementById('cartTable');
    if (!cartItems || cartItems.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="empty-state"><i class="fas fa-shopping-cart"></i><div>购物车为空</div></td></tr>';
        return;
    }

    tbody.innerHTML = cartItems.map(item => {
        const itemData = typeof item === 'string' ? JSON.parse(item) : item;
        const quantity = itemData.quantity || 1;
        const productId = itemData.productId || '';
        const selected = itemData.selected !== false; // 默认选中
        
        // 使用CartItem中的商品信息，如果没有则使用默认值
        const productName = itemData.productName || productId;
        const price = itemData.price || 0;
        
        return `
            <tr>
                <td>
                    <input type="checkbox" ${selected ? 'checked' : ''} 
                           onchange="updateCartSelected('${productId}', this.checked)" 
                           style="margin-right: 8px;">
                    ${escapeHtml(productId)}
                </td>
                <td>${escapeHtml(productName)}</td>
                <td>${formatMoney(price)}</td>
                <td>
                    <input type="number" value="${quantity}" min="1" 
                           onchange="updateCartQuantity('${productId}', this.value)" 
                           style="width: 80px; padding: 6px; border: 1px solid var(--border); border-radius: 6px;">
                </td>
                <td>${formatMoney(price * quantity)}</td>
                <td>
                    <button class="btn btn-sm btn-success" onclick="quickPayCartItem('${productId}', ${quantity})">
                        <i class="fas fa-credit-card"></i> 支付
                    </button>
                </td>
                <td>
                    <button class="btn btn-sm btn-danger" onclick="removeFromCart('${productId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>
                </td>
            </tr>
        `;
    }).join('');
    
    // 添加结算按钮区域
    const checkoutArea = document.getElementById('checkoutArea');
    if (checkoutArea) {
        checkoutArea.innerHTML = `
            <div style="margin-top: 20px; padding: 15px; border-top: 1px solid var(--border);">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                    <div>
                        <button class="btn btn-outline" onclick="selectAllItems(true)">全选</button>
                        <button class="btn btn-outline" onclick="selectAllItems(false)">全不选</button>
                        <button class="btn btn-outline" onclick="clearCart()">清空购物车</button>
                    </div>
                    <div>
                        <span style="margin-right: 15px; font-weight: bold;">
                            已选择: <span id="selectedCount">0</span> 件，
                            总计: <span id="totalAmount">¥0.00</span>
                        </span>
                        <button class="btn btn-primary" onclick="checkout()" id="checkoutBtn">
                            <i class="fas fa-credit-card"></i> 结算
                        </button>
                    </div>
                </div>
            </div>
        `;
    }
    
    // 更新选中状态
    updateSelectedInfo();
}

// 检查购物车库存状态
async function checkCartStockStatus(cartItems) {
    for (const item of cartItems) {
        const itemData = typeof item === 'string' ? JSON.parse(item) : item;
        const productId = itemData.productId;
        const stockInfoEl = document.querySelector(`.stock-info[data-product="${productId}"]`);
        
        if (stockInfoEl) {
            try {
                // 获取库存信息
                const stock = await apiRequest(`/products/${productId}/stock`);
                const quantity = itemData.quantity || 1;
                
                if (stock >= quantity) {
                    stockInfoEl.innerHTML = `<span style="color: var(--success);">库存充足 (${stock})</span>`;
                } else {
                    stockInfoEl.innerHTML = `<span style="color: var(--danger);">库存不足 (${stock})</span>`;
                }
            } catch (error) {
                stockInfoEl.innerHTML = `<span style="color: var(--warning);">库存检查失败</span>`;
            }
        }
    }
}

// 异步加载购物车商品详情
async function loadCartProductDetails(cartItems) {
    for (const item of cartItems) {
        const itemData = typeof item === 'string' ? JSON.parse(item) : item;
        const productId = itemData.productId;
        
        if (!productId) continue;
        
        try {
            // 获取商品详情
            const product = await apiRequest(`/products/${productId}`);
            
            // 更新商品名称
            const nameEl = document.querySelector(`.product-name[data-product="${productId}"]`);
            if (nameEl) {
                nameEl.textContent = escapeHtml(product.name || productId);
            }
            
            // 更新价格
            const priceEl = document.querySelector(`.product-price[data-product="${productId}"]`);
            if (priceEl) {
                priceEl.textContent = formatMoney(product.price || 0);
            }
            
            // 更新小计
            const quantity = itemData.quantity || 1;
            const subtotalEl = document.querySelector(`.product-subtotal[data-product="${productId}"]`);
            if (subtotalEl) {
                subtotalEl.textContent = formatMoney((product.price || 0) * quantity);
            }
            
            // 更新库存信息
            const stockInfoEl = document.querySelector(`.stock-info[data-product="${productId}"]`);
            if (stockInfoEl) {
                const stock = product.realTimeStock || product.totalStock || 0;
                if (stock >= quantity) {
                    stockInfoEl.innerHTML = `<span style="color: var(--success);">库存充足 (${stock})</span>`;
                } else {
                    stockInfoEl.innerHTML = `<span style="color: var(--danger);">库存不足 (${stock})</span>`;
                }
            }
        } catch (error) {
            console.error(`Failed to load product details for ${productId}:`, error);
            // 设置错误状态
            const nameEl = document.querySelector(`.product-name[data-product="${productId}"]`);
            if (nameEl) {
                nameEl.textContent = '加载失败';
            }
            
            const stockInfoEl = document.querySelector(`.stock-info[data-product="${productId}"]`);
            if (stockInfoEl) {
                stockInfoEl.innerHTML = `<span style="color: var(--warning);">加载失败</span>`;
            }
        }
    }
    
    // 更新选中状态和总价
    updateSelectedInfo();
}

async function updateCartQuantity(productId, quantity) {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) return;
    
    const newQuantity = parseInt(quantity);
    if (isNaN(newQuantity) || newQuantity < 1) {
        showToast('请输入有效的数量', 'warning');
        loadCart(); // 重新加载以恢复原值
        return;
    }
    
    try {
        await apiRequest(`/cart/${userId}/items/${productId}?quantity=${newQuantity}`, { method: 'PUT' });
        showToast('数量更新成功', 'success');
        loadCart();
    } catch (error) {
        showToast('更新数量失败: ' + (error.message || '未知错误'), 'error');
        loadCart(); // 重新加载以恢复原值
    }
}

async function removeFromCart(productId) {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) return;
    
    if (!confirm('确认移除该商品?')) return;
    
    try {
        await apiRequest(`/cart/${userId}/items/${productId}`, { method: 'DELETE' });
        showToast('已从购物车移除', 'success');
        loadCart();
    } catch (error) {
        showToast('移除失败: ' + (error.message || '未知错误'), 'error');
    }
}

async function clearCart() {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) {
        showToast('请输入用户ID', 'warning');
        return;
    }
    if (!confirm('确认清空购物车?')) return;
    try {
        await apiRequest(`/cart/${userId}`, { method: 'DELETE' });
        showToast('购物车已清空', 'success');
        loadCart();
    } catch (error) {
        showToast('清空失败: ' + (error.message || '未知错误'), 'error');
    }
}

// 新增购物车功能函数

async function updateCartSelected(productId, selected) {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) return;
    
    try {
        await apiRequest(`/cart/${userId}/items/${productId}/select?selected=${selected}`, { method: 'PUT' });
        updateSelectedInfo();
    } catch (error) {
        showToast('更新选中状态失败', 'error');
        // 重新加载以恢复原状态
        loadCart();
    }
}

function selectAllItems(selected) {
    const checkboxes = document.querySelectorAll('#cartTable input[type="checkbox"]');
    checkboxes.forEach(checkbox => {
        checkbox.checked = selected;
        const productId = checkbox.getAttribute('onchange').match(/'([^']+)'/)[1];
        updateCartSelected(productId, selected);
    });
}

function updateSelectedInfo() {
    const checkboxes = document.querySelectorAll('#cartTable input[type="checkbox"]:checked');
    const selectedCount = checkboxes.length;
    let totalAmount = 0;
    
    checkboxes.forEach(checkbox => {
        const row = checkbox.closest('tr');
        const priceText = row.cells[2].textContent; // 第3列是价格
        const quantity = parseInt(row.cells[3].querySelector('input').value); // 第4列是数量输入框
        const price = parseFloat(priceText.replace(/[^\d.]/g, ''));
        
        if (!isNaN(price) && !isNaN(quantity)) {
            totalAmount += price * quantity;
        }
    });
    
    const selectedCountEl = document.getElementById('selectedCount');
    const totalAmountEl = document.getElementById('totalAmount');
    const checkoutBtn = document.getElementById('checkoutBtn');
    
    if (selectedCountEl) selectedCountEl.textContent = selectedCount;
    if (totalAmountEl) totalAmountEl.textContent = formatMoney(totalAmount);
    if (checkoutBtn) {
        checkoutBtn.disabled = selectedCount === 0;
        checkoutBtn.textContent = selectedCount > 0 ? `结算 (${selectedCount})` : '结算';
    }
}

async function checkStockStatus(cartItems) {
    for (const item of cartItems) {
        const itemData = typeof item === 'string' ? JSON.parse(item) : item;
        const productId = itemData.productId;
        const stockInfoEl = document.querySelector(`.stock-info[data-product="${productId}"]`);
        
        if (stockInfoEl) {
            try {
                const product = await apiRequest(`/products/${productId}`);
                const stock = product.realTimeStock || product.totalStock || 0;
                const quantity = itemData.quantity || 1;
                
                if (stock >= quantity) {
                    stockInfoEl.innerHTML = `<span style="color: var(--success);">库存充足 (${stock})</span>`;
                } else {
                    stockInfoEl.innerHTML = `<span style="color: var(--danger);">库存不足 (${stock})</span>`;
                }
            } catch (error) {
                stockInfoEl.innerHTML = `<span style="color: var(--warning);">库存检查失败</span>`;
            }
        }
    }
}

async function checkout() {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) {
        showToast('请输入用户ID', 'warning');
        return;
    }
    
    const selectedItems = document.querySelectorAll('#cartTable input[type="checkbox"]:checked');
    if (selectedItems.length === 0) {
        showToast('请选择要结算的商品', 'warning');
        return;
    }
    
    if (!confirm(`确认结算选中的 ${selectedItems.length} 件商品?`)) return;
    
    try {
        const order = await apiRequest(`/cart/${userId}/checkout`, { method: 'POST' });
        showToast('订单创建成功！订单号: ' + order.orderId, 'success');
        
        // 显示订单信息
        showOrderModal(order);
        
        // 重新加载购物车
        loadCart();
    } catch (error) {
        showToast('结算失败: ' + (error.message || '未知错误'), 'error');
    }
}

function showOrderModal(order) {
    const modal = document.createElement('div');
    modal.className = 'modal active';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3>订单创建成功</h3>
                <button class="btn btn-outline" onclick="this.closest('.modal').remove()">关闭</button>
            </div>
            <div class="modal-body">
                <div style="display: grid; grid-template-columns: 150px 1fr; gap: 12px; margin-bottom: 20px;">
                    <div><strong>订单号:</strong></div><div>${escapeHtml(order.orderId)}</div>
                    <div><strong>用户ID:</strong></div><div>${escapeHtml(order.userId)}</div>
                    <div><strong>订单状态:</strong></div><div id="orderStatus-${order.orderId}"><span class="badge badge-warning">待支付</span></div>
                    <div><strong>订单金额:</strong></div><div style="color: var(--primary); font-weight: bold;">${formatMoney(order.totalAmount)}</div>
                    <div><strong>实付金额:</strong></div><div style="color: var(--success); font-weight: bold;">${formatMoney(order.actualAmount)}</div>
                    <div><strong>创建时间:</strong></div><div>${new Date(order.createTime).toLocaleString()}</div>
                </div>
                
                <h4>商品明细</h4>
                <div style="max-height: 200px; overflow-y: auto;">
                    ${order.items ? order.items.map(item => `
                        <div style="padding: 8px; border-bottom: 1px solid var(--border);">
                            <div style="display: flex; justify-content: space-between;">
                                <div>
                                    <div style="font-weight: bold;">${escapeHtml(item.productName)}</div>
                                    <div style="color: var(--text-secondary); font-size: 0.9em;">${escapeHtml(item.productId)}</div>
                                </div>
                                <div style="text-align: right;">
                                    <div>${formatMoney(item.price)} × ${item.quantity}</div>
                                    <div style="color: var(--primary); font-weight: bold;">${formatMoney(item.amount)}</div>
                                </div>
                            </div>
                        </div>
                    `).join('') : '<div style="text-align: center; color: var(--text-secondary);">无商品信息</div>'}
                </div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-success" onclick="quickPayOrder('${order.orderId}')" id="payBtn-${order.orderId}">
                    <i class="fas fa-credit-card"></i> 一键支付
                </button>
                <button class="btn btn-outline" onclick="this.closest('.modal').remove()">关闭</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
}

async function quickPayCartItem(productId, quantity) {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) {
        showToast('请输入用户ID', 'warning');
        return;
    }
    
    if (!confirm(`确认支付该商品？`)) return;
    
    try {
        // 创建单个商品的订单
        const product = await apiRequest(`/products/${productId}`);
        const orderItem = {
            productId: productId,
            productName: product.name,
            quantity: quantity,
            price: product.price,
            amount: product.price * quantity
        };
        
        const order = {
            userId: userId,
            items: [orderItem],
            remark: "快速支付"
        };
        
        // 创建订单
        const createdOrder = await apiRequest('/orders', {
            method: 'POST',
            body: JSON.stringify(order)
        });
        
        showToast('订单创建成功！正在支付...', 'success');
        
        // 立即支付
        const paidOrder = await apiRequest(`/orders/${createdOrder.orderId}/quick-pay`, {
            method: 'POST'
        });
        
        showToast('支付成功！', 'success');
        
        // 从购物车中移除已支付的商品
        await removeFromCart(productId);
        
        // 刷新购物车
        await loadCart();
        
        // 刷新仪表板数据
        if (typeof loadDashboard === 'function') {
            loadDashboard();
        }
        
    } catch (error) {
        showToast('支付失败: ' + (error.message || '未知错误'), 'error');
    }
}

async function quickPayOrder(orderId) {
    const payBtn = document.getElementById(`payBtn-${orderId}`);
    const statusEl = document.getElementById(`orderStatus-${orderId}`);
    
    // 显示支付中状态
    payBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 支付中...';
    payBtn.disabled = true;
    
    try {
        const order = await apiRequest(`/orders/${orderId}/quick-pay`, { method: 'POST' });
        
        // 更新订单状态显示
        statusEl.innerHTML = '<span class="badge badge-success">已支付</span>';
        
        // 显示支付成功信息
        showToast('支付成功！订单已进入待发货状态', 'success');
        
        // 更新按钮状态
        payBtn.innerHTML = '<i class="fas fa-check"></i> 支付成功';
        payBtn.className = 'btn btn-outline';
        payBtn.disabled = true;
        
        // 刷新仪表板数据
        if (typeof loadDashboard === 'function') {
            loadDashboard();
        }
        
        // 3秒后自动关闭模态框
        setTimeout(() => {
            const modal = document.querySelector('.modal');
            if (modal) modal.remove();
        }, 3000);
        
    } catch (error) {
        showToast('支付失败: ' + (error.message || '未知错误'), 'error');
        
        // 恢复按钮状态
        payBtn.innerHTML = '<i class="fas fa-credit-card"></i> 一键支付';
        payBtn.disabled = false;
    }
}

function updateCartBadge(count) {
    const badge = document.getElementById('cartBadge');
    if (count > 0) {
        badge.textContent = count;
        badge.style.display = 'inline-block';
    } else {
        badge.style.display = 'none';
    }
}

// 用户管理
async function loadUsers() {
    // 由于后端没有获取所有用户的接口，这里需要先登录获取用户信息
    // 或者使用其他方式获取用户列表
    document.getElementById('usersTable').innerHTML = '<tr><td colspan="7" class="empty-state"><i class="fas fa-info-circle"></i><div>请先登录后查看用户信息</div></td></tr>';
}

function showRegisterModal() {
    const modal = document.createElement('div');
    modal.className = 'modal active';
    modal.innerHTML = `
        <div class="modal-content" onclick="event.stopPropagation()">
            <div class="modal-header">
                <h3>用户注册</h3>
                <button class="btn" onclick="this.closest('.modal').remove()" style="background: transparent; padding: 0; width: 32px; height: 32px;">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body">
                <form onsubmit="registerUser(event)">
                    <div class="form-group">
                        <label class="form-label">用户ID *</label>
                        <input type="text" id="regUserId" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label class="form-label">用户名 *</label>
                        <input type="text" id="regUsername" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label class="form-label">昵称</label>
                        <input type="text" id="regNickname" class="form-input">
                    </div>
                    <div class="form-group">
                        <label class="form-label">手机号</label>
                        <input type="tel" id="regPhone" class="form-input">
                    </div>
                    <div class="form-group">
                        <label class="form-label">邮箱</label>
                        <input type="email" id="regEmail" class="form-input">
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-outline" onclick="this.closest('.modal').remove()">取消</button>
                        <button type="submit" class="btn btn-primary">注册</button>
        </div>
                </form>
            </div>
        </div>
    `;
    document.body.appendChild(modal);
    modal.onclick = () => modal.remove();
}

async function registerUser(event) {
    event.preventDefault();
    const user = {
        userId: document.getElementById('regUserId').value,
        username: document.getElementById('regUsername').value,
        nickname: document.getElementById('regNickname').value,
        phone: document.getElementById('regPhone').value,
        email: document.getElementById('regEmail').value,
        status: 1
    };

    try {
        await apiRequest('/users/register', {
            method: 'POST',
            body: JSON.stringify(user)
        });
        showToast('注册成功', 'success');
        event.target.closest('.modal').remove();
    } catch (error) {
        showToast('注册失败', 'error');
    }
}

// 排行榜
async function loadRanking() {
    const type = document.getElementById('rankingType').value;
    const limit = document.getElementById('rankingLimit').value || 10;

    try {
        const ranking = await apiRequest(`/ranking/${type}?limit=${limit}`);
        renderRanking(ranking, type);
    } catch (error) {
        showToast('加载排行榜失败', 'error');
        document.getElementById('rankingTable').innerHTML = '<tr><td colspan="3" class="empty-state"><i class="fas fa-exclamation-circle"></i><div>加载失败</div></td></tr>';
    }
}

function renderRanking(ranking, type) {
    const tbody = document.getElementById('rankingTable');
    if (!ranking || ranking.size === 0) {
        tbody.innerHTML = '<tr><td colspan="3" class="empty-state"><i class="fas fa-trophy"></i><div>暂无排行数据</div></td></tr>';
            return;
        }

    const items = Array.from(ranking);
    tbody.innerHTML = items.map((item, index) => {
        const productId = String(item).replace('product:', '');
            return `
                <tr>
                <td>
                    <span class="badge ${index < 3 ? 'badge-warning' : 'badge-info'}" style="font-size: 16px; padding: 8px 16px;">
                        ${index + 1}
                    </span>
                </td>
                <td>${escapeHtml(productId)}</td>
                <td>
                    <button class="btn btn-sm btn-primary" onclick="viewProduct('${productId}')">
                        <i class="fas fa-eye"></i> 查看商品
                    </button>
                    </td>
                </tr>
            `;
        }).join('');
}

// 销售分析
async function loadAnalysis() {
    // 可以根据日期动态生成一些伪数据
    const date = document.getElementById('analysisDate')?.value || new Date().toISOString().split('T')[0];

    try {
        // 模拟每日销售数据
        const data = {
            saleCount: Math.floor(Math.random() * 500) + 50,        // 销售数量
            saleAmount: parseFloat((Math.random() * 50000 + 5000).toFixed(2)), // 销售金额
            refundCount: Math.floor(Math.random() * 20),            // 退货数量
            refundAmount: parseFloat((Math.random() * 2000).toFixed(2)) // 退货金额
        };

        renderAnalysis(data);
    } catch (error) {
        showToast('加载分析数据失败', 'error');
        document.getElementById('analysisContent').innerHTML = '<div class="empty-state"><i class="fas fa-exclamation-circle"></i><div>加载失败</div></div>';
    }
}


function renderAnalysis(data) {
    const container = document.getElementById('analysisContent');
    container.innerHTML = `
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-shopping-cart"></i></div>
                <div class="stat-label">销售数量</div>
                <div class="stat-value">${data.saleCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-dollar-sign"></i></div>
                <div class="stat-label">销售金额</div>
                <div class="stat-value">${formatMoney(data.saleAmount)}</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-undo"></i></div>
                <div class="stat-label">退货数量</div>
                <div class="stat-value">${data.refundCount}</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-money-bill-wave"></i></div>
                <div class="stat-label">退货金额</div>
                <div class="stat-value">${formatMoney(data.refundAmount)}</div>
            </div>
        </div>
    `;
}

// 用户会话管理
function toggleSessionPanel() {
    const panel = document.getElementById('sessionPanel');
    panel.classList.toggle('show');
}

async function handleLogin() {
    const username = document.getElementById('loginUsername').value;
    if (!username) {
        showToast('请输入用户名', 'warning');
        return;
    }

    try {
        const res = await apiRequest(`/users/login?username=${username}`, { method: 'POST' });
        const sessionId = res.data.sessionId;
        localStorage.setItem('sessionId', sessionId);
        
        // 获取用户信息
        const user = await apiRequest(`/users/me?sessionId=${sessionId}`);
        if (user) {
            currentUserId = user.userId;
            localStorage.setItem('userId', user.userId);
            document.getElementById('currentUser').textContent = user.username || user.nickname || '已登录';
            updateSessionInfo(user);
        }
        
        showToast('登录成功', 'success');
        toggleSessionPanel();
    } catch (error) {
        showToast('登录失败', 'error');
    }
}

async function handleLogout() {
    if (!currentSessionId) {
        showToast('未登录', 'warning');
        return;
    }

    try {
        await apiRequest(`/users/logout?sessionId=${currentSessionId}`, { method: 'POST' });
        currentSessionId = '';
        currentUserId = '';
        localStorage.removeItem('sessionId');
        localStorage.removeItem('userId');
        document.getElementById('currentUser').textContent = '未登录';
        document.getElementById('sessionInfo').innerHTML = '';
        showToast('登出成功', 'success');
        toggleSessionPanel();
    } catch (error) {
        showToast('登出失败', 'error');
    }
}

function updateSessionInfo(user) {
    const info = document.getElementById('sessionInfo');
    info.innerHTML = `
        <div><strong>用户ID:</strong> ${user.userId}</div>
        <div><strong>用户名:</strong> ${user.username}</div>
        <div><strong>昵称:</strong> ${user.nickname || '-'}</div>
    `;
}

// 模态框关闭
function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

// 初始化
document.addEventListener('DOMContentLoaded', () => {
    // 恢复会话
    if (currentSessionId) {
        apiRequest(`/users/me?sessionId=${currentSessionId}`).then(user => {
            if (user) {
                currentUserId = user.userId;
                document.getElementById('currentUser').textContent = user.username || user.nickname || '已登录';
                updateSessionInfo(user);
            }
        }).catch(() => {
            currentSessionId = '';
            localStorage.removeItem('sessionId');
        });
    }

    // 加载首页
    loadDashboard();

    // 点击外部关闭会话面板
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.session-panel') && !e.target.closest('.navbar-actions button')) {
            document.getElementById('sessionPanel').classList.remove('show');
        }
    });
});

// 导出全局函数
window.showPage = showPage;
window.viewProduct = viewProduct;
window.editProduct = editProduct;
window.deleteProduct = deleteProduct;
window.showAddProductModal = showAddProductModal;
window.closeModal = closeModal;
window.loadOrders = loadOrders;
window.viewOrder = viewOrder;
window.updateOrderStatus = updateOrderStatus;
window.loadCart = loadCart;
window.updateCartQuantity = updateCartQuantity;
window.removeFromCart = removeFromCart;
window.clearCart = clearCart;
window.loadRanking = loadRanking;
window.loadAnalysis = loadAnalysis;
window.handleLogin = handleLogin;
window.handleLogout = handleLogout;
window.toggleSessionPanel = toggleSessionPanel;
window.showRegisterModal = showRegisterModal;
window.registerUser = registerUser;
