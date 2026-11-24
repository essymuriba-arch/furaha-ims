-- ============================================================================
-- FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)
-- Comprehensive Seed Data per SDS & SRS Specifications
-- ============================================================================

USE furaha_ims;

-- 1. Roles (SDS Table 4)
INSERT IGNORE INTO roles (role_id, role_name, description) VALUES
  (1, 'ADMINISTRATOR', 'Full access: inventory management, price changes, adjustment approvals, user management, financial reports, audit trail, and backups.'),
  (2, 'ATTENDANT', 'Operational access: barcode search, POS sales, receipt printing, customer credit recording, and physical stock count submission.');

-- 2. Users (SDS Table 5 & UI Mockup Figure 9)
-- Passwords (SHA-256):
-- admin / admin123        -> 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
-- amina / attendant123    -> 9a7852c035c91b5c3e031e0303fe20f2db9d251d2fbe4f6050b11e2f790c6fa7
-- john / cashier123       -> 129b09f448c5b0c4cf1b9cf3832ca87e3831ecad3474a2dfbfab043c85fdf294
INSERT IGNORE INTO users (user_id, role_id, username, password_hash, display_name, is_active, created_at) VALUES
  (1, 1, 'admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Mitsanze Kazungu (Admin)', 1, NOW()),
  (2, 2, 'amina', '9a7852c035c91b5c3e031e0303fe20f2db9d251d2fbe4f6050b11e2f790c6fa7', 'Amina Mwangi (Attendant)', 1, NOW()),
  (3, 2, 'john',  '129b09f448c5b0c4cf1b9cf3832ca87e3831ecad3474a2dfbfab043c85fdf294', 'John Ochieng (Attendant)', 1, NOW());

-- 3. Stock Units (SDS Table 7)
INSERT IGNORE INTO units (unit_id, unit_name, abbreviation) VALUES
  (1, 'Piece / Unit', 'pcs'),
  (2, 'Kilogram', 'kg'),
  (3, 'Litre', 'ltr'),
  (4, 'Packet', 'pkt'),
  (5, 'Box / Carton', 'box');

-- 4. Suppliers (SDS Table 6)
INSERT IGNORE INTO suppliers (supplier_id, supplier_name, phone, email, address, is_active) VALUES
  (1, 'Highland Dairies Kenya', '+254 712 000 001', 'orders@highlanddairies.co.ke', 'Industrial Area, Off Enterprise Rd, Nairobi', 1),
  (2, 'Harvest Foods & Millers Ltd', '+254 722 000 002', 'sales@harvestfoods.co.ke', 'Commercial St, Mombasa', 1),
  (3, 'Premier FMCG Distributors', '+254 733 000 003', 'supplies@premierfmcg.co.ke', 'Airport Road, Kilifi', 1),
  (4, 'Brookside Dairy Ltd', '+254 700 000 004', 'info@brookside.co.ke', 'Ruiru, Kiambu', 1);

-- 5. Products Catalog (SDS Table 8)
INSERT IGNORE INTO products (product_id, sku, product_name, category, unit_id, preferred_supplier_id, cost_price, selling_price, quantity_on_hand, reorder_level, lead_time_days, safety_stock, is_active) VALUES
  (1, 'FMM-1001', 'Brookside Fresh Milk 500ml', 'Dairy & Eggs', 1, 4, 52.00, 65.00, 4.000, 15.000, 2, 5.000, 1),
  (2, 'FMM-1002', 'Jogoo Maize Meal 2kg', 'Grains & Flour', 1, 2, 180.00, 210.00, 28.000, 12.000, 4, 6.000, 1),
  (3, 'FMM-1003', 'Kabras Pure White Sugar 1kg', 'Groceries', 1, 2, 142.00, 165.00, 18.000, 15.000, 3, 5.000, 1),
  (4, 'FMM-1004', 'Rina Vegetable Cooking Oil 1L', 'Oils & Fats', 1, 3, 260.00, 310.00, 9.000, 10.000, 3, 4.000, 1),
  (5, 'FMM-1005', 'Broadways White Bread 400g', 'Bakery', 1, 2, 55.00, 65.00, 3.000, 12.000, 1, 4.000, 1),
  (6, 'FMM-1006', 'Daawat Super Basmati Rice 2kg', 'Grains & Flour', 1, 2, 380.00, 450.00, 22.000, 8.000, 5, 3.000, 1),
  (7, 'FMM-1007', 'Ketepa Pride Tea Bags 50s', 'Beverages', 4, 3, 115.00, 140.00, 14.000, 8.000, 4, 2.000, 1),
  (8, 'FMM-1008', 'Geisha Beauty Soap Aloe Vera 225g', 'Toiletries', 1, 3, 90.00, 115.00, 2.000, 10.000, 4, 3.000, 1),
  (9, 'FMM-1009', 'Omo Hand Washing Powder 500g', 'Household', 4, 3, 155.00, 185.00, 16.000, 8.000, 4, 3.000, 1),
  (10, 'FMM-1010', 'Highland Natural Yoghurt 500ml', 'Dairy & Eggs', 1, 1, 85.00, 110.00, 7.000, 10.000, 2, 4.000, 1);

-- 6. Customers (SDS Table 9)
INSERT IGNORE INTO customers (customer_id, customer_name, phone, email, credit_limit, balance, is_active, created_at) VALUES
  (1, 'Walk-in Customer (General)', NULL, NULL, 0.00, 0.00, 1, NOW()),
  (2, 'Mama Wanjiru (Local Resident)', '+254 722 123 456', 'wanjiru@gmail.com', 5000.00, 1850.00, 1, NOW()),
  (3, 'Bwana Suleiman Said', '+254 733 654 321', 'suleiman.said@yahoo.com', 10000.00, 3200.00, 1, NOW()),
  (4, 'Celestine Achieng', '+254 711 987 654', 'achieng.c@gmail.com', 3000.00, 450.00, 1, NOW());

-- 7. Opening Stock Movements (SDS Table 18)
INSERT IGNORE INTO stock_movements (movement_id, product_id, movement_type, quantity_delta, actor_id, reference, occurred_at) VALUES
  (1, 1, 'OPENING', 4.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (2, 2, 'OPENING', 28.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (3, 3, 'OPENING', 18.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (4, 4, 'OPENING', 9.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (5, 5, 'OPENING', 3.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (6, 6, 'OPENING', 22.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (7, 7, 'OPENING', 14.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (8, 8, 'OPENING', 2.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (9, 9, 'OPENING', 16.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (10, 10, 'OPENING', 7.000, 1, 'INITIAL-COUNT', DATE_SUB(NOW(), INTERVAL 7 DAY));

-- 8. Customer Initial Credit Ledger Entries (SDS Table 19)
INSERT IGNORE INTO customer_ledger_entries (entry_id, customer_id, sale_id, entry_type, signed_amount, payment_method, reference, created_by, occurred_at) VALUES
  (1, 2, NULL, 'CREDIT_SALE', 2500.00, 'CREDIT', 'RCP-PREV-001', 1, DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (2, 2, NULL, 'REPAYMENT', -650.00, 'MPESA', 'QK781290AA', 2, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (3, 3, NULL, 'CREDIT_SALE', 3200.00, 'CREDIT', 'RCP-PREV-002', 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (4, 4, NULL, 'CREDIT_SALE', 1000.00, 'CREDIT', 'RCP-PREV-003', 2, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (5, 4, NULL, 'REPAYMENT', -550.00, 'CASH', 'REC-001', 2, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 9. Sample Physical Stock Count & Adjustment for Approval Queue (SDS Figure 12)
INSERT IGNORE INTO stock_counts (count_id, created_by, counted_at, status, notes) VALUES
  (1, 2, DATE_SUB(NOW(), INTERVAL 1 HOUR), 'SUBMITTED', 'Bi-weekly routine count by Attendant Amina');

INSERT IGNORE INTO stock_count_items (count_item_id, count_id, product_id, system_quantity, counted_quantity, variance) VALUES
  (1, 1, 1, 6.000, 4.000, -2.000), -- Brookside Milk had 2 damaged cartons
  (2, 1, 3, 18.000, 18.000, 0.000);

INSERT IGNORE INTO stock_adjustments (adjustment_id, count_item_id, product_id, quantity_delta, reason, status, proposed_by, proposed_at) VALUES
  (1, 1, 1, -2.000, 'Packaging leak during shelf restocking; items removed and safely disposed', 'PENDING', 2, DATE_SUB(NOW(), INTERVAL 1 HOUR));

-- 10. Audit Logs (SDS Table 21)
INSERT IGNORE INTO audit_logs (audit_id, user_id, action, entity_type, entity_id, reason, occurred_at) VALUES
  (1, 1, 'SYSTEM_INIT', 'SYSTEM', '1', 'Initial database schema and catalog configuration loaded', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (2, 1, 'USER_CREATE', 'USERS', '2', 'Created attendant user account for Amina Mwangi', DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (3, 2, 'LOGIN', 'SESSION', '2', 'Attendant Amina logged in to POS workstation', DATE_SUB(NOW(), INTERVAL 1 HOUR)),
  (4, 2, 'COUNT_SUBMIT', 'STOCK_COUNTS', '1', 'Submitted physical stock count CNT-0001 for Administrator review', DATE_SUB(NOW(), INTERVAL 1 HOUR));
