ALTER TABLE enterprises ADD COLUMN legal_name varchar(160), ADD COLUMN legal_identifier varchar(80),
 ADD COLUMN tax_identifier varchar(80), ADD COLUMN contact_email varchar(160), ADD COLUMN contact_phone varchar(40),
 ADD COLUMN website varchar(255), ADD COLUMN address_line1 varchar(255), ADD COLUMN address_line2 varchar(255),
 ADD COLUMN city varchar(100), ADD COLUMN postal_code varchar(30), ADD COLUMN country_code varchar(2) DEFAULT 'MA',
 ADD COLUMN currency_code varchar(3) DEFAULT 'MAD', ADD COLUMN timezone varchar(80) DEFAULT 'Africa/Casablanca';
CREATE TABLE password_reset_tokens (id bigserial PRIMARY KEY, token_hash varchar(64) NOT NULL UNIQUE,
 user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE, expires_at timestamp NOT NULL,
 created_at timestamp NOT NULL, used_at timestamp);
CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);
