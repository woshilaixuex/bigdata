// 全局变量
const API_BASE = '/sales/api';

function formatMoney(value) {
    const num = Number(value);
    if (!Number.isFinite(num)) {
        return '0.00';
    }
    return num.toFixed(2);
}

async function fetchJson(url, options) {
    const response = await fetch(url, options);
    const text = await response.text();

    if (!response.ok) {
        const snippet = text ? text.slice(0, 300) : '';
        throw new Error(`HTTP ${response.status} ${response.statusText}: ${snippet}`);
    }

    if (!text) {
        return null;
    }

    try {
        return JSON.parse(text);
    } catch (e) {
        throw new Error(`Invalid JSON: ${text.slice(0, 300)}`);
    }
}

// 页面切换函数
function showPage(pageId) {
    // 隐藏所有页面
    const pages = document.querySelectorAll('.page-container');
    pages.forEach(page => {
        page.classList.remove('active');
    });
    
    // 显示目标页面
    document.getElementById(pageId).classList.add('active');
    
    // 更新导航激活状态
    const navItems = document.querySelectorAll('.nav-item');
    navItems.forEach(item => {
        item.classList.remove('active');
    });
    
    // 设置当前导航项为激活状态
    event.target.closest('.nav-item').classList.add('active');
    
    // 根据页面加载相应数据
    loadPageData(pageId);
}

// 加载页面数据
function loadPageData(pageId) {
    switch(pageId) {
        case 'home':
            loadDashboardData();
            break;
        case 'products':
            loadProductsData();
            break;
        case 'orders':
            loadOrdersData();
            break;
        case 'cart':
            loadCartData();
            break;
        case 'analysis':
            loadAnalysisData();
            break;
        case 'users':
            loadUsersData();
            break;
        case 'ranking':
            loadRankingData();
            break;
    }
}

function escapeHtml(text) {
    const str = String(text ?? '');
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function renderJson(containerId, data) {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = `<pre style="white-space: pre-wrap; word-break: break-word; background: #f8fafc; border: 1px solid #e5e7eb; padding: 12px; border-radius: 8px;">${escapeHtml(JSON.stringify(data, null, 2))}</pre>`;
}

function getInputValue(id) {
    const el = document.getElementById(id);
    return el ? el.value.trim() : '';
}

function setText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text;
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    try {
        const d = new Date(dateStr);
        if (Number.isNaN(d.getTime())) return String(dateStr);
        return d.toLocaleDateString('zh-CN');
    } catch (e) {
        return String(dateStr);
    }
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '未知时间';
    try {
        const date = new Date(dateTimeStr);
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (error) {
        return dateTimeStr;
    }
}

function renderUserDetail(user) {
    const el = document.getElementById('userDetailCard');
    if (!el) return;

    if (!user) {
        el.innerHTML = '<div style="color:#6b7280;">选择一个用户查看详情</div>';
        return;
    }

    const statusText = user?.status === 1 ? '正常' : user?.status === 0 ? '禁用' : '-';
    el.innerHTML = `
        <div class="kv">
            <div class="k">username</div><div>${escapeHtml(user?.username ?? '-')}</div>
            <div class="k">nickname</div><div>${escapeHtml(user?.nickname ?? '-')}</div>
            <div class="k">phone</div><div>${escapeHtml(user?.phone ?? '-')}</div>
            <div class="k">email</div><div>${escapeHtml(user?.email ?? '-')}</div>
            <div class="k">gender</div><div>${escapeHtml(user?.gender ?? '-')}</div>
            <div class="k">birthday</div><div>${escapeHtml(formatDate(user?.birthday))}</div>
            <div class="k">register_time</div><div>${escapeHtml(formatDateTime(user?.registerTime))}</div>
            <div class="k">status</div><div>${escapeHtml(statusText)}</div>
        </div>
    `;
}

// 加载仪表板数据
async function loadDashboardData() {
    try {
        // 加载看板数据
        const dashboardData = await fetchJson(`${API_BASE}/analysis/dashboard`);
        
        document.getElementById('todaySalesAmount').textContent = `¥ ${formatMoney(dashboardData.totalAmount)}`;
        document.getElementById('todayOrderCount').textContent = dashboardData.orderCount;
        document.getElementById('todayUserCount').textContent = dashboardData.userCount;
        
        // 加载热销商品
        loadHotProducts();
        
        // 加载最近订单
        loadRecentOrders();
        
        // 加载低库存商品数量
        loadLowStockCount();
        
    } catch (error) {
        console.error('加载仪表板数据失败:', error);
        showToast('加载数据失败', 'error');
    }
}

// 加载热销商品
async function loadHotProducts() {
    try {
        const products = await fetchJson(`${API_BASE}/products/hot?limit=3`);
        
        const container = document.getElementById('hotProducts');
        container.innerHTML = '';
        
        products.forEach(product => {
            const productCard = createProductCard(product);
            container.appendChild(productCard);
        });
        
    } catch (error) {
        console.error('加载热销商品失败:', error);
        document.getElementById('hotProducts').innerHTML = '<p>加载失败</p>';
    }
}

// 创建商品卡片
function createProductCard(product) {
    const card = document.createElement('div');
    card.className = 'product-card';
    
    const imageUrl = product.images && product.images.length > 0 ? product.images[0] : 
                    'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?ixlib=rb-1.2.1&auto=format&fit=crop&w=500&q=80';
    
    card.innerHTML = `
        <img src="${imageUrl}" alt="${product.name}" class="product-image">
        <div class="product-info">
            <div class="product-name">${product.name}</div>
            <div class="product-price">¥ ${formatMoney(product.price)}</div>
            <div class="product-stock">库存: ${product.realTimeStock || product.totalStock || 0}件 | 已售: ${product.saleCount || 0}件</div>
            <div style="display:flex; gap:10px; margin-top:10px;">
                <button class="btn btn-primary" onclick="viewProduct('${product.productId}')">
                    <i class="fas fa-eye"></i> 查看详情
                </button>
                <button class="btn btn-danger" onclick="deleteProduct('${product.productId}')">
                    <i class="fas fa-trash"></i> 删除
                </button>
            </div>
        </div>
    `;
    
    return card;
}

// 加载最近订单
async function loadRecentOrders() {
    try {
        const orders = await fetchJson(`${API_BASE}/orders/recent?limit=5`);
        
        const tbody = document.getElementById('recentOrders');
        tbody.innerHTML = '';
        
        orders.forEach(order => {
            const row = createOrderRow(order);
            tbody.appendChild(row);
        });
        
    } catch (error) {
        console.error('加载最近订单失败:', error);
        document.getElementById('recentOrders').innerHTML = '<tr><td colspan="6">加载失败</td></tr>';
    }
}

// 创建订单行
function createOrderRow(order) {
    const row = document.createElement('tr');
    
    const statusClass = getStatusClass(order.status);
    const statusText = getStatusText(order.status);
    
    row.innerHTML = `
        <td>${order.orderId}</td>
        <td>${order.receiver || '未知客户'}</td>
        <td>¥ ${formatMoney(order.actualAmount)}</td>
        <td><span class="status-badge ${statusClass}">${statusText}</span></td>
        <td>${formatDateTime(order.createTime)}</td>
        <td>
            <button class="btn" style="padding: 5px 10px; font-size: 12px;" onclick="viewOrder('${order.orderId}')">
                <i class="fas fa-eye"></i>
            </button>
        </td>
    `;
    
    return row;
}

// 加载低库存商品数量
async function loadLowStockCount() {
    try {
        const products = await fetchJson(`${API_BASE}/products/low-stock?limit=100`);
        
        document.getElementById('lowStockCount').textContent = products.length;
        
    } catch (error) {
        console.error('加载低库存商品数量失败:', error);
        document.getElementById('lowStockCount').textContent = '?';
    }
}

// 加载商品数据
async function loadProductsData() {
    const container = document.querySelector('#products .loading');
    if (container) {
        container.parentElement.innerHTML = '<div class="loading"><div class="loading-spinner"></div></div>';
    }
    
    try {
        const products = await fetchJson(`${API_BASE}/products?limit=20`);
        
        const pageContainer = document.getElementById('products');
        pageContainer.innerHTML = `
            <h2 class="page-title">
                <i class="fas fa-boxes"></i>
                商品管理
            </h2>
            <div class="product-grid">
                ${products.map(product => createProductCard(product).outerHTML).join('')}
            </div>
        `;
        
    } catch (error) {
        console.error('加载商品数据失败:', error);
        document.getElementById('products').innerHTML = '<h2 class="page-title"><i class="fas fa-boxes"></i> 商品管理</h2><p>加载失败</p>';
    }
}

// 加载订单数据
async function loadOrdersData() {
    const container = document.querySelector('#orders .loading');
    if (container) {
        container.parentElement.innerHTML = '<div class="loading"><div class="loading-spinner"></div></div>';
    }
    
    try {
        const orders = await fetchJson(`${API_BASE}/orders/recent?limit=20`);
        
        const pageContainer = document.getElementById('orders');
        pageContainer.innerHTML = `
            <h2 class="page-title">
                <i class="fas fa-shopping-bag"></i>
                订单管理
            </h2>
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>订单号</th>
                            <th>客户</th>
                            <th>金额</th>
                            <th>状态</th>
                            <th>时间</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${orders.map(order => createOrderRow(order).outerHTML).join('')}
                    </tbody>
                </table>
            </div>
        `;
        
    } catch (error) {
        console.error('加载订单数据失败:', error);
        document.getElementById('orders').innerHTML = '<h2 class="page-title"><i class="fas fa-shopping-bag"></i> 订单管理</h2><p>加载失败</p>';
    }
}

// 加载购物车数据
async function loadCartData() {
    const container = document.querySelector('#cart .loading');
    if (container) {
        container.parentElement.innerHTML = '<div class="loading"><div class="loading-spinner"></div></div>';
    }
    
    try {
        // 模拟用户ID，实际应该从会话中获取
        const userId = 'U001';
        const cartItems = await fetchJson(`${API_BASE}/cart/${userId}`);
        
        const pageContainer = document.getElementById('cart');
        pageContainer.innerHTML = `
            <h2 class="page-title">
                <i class="fas fa-shopping-cart"></i>
                购物车
            </h2>
            <div class="table-container">
                <table class="table">
                    <thead>
                        <tr>
                            <th>商品</th>
                            <th>单价</th>
                            <th>数量</th>
                            <th>小计</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${cartItems.map(item => createCartItemRow(item).outerHTML).join('')}
                    </tbody>
                </table>
            </div>
        `;
        
        updateCartCount(cartItems.length);
        
    } catch (error) {
        console.error('加载购物车数据失败:', error);
        document.getElementById('cart').innerHTML = '<h2 class="page-title"><i class="fas fa-shopping-cart"></i> 购物车</h2><p>加载失败</p>';
    }
}

// 创建购物车行
function createCartItemRow(item) {
    const row = document.createElement('tr');
    
    row.innerHTML = `
        <td>
            <div style="display: flex; align-items: center; gap: 10px;">
                <img src="https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?ixlib=rb-1.2.1&auto=format&fit=crop&w=100&q=80" 
                     style="width: 60px; height: 60px; border-radius: 4px; object-fit: cover;">
                <span>${item.name || item.productId}</span>
            </div>
        </td>
        <td>¥ ${(item.price || 0).toFixed(2)}</td>
        <td>${item.quantity || 1}</td>
        <td>¥ ${((item.price || 0) * (item.quantity || 1)).toFixed(2)}</td>
        <td>
            <button class="btn btn-danger" style="padding: 5px 10px; font-size: 12px;" 
                    onclick="removeFromCart('${item.productId}')">
                <i class="fas fa-trash"></i>
            </button>
        </td>
    `;
    
    return row;
}

// 加载分析数据
async function loadAnalysisData() {
    const container = document.querySelector('#analysis .loading');
    if (container) {
        container.parentElement.innerHTML = '<div class="loading"><div class="loading-spinner"></div></div>';
    }
    
    try {
        // 加载今日销售数据
        const today = new Date().toISOString().split('T')[0];
        const salesData = await fetchJson(`${API_BASE}/analysis/daily/${today}`);
        
        const pageContainer = document.getElementById('analysis');
        pageContainer.innerHTML = `
            <h2 class="page-title">
                <i class="fas fa-chart-line"></i>
                销售分析
            </h2>
            <div class="dashboard-cards">
                <div class="card">
                    <div class="card-title">今日销售额</div>
                    <div class="card-value">¥ ${salesData.netAmount ? salesData.netAmount.toFixed(2) : '0.00'}</div>
                </div>
                <div class="card">
                    <div class="card-title">今日订单数</div>
                    <div class="card-value">${salesData.netCount || 0}</div>
                </div>
                <div class="card">
                    <div class="card-title">退货率</div>
                    <div class="card-value">${salesData.refundRate ? salesData.refundRate.toFixed(2) + '%' : '0%'}</div>
                </div>
            </div>
            <div style="background: white; border-radius: 12px; padding: 20px; margin-top: 20px;">
                <h3>销售数据详情</h3>
                <p>销售数量: ${salesData.saleCount || 0}</p>
                <p>销售金额: ¥ ${salesData.saleAmount ? salesData.saleAmount.toFixed(2) : '0.00'}</p>
                <p>退货数量: ${salesData.refundCount || 0}</p>
                <p>退货金额: ¥ ${salesData.refundAmount ? salesData.refundAmount.toFixed(2) : '0.00'}</p>
            </div>
        `;
        
    } catch (error) {
        console.error('加载分析数据失败:', error);
        document.getElementById('analysis').innerHTML = '<h2 class="page-title"><i class="fas fa-chart-line"></i> 销售分析</h2><p>加载失败</p>';
    }
}

// 查看商品详情
function openProductModal(title, html) {
    const modal = document.getElementById('productModal');
    const titleEl = document.getElementById('productModalTitle');
    const bodyEl = document.getElementById('productModalBody');
    if (!modal || !titleEl || !bodyEl) return;
    titleEl.textContent = title;
    bodyEl.innerHTML = html;
    modal.classList.add('active');
}

function closeProductModal() {
    const modal = document.getElementById('productModal');
    if (!modal) return;
    modal.classList.remove('active');
}

async function viewProduct(productId) {
    try {
        if (!productId) {
            showToast('productId 为空', 'warning');
            return;
        }
        const p = await fetchJson(`${API_BASE}/products/${encodeURIComponent(productId)}`);
        const img = (p.images && p.images.length > 0) ? p.images[0] : '';

        const html = `
            <div style="display:flex; gap:16px; flex-wrap:wrap;">
                <div style="width:220px;">
                    ${img ? `<img src="${escapeHtml(img)}" style="width:220px; height:220px; object-fit:cover; border-radius: 10px; border: 1px solid #e5e7eb;">` : ''}
                </div>
                <div style="flex:1; min-width:240px;">
                    <div class="kv">
                        <div class="k">productId</div><div>${escapeHtml(p.productId ?? '-')}</div>
                        <div class="k">name</div><div>${escapeHtml(p.name ?? '-')}</div>
                        <div class="k">category</div><div>${escapeHtml(p.category ?? '-')}</div>
                        <div class="k">price</div><div>¥ ${escapeHtml(formatMoney(p.price))}</div>
                        <div class="k">status</div><div>${escapeHtml(String(p.status ?? '-'))}</div>
                        <div class="k">realTimeStock</div><div>${escapeHtml(String(p.realTimeStock ?? '-'))}</div>
                        <div class="k">totalStock</div><div>${escapeHtml(String(p.totalStock ?? '-'))}</div>
                        <div class="k">saleCount</div><div>${escapeHtml(String(p.saleCount ?? '-'))}</div>
                        <div class="k">viewCount</div><div>${escapeHtml(String(p.viewCount ?? '-'))}</div>
                        <div class="k">description</div><div>${escapeHtml(p.description ?? '-')}</div>
                    </div>
                </div>
            </div>
        `;

        openProductModal(`商品详情：${p.name || p.productId || ''}`, html);
    } catch (e) {
        console.error(e);
        showToast('获取商品详情失败', 'error');
    }
}

async function deleteProduct(productId) {
    try {
        if (!productId) {
            showToast('productId 为空', 'warning');
            return;
        }
        const ok = confirm(`确认删除商品：${productId}？`);
        if (!ok) return;

        await fetchJson(`${API_BASE}/products/${encodeURIComponent(productId)}`, { method: 'DELETE' });
        showToast('删除成功', 'success');
        loadProductsData();
    } catch (e) {
        console.error(e);
        showToast('删除失败', 'error');
    }
}

// 查看订单详情
function viewOrder(orderId) {
    showToast(`查看订单详情: ${orderId}`, 'success');
}

// 从购物车移除
async function removeFromCart(productId) {
    try {
        const userId = 'U001';
        const response = await fetch(`${API_BASE}/cart/${userId}/items/${productId}`, {
            method: 'DELETE'
        });
        
        if (response.ok) {
            showToast('商品已从购物车移除', 'success');
            loadCartData(); // 重新加载购物车
        } else {
            showToast('移除失败', 'error');
        }
    } catch (error) {
        console.error('移除购物车商品失败:', error);
        showToast('移除失败', 'error');
    }
}

// 更新购物车数量
function updateCartCount(count) {
    const cartCountElement = document.querySelector('.cart-count');
    if (cartCountElement) {
        cartCountElement.textContent = count;
    }
}

// 获取状态样式类
function getStatusClass(status) {
    const statusMap = {
        1: 'status-warning',  // 待付款
        2: 'status-processing', // 待发货
        3: 'status-processing', // 已发货
        4: 'status-success',   // 已完成
        5: 'status-warning'    // 已取消
    };
    return statusMap[status] || 'status-warning';
}

// 获取状态文本
function getStatusText(status) {
    const statusMap = {
        1: '待付款',
        2: '待发货',
        3: '已发货',
        4: '已完成',
        5: '已取消'
    };
    return statusMap[status] || '未知';
}

// 格式化日期时间

// 显示提示信息
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    const toastMessage = document.getElementById('toastMessage');
    
    // 设置样式和消息
    toast.className = `toast ${type}`;
    toastMessage.textContent = message;
    
    // 设置图标
    const icon = toast.querySelector('i');
    icon.className = type === 'success' ? 'fas fa-check-circle' : 
                   type === 'error' ? 'fas fa-exclamation-circle' : 
                   'fas fa-info-circle';
    
    // 显示提示
    toast.classList.add('show');
    
    // 3秒后自动隐藏
    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    setText('navUserName', '管理员');

    // 加载首页数据
    loadDashboardData();
    
    // 设置定时刷新（每30秒）
    setInterval(() => {
        const activePage = document.querySelector('.page-container.active');
        if (activePage) {
            loadPageData(activePage.id);
        }
    }, 30000);
});

// 导出函数供全局使用
window.showPage = showPage;
window.viewProduct = viewProduct;
window.viewOrder = viewOrder;
window.removeFromCart = removeFromCart;
window.deleteProduct = deleteProduct;
window.closeProductModal = closeProductModal;

async function loadUsersData() {
    const pageContainer = document.getElementById('users');
    pageContainer.innerHTML = `
        <h2 class="page-title">
            <i class="fas fa-user"></i>
            用户管理
        </h2>
        <div class="table-container">
            <table class="table">
                <thead>
                    <tr>
                        <th>用户名</th>
                        <th>昵称</th>
                        <th>手机号</th>
                        <th>邮箱</th>
                        <th>性别</th>
                        <th>生日</th>
                        <th>注册时间</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody id="usersTableBody">
                    <tr><td colspan="9" style="text-align:center; padding: 30px; color:#6b7280;">加载中...</td></tr>
                </tbody>
            </table>
        </div>

        <div class="card" style="margin-top: 20px;">
            <div class="card-title">用户详情</div>
            <div id="userDetailCard" style="margin-top: 10px;">
                <div style="color:#6b7280;">选择一个用户查看详情</div>
            </div>
        </div>
    `;

    loadUsersTable();
}

async function loadUsersTable() {
    try {
        const status = 1;
        const limit = 20;
        const users = await fetchJson(`${API_BASE}/users/status/${encodeURIComponent(String(status))}?limit=${encodeURIComponent(String(limit))}`);

        const tbody = document.getElementById('usersTableBody');
        if (!tbody) return;
        if (!users || users.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9" style="text-align:center; padding: 30px; color:#6b7280;">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = users.map(u => {
            const statusText = u.status === 1 ? '正常' : u.status === 0 ? '禁用' : '未知';
            const badge = u.status === 1 ? 'status-success' : 'status-warning';
            return `
                <tr>
                    <td>${escapeHtml(u.username ?? '-') }</td>
                    <td>${escapeHtml(u.nickname ?? '-') }</td>
                    <td>${escapeHtml(u.phone ?? '-') }</td>
                    <td>${escapeHtml(u.email ?? '-') }</td>
                    <td>${escapeHtml(u.gender ?? '-') }</td>
                    <td>${escapeHtml(formatDate(u.birthday))}</td>
                    <td>${escapeHtml(formatDateTime(u.registerTime))}</td>
                    <td><span class="status-badge ${badge}">${escapeHtml(statusText)}</span></td>
                    <td>
                        <button class="btn" style="padding: 6px 10px; font-size: 12px;" onclick="viewUser('${escapeHtml(u.userId ?? '')}')"><i class="fas fa-eye"></i></button>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        console.error(e);
        const tbody = document.getElementById('usersTableBody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="9" style="text-align:center; padding: 30px; color:#ef4444;">加载失败</td></tr>';
        }
        showToast('加载用户失败', 'error');
    }
}

async function doLogin() {
    try {
        showToast('管理员无需登录', 'warning');
    } catch (e) {
        console.error(e);
        showToast('操作失败', 'error');
    }
}

async function validateSession() {
    try {
        showToast('管理员无需验证会话', 'warning');
    } catch (e) {
        console.error(e);
        showToast('操作失败', 'error');
    }
}

async function getMe() {
    try {
        showToast('管理员无需登录', 'warning');
    } catch (e) {
        console.error(e);
        showToast('操作失败', 'error');
    }
}

async function logout() {
    try {
        showToast('管理员无需登出', 'warning');
    } catch (e) {
        console.error(e);
        showToast('操作失败', 'error');
    }
}

async function viewUser(userId) {
    try {
        if (!userId) {
            showToast('userId 为空', 'warning');
            return;
        }
        const user = await fetchJson(`${API_BASE}/users/${encodeURIComponent(userId)}`);
        renderUserDetail(user);
        showToast('已展示用户信息', 'success');
    } catch (e) {
        console.error(e);
        showToast('获取用户失败', 'error');
    }
}

async function loadRankingData() {
    const pageContainer = document.getElementById('ranking');
    pageContainer.innerHTML = `
        <h2 class="page-title">
            <i class="fas fa-trophy"></i>
            热销商品榜
        </h2>
        <div class="card" style="margin-bottom: 16px;">
            <div class="card-title">榜单设置</div>
            <div style="display:flex; gap:10px; margin-top:10px; flex-wrap:wrap; align-items:center;">
                <input id="hotLimit" placeholder="limit，默认 10" style="width:160px; padding:10px; border:1px solid #e5e7eb; border-radius:8px;">
                <button class="btn btn-primary" onclick="loadHotRanking()"><i class="fas fa-sync"></i> 刷新榜单</button>
                <div style="color:#6b7280; font-size:13px;">说明：榜单数据来自后端热销接口（Redis 排行榜聚合）。</div>
            </div>
        </div>

        <div class="table-container">
            <table class="table">
                <thead>
                    <tr>
                        <th style="width:70px;">排名</th>
                        <th>商品</th>
                        <th style="width:120px;">价格</th>
                        <th style="width:120px;">销量</th>
                        <th style="width:120px;">库存</th>
                        <th style="width:120px;">操作</th>
                    </tr>
                </thead>
                <tbody id="hotRankingBody">
                    <tr><td colspan="6" style="text-align:center; padding: 30px; color:#6b7280;">加载中...</td></tr>
                </tbody>
            </table>
        </div>
    `;

    loadHotRanking();
}

async function loadHotRanking() {
    try {
        const limitStr = getInputValue('hotLimit');
        const limit = limitStr ? Number(limitStr) : 10;
        const products = await fetchJson(`${API_BASE}/products/hot?limit=${encodeURIComponent(String(limit))}`);

        const tbody = document.getElementById('hotRankingBody');
        if (!tbody) return;
        if (!products || products.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 30px; color:#6b7280;">暂无数据</td></tr>';
            return;
        }

        tbody.innerHTML = products.map((p, idx) => {
            const rank = idx + 1;
            const badgeColor = rank === 1 ? '#f59e0b' : rank === 2 ? '#9ca3af' : rank === 3 ? '#b45309' : '#6b7280';
            const stock = p.realTimeStock ?? p.totalStock ?? '-';
            return `
                <tr>
                    <td>
                        <span style="display:inline-flex; width:32px; height:32px; border-radius:999px; align-items:center; justify-content:center; background:${badgeColor}; color:white; font-weight:700;">
                            ${rank}
                        </span>
                    </td>
                    <td>
                        <div style="display:flex; flex-direction:column; gap:4px;">
                            <div style="font-weight:700;">${escapeHtml(p.name ?? '-')}</div>
                            <div style="font-size:12px; color:#6b7280;">${escapeHtml(p.productId ?? '')} · ${escapeHtml(p.category ?? '-')}
                            </div>
                        </div>
                    </td>
                    <td>¥ ${escapeHtml(formatMoney(p.price))}</td>
                    <td>${escapeHtml(String(p.saleCount ?? 0))}</td>
                    <td>${escapeHtml(String(stock))}</td>
                    <td>
                        <button class="btn" style="padding: 6px 10px; font-size: 12px;" onclick="viewProduct('${escapeHtml(p.productId ?? '')}')">
                            <i class="fas fa-eye"></i>
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

    } catch (e) {
        console.error(e);
        const tbody = document.getElementById('hotRankingBody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding: 30px; color:#ef4444;">加载失败</td></tr>';
        }
        showToast('加载热销榜失败', 'error');
    }
}

window.loadHotRanking = loadHotRanking;

window.loadUsersTable = loadUsersTable;
window.viewUser = viewUser;
