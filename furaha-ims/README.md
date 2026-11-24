# Furaha Inventory & Sales Management System (ISMS)
**For Furaha Mini Mart**

* **Author:** Mitsanze Lee Kazungu
* **Registration Number:** 25/00569
* **Project Code:** STU 3101 (Programming Project)
* **Supervisor:** Dr. Lucy Mburu
* **Institution:** KCA University — Bachelor of Science in Information Security & Forensics

---

## 1. System Overview

The **Furaha Inventory & Sales Management System (ISMS)** replaces fragmented paper records with an automated, auditable, browser-based 3-tier client-server solution designed for desktop, laptop, and smartphone browsers. 

The system implements strict **Role-Based Access Control (RBAC)** distinguishing between **Administrator / Owner** and **Attendant / Cashier** roles, atomic transactions for POS checkout, immutable stock movement audit trails, variance reconciliation with supervisory approval, customer credit tracking, replenishment guidance using 30-day velocity, and thermal receipt printing.

### Key Capabilities & Traceability:
* **Authentication & RBAC (REQ-ACC-001 - 008):** Salted SHA-256 password hashing, protected sessions, server-side role enforcement, audit of sign-in events, and generic authentication error messages.
* **Product Catalog & Suppliers (REQ-CAT-001 - 007):** Unique SKU enforcement, unit of measure management, supplier lead time tracking, active/inactive lifecycle, and historic sale price snapshots.
* **Point of Sale (POS) & Receipts (REQ-POS-001 - 014):** Barcode scanner wedge input, real-time stock locking (`FOR UPDATE`), multi-tender checkout (Cash, M-Pesa, Customer Credit), automatic cash change calculation, and printable 58mm/80mm thermal receipt slips.
* **Stock Control & Variance Approvals (REQ-STK-001 - 011):** Goods Received Notes (GRN), physical stock count session recording, automatic discrepancy variance calculation (`counted - system`), and an Administrator approval queue.
* **Immutable Stock Movement Ledger (SDS §3.4.15):** Every stock increase or decrease is committed to `stock_movements` with actor ID and reference code. Posted records cannot be edited or deleted.
* **Customer Credit Ledger (REQ-CRD-001 - 007):** Credit sale authorization against configured limits, repayment recording, debt balance tracking, and customer statements of account.
* **Replenishment Guidance (REQ-ROP-001 - 007):** Real-time low-stock flagging and replenishment formula:
  $$\text{Suggested Qty} = \max\Big(0, \lceil(\text{30-Day Avg Daily Sales} \times \text{Lead Time}) + \text{Safety Buffer} - \text{Stock on Hand}\rceil\Big)$$
* **Reports, COGS & Valuation (REQ-RPT-001 - 009):** Daily sales summaries, accurate Cost of Goods Sold (COGS) gross profit analysis, inventory retail/cost valuation, and CSV exports.
* **Audit Trail & Backup (REQ-AUD-001 - 003, REQ-BKP-001 - 003):** Tamper-evident operational audit logs and one-click full SQL database backup export.

---

## 2. Technology Stack

* **Backend Server:** Java 17+ (Zero-dependency modular monolith using Java SE `com.sun.net.httpserver.HttpServer`).
* **Database:** MySQL 8.0+ (InnoDB storage engine, strict foreign key constraints, atomic transactions, UTF-8 unicode encoding).
* **Driver:** Official MySQL Connector/J (`com.mysql:mysql-connector-j:8.4.0`).
* **Presentation Tier:** HTML5, CSS3 (responsive grid, thermal receipt print styles), vanilla JavaScript ES6+.
* **Packaging:** Apache Maven shade plugin (compiles to a single executable fat JAR).
* **Containerization:** Docker & Docker Compose.

---

## 3. Database Schema (SDS §3.4 & ERD Figures 6–7)

The database `furaha_ims` is normalized to Third Normal Form (3NF) and consists of 18 tables:

1. `roles` — System roles (`ADMINISTRATOR`, `ATTENDANT`).
2. `users` — User credentials, role assignments, active status, last login timestamp.
3. `suppliers` — Supplier profiles, contacts, and delivery addresses.
4. `units` — Stock units (pcs, kg, ltr, pkt, box).
5. `products` — SKU, product name, unit, cost price, selling price, quantity on hand, reorder level, lead time, safety stock.
6. `customers` — Customer accounts, credit limits, and outstanding balances.
7. `sales` — Sale transactions, receipt numbers, cashier user ID, customer ID, subtotal, total.
8. `sale_items` — Line items storing historic selling price and unit cost snapshots at the time of sale.
9. `sale_payments` — Tender method (`CASH`, `MOBILE_MONEY`, `CREDIT`), amount, tendered, change due, reference code.
10. `stock_receipts` — Goods Received Notes (GRN) headers from suppliers.
11. `stock_receipt_items` — Received line items and delivered unit costs.
12. `stock_counts` — Physical inventory count sessions and notes.
13. `stock_count_items` — Recorded physical counted quantity vs system stock and calculated variance.
14. `stock_adjustments` — Proposed variance adjustments awaiting Administrator decision.
15. `stock_movements` — Immutable transaction ledger for all stock additions, deductions, returns, and adjustments.
16. `customer_ledger_entries` — Credit charges, repayments, reversals, and debt history.
17. `sale_returns` & `sale_return_items` — Formal returns, voids, and restock records.
18. `audit_logs` — System security, authentication events, and administrative actions.

---

## 4. Setup and Running Instructions

### Option 1: Native Windows Setup (Recommended)
1. Ensure **Java 17+** and **MySQL 8** are installed.
2. Open a terminal or Command Prompt in the `furaha-ims` folder.
3. Run the automated installer:
   ```cmd
   scripts\install-windows.bat
   ```
   *This script runs `db/schema.sql`, `db/seed.sql`, configures the database user, and builds the application JAR.*
4. Start the application:
   ```cmd
   scripts\start-windows.bat
   ```
5. Open your web browser at:
   **http://localhost:8080**

---

### Option 2: Linux / macOS Setup
1. Ensure Java 17+ and MySQL are running.
2. Initialize database:
   ```bash
   mysql -u root -p < db/schema.sql
   mysql -u root -p < db/seed.sql
   ```
3. Build and run:
   ```bash
   mvn clean package -DskipTests
   java -jar target/furaha-ims.jar
   ```
4. Access: `http://localhost:8080`

---

### Option 3: Docker Compose (All-in-One Deployment)
If Docker is installed on your machine, you can run both the MySQL database and the Java application with a single command without installing Java or MySQL locally:
```bash
docker compose up -d --build
```
Then open `http://localhost:8080`.

---

## 5. Default User Credentials

| Role | Username | Password | Permitted Functions |
|---|---|---|---|
| **Administrator / Owner** | `admin` | `admin123` | Full system access: Products, Pricing, Adjustment Approvals, User Management, Profit/Valuation Reports, Audit Trail, Backups |
| **Attendant / Cashier** | `amina` | `attendant123` | Operational access: Barcode scanner search, POS checkout, Thermal Receipt printing, Physical stock count entry, Customer repayments |
| **Attendant / Cashier** | `john` | `cashier123` | Operational access: POS checkout, stock counts, customer credit transactions |

---

## 6. Project Defense & Verification Guide

1. **Sign in as Attendant (`amina` / `attendant123`)**:
   - Verify that administrative tabs (Approvals, Receiving, Reports, User Accounts, Audit & Backup) are automatically hidden from the navigation bar.
   - Go to **Point of Sale**: Type or scan an SKU (e.g. `FMM-1001` for Brookside Milk) and press Enter.
   - Adjust quantity. Select payment method (Cash, M-Pesa, or Credit).
   - Enter cash tendered, observe instant change calculation.
   - Click **Complete Sale**: View the formatted thermal receipt slip. Click **Print Thermal Slip** to test print formatting.
2. **Perform a Physical Count**:
   - Open **Physical Count** tab. Enter a counted quantity that differs from the system quantity (e.g. 2 units less due to damaged packaging).
   - Enter the reason for variance and click **Submit Count & Propose Adjustments**.
3. **Sign in as Administrator (`admin` / `admin123`)**:
   - Notice the **Approvals** badge count on the navigation bar.
   - Navigate to **Approvals Queue**: Review the pending discrepancy submitted by Attendant Amina. Click **Approve**.
   - Navigate to **Reports & Profit**: Verify that gross profit and profit margin % are calculated from historic cost snapshots.
   - Navigate to **Reorder Guidance**: Observe the automatic demand calculation showing 30-day velocity and suggested order quantity.
   - Navigate to **Audit & Backup**: Review the timestamped audit log of all transactions and click **Download Full Database Backup (.sql)**.
