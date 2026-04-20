CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- users
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email VARCHAR(255) UNIQUE NOT NULL,
  full_name VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- products
CREATE TABLE products (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  price_amount NUMERIC(15,2) NOT NULL,
  price_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  image_url VARCHAR(500),
  condition VARCHAR(10) NOT NULL,
  brand VARCHAR(255) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- discounts
CREATE TABLE discounts (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  code VARCHAR(50) UNIQUE NOT NULL,
  type VARCHAR(20) NOT NULL,
  value NUMERIC(15,2) NOT NULL,
  min_order_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
  min_order_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  valid_from DATE NOT NULL,
  valid_until DATE NOT NULL,
  max_uses INT NOT NULL DEFAULT 1,
  times_used INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true
);

-- customer_credits
CREATE TABLE customer_credits (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  customer_id UUID NOT NULL REFERENCES users(id),
  balance_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
  balance_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  UNIQUE(customer_id)
);

-- credit_movements
CREATE TABLE credit_movements (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  customer_id UUID NOT NULL REFERENCES users(id),
  type VARCHAR(30) NOT NULL,
  amount_value NUMERIC(15,2) NOT NULL,
  amount_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  reason TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- orders
CREATE TABLE orders (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  customer_id UUID NOT NULL REFERENCES users(id),
  subtotal_amount NUMERIC(15,2) NOT NULL,
  subtotal_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
  discount_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  total_amount NUMERIC(15,2) NOT NULL,
  total_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  payment_method VARCHAR(30) NOT NULL,
  notes TEXT,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- order_items (snapshot)
CREATE TABLE order_items (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id UUID NOT NULL REFERENCES orders(id),
  product_id UUID NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  unit_price_amount NUMERIC(15,2) NOT NULL,
  unit_price_currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  quantity INT NOT NULL
);

-- payments
CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id UUID NOT NULL REFERENCES orders(id),
  method VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  proof_reference TEXT,
  reviewed_by UUID REFERENCES users(id),
  reviewed_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
