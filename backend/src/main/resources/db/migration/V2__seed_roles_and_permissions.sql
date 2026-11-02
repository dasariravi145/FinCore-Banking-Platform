-- ==============================================================================
-- Migration V2: Seed Essential Roles and Permissions
-- FinCore Banking Platform
-- Uses ANSI SQL (WHERE NOT EXISTS) for dual PostgreSQL & H2 compatibility
-- ==============================================================================

-- Insert Base Roles
INSERT INTO roles (name, description)
SELECT 'ROLE_CUSTOMER', 'Standard banking customer with self-service account access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_CUSTOMER');

INSERT INTO roles (name, description)
SELECT 'ROLE_ADMIN', 'Platform security and system administrator with management privileges'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');

-- Insert Base Permissions
INSERT INTO permissions (name, description)
SELECT 'USER_READ', 'Permission to view own profile information'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'USER_READ');

INSERT INTO permissions (name, description)
SELECT 'USER_WRITE', 'Permission to update own profile information'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'USER_WRITE');

INSERT INTO permissions (name, description)
SELECT 'ADMIN_READ', 'Permission to view administrative metrics and user registries'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'ADMIN_READ');

INSERT INTO permissions (name, description)
SELECT 'ADMIN_WRITE', 'Permission to manage system configurations and user statuses'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'ADMIN_WRITE');

INSERT INTO permissions (name, description)
SELECT 'AUDIT_READ', 'Permission to inspect security audit trails and compliance logs'
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'AUDIT_READ');

-- Assign Permissions to ROLE_CUSTOMER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_CUSTOMER' AND p.name IN ('USER_READ', 'USER_WRITE')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Assign Permissions to ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name IN ('USER_READ', 'USER_WRITE', 'ADMIN_READ', 'ADMIN_WRITE', 'AUDIT_READ')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
