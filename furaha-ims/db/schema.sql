-- ============================================================================
-- FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)
-- FOR FURAHA MINI MART
-- System Design Specification (SDS) & System Requirements Specification (SRS)
-- Author: Mitsanze Lee Kazungu 
-- ============================================================================

CREATE DATABASE IF NOT EXISTS furaha_ims
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE furaha_ims;

-- ----------------------------------------------------------------------------
-- 1. ROLES & ACCESS CONTROL (SDS §3.4.1, Table 4)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
  role_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(40) NOT NULL UNIQUE,
  description VARCHAR(180) NULL
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. USERS (SDS §3.4.2, Table 5)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  user_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT UNSIGNED NOT NULL,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at DATETIME NULL,
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (role_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 3. SUPPLIERS (SDS §3.4.3, Table 6)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suppliers (
  supplier_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  supplier_name VARCHAR(150) NOT NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(160) NULL,
  address VARCHAR(255) NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 4. STOCK UNITS (SDS §3.4.4, Table 7)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS units (
  unit_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  unit_name VARCHAR(60) NOT NULL UNIQUE,
  abbreviation VARCHAR(16) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5. PRODUCTS CATALOG (SDS §3.4.5, Table 8)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
  product_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sku VARCHAR(64) NOT NULL UNIQUE,
  product_name VARCHAR(160) NOT NULL,
  category VARCHAR(80) NOT NULL DEFAULT 'General',
  unit_id BIGINT UNSIGNED NOT NULL,
  preferred_supplier_id BIGINT UNSIGNED NULL,
  selling_price DECIMAL(12,2) NOT NULL,
  cost_price DECIMAL(12,2) NOT NULL,
  quantity_on_hand DECIMAL(12,3) NOT NULL DEFAULT 0.000,
  reorder_level DECIMAL(12,3) NOT NULL DEFAULT 0.000,
  lead_time_days SMALLINT UNSIGNED NULL DEFAULT 3,
  safety_stock DECIMAL(12,3) NOT NULL DEFAULT 0.000,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT fk_products_unit FOREIGN KEY (unit_id) REFERENCES units (unit_id),
  CONSTRAINT fk_products_supplier FOREIGN KEY (preferred_supplier_id) REFERENCES suppliers (supplier_id),
  CONSTRAINT chk_products_selling_price CHECK (selling_price >= 0),
  CONSTRAINT chk_products_cost_price CHECK (cost_price >= 0),
  CONSTRAINT chk_products_reorder_level CHECK (reorder_level >= 0)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 6. CUSTOMERS & CREDIT ACCOUNTS (SDS §3.4.6, Table 9)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
  customer_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  customer_name VARCHAR(150) NOT NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(160) NULL,
  credit_limit DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 7. SALES (SDS §3.4.7, Table 10)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sales (
  sale_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  receipt_no VARCHAR(40) NOT NULL UNIQUE,
  cashier_id BIGINT UNSIGNED NOT NULL,
  customer_id BIGINT UNSIGNED NULL,
  sold_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', -- COMPLETED, VOIDED, REVERSED
  subtotal DECIMAL(12,2) NOT NULL,
  total DECIMAL(12,2) NOT NULL,
  INDEX idx_sales_sold_at (sold_at),
  INDEX idx_sales_cashier (cashier_id),
  CONSTRAINT fk_sales_cashier FOREIGN KEY (cashier_id) REFERENCES users (user_id),
  CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 8. SALE ITEMS (SDS §3.4.8, Table 11)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sale_items (
  sale_item_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sale_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  unit_selling_price DECIMAL(12,2) NOT NULL,
  unit_cost_at_sale DECIMAL(12,2) NOT NULL,
  line_total DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales (sale_id),
  CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products (product_id),
  CONSTRAINT chk_sale_items_qty CHECK (quantity > 0)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 9. SALE PAYMENTS (SDS §3.4.9, Table 12)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sale_payments (
  payment_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sale_id BIGINT UNSIGNED NOT NULL,
  method VARCHAR(20) NOT NULL, -- CASH, MOBILE_MONEY, CREDIT
  amount_received DECIMAL(12,2) NOT NULL,
  amount_tendered DECIMAL(12,2) NULL,
  change_due DECIMAL(12,2) NULL,
  reference VARCHAR(100) NULL,
  CONSTRAINT fk_payments_sale FOREIGN KEY (sale_id) REFERENCES sales (sale_id),
  CONSTRAINT chk_payment_method CHECK (method IN ('CASH', 'MOBILE_MONEY', 'CREDIT'))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 10. STOCK RECEIPTS (SDS §3.4.10, Table 13)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_receipts (
  receipt_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  supplier_id BIGINT UNSIGNED NULL,
  received_by BIGINT UNSIGNED NOT NULL,
  supplier_reference VARCHAR(100) NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'POSTED', -- POSTED, REVERSED
  CONSTRAINT fk_receipts_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (supplier_id),
  CONSTRAINT fk_receipts_user FOREIGN KEY (received_by) REFERENCES users (user_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 11. STOCK RECEIPT ITEMS (SDS §3.4.11, Table 14)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_receipt_items (
  receipt_item_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  receipt_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  unit_cost DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_receipt_items_receipt FOREIGN KEY (receipt_id) REFERENCES stock_receipts (receipt_id),
  CONSTRAINT fk_receipt_items_product FOREIGN KEY (product_id) REFERENCES products (product_id),
  CONSTRAINT chk_receipt_item_qty CHECK (quantity > 0),
  CONSTRAINT chk_receipt_item_cost CHECK (unit_cost >= 0)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 12. PHYSICAL STOCK COUNTS (SDS §3.4.12, Table 15)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_counts (
  count_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  created_by BIGINT UNSIGNED NOT NULL,
  counted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SUBMITTED, APPROVED, CLOSED
  approved_by BIGINT UNSIGNED NULL,
  notes VARCHAR(255) NULL,
  CONSTRAINT fk_counts_creator FOREIGN KEY (created_by) REFERENCES users (user_id),
  CONSTRAINT fk_counts_approver FOREIGN KEY (approved_by) REFERENCES users (user_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 13. STOCK COUNT ITEMS (SDS §3.4.13, Table 16)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_count_items (
  count_item_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  count_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  system_quantity DECIMAL(12,3) NOT NULL,
  counted_quantity DECIMAL(12,3) NOT NULL,
  variance DECIMAL(12,3) NOT NULL,
  CONSTRAINT fk_count_items_count FOREIGN KEY (count_id) REFERENCES stock_counts (count_id),
  CONSTRAINT fk_count_items_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 14. STOCK ADJUSTMENTS & APPROVALS (SDS §3.4.14, Table 17)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_adjustments (
  adjustment_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  count_item_id BIGINT UNSIGNED NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity_delta DECIMAL(12,3) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
  proposed_by BIGINT UNSIGNED NOT NULL,
  approved_by BIGINT UNSIGNED NULL,
  proposed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at DATETIME NULL,
  CONSTRAINT fk_adjustments_item FOREIGN KEY (count_item_id) REFERENCES stock_count_items (count_item_id),
  CONSTRAINT fk_adjustments_product FOREIGN KEY (product_id) REFERENCES products (product_id),
  CONSTRAINT fk_adjustments_proposer FOREIGN KEY (proposed_by) REFERENCES users (user_id),
  CONSTRAINT fk_adjustments_approver FOREIGN KEY (approved_by) REFERENCES users (user_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 15. IMMUTABLE STOCK MOVEMENT LEDGER (SDS §3.4.15, Table 18)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_movements (
  movement_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT UNSIGNED NOT NULL,
  movement_type VARCHAR(24) NOT NULL, -- OPENING, RECEIPT, SALE, RETURN, ADJUSTMENT
  quantity_delta DECIMAL(12,3) NOT NULL,
  receipt_item_id BIGINT UNSIGNED NULL,
  sale_item_id BIGINT UNSIGNED NULL,
  adjustment_id BIGINT UNSIGNED NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  reference VARCHAR(100) NULL,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_movements_prod_time (product_id, occurred_at),
  CONSTRAINT fk_movements_product FOREIGN KEY (product_id) REFERENCES products (product_id),
  CONSTRAINT fk_movements_receipt_item FOREIGN KEY (receipt_item_id) REFERENCES stock_receipt_items (receipt_item_id),
  CONSTRAINT fk_movements_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_items (sale_item_id),
  CONSTRAINT fk_movements_adjustment FOREIGN KEY (adjustment_id) REFERENCES stock_adjustments (adjustment_id),
  CONSTRAINT fk_movements_actor FOREIGN KEY (actor_id) REFERENCES users (user_id),
  CONSTRAINT chk_movement_type CHECK (movement_type IN ('OPENING', 'RECEIPT', 'SALE', 'RETURN', 'ADJUSTMENT'))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 16. CUSTOMER CREDIT LEDGER (SDS §3.4.16, Table 19)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer_ledger_entries (
  entry_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT UNSIGNED NOT NULL,
  sale_id BIGINT UNSIGNED NULL,
  entry_type VARCHAR(24) NOT NULL, -- CREDIT_SALE, REPAYMENT, REVERSAL, ADJUSTMENT
  signed_amount DECIMAL(12,2) NOT NULL, -- positive increases debt, negative decreases debt
  payment_method VARCHAR(20) NULL,
  reference VARCHAR(100) NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ledger_cust_time (customer_id, occurred_at),
  CONSTRAINT fk_ledger_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
  CONSTRAINT fk_ledger_sale FOREIGN KEY (sale_id) REFERENCES sales (sale_id),
  CONSTRAINT fk_ledger_creator FOREIGN KEY (created_by) REFERENCES users (user_id),
  CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('CREDIT_SALE', 'REPAYMENT', 'REVERSAL', 'ADJUSTMENT'))
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 17. SALE RETURNS & REVERSALS (SDS §3.4.17, Table 20)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sale_returns (
  return_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sale_id BIGINT UNSIGNED NOT NULL,
  processed_by BIGINT UNSIGNED NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  returned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_returns_sale FOREIGN KEY (sale_id) REFERENCES sales (sale_id),
  CONSTRAINT fk_returns_processor FOREIGN KEY (processed_by) REFERENCES users (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sale_return_items (
  return_item_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  return_id BIGINT UNSIGNED NOT NULL,
  sale_item_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  refund_amount DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_ret_items_return FOREIGN KEY (return_id) REFERENCES sale_returns (return_id),
  CONSTRAINT fk_ret_items_sale_item FOREIGN KEY (sale_item_id) REFERENCES sale_items (sale_item_id),
  CONSTRAINT fk_ret_items_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 18. AUDIT LOGS (SDS §3.4.18, Table 21)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
  audit_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NULL,
  action VARCHAR(80) NOT NULL,
  entity_type VARCHAR(80) NOT NULL,
  entity_id VARCHAR(80) NULL,
  before_data TEXT NULL,
  after_data TEXT NULL,
  reason VARCHAR(500) NULL,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_time (occurred_at),
  INDEX idx_audit_user (user_id),
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB;
