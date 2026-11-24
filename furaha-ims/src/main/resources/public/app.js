/**
 * FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)
 * Front-end Application Controller (STU 3101 · Furaha Mini Mart)
 */

// Application State
let authToken = localStorage.getItem('fmm_token') || null;
let currentUser = null;
let productsCache = [];
let customersCache = [];
let suppliersCache = [];
let unitsCache = [];
let cart = []; // Array of { productId, name, sku, price, qty, lineTotal }

// Utility Selectors and Formatters
const $ = id => document.getElementById(id);
const formatKes = num => 'KES ' + Number(num || 0).toLocaleString('en-KE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const formatDate = dStr => dStr ? new Date(dStr).toLocaleString('en-GB') : '-';

// ============================================================================
// API CLIENT
// ============================================================================
async function api(endpoint, options = {}) {
  const url = '/api/' + endpoint;
  const headers = { 'Content-Type': 'application/json' };
  if (authToken) headers['Authorization'] = 'Bearer ' + authToken;

  const config = {
    method: options.method || 'GET',
    headers: headers
  };

  if (options.body) {
    config.body = JSON.stringify(options.body);
  }

  try {
    const res = await fetch(url, config);
    if (res.status === 401) {
      logout();
      throw new Error('Session expired. Please sign in again.');
    }
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error || 'Request failed with status ' + res.status);
    }
    return data;
  } catch (err) {
    console.error('API Error (' + endpoint + '):', err);
    throw err;
  }
}

// ============================================================================
// AUTHENTICATION & SESSION MANAGEMENT
// ============================================================================
$('loginForm').onsubmit = async function(e) {
  e.preventDefault();
  const alertEl = $('loginAlert');
  alertEl.style.display = 'none';

  const username = $('loginUsername').value.trim();
  const password = $('loginPassword').value;

  try {
    const res = await api('login', {
      method: 'POST',
      body: { username, password }
    });

    authToken = res.token;
    localStorage.setItem('fmm_token', authToken);
    currentUser = {
      userId: res.userId,
      role: res.role,
      username: res.username,
      displayName: res.displayName,
      isAdmin: res.role === 'ADMINISTRATOR'
    };
    localStorage.setItem('fmm_user', JSON.stringify(currentUser));

    initAppWorkspace();
  } catch (err) {
    alertEl.textContent = err.message || 'Sign-in failed. Check your details or ask the Administrator.';
    alertEl.style.display = 'block';
  }
};

$('togglePasswordBtn').onclick = function() {
  const input = $('loginPassword');
  input.type = input.type === 'password' ? 'text' : 'password';
};

function fillLogin(u, p) {
  $('loginUsername').value = u;
  $('loginPassword').value = p;
  $('loginAlert').style.display = 'none';
}

function logout() {
  authToken = null;
  currentUser = null;
  localStorage.removeItem('fmm_token');
  localStorage.removeItem('fmm_user');
  $('appScreen').style.display = 'none';
  $('loginScreen').style.display = 'flex';
  $('loginAlert').style.display = 'none';
  $('loginUsername').value = '';
  $('loginPassword').value = '';
}

// ============================================================================
// WORKSPACE INITIALIZATION & ROLE-BASED UI
// ============================================================================
function initAppWorkspace() {
  $('loginScreen').style.display = 'none';
  $('appScreen').style.display = 'flex';

  // Display user badge
  $('userNameRole').textContent = currentUser.displayName + ' (' + currentUser.role + ')';
  $('posCashierTag').textContent = 'Cashier: ' + currentUser.displayName;

  // Enforce role-based tab visibility
  const adminElements = document.querySelectorAll('.admin-only');
  adminElements.forEach(el => {
    el.style.display = currentUser.isAdmin ? '' : 'none';
  });

  // Load initial global caches
  loadCatalogData();
  loadCustomersData();
  loadSuppliersAndUnits();

  // Initialize auto-print preference (defaults to true)
  const autoPrintSaved = localStorage.getItem('fmm_autoprint');
  if ($('posAutoPrintToggle')) {
    $('posAutoPrintToggle').checked = autoPrintSaved === null ? true : autoPrintSaved === 'true';
  }

  // Load Dashboard by default
  switchTab('tabDashboard');
}

function switchTab(tabId) {
  document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
  document.querySelectorAll('.nav-tab').forEach(btn => btn.classList.remove('active'));

  const targetTab = $(tabId);
  if (targetTab) targetTab.classList.add('active');

  const navBtn = document.querySelector(`.nav-tab[data-tab="${tabId}"]`);
  if (navBtn) navBtn.classList.add('active');

  // Trigger tab-specific refresh
  if (tabId === 'tabDashboard') loadDashboardOverview();
  else if (tabId === 'tabPOS') renderPosCart();
  else if (tabId === 'tabProducts') renderProductsTable();
  else if (tabId === 'tabCounts') loadCountSessionView();
  else if (tabId === 'tabCustomers') renderCustomersTable();
  else if (tabId === 'tabApprovals') loadPendingApprovals();
  else if (tabId === 'tabReplenishment') loadReplenishmentGuidance();
  else if (tabId === 'tabReports') loadReportsProfit();
  else if (tabId === 'tabUsers') loadUsersTable();
  else if (tabId === 'tabAudit') loadAuditTrail();
}

// ============================================================================
// DASHBOARD CONTROLLER (SDS §4.3.2 Figure 9)
// ============================================================================
async function loadDashboardOverview() {
  try {
    const summary = await api('reports/summary');
    if (summary && summary.length > 0) {
      const s = summary[0];
      $('kpiSalesToday').textContent = formatKes(s.sales_today);
      $('kpiReceiptsToday').textContent = s.receipts_today + ' transactions';
      $('kpiLowStock').textContent = s.low_stock_count;
      $('kpiPendingAdj').textContent = s.pending_adjustments_count;
      $('kpiCreditOutstanding').textContent = formatKes(s.credit_outstanding);
      $('kpiStockRetail').textContent = formatKes(s.stock_retail_value);
      $('kpiStockCost').textContent = 'Cost: ' + formatKes(s.stock_cost_value);

      // Approvals badge in nav
      const badge = $('pendingApprovalsBadge');
      if (s.pending_adjustments_count > 0) {
        badge.textContent = s.pending_adjustments_count;
        badge.style.display = 'inline-block';
      } else {
        badge.style.display = 'none';
      }
    }

    // Load recent sales
    const sales = await api('sales');
    const recentBody = $('dashRecentSalesTable');
    if (sales.length === 0) {
      recentBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No sales recorded yet today.</td></tr>';
    } else {
      recentBody.innerHTML = sales.slice(0, 7).map(s => `
        <tr>
          <td><b>${s.receipt_no}</b></td>
          <td>${formatDate(s.sold_at)}</td>
          <td><span class="badge-tag">${s.payment_method || 'CASH'}</span></td>
          <td>${s.customer_name}</td>
          <td><b>${formatKes(s.total)}</b></td>
          <td>
            <button class="btn btn-sm btn-outline" onclick="openSaleReceipt(${s.sale_id})" title="Print or view receipt">
              🖨️ Receipt
            </button>
          </td>
        </tr>
      `).join('');
    }

    // Load low stock items in dashboard
    const lowStock = productsCache.filter(p => p.is_low_stock || p.quantity_on_hand <= p.reorder_level);
    const lowBody = $('dashLowStockTable');
    if (lowStock.length === 0) {
      lowBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">All products have healthy inventory levels!</td></tr>';
    } else {
      lowBody.innerHTML = lowStock.map(p => `
        <tr>
          <td><b>${p.product_name}</b></td>
          <td><code>${p.sku}</code></td>
          <td><span class="badge-tag badge-danger">${p.quantity_on_hand} ${p.unit_abbr || ''}</span></td>
          <td>${p.reorder_level}</td>
          <td>${p.lead_time_days || 3} days</td>
        </tr>
      `).join('');
    }
  } catch (err) {
    console.error('Failed to load dashboard summary:', err);
  }
}

// ============================================================================
// CATALOG DATA LOADER
// ============================================================================
async function loadCatalogData() {
  try {
    productsCache = await api('products');
    updateProductDropdowns();
  } catch (err) {
    console.error('Error loading products:', err);
  }
}

async function loadCustomersData() {
  try {
    customersCache = await api('customers');
    updateCustomerDropdowns();
  } catch (err) {
    console.error('Error loading customers:', err);
  }
}

async function loadSuppliersAndUnits() {
  try {
    suppliersCache = await api('suppliers');
    unitsCache = await api('units');
    updateSupplierAndUnitDropdowns();
  } catch (err) {
    console.error('Error loading suppliers/units:', err);
  }
}

function updateProductDropdowns() {
  const optionsHtml = '<option value="">-- Choose product --</option>' +
    productsCache.filter(p => p.is_active).map(p =>
      `<option value="${p.product_id}">${p.sku} · ${p.product_name} (${formatKes(p.selling_price)} | Stock: ${p.quantity_on_hand})</option>`
    ).join('');

  if ($('posProductDropdown')) $('posProductDropdown').innerHTML = optionsHtml;
  if ($('recvProductSelect')) $('recvProductSelect').innerHTML = optionsHtml;
}

function updateCustomerDropdowns() {
  const optionsHtml = '<option value="">Walk-in Customer (General)</option>' +
    customersCache.filter(c => c.is_active).map(c =>
      `<option value="${c.customer_id}">${c.customer_name} (Bal: ${formatKes(c.balance)} / Limit: ${formatKes(c.credit_limit)})</option>`
    ).join('');

  if ($('posCustomerSelect')) $('posCustomerSelect').innerHTML = optionsHtml;

  const repayOpts = '<option value="">-- Choose customer --</option>' +
    customersCache.filter(c => c.is_active).map(c =>
      `<option value="${c.customer_id}">${c.customer_name} (Balance: ${formatKes(c.balance)})</option>`
    ).join('');
  if ($('repayCustomerSelect')) $('repayCustomerSelect').innerHTML = repayOpts;
}

function updateSupplierAndUnitDropdowns() {
  if ($('recvSupplierSelect')) {
    $('recvSupplierSelect').innerHTML = '<option value="">-- Direct Delivery / Select Supplier --</option>' +
      suppliersCache.map(s => `<option value="${s.supplier_id}">${s.supplier_name}</option>`).join('');
  }
  if ($('modalProdSupplier')) {
    $('modalProdSupplier').innerHTML = '<option value="">-- Select Preferred Supplier --</option>' +
      suppliersCache.map(s => `<option value="${s.supplier_id}">${s.supplier_name}</option>`).join('');
  }
  if ($('modalProdUnit')) {
    $('modalProdUnit').innerHTML = unitsCache.map(u =>
      `<option value="${u.unit_id}">${u.unit_name} (${u.abbreviation})</option>`
    ).join('');
  }
}

// ============================================================================
// POINT OF SALE (POS) CONTROLLER (SDS §4.3.3 Figure 10 & 11)
// ============================================================================
// Scanner wedge / Enter key handler on search input
$('posSearchInput').addEventListener('keydown', function(e) {
  if (e.key === 'Enter') {
    e.preventDefault();
    const query = this.value.trim().toLowerCase();
    if (!query) return;

    // Search by exact SKU first, then by name substring
    const match = productsCache.find(p => p.sku.toLowerCase() === query) ||
                  productsCache.find(p => p.product_name.toLowerCase().includes(query));

    if (match) {
      const qty = parseFloat($('posAddQty').value) || 1;
      addItemToCart(match, qty);
      this.value = '';
    } else {
      alert('Product not found for: ' + query);
    }
  }
});

function addSelectedProductToCart() {
  const select = $('posProductDropdown');
  const prodId = select.value;
  if (!prodId) {
    alert('Please select a product or scan an item.');
    return;
  }
  const prod = productsCache.find(p => p.product_id == prodId);
  const qty = parseFloat($('posAddQty').value) || 1;
  if (prod) {
    addItemToCart(prod, qty);
    select.value = '';
  }
}

function addItemToCart(prod, qty) {
  if (qty <= 0) {
    alert('Quantity must be greater than zero.');
    return;
  }
  if (prod.quantity_on_hand <= 0) {
    alert('Warning: Product "' + prod.product_name + '" is out of stock!');
    return;
  }

  const existing = cart.find(item => item.productId === prod.product_id);
  if (existing) {
    const newQty = existing.qty + qty;
    if (newQty > prod.quantity_on_hand) {
      alert('Requested quantity exceeds available stock (' + prod.quantity_on_hand + ').');
      return;
    }
    existing.qty = newQty;
    existing.lineTotal = existing.qty * existing.price;
  } else {
    if (qty > prod.quantity_on_hand) {
      alert('Requested quantity exceeds available stock (' + prod.quantity_on_hand + ').');
      return;
    }
    cart.push({
      productId: prod.product_id,
      name: prod.product_name,
      sku: prod.sku,
      price: parseFloat(prod.selling_price),
      qty: qty,
      lineTotal: qty * parseFloat(prod.selling_price)
    });
  }

  renderPosCart();
}

function updateCartItemQty(index, newQtyStr) {
  const newQty = parseFloat(newQtyStr);
  if (isNaN(newQty) || newQty <= 0) {
    removeCartItem(index);
    return;
  }
  const item = cart[index];
  const prod = productsCache.find(p => p.product_id === item.productId);
  if (prod && newQty > prod.quantity_on_hand) {
    alert('Cannot exceed stock on hand (' + prod.quantity_on_hand + ').');
    renderPosCart();
    return;
  }
  item.qty = newQty;
  item.lineTotal = item.qty * item.price;
  renderPosCart();
}

function removeCartItem(index) {
  cart.splice(index, 1);
  renderPosCart();
}

function clearCart() {
  if (cart.length > 0 && !confirm('Are you sure you want to cancel this sale and clear the cart?')) {
    return;
  }
  cart = [];
  renderPosCart();
}

function getCartTotal() {
  return cart.reduce((acc, item) => acc + item.lineTotal, 0);
}

function renderPosCart() {
  const tbody = $('posCartBody');
  const countEl = $('posCartItemCount');
  const totalDisplay = $('posTotalDueDisplay');

  if (cart.length === 0) {
    tbody.innerHTML = '<tr id="emptyCartRow"><td colspan="6" class="text-center text-muted empty-cart-msg">Cart is empty. Scan an item or select from catalog above.</td></tr>';
    countEl.textContent = '0';
    totalDisplay.textContent = formatKes(0);
    calculateChange();
    return;
  }

  let total = 0;
  let itemsCount = 0;

  tbody.innerHTML = cart.map((item, idx) => {
    total += item.lineTotal;
    itemsCount += item.qty;
    return `
      <tr>
        <td><b>${item.name}</b></td>
        <td><code>${item.sku}</code></td>
        <td>${formatKes(item.price)}</td>
        <td>
          <input type="number" value="${item.qty}" min="0.1" step="any" class="form-control"
                 style="width:90px; text-align:center;" onchange="updateCartItemQty(${idx}, this.value)">
        </td>
        <td><b>${formatKes(item.lineTotal)}</b></td>
        <td>
          <button class="btn btn-sm btn-danger-outline" onclick="removeCartItem(${idx})">✕</button>
        </td>
      </tr>
    `;
  }).join('');

  countEl.textContent = itemsCount;
  totalDisplay.textContent = formatKes(total);
  calculateChange();
}

function updatePaymentModeUI() {
  const method = document.querySelector('input[name="payMethod"]:checked').value;
  $('cashFieldsGroup').style.display = method === 'CASH' ? 'block' : 'none';
  $('mpesaFieldsGroup').style.display = method === 'MOBILE_MONEY' ? 'block' : 'none';

  if (method === 'CREDIT') {
    checkCustomerCreditStatus();
  } else {
    $('customerCreditNotice').style.display = 'none';
  }
  calculateChange();
}

function calculateChange() {
  const method = document.querySelector('input[name="payMethod"]:checked').value;
  if (method !== 'CASH') return;

  const total = getCartTotal();
  const tenderedStr = $('posAmountTendered').value;
  const tendered = parseFloat(tenderedStr) || 0;
  const change = Math.max(0, tendered - total);

  $('posChangeDueDisplay').textContent = formatKes(change);
  $('posChangeDueDisplay').style.color = (tendered >= total && total > 0) ? 'var(--success)' : 'var(--text-muted)';
}

function checkCustomerCreditStatus() {
  const custId = $('posCustomerSelect').value;
  const notice = $('customerCreditNotice');
  const method = document.querySelector('input[name="payMethod"]:checked').value;

  if (!custId) {
    if (method === 'CREDIT') {
      notice.textContent = '⚠️ A customer account must be selected to charge credit.';
      notice.className = 'credit-notice alert-danger';
      notice.style.display = 'block';
    } else {
      notice.style.display = 'none';
    }
    return;
  }

  const cust = customersCache.find(c => c.customer_id == custId);
  if (!cust) return;

  const total = getCartTotal();
  const newBal = parseFloat(cust.balance) + total;
  const limit = parseFloat(cust.credit_limit);

  if (newBal > limit) {
    notice.innerHTML = `⚠️ <b>Credit limit exceeded!</b> Current: ${formatKes(cust.balance)} + Sale: ${formatKes(total)} = ${formatKes(newBal)} (Limit: ${formatKes(limit)})`;
    notice.className = 'credit-notice alert-danger';
    notice.style.display = 'block';
  } else {
    notice.innerHTML = `ℹ️ Customer Debt: ${formatKes(cust.balance)} / Limit: ${formatKes(limit)} (Available Credit: ${formatKes(limit - cust.balance)})`;
    notice.className = 'credit-notice alert-success';
    notice.style.display = 'block';
  }
}

async function handlePosCheckout(e) {
  e.preventDefault();
  const alertEl = $('posAlert');
  alertEl.style.display = 'none';

  if (cart.length === 0) {
    alertEl.textContent = 'Cart is empty. Add products before completing checkout.';
    alertEl.style.display = 'block';
    return;
  }

  const method = document.querySelector('input[name="payMethod"]:checked').value;
  const custId = $('posCustomerSelect').value || null;
  const tendered = parseFloat($('posAmountTendered').value) || getCartTotal();
  const ref = $('posMpesaRef').value.trim();
  const total = getCartTotal();

  if (method === 'CASH' && tendered < total) {
    alertEl.textContent = `Cash tendered (${formatKes(tendered)}) cannot be less than total due (${formatKes(total)}).`;
    alertEl.style.display = 'block';
    return;
  }

  if (method === 'CREDIT' && !custId) {
    alertEl.textContent = 'Please choose a customer account to process a credit sale.';
    alertEl.style.display = 'block';
    return;
  }

  const payload = {
    method: method,
    customerId: custId,
    amountTendered: tendered,
    reference: method === 'MOBILE_MONEY' ? ref : (method === 'CREDIT' ? 'CR-' + Date.now() : null),
    items: cart.map(i => ({ productId: i.productId, quantity: i.qty }))
  };

  try {
    $('posCompleteBtn').disabled = true;
    $('posCompleteBtn').textContent = 'Processing Sale...';

    const receiptData = await api('sales', {
      method: 'POST',
      body: payload
    });

    // Reset POS cart & reload products/dashboard
    cart = [];
    renderPosCart();
    $('posAmountTendered').value = '';
    $('posMpesaRef').value = '';
    loadCatalogData();
    loadCustomersData();
    loadDashboardOverview();

    // Immediately display and produce thermal receipt slip
    showReceiptModal(receiptData, true);
  } catch (err) {
    alertEl.textContent = err.message || 'Failed to complete transaction.';
    alertEl.style.display = 'block';
  } finally {
    $('posCompleteBtn').disabled = false;
    $('posCompleteBtn').textContent = '✅ Complete Sale & Print Receipt';
  }
}

// ============================================================================
// THERMAL RECEIPT MODAL & IMMEDIATE PRINTING
// ============================================================================
function isAutoPrintEnabled() {
  const toggle = $('posAutoPrintToggle');
  if (toggle) return toggle.checked;
  const saved = localStorage.getItem('fmm_autoprint');
  return saved === null ? true : saved === 'true';
}

function toggleAutoPrint(enabled) {
  localStorage.setItem('fmm_autoprint', enabled ? 'true' : 'false');
  const toggle = $('posAutoPrintToggle');
  if (toggle) toggle.checked = !!enabled;
}

async function openSaleReceipt(saleId) {
  try {
    const saleData = await api(`sales?saleId=${saleId}`);
    showReceiptModal(saleData, false);
  } catch (err) {
    alert('Unable to load receipt for sale #' + saleId + ': ' + err.message);
  }
}

function showReceiptModal(data, autoPrint = false) {
  if (!data) return;
  $('recReceiptNo').textContent = data.receiptNo || data.receipt_no || '-';
  $('recDate').textContent = data.date || data.sold_at || formatDate(new Date());
  $('recCashier').textContent = data.cashier || data.cashier_name || 'Cashier';
  $('recGrandTotal').textContent = formatKes(data.total);
  $('recSubtotal').textContent = formatKes(data.subtotal || data.total);
  $('recMethod').textContent = data.method || data.payment_method || 'CASH';

  const custName = data.customerName || data.customer_name;
  if (custName && custName !== 'Walk-in Customer (General)' && custName !== 'Walk-in Customer') {
    $('recCustomerRow').style.display = 'flex';
    $('recCustomer').textContent = custName;
  } else {
    $('recCustomerRow').style.display = 'none';
  }

  const method = (data.method || data.payment_method || 'CASH').toUpperCase();
  if (method === 'CASH') {
    $('recTenderedRow').style.display = 'flex';
    $('recChangeRow').style.display = 'flex';
    $('recTendered').textContent = formatKes(data.tendered ?? data.amount_tendered ?? data.total);
    $('recChange').textContent = formatKes(data.change ?? data.change_due ?? 0);
  } else {
    $('recTenderedRow').style.display = 'none';
    $('recChangeRow').style.display = 'none';
  }

  const ref = data.reference;
  if (ref) {
    $('recRefRow').style.display = 'flex';
    $('recRef').textContent = ref;
  } else {
    $('recRefRow').style.display = 'none';
  }

  const tbody = $('recItemsBody');
  const items = data.items || [];
  if (items.length > 0) {
    tbody.innerHTML = items.map(i => `
      <tr>
        <td>${i.name || i.product_name}</td>
        <td class="text-right">${i.quantity}</td>
        <td class="text-right">${formatKes(i.price || i.unit_selling_price)}</td>
        <td class="text-right">${formatKes(i.lineTotal || i.line_total)}</td>
      </tr>
    `).join('');
  } else {
    tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">Item details recorded in ledger</td></tr>';
  }

  $('receiptModal').style.display = 'flex';

  // Produce receipt immediately if auto-print is enabled
  if (autoPrint && isAutoPrintEnabled()) {
    setTimeout(() => {
      printReceiptSlip();
    }, 200);
  }
}

function closeReceiptModal() {
  $('receiptModal').style.display = 'none';
  // Refocus product search/scanner input for fast consecutive scanning
  const searchInput = $('posSearchInput');
  if (searchInput && $('tabPOS').classList.contains('active')) {
    searchInput.focus();
  }
}

function printReceiptSlip() {
  window.print();
}

// Global keyboard shortcuts for quick cashier operations
window.addEventListener('keydown', function(e) {
  const modal = $('receiptModal');
  if (modal && modal.style.display !== 'none' && modal.style.display !== '') {
    if (e.key === 'Escape' || e.key === 'Enter') {
      e.preventDefault();
      closeReceiptModal();
    } else if ((e.key === 'p' || e.key === 'P') && !e.ctrlKey && !e.metaKey) {
      e.preventDefault();
      printReceiptSlip();
    }
  }
});

// ============================================================================
// PRODUCTS CATALOG & MODAL
// ============================================================================
function renderProductsTable() {
  filterProductsTable();
}

function filterProductsTable() {
  const query = ($('prodSearchInput').value || '').trim().toLowerCase();
  const lowOnly = $('filterLowStockOnly').checked;

  let filtered = productsCache;
  if (query) {
    filtered = filtered.filter(p =>
      p.product_name.toLowerCase().includes(query) ||
      p.sku.toLowerCase().includes(query) ||
      (p.category && p.category.toLowerCase().includes(query))
    );
  }
  if (lowOnly) {
    filtered = filtered.filter(p => p.is_low_stock || p.quantity_on_hand <= p.reorder_level);
  }

  const tbody = $('productsTableBody');
  if (filtered.length === 0) {
    tbody.innerHTML = '<tr><td colspan="11" class="text-center text-muted">No products matched the filter criteria.</td></tr>';
    return;
  }

  tbody.innerHTML = filtered.map(p => `
    <tr>
      <td><code>${p.sku}</code></td>
      <td><b>${p.product_name}</b></td>
      <td><span class="badge-tag">${p.category || 'General'}</span></td>
      <td>${p.unit_abbr || 'pcs'}</td>
      <td><b>${formatKes(p.selling_price)}</b></td>
      <td>${formatKes(p.cost_price)}</td>
      <td>
        <span class="badge-tag ${p.is_low_stock ? 'badge-danger' : 'badge-success'}">
          ${p.quantity_on_hand}
        </span>
      </td>
      <td>${p.reorder_level}</td>
      <td>${p.supplier_name || 'None'}</td>
      <td>
        <span class="badge-tag ${p.is_active ? 'badge-success' : 'badge-warning'}">
          ${p.is_active ? 'Active' : 'Inactive'}
        </span>
      </td>
      <td class="admin-only">
        <button class="btn btn-sm btn-outline" onclick="openEditProductModal(${p.product_id})">✏️ Edit</button>
      </td>
    </tr>
  `).join('');
}

function openAddProductModal() {
  $('productModalTitle').textContent = 'Add New Product to Catalog';
  $('modalProdId').value = '';
  $('modalProdSku').value = 'FMM-' + (1000 + productsCache.length + 1);
  $('modalProdSku').disabled = false;
  $('modalProdName').value = '';
  $('modalProdCategory').value = 'General';
  $('modalProdSellingPrice').value = '';
  $('modalProdCostPrice').value = '';
  $('modalProdReorderLevel').value = '10';
  $('modalProdLeadTime').value = '3';
  $('productModal').style.display = 'flex';
}

function openEditProductModal(id) {
  const p = productsCache.find(x => x.product_id == id);
  if (!p) return;
  $('productModalTitle').textContent = 'Edit Product: ' + p.product_name;
  $('modalProdId').value = p.product_id;
  $('modalProdSku').value = p.sku;
  $('modalProdSku').disabled = true;
  $('modalProdName').value = p.product_name;
  $('modalProdCategory').value = p.category;
  $('modalProdSellingPrice').value = p.selling_price;
  $('modalProdCostPrice').value = p.cost_price;
  $('modalProdReorderLevel').value = p.reorder_level;
  $('modalProdLeadTime').value = p.lead_time_days || 3;
  $('productModal').style.display = 'flex';
}

function closeProductModal() {
  $('productModal').style.display = 'none';
}

async function saveProductModal(e) {
  e.preventDefault();
  const id = $('modalProdId').value;
  const isEdit = !!id;

  const payload = {
    productId: id || undefined,
    sku: $('modalProdSku').value.trim(),
    productName: $('modalProdName').value.trim(),
    category: $('modalProdCategory').value.trim(),
    unitId: $('modalProdUnit').value || 1,
    preferredSupplierId: $('modalProdSupplier').value || null,
    sellingPrice: $('modalProdSellingPrice').value,
    costPrice: $('modalProdCostPrice').value,
    reorderLevel: $('modalProdReorderLevel').value,
    leadTimeDays: $('modalProdLeadTime').value,
    isActive: true
  };

  try {
    await api('products', {
      method: isEdit ? 'PUT' : 'POST',
      body: payload
    });
    closeProductModal();
    loadCatalogData();
    alert('Product saved successfully.');
  } catch (err) {
    alert('Error saving product: ' + err.message);
  }
}

// ============================================================================
// PHYSICAL STOCK COUNT & VARIANCE SUBMISSION (SDS §4.3.4 Figure 12)
// ============================================================================
async function loadCountSessionView() {
  const tbody = $('countEntryBody');
  tbody.innerHTML = productsCache.map((p, idx) => `
    <tr>
      <td><code>${p.sku}</code></td>
      <td><b>${p.product_name}</b></td>
      <td><span id="sysQty_${p.product_id}">${p.quantity_on_hand}</span></td>
      <td>
        <input type="number" id="countedQty_${p.product_id}" class="form-control"
               value="${p.quantity_on_hand}" min="0" step="any"
               oninput="updateCountVariance(${p.product_id})">
      </td>
      <td><b id="varDisplay_${p.product_id}">0.000</b></td>
      <td>
        <input type="text" id="reason_${p.product_id}" class="form-control" placeholder="Required if variance exists">
      </td>
    </tr>
  `).join('');

  // Load past count history
  try {
    const counts = await api('stock/counts');
    const histBody = $('countHistoryTableBody');
    if (counts.length === 0) {
      histBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No historical count sessions recorded yet.</td></tr>';
    } else {
      histBody.innerHTML = counts.map(c => `
        <tr>
          <td><b>CNT-${String(c.count_id).padStart(4, '0')}</b></td>
          <td>${formatDate(c.counted_at)}</td>
          <td>${c.created_by_name}</td>
          <td><span class="badge-tag">${c.status}</span></td>
          <td>${c.notes || '-'}</td>
          <td>${c.total_items}</td>
          <td><b style="color:${c.total_variance < 0 ? 'var(--danger)' : 'var(--success)'}">${c.total_variance || 0}</b></td>
        </tr>
      `).join('');
    }
  } catch (err) {
    console.error('Error loading count history:', err);
  }
}

function updateCountVariance(prodId) {
  const sys = parseFloat($('sysQty_' + prodId).textContent) || 0;
  const counted = parseFloat($('countedQty_' + prodId).value) || 0;
  const variance = counted - sys;
  const disp = $('varDisplay_' + prodId);
  disp.textContent = (variance > 0 ? '+' : '') + variance.toFixed(3);
  disp.style.color = variance === 0 ? 'var(--text-muted)' : (variance < 0 ? 'var(--danger)' : 'var(--success)');
}

async function submitStockCountSession() {
  const notes = $('countNotes').value.trim() || 'Physical inventory audit';
  const items = [];

  for (const p of productsCache) {
    const countedInput = $('countedQty_' + p.product_id);
    if (!countedInput) continue;
    const counted = parseFloat(countedInput.value) || 0;
    const sys = parseFloat($('sysQty_' + p.product_id).textContent) || 0;
    const variance = counted - sys;
    const reasonInput = $('reason_' + p.product_id);
    const reason = reasonInput ? reasonInput.value.trim() : '';

    if (variance !== 0 && !reason) {
      alert(`Please enter a justification reason for variance on "${p.product_name}".`);
      reasonInput.focus();
      return;
    }

    items.push({
      productId: p.product_id,
      countedQty: counted,
      reason: reason || 'Count verified matched'
    });
  }

  if (items.length === 0) {
    alert('No items to submit.');
    return;
  }

  try {
    const res = await api('stock/counts', {
      method: 'POST',
      body: { notes, items }
    });
    alert('Stock count session submitted successfully! Proposed variances queued for Administrator approval.');
    $('countNotes').value = '';
    loadCountSessionView();
    loadDashboardOverview();
  } catch (err) {
    alert('Failed to submit count: ' + err.message);
  }
}

// ============================================================================
// ADMIN ADJUSTMENTS APPROVAL QUEUE (REQ-STK-007 - REQ-STK-009)
// ============================================================================
async function loadPendingApprovals() {
  try {
    const adjs = await api('stock/adjustments');
    const tbody = $('approvalsTableBody');

    if (adjs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" class="text-center text-muted">No stock adjustments requiring review.</td></tr>';
      return;
    }

    tbody.innerHTML = adjs.map(a => `
      <tr>
        <td><b>ADJ-${String(a.adjustment_id).padStart(4, '0')}</b></td>
        <td>${formatDate(a.proposed_at)}</td>
        <td><code>${a.sku}</code></td>
        <td><b>${a.product_name}</b></td>
        <td><b style="color:${a.quantity_delta < 0 ? 'var(--danger)' : 'var(--success)'}">${a.quantity_delta > 0 ? '+' : ''}${a.quantity_delta}</b></td>
        <td>${a.reason}</td>
        <td>${a.proposed_by_name}</td>
        <td><span class="badge-tag ${a.status === 'PENDING' ? 'badge-warning' : (a.status === 'APPROVED' ? 'badge-success' : 'badge-danger')}">${a.status}</span></td>
        <td>
          ${a.status === 'PENDING' ? `
            <div class="flex-end">
              <button class="btn btn-sm btn-success" onclick="decideAdjustment(${a.adjustment_id}, 'APPROVE')">✓ Approve</button>
              <button class="btn btn-sm btn-danger-outline" onclick="decideAdjustment(${a.adjustment_id}, 'REJECT')">✕ Reject</button>
            </div>
          ` : `<small class="text-muted">By: ${a.approved_by_name || '-'}</small>`}
        </td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading adjustments:', err);
  }
}

async function decideAdjustment(adjId, decision) {
  if (!confirm(`Are you sure you want to ${decision} this stock adjustment?`)) return;

  try {
    await api('stock/adjustments', {
      method: 'PUT',
      body: { adjustmentId: adjId, decision }
    });
    loadPendingApprovals();
    loadCatalogData();
    loadDashboardOverview();
    alert(`Adjustment ADJ-${adjId} ${decision}D successfully.`);
  } catch (err) {
    alert('Error processing adjustment: ' + err.message);
  }
}

// ============================================================================
// STOCK RECEIVING (GRN)
// ============================================================================
async function handleStockReceive(e) {
  e.preventDefault();
  const alertEl = $('recvAlert');
  alertEl.style.display = 'none';

  const payload = {
    supplierId: $('recvSupplierSelect').value || null,
    invoiceNo: $('recvInvoiceNo').value.trim(),
    productId: $('recvProductSelect').value,
    quantity: $('recvQuantity').value,
    unitCost: $('recvUnitCost').value
  };

  try {
    await api('stock/receive', {
      method: 'POST',
      body: payload
    });
    alertEl.textContent = 'Stock receipt posted successfully! Inventory on hand updated.';
    alertEl.style.display = 'block';
    $('receivingForm').reset();
    loadCatalogData();
    loadDashboardOverview();
  } catch (err) {
    alert('Failed to post stock receipt: ' + err.message);
  }
}

// ============================================================================
// CUSTOMER CREDIT & REPAYMENTS
// ============================================================================
function renderCustomersTable() {
  const tbody = $('customerTableBody');
  if (customersCache.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No customers registered.</td></tr>';
    return;
  }

  tbody.innerHTML = customersCache.map(c => `
    <tr>
      <td><b>${c.customer_name}</b></td>
      <td>${c.phone || '-'}</td>
      <td>${formatKes(c.credit_limit)}</td>
      <td><b style="color:${c.balance > 0 ? 'var(--danger)' : 'var(--text-main)'}">${formatKes(c.balance)}</b></td>
      <td>
        <button class="btn btn-sm btn-outline" onclick="openStatementModal(${c.customer_id})">📜 Statement</button>
      </td>
    </tr>
  `).join('');
}

function openAddCustomerModal() {
  $('customerModal').style.display = 'flex';
}

function closeCustomerModal() {
  $('customerModal').style.display = 'none';
}

async function saveCustomerModal(e) {
  e.preventDefault();
  const payload = {
    customerName: $('modalCustName').value.trim(),
    phone: $('modalCustPhone').value.trim(),
    email: $('modalCustEmail').value.trim(),
    creditLimit: $('modalCustLimit').value
  };

  try {
    await api('customers', {
      method: 'POST',
      body: payload
    });
    closeCustomerModal();
    loadCustomersData();
    alert('Customer credit account created.');
  } catch (err) {
    alert('Error creating customer: ' + err.message);
  }
}

function updateRepaymentCustInfo() {
  const cid = $('repayCustomerSelect').value;
  const cust = customersCache.find(c => c.customer_id == cid);
  if (cust) {
    $('repayCurrentBalanceInfo').textContent = `Current Outstanding Balance: ${formatKes(cust.balance)} (Credit Limit: ${formatKes(cust.credit_limit)})`;
    $('repayAmount').max = cust.balance;
  } else {
    $('repayCurrentBalanceInfo').textContent = '';
  }
}

async function handleCustomerRepayment(e) {
  e.preventDefault();
  const alertEl = $('repayAlert');
  alertEl.style.display = 'none';

  const payload = {
    customerId: $('repayCustomerSelect').value,
    amount: $('repayAmount').value,
    method: $('repayMethod').value,
    reference: $('repayRef').value.trim() || 'REP-CASH'
  };

  try {
    await api('customers/repayment', {
      method: 'POST',
      body: payload
    });
    alertEl.textContent = 'Repayment saved! Customer balance reduced.';
    alertEl.style.display = 'block';
    $('repaymentForm').reset();
    loadCustomersData();
    loadDashboardOverview();
  } catch (err) {
    alert('Failed to save repayment: ' + err.message);
  }
}

async function openStatementModal(cid) {
  try {
    const data = await api('customers/statement?customerId=' + cid);
    $('stmtCustomerTitle').textContent = 'Customer: ' + data.customerName;
    $('stmtCustomerPhone').textContent = 'Phone: ' + (data.phone || '-');
    $('stmtCreditLimit').textContent = 'Credit Limit: ' + formatKes(data.creditLimit);
    $('stmtCurrentBal').innerHTML = `<b>Outstanding Balance: ${formatKes(data.currentBalance)}</b>`;

    const entries = JSON.parse(data.entries || '[]');
    const tbody = $('statementEntriesBody');

    if (entries.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No credit transactions on this account.</td></tr>';
    } else {
      tbody.innerHTML = entries.map(e => `
        <tr>
          <td>${formatDate(e.occurred_at)}</td>
          <td><span class="badge-tag">${e.entry_type}</span></td>
          <td>${e.reference || '-'}</td>
          <td style="color:${e.signed_amount > 0 ? 'var(--danger)' : 'var(--success)'}; font-weight:bold;">
            ${e.signed_amount > 0 ? '+' : ''}${formatKes(e.signed_amount)}
          </td>
          <td>${e.created_by_name}</td>
        </tr>
      `).join('');
    }

    $('statementModal').style.display = 'flex';
  } catch (err) {
    alert('Error loading statement: ' + err.message);
  }
}

function closeStatementModal() {
  $('statementModal').style.display = 'none';
}

// ============================================================================
// REPLENISHMENT GUIDANCE & DEMAND FORECAST (REQ-ROP-001 - 007)
// ============================================================================
async function loadReplenishmentGuidance() {
  try {
    const rows = await api('replenishment');
    const tbody = $('replenishmentTableBody');

    if (rows.length === 0) {
      tbody.innerHTML = '<tr><td colspan="10" class="text-center text-muted">No replenishment data available.</td></tr>';
      return;
    }

    tbody.innerHTML = rows.map(r => `
      <tr>
        <td><b>${r.productName}</b></td>
        <td><code>${r.sku}</code></td>
        <td><span class="badge-tag ${r.isLowStock ? 'badge-danger' : 'badge-success'}">${r.quantityOnHand}</span></td>
        <td>${r.reorderLevel}</td>
        <td>${r.unitsSold30d}</td>
        <td>${r.avgDailySales} /day</td>
        <td>${r.leadTimeDays} days</td>
        <td>${r.safetyStock}</td>
        <td>
          <b style="font-size:15px; color:${r.suggestedQuantity > 0 ? 'var(--danger)' : 'var(--text-muted)'}">
            ${r.suggestedQuantity > 0 ? r.suggestedQuantity + ' units' : '0 (Adequate)'}
          </b>
        </td>
        <td>
          <span class="badge-tag ${r.isLowStock ? 'badge-danger' : 'badge-success'}">
            ${r.isLowStock ? 'ORDER NOW' : 'HEALTHY'}
          </span>
        </td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading replenishment:', err);
  }
}

// ============================================================================
// REPORTS & COGS/MARGIN ANALYSIS (REQ-RPT-001 - 009)
// ============================================================================
let activeReportSubTab = 'repProfit';

function switchReportSubTab(subId) {
  activeReportSubTab = subId;
  document.querySelectorAll('.sub-tab').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.sub-report').forEach(p => p.style.display = 'none');

  if (subId === 'repProfit') {
    $('subRepProfit').style.display = 'block';
    loadReportsProfit();
  } else if (subId === 'repSalesSummary') {
    $('subRepSalesSummary').style.display = 'block';
    loadReportsDailySales();
  } else if (subId === 'repValuation') {
    $('subRepValuation').style.display = 'block';
    loadReportsValuation();
  } else if (subId === 'repMovements') {
    $('subRepMovements').style.display = 'block';
    loadReportsMovements();
  }

  // Highlight button
  const activeBtn = Array.from(document.querySelectorAll('.sub-tab')).find(b => b.getAttribute('onclick').includes(subId));
  if (activeBtn) activeBtn.classList.add('active');
}

async function loadReportsProfit() {
  try {
    const data = await api('reports/profit');
    const tbody = $('profitTableBody');
    if (data.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No completed sales recorded for profit analysis.</td></tr>';
      return;
    }
    tbody.innerHTML = data.map(d => `
      <tr>
        <td><code>${d.sku}</code></td>
        <td><b>${d.product_name}</b></td>
        <td><span class="badge-tag">${d.category}</span></td>
        <td>${d.total_units_sold}</td>
        <td><b>${formatKes(d.total_revenue)}</b></td>
        <td>${formatKes(d.total_cogs)}</td>
        <td style="color:var(--success); font-weight:bold;">${formatKes(d.gross_profit)}</td>
        <td><span class="badge-tag badge-success">${d.profit_margin_pct}%</span></td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading profit report:', err);
  }
}

async function loadReportsDailySales() {
  try {
    const data = await api('reports/sales');
    const tbody = $('salesSummaryTableBody');
    if (data.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No sales history recorded.</td></tr>';
      return;
    }
    tbody.innerHTML = data.map(d => `
      <tr>
        <td><b>${d.sale_date}</b></td>
        <td>${d.total_sales_count}</td>
        <td><b>${formatKes(d.total_revenue)}</b></td>
        <td>${formatKes(d.cash_revenue)}</td>
        <td>${formatKes(d.mpesa_revenue)}</td>
        <td>${formatKes(d.credit_revenue)}</td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading sales summary:', err);
  }
}

async function loadReportsValuation() {
  try {
    const data = await api('reports/valuation');
    const tbody = $('valuationTableBody');
    if (data.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No valuation data.</td></tr>';
      return;
    }
    tbody.innerHTML = data.map(d => `
      <tr>
        <td><b>${d.category}</b></td>
        <td>${d.product_count} items</td>
        <td>${d.total_units} units</td>
        <td>${formatKes(d.inventory_cost_value)}</td>
        <td><b>${formatKes(d.inventory_retail_value)}</b></td>
        <td style="color:var(--success); font-weight:bold;">${formatKes(d.potential_profit)}</td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading valuation report:', err);
  }
}

async function loadReportsMovements() {
  try {
    const data = await api('stock/movements');
    const tbody = $('movementsTableBody');
    if (data.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No stock movement ledger records.</td></tr>';
      return;
    }
    tbody.innerHTML = data.map(d => `
      <tr>
        <td><b>SM-${String(d.movement_id).padStart(5, '0')}</b></td>
        <td>${formatDate(d.occurred_at)}</td>
        <td><code>${d.sku}</code></td>
        <td>${d.product_name}</td>
        <td><span class="badge-tag">${d.movement_type}</span></td>
        <td><b style="color:${d.quantity_delta < 0 ? 'var(--danger)' : 'var(--success)'}">${d.quantity_delta > 0 ? '+' : ''}${d.quantity_delta}</b></td>
        <td>${d.reference || '-'}</td>
        <td>${d.actor_name}</td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading movements:', err);
  }
}

function exportCurrentReportCSV() {
  let exportType = 'sales';
  if (activeReportSubTab === 'repProfit') exportType = 'products';
  window.open('/api/reports/export?type=' + exportType, '_blank');
}

// ============================================================================
// USER MANAGEMENT & AUDIT LOGS (REQ-ACC-001 & REQ-AUD-001)
// ============================================================================
async function loadUsersTable() {
  try {
    const users = await api('users');
    const tbody = $('usersTableBody');
    tbody.innerHTML = users.map(u => `
      <tr>
        <td>#${u.user_id}</td>
        <td><b>${u.display_name}</b></td>
        <td><code>${u.username}</code></td>
        <td><span class="badge-tag">${u.role_name}</span></td>
        <td><span class="badge-tag ${u.is_active ? 'badge-success' : 'badge-danger'}">${u.is_active ? 'Active' : 'Disabled'}</span></td>
        <td>${formatDate(u.created_at)}</td>
        <td>${formatDate(u.last_login_at)}</td>
        <td>
          <button class="btn btn-sm btn-outline" onclick="toggleUserStatus(${u.user_id}, ${!u.is_active})">
            ${u.is_active ? 'Deactivate' : 'Activate'}
          </button>
        </td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading users:', err);
  }
}

function openAddUserModal() {
  $('userModal').style.display = 'flex';
}

function closeUserModal() {
  $('userModal').style.display = 'none';
}

async function saveUserModal(e) {
  e.preventDefault();
  const payload = {
    displayName: $('modalUserFullName').value.trim(),
    username: $('modalUserUsername').value.trim(),
    roleId: $('modalUserRole').value,
    password: $('modalUserPassword').value
  };

  try {
    await api('users', {
      method: 'POST',
      body: payload
    });
    closeUserModal();
    loadUsersTable();
    alert('User created successfully.');
  } catch (err) {
    alert('Error creating user: ' + err.message);
  }
}

async function toggleUserStatus(uid, newStatus) {
  if (!confirm(`Are you sure you want to ${newStatus ? 'activate' : 'deactivate'} this user account?`)) return;

  try {
    await api('users', {
      method: 'PUT',
      body: { userId: uid, isActive: newStatus }
    });
    loadUsersTable();
  } catch (err) {
    alert('Error updating user: ' + err.message);
  }
}

async function loadAuditTrail() {
  try {
    const logs = await api('audit');
    const tbody = $('auditTableBody');
    if (logs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted">No audit events recorded.</td></tr>';
      return;
    }
    tbody.innerHTML = logs.map(l => `
      <tr>
        <td>#${l.audit_id}</td>
        <td>${formatDate(l.occurred_at)}</td>
        <td><b>${l.user_name}</b></td>
        <td><span class="badge-tag">${l.action}</span></td>
        <td>${l.entity_type}</td>
        <td>${l.entity_id || '-'}</td>
        <td>${l.reason || '-'}</td>
      </tr>
    `).join('');
  } catch (err) {
    console.error('Error loading audit logs:', err);
  }
}

// Auto-boot if session token is preserved
window.addEventListener('DOMContentLoaded', () => {
  const savedUser = localStorage.getItem('fmm_user');
  if (authToken && savedUser) {
    try {
      currentUser = JSON.parse(savedUser);
      initAppWorkspace();
    } catch (e) {
      logout();
    }
  }
});
