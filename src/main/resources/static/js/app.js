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
        const data = await apiRequest('/analysis/dashboard');
        
        document.getElementById('todaySales').textContent = formatMoney(data.totalAmount || 0);
        document.getElementById('todayOrders').textContent = data.orderCount || 0;
        document.getElementById('todayUsers').textContent = data.userCount || 0;
        document.getElementById('avgPrice').textContent = formatMoney(data.avgPrice || 0);

        // 加载热门商品（使用排行榜接口）
        const hotProducts = await apiRequest('/ranking/hot?limit=4');
        renderHotProducts(hotProducts);
    } catch (error) {
        showToast('加载仪表板数据失败', 'error');
        console.error(error);
    }
}

function renderHotProducts(products) {
    const container = document.getElementById('hotProducts');
    if (!products || products.length === 0) {
        container.innerHTML = '<div class="empty-state"><i class="fas fa-box"></i><div>暂无热门商品</div></div>';
        return;
    }

    // 排行榜返回的是Set<Object>，需要转换为数组并获取商品详情
    const productIds = Array.from(products).slice(0, 4);
    Promise.all(productIds.map(id => {
        const productId = String(id).replace('product:', '');
        return apiRequest(`/products/${productId}`).catch(() => null);
    })).then(productList => {
        const validProducts = productList.filter(p => p !== null);
        if (validProducts.length === 0) {
            container.innerHTML = '<div class="empty-state"><i class="fas fa-box"></i><div>暂无热门商品</div></div>';
            return;
        }
        container.innerHTML = validProducts.map(p => createProductCard(p)).join('');
    });
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

    return `
        <div class="product-card" onclick="viewProduct('${product.productId}')">
            <img src="${imageUrl}" alt="${product.name}" class="product-image" onerror="this.src='https://via.placeholder.com/300x200'">
            <div class="product-info">
                <div class="product-name">${escapeHtml(product.name || '未命名商品')}</div>
                <div class="product-price">${formatMoney(product.price || 0)}</div>
                <div class="product-meta">
                    <span><i class="fas fa-tag"></i> ${escapeHtml(product.category || '-')}</span>
                    <span><i class="fas fa-box"></i> 库存: ${product.realTimeStock || product.totalStock || 0}</span>
                </div>
                ${showActions ? `
                    <div class="product-actions">
                        <button class="btn btn-primary btn-sm" onclick="event.stopPropagation(); editProduct('${product.productId}')">
                            <i class="fas fa-edit"></i> 编辑
                        </button>
                        <button class="btn btn-danger btn-sm" onclick="event.stopPropagation(); deleteProduct('${product.productId}')">
                            <i class="fas fa-trash"></i> 删除
                        </button>
                    </div>
                ` : ''}
            </div>
        </div>
    `;
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

function showProductModal(title, product, editable) {
    document.getElementById('productModalTitle').textContent = title;
    const body = document.getElementById('productModalBody');

    if (editable) {
        body.innerHTML = `
            <form id="productForm" onsubmit="saveProduct(event)">
                <input type="hidden" id="productId" value="${product?.productId || ''}">
                <div class="form-group">
                    <label class="form-label">商品ID *</label>
                    <input type="text" id="productIdInput" class="form-input" value="${product?.productId || ''}" ${product ? 'readonly' : ''} required>
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
        body.innerHTML = `
            <div style="display: grid; grid-template-columns: 200px 1fr; gap: 16px;">
                <div><strong>商品ID:</strong></div><div>${escapeHtml(product?.productId || '-')}</div>
                <div><strong>商品名称:</strong></div><div>${escapeHtml(product?.name || '-')}</div>
                <div><strong>分类:</strong></div><div>${escapeHtml(product?.category || '-')}</div>
                <div><strong>价格:</strong></div><div>${formatMoney(product?.price || 0)}</div>
                <div><strong>库存:</strong></div><div>${product?.realTimeStock || product?.totalStock || 0}</div>
                <div><strong>销量:</strong></div><div>${product?.saleCount || 0}</div>
                <div><strong>浏览量:</strong></div><div>${product?.viewCount || 0}</div>
                <div><strong>状态:</strong></div><div><span class="badge ${product?.status === 1 ? 'badge-success' : 'badge-warning'}">${product?.status === 1 ? '上架' : '下架'}</span></div>
                <div><strong>描述:</strong></div><div>${escapeHtml(product?.description || '-')}</div>
            </div>
            <div class="modal-footer">
                <button class="btn btn-primary" onclick="closeModal('productModal'); editProduct('${product?.productId}')">编辑</button>
                <button class="btn" onclick="closeModal('productModal')">关闭</button>
        </div>
    `;
    }

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
        tbody.innerHTML = '<tr><td colspan="6" class="empty-state"><i class="fas fa-shopping-cart"></i><div>购物车为空</div></td></tr>';
            return;
        }

    tbody.innerHTML = cartItems.map(item => {
        const itemData = typeof item === 'string' ? JSON.parse(item) : item;
        const quantity = itemData.quantity || 1;
        const price = itemData.price || 0;
        return `
            <tr>
                <td>${escapeHtml(itemData.productId || '-')}</td>
                <td>${escapeHtml(itemData.name || itemData.productId || '-')}</td>
                <td>${formatMoney(price)}</td>
                <td>
                    <input type="number" value="${quantity}" min="1" 
                           onchange="updateCartQuantity('${itemData.productId}', this.value)" 
                           style="width: 80px; padding: 6px; border: 1px solid var(--border); border-radius: 6px;">
                </td>
                <td>${formatMoney(price * quantity)}</td>
                <td>
                    <button class="btn btn-sm btn-danger" onclick="removeFromCart('${itemData.productId}')">
                        <i class="fas fa-trash"></i> 删除
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

async function updateCartQuantity(productId, quantity) {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) return;
    try {
        await apiRequest(`/cart/${userId}/items/${productId}?quantity=${quantity}`, { method: 'PUT' });
        loadCart();
    } catch (error) {
        showToast('更新数量失败', 'error');
    }
}

async function removeFromCart(productId) {
    const userId = document.getElementById('cartUserId').value || currentUserId;
    if (!userId) return;
    try {
        await apiRequest(`/cart/${userId}/items/${productId}`, { method: 'DELETE' });
        showToast('已从购物车移除', 'success');
        loadCart();
    } catch (error) {
        showToast('移除失败', 'error');
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
        showToast('清空失败', 'error');
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
    const date = document.getElementById('analysisDate').value || new Date().toISOString().split('T')[0];
    
    try {
        const data = await apiRequest(`/analysis/daily/${date}`);
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
                <div class="stat-value">${data.saleCount || 0}</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-dollar-sign"></i></div>
                <div class="stat-label">销售金额</div>
                <div class="stat-value">${formatMoney(data.saleAmount || 0)}</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-undo"></i></div>
                <div class="stat-label">退货数量</div>
                <div class="stat-value">${data.refundCount || 0}</div>
            </div>
            <div class="stat-card">
                <div class="stat-icon"><i class="fas fa-money-bill-wave"></i></div>
                <div class="stat-label">退货金额</div>
                <div class="stat-value">${formatMoney(data.refundAmount || 0)}</div>
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
