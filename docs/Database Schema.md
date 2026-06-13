# 🗄️ DATABASE SCHEMA DESIGN - PostgreSQL

## ER DIAGRAM (Visual)

```
                    ┌─────────────┐
                    │    User     │
                    └──────┬──────┘
                           │ 1
                    ┌──────┴──────┐
                    │ 1          1│
           ┌────────▼─────┐  ┌───▼──────────┐
           │   Cart        │  │ Notification │
           └──────┬────────┘  └──────────────┘
                  │ 1
                  │
          ┌───────▼──────┐
          │  CartItem     │
          │ (1:M)         │
          └───────┬──────┘
                  │
           ┌──────┴──────┐
           │ 1          1│
        ┌──▼────┐    ┌───▼──────────┐
        │Product│    │    Order     │
        └───────┘    └───┬──────────┘
                         │ 1
                    ┌────▼───────┐
                    │ OrderItem   │
                    │ (1:M)       │
                    └────┬───────┘
                         │
                    ┌────▼────────┐
                    │  Payment     │
                    │ (1:1 or 1:M) │
                    └─────────────┘
```

---

## 📋 TABLE DEFINITIONS (SQL)

### 1️⃣ USERS TABLE
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    
    -- Authentication & Identity
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    
    -- Profile & Status
    user_role VARCHAR(50) NOT NULL DEFAULT 'CUSTOMER', -- CUSTOMER, ADMIN
    account_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, DELETED
    email_verified BOOLEAN DEFAULT FALSE,
    verification_token VARCHAR(255),
    verification_token_expiry TIMESTAMP,
    
    -- Address Information
    street_address VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    
    -- Account Info
    preferred_payment_method VARCHAR(50), -- STRIPE, PAYPAL
    preferred_currency VARCHAR(3) DEFAULT 'USD',
    
    -- Tracking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    
    -- Soft Delete Support
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP
);

-- INDEXES
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_user_role ON users(user_role);
CREATE INDEX idx_users_account_status ON users(account_status);
CREATE INDEX idx_users_created_at ON users(created_at);
```

---

### 2️⃣ PRODUCTS TABLE
```sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    
    -- Product Info
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL, -- Electronics, Clothing, etc.
    
    -- Pricing
    price DECIMAL(19, 2) NOT NULL CHECK (price >= 0),
    discount_price DECIMAL(19, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Inventory
    quantity_in_stock INT NOT NULL DEFAULT 0 CHECK (quantity_in_stock >= 0),
    reserved_quantity INT DEFAULT 0, -- Items in pending orders
    low_stock_threshold INT DEFAULT 10,
    
    -- Media
    image_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    
    -- Status & Metadata
    is_active BOOLEAN DEFAULT TRUE,
    is_featured BOOLEAN DEFAULT FALSE,
    rating DECIMAL(3, 2) DEFAULT 0,
    review_count INT DEFAULT 0,
    
    -- Tracking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT REFERENCES users(id),
    
    -- SEO & Search
    search_keywords TEXT, -- "phone smartphone android"
    
    CONSTRAINT check_discount_price CHECK (
        discount_price IS NULL OR discount_price < price
    )
);

-- INDEXES
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_is_active ON products(is_active);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_created_at ON products(created_at);
```

---

### 3️⃣ CART TABLE
```sql
CREATE TABLE cart (
    id BIGSERIAL PRIMARY KEY,
    
    -- Association
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    
    -- Cart Totals
    total_items INT DEFAULT 0,
    subtotal DECIMAL(19, 2) DEFAULT 0,
    tax_amount DECIMAL(19, 2) DEFAULT 0,
    discount_amount DECIMAL(19, 2) DEFAULT 0,
    total_price DECIMAL(19, 2) DEFAULT 0,
    
    -- Coupon/Promo
    coupon_code VARCHAR(100),
    coupon_discount_percent DECIMAL(5, 2),
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

-- INDEXES
CREATE INDEX idx_cart_user_id ON cart(user_id);
CREATE INDEX idx_cart_updated_at ON cart(updated_at);
```

---

### 4️⃣ CART_ITEMS TABLE
```sql
CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    
    -- Associations
    cart_id BIGINT NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    
    -- Quantity & Pricing
    quantity INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price DECIMAL(19, 2) NOT NULL, -- Price at time of adding to cart
    subtotal DECIMAL(19, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    
    -- Tracking
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(cart_id, product_id) -- One product per cart only once
);

-- INDEXES
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);
```

---

### 5️⃣ ORDERS TABLE
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    
    -- Order Identification
    order_number VARCHAR(50) NOT NULL UNIQUE, -- ORD-2024-001234 format
    user_id BIGINT NOT NULL REFERENCES users(id),
    
    -- Order Status
    order_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- PENDING -> PROCESSING -> SHIPPED -> DELIVERED
    -- OR -> CANCELLED
    
    payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- PENDING -> SUCCESS, FAILED, REFUNDED
    
    fulfillment_status VARCHAR(50) NOT NULL DEFAULT 'NOT_SHIPPED',
    -- NOT_SHIPPED, SHIPPED, DELIVERED, RETURNED
    
    -- Pricing Details
    subtotal DECIMAL(19, 2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    shipping_cost DECIMAL(19, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(19, 2) DEFAULT 0,
    total_price DECIMAL(19, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Shipping Information
    shipping_address_street VARCHAR(255) NOT NULL,
    shipping_address_city VARCHAR(100) NOT NULL,
    shipping_address_state VARCHAR(100),
    shipping_address_postal_code VARCHAR(20) NOT NULL,
    shipping_address_country VARCHAR(100) NOT NULL,
    
    -- Billing Information (can be same as shipping)
    billing_address_street VARCHAR(255),
    billing_address_city VARCHAR(100),
    billing_address_state VARCHAR(100),
    billing_address_postal_code VARCHAR(20),
    billing_address_country VARCHAR(100),
    
    -- Shipping & Tracking
    tracking_number VARCHAR(100),
    estimated_delivery_date DATE,
    actual_delivery_date DATE,
    
    -- Notes
    customer_notes TEXT,
    admin_notes TEXT,
    
    -- Tracking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    
    CONSTRAINT check_addresses CHECK (
        billing_address_street IS NOT NULL AND
        billing_address_city IS NOT NULL
    )
);

-- INDEXES
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_order_status ON orders(order_status);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_user_created ON orders(user_id, created_at);
```

---

### 6️⃣ ORDER_ITEMS TABLE
```sql
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    
    -- Associations
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    
    -- Product Snapshot (denormalization for historical accuracy)
    product_name VARCHAR(255) NOT NULL,
    product_sku VARCHAR(100) NOT NULL,
    
    -- Quantity & Pricing
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(19, 2) NOT NULL, -- Price at time of order
    discount_per_unit DECIMAL(19, 2) DEFAULT 0,
    subtotal DECIMAL(19, 2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    
    -- Tracking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- INDEXES
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
```

---

### 7️⃣ PAYMENTS TABLE
```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    
    -- Association
    order_id BIGINT NOT NULL REFERENCES orders(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    
    -- Payment Details
    payment_method VARCHAR(50) NOT NULL, -- STRIPE, PAYPAL, CARD, etc.
    payment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- PENDING, SUCCESS, FAILED, REFUNDED, PARTIALLY_REFUNDED
    
    -- Amount Details
    amount DECIMAL(19, 2) NOT NULL CHECK (amount > 0),
    refunded_amount DECIMAL(19, 2) DEFAULT 0 CHECK (refunded_amount >= 0),
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- External Reference (from Stripe/PayPal)
    stripe_payment_intent_id VARCHAR(255), -- pi_1234567890abc
    stripe_customer_id VARCHAR(255),
    transaction_id VARCHAR(255) UNIQUE,
    
    -- Card Information (encrypted in production)
    card_last_four VARCHAR(4),
    card_brand VARCHAR(50), -- VISA, MASTERCARD, AMEX
    
    -- Payment Metadata
    payment_metadata JSONB, -- Flexible storage for additional data
    -- Example: {"ip_address": "192.168.1.1", "user_agent": "...", "billing_zip": "90210"}
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    refunded_at TIMESTAMP,
    
    -- Error Handling
    error_message TEXT,
    error_code VARCHAR(100),
    retry_count INT DEFAULT 0,
    
    CONSTRAINT check_refund_amount CHECK (refunded_amount <= amount)
);

-- INDEXES
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_payment_status ON payments(payment_status);
CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_payments_stripe_intent ON payments(stripe_payment_intent_id);
```

---

### 8️⃣ NOTIFICATIONS TABLE
```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    
    -- Association
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Notification Content
    type VARCHAR(50) NOT NULL, -- ORDER_CREATED, PAYMENT_SUCCESS, SHIPMENT, etc.
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    
    -- Related Entity
    related_entity_type VARCHAR(50), -- ORDER, PAYMENT, etc.
    related_entity_id BIGINT,
    
    -- Status
    is_read BOOLEAN DEFAULT FALSE,
    read_at TIMESTAMP,
    
    -- Notification Channels
    sent_email BOOLEAN DEFAULT FALSE,
    sent_websocket BOOLEAN DEFAULT FALSE,
    sent_sms BOOLEAN DEFAULT FALSE,
    
    -- Tracking
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP -- Auto-delete old notifications
);

-- INDEXES
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
```

---

### 9️⃣ AUDIT_LOG TABLE (For Compliance & Debugging)
```sql
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    
    -- What happened
    action VARCHAR(100) NOT NULL, -- CREATE, UPDATE, DELETE, PAYMENT_ATTEMPT
    entity_type VARCHAR(100) NOT NULL, -- ORDER, PAYMENT, USER
    entity_id BIGINT NOT NULL,
    
    -- Who did it
    performed_by BIGINT REFERENCES users(id),
    
    -- Details
    old_values JSONB, -- Previous state
    new_values JSONB, -- New state
    change_description TEXT,
    
    -- Context
    ip_address VARCHAR(45),
    user_agent TEXT,
    
    -- Timestamp
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- INDEXES
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_performed_by ON audit_log(performed_by);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at);
```

---

## 🔑 KEY DESIGN DECISIONS

### 1. **Order Number Generation**
```
Format: ORD-YYYY-XXXXXX
Example: ORD-2024-001234
Purpose: Human-readable, user-facing identifier (vs. auto-increment ID)
```

### 2. **Status State Machines**

**Order Status Flow:**
```
PENDING → PROCESSING → SHIPPED → DELIVERED → COMPLETED
                ↓
              CANCELLED (anytime)
```

**Payment Status Flow:**
```
PENDING → SUCCESS → REFUNDED
       ↘ FAILED ↙
```

### 3. **Denormalization Decision**
Product name, SKU stored in order_items because:
- Historical accuracy (product name might change)
- Query performance (no need to join with products)
- Data integrity (product deleted shouldn't affect orders)

### 4. **JSONB Usage**
- `payment_metadata` - Flexible payment gateway responses
- `audit_log` - Store dynamic changes
- NOT used for: Core business data (remains relational)

### 5. **Soft Deletes vs Hard Deletes**
- Users: Soft delete (is_deleted flag)
- Orders/Payments: NEVER delete (compliance)
- Products: Soft delete (archive)

### 6. **Inventory Management**
```
Actual Stock = quantity_in_stock - reserved_quantity
Reserved = items in PENDING orders
When order ships: reserved_quantity decreases
```

---

## 📊 CONSTRAINTS & RULES

### Business Rules Encoded in DB:
```sql
-- Price checks
CHECK (price >= 0)
CHECK (discount_price < price)

-- Quantity checks
CHECK (quantity > 0)
CHECK (quantity_in_stock >= 0)

-- Payment validation
CHECK (refunded_amount <= amount)

-- One product per cart (unique constraint)
UNIQUE(cart_id, product_id)
```

---

## 🚀 PERFORMANCE OPTIMIZATIONS

### 1. **Indexes Strategy**
- **Primary Keys**: Automatic (PK)
- **Foreign Keys**: Auto-index (referenced in JOINs)
- **Status Columns**: Indexed (frequently filtered)
- **Date Columns**: Indexed (date range queries)
- **Composite**: (user_id, created_at) for "user's recent orders"

### 2. **Query Optimization Examples**

**Fast: Get user's recent orders with items**
```sql
SELECT o.*, oi.*, p.name
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
WHERE o.user_id = ? AND o.created_at > NOW() - INTERVAL '30 days'
ORDER BY o.created_at DESC;
-- Uses: idx_orders_user_created, idx_order_items_order_id
```

**Fast: Get payment by Stripe ID**
```sql
SELECT * FROM payments WHERE stripe_payment_intent_id = ?;
-- Uses: idx_payments_stripe_intent
```

### 3. **Connection Pooling**
- Use HikariCP (default in Spring Boot)
- Min connections: 5, Max: 20

---

## 🔐 SECURITY CONSIDERATIONS

1. **Passwords**: Never store raw (use BCrypt) ✅
2. **Card Data**: Never store (use Stripe tokens) ✅
3. **PII Encryption**: Email, phone (in production) ⚠️
4. **Audit Trail**: All changes logged ✅
5. **Row-Level Security**: Users only see own data ✅

---

## 📈 FUTURE EXTENSIONS

```sql
-- Wishlists (for future)
CREATE TABLE wishlists (...)

-- Product Reviews/Ratings (for future)
CREATE TABLE product_reviews (...)

-- Inventory History (for future)
CREATE TABLE inventory_history (...)

-- Refunds (detailed, vs. just payments)
CREATE TABLE refunds (...)
```

---

## 🗂️ MIGRATION STRATEGY (Flyway)

```
db/migration/
├── V1__initial_schema.sql           -- All tables
├── V2__add_indexes.sql              -- Performance indexes
├── V3__add_audit_table.sql          -- Audit logging
├── V4__add_payment_metadata.sql     -- JSONB columns
└── V5__seed_initial_data.sql        -- Test data
```