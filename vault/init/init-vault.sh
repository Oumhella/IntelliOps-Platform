#!/bin/sh
set -e

export VAULT_ADDR="http://vault:8200"

echo "Waiting for Vault to start..."
until wget -qO- "$VAULT_ADDR/v1/sys/init" 2>&1 | grep -q '"initialized":'; do
    sleep 1
done

IS_INITIALIZED=$(wget -qO- "$VAULT_ADDR/v1/sys/init" | grep -o '"initialized":[a-z]*' | cut -d: -f2)
CREDENTIALS_FILE="/vault/file/credentials.json"

if [ "$IS_INITIALIZED" = "false" ]; then
    echo "Initializing Vault..."
    vault operator init -key-shares=1 -key-threshold=1 -format=json > "$CREDENTIALS_FILE"

    FLAT=$(tr -d '\n' < "$CREDENTIALS_FILE")

    UNKEY=$(echo "$FLAT" | grep -o '"unseal_keys_b64": *\[ *"[^"]*"' | cut -d'"' -f4)
    ROOT_TOKEN=$(echo "$FLAT" | grep -o '"root_token": *"[^"]*"' | cut -d'"' -f4)

    vault operator unseal "$UNKEY"
else
    echo "Vault already initialized."
    if [ -f "$CREDENTIALS_FILE" ]; then
        echo "Loading persisted credentials from $CREDENTIALS_FILE..."
        FLAT=$(tr -d '\n' < "$CREDENTIALS_FILE")
        UNKEY=$(echo "$FLAT" | grep -o '"unseal_keys_b64": *\[ *"[^"]*"' | cut -d'"' -f4)
        ROOT_TOKEN=$(echo "$FLAT" | grep -o '"root_token": *"[^"]*"' | cut -d'"' -f4)
        vault operator unseal "$UNKEY"
    else
        echo "Credentials file not found. Falling back to env variables..."
        ROOT_TOKEN="$VAULT_LOCAL_ROOT_TOKEN"
        vault operator unseal "$VAULT_LOCAL_UNKEY" || true
    fi
fi

export VAULT_TOKEN="$ROOT_TOKEN"

# Save the root token to disk too, purely for local dev convenience (manual
# vault CLI testing via `docker exec -e VAULT_TOKEN=...`). Never do this
# outside a throwaway dev environment - anyone with filesystem access to the
# volume gets full Vault admin.
echo "$ROOT_TOKEN" > /vault/file/root-token.txt

# Activation des moteurs
vault secrets enable -path=secret kv-v2 || true
vault auth enable approle || true

# ============================================================
# PKI: Root CA + Intermediate CA
#
# The root CA only ever does one thing: sign the intermediate.
# All day-to-day leaf certificate issuance happens through the
# intermediate instead. This means if the intermediate is ever
# compromised, we revoke and re-issue just the intermediate -
# the root, and the trust every client has in it, stays intact.
# Issuing straight from the root would mean a compromise forces
# redistributing trust to every single client - much worse blast
# radius for day-to-day risk.
# ============================================================

# --- Root CA (rarely touched after this point) ---
vault secrets enable -path=pki pki || true
vault secrets tune -max-lease-ttl=87600h pki || true
# 10 years: root CAs are long-lived by design. Rotating a root means
# redistributing trust to every client that trusts it, so the standard
# industry practice is a long root lifetime (10-20y) precisely so this
# almost never has to happen.

vault write -field=certificate pki/root/generate/internal \
    common_name="erp-intelliops-root-ca" \
    ttl=87600h > /vault/file/root-ca-cert.pem || true

# --- Intermediate CA (does the actual issuance) ---
vault secrets enable -path=pki_int pki || true
vault secrets tune -max-lease-ttl=43800h pki_int || true
# 5 years: half the root's lifetime. The intermediate is used far more
# actively than the root (it signs every leaf cert), so a shorter cap
# limits how long a compromised intermediate stays trusted, while still
# being long enough to avoid frequent, disruptive re-issuance of the
# intermediate itself.

vault write -field=csr pki_int/intermediate/generate/internal \
    common_name="erp-intelliops-intermediate-ca" > /tmp/int.csr

if [ ! -s /tmp/int.csr ]; then
    echo "ERROR: CSR generation produced an empty file. Aborting."
    exit 1
fi

vault write -field=certificate pki/root/sign-intermediate \
    csr=@/tmp/int.csr format=pem_bundle ttl="43800h" > /tmp/intermediate.cert.pem

if [ ! -s /tmp/intermediate.cert.pem ]; then
    echo "ERROR: Intermediate signing produced an empty file. Aborting."
    exit 1
fi

vault write pki_int/intermediate/set-signed certificate=@/tmp/intermediate.cert.pem

vault write pki_int/config/urls \
    issuing_certificates="http://vault:8200/v1/pki_int/ca" \
    crl_distribution_points="http://vault:8200/v1/pki_int/crl"

# --- Role: what leaf certs services can request from the intermediate ---
vault write pki_int/roles/erp-service-role \
    allowed_domains="erp-network,localhost" \
    allow_subdomains=true \
    allow_bare_domains=true \
    allow_localhost=true \
    max_ttl="72h"
# 3 days: short enough to keep a compromised leaf cert's usable window
# small, matching the industry trend toward short-lived leaf certs backed
# by automated renewal. Not shorter yet because certificate renewal isn't
# automated in this setup yet (that's the next step, via Vault Agent) -
# anything much shorter risks a service losing its cert before anything
# renews it. Revisit down to ~24h once Vault Agent handles renewal.

echo "PKI root + intermediate CA configured."

# ============================================================
# Secrets applicatifs (KV v2)
# ============================================================

vault kv put secret/application \
  JWT_SECRET="${JWT_SECRET:-MaCleSecreteUltraSecuriseeEtTresLonguePourLeCRM2026!}" \
  JWT_EXPIRATION="${JWT_EXPIRATION:-86400000}" \
  app.jwt.secret="${JWT_SECRET:-MaCleSecreteUltraSecuriseeEtTresLonguePourLeCRM2026!}" \
  app.jwt.expiration="${JWT_EXPIRATION:-86400000}" \
  jwt.secret="${JWT_SECRET:-MaCleSecreteUltraSecuriseeEtTresLonguePourLeCRM2026!}" \
  jwt.expiration="${JWT_EXPIRATION:-86400000}"
# Stored under three name variants because different services expect different
# property names for the same secret: common_lib's JwtUtils (used by paiement-service
# and others) reads "app.jwt.secret", gateway-service's own JwtAuthenticationFilter
# reads "jwt.secret". Storing all variants here means every service resolves it
# correctly straight from Vault with zero local placeholder bridging needed.
# TODO: standardize on one property name across all services (ideally via common_lib)
# so this duplication can be removed.

vault kv put secret/user-service \
  spring.datasource.username="${DB_USER:-postgres}" \
  spring.datasource.password="${DB_PASSWORD:-changeme}"

vault kv put secret/abonnement-service \
  spring.datasource.username="${DB_USER:-postgres}" \
  spring.datasource.password="${DB_PASSWORD:-changeme}"

vault kv put secret/lead-service \
  spring.datasource.username="${DB_USER:-postgres}" \
  spring.datasource.password="${DB_PASSWORD:-changeme}"

vault kv put secret/stock-service \
  spring.datasource.username="${DB_USER:-postgres}" \
  spring.datasource.password="${DB_PASSWORD:-changeme}"

vault kv put secret/mcp-server \
  spring.ai.openai.api-key="${NVIDIA_API_KEY}" \
  spring.ai.openai.chat.options.model="${NVIDIA_MODEL}"\
  agent.llm.provider="${AGENT_LLM_PROVIDER:-none}" \

vault kv put secret/paiement-service \
    spring.datasource.username="${DB_USER:-postgres}" \
    spring.datasource.password="${DB_PASSWORD:-changeme}" \
    stripe.api-key="${STRIPE_API_KEY}" \
    minio.url="http://minio:9000" \
    minio.access-key="${MINIO_ROOT_USER}" \
    minio.secret-key="${MINIO_ROOT_PASSWORD}" \
    minio.bucket-name="invoices-erp"

vault kv put secret/delivery-service \
  spring.datasource.username="${DB_USER:-postgres}" \
  spring.datasource.password="${DB_PASSWORD:-changeme}"
# ============================================================
# Policy + AppRole
# ============================================================

cat <<'EOF' > /tmp/config-server-policy.hcl
path "secret/data/*" { capabilities = ["read"] }
path "pki_int/issue/erp-service-role" { capabilities = ["create", "update"] }
EOF
vault policy write config-server-policy /tmp/config-server-policy.hcl

# Periodic token instead of a hard max_ttl. Spring Cloud Vault's
# LifecycleAwareSessionManager renews this automatically in the
# background (roughly at the midpoint of its remaining life), so as
# long as a service stays up and keeps renewing, its token never
# expires. A service that crashes or is killed simply stops renewing,
# and its token dies within one period on its own - no hard ceiling
# to hit, but also nothing dangling indefinitely once a service is gone.
vault write auth/approle/role/config-server-role \
    token_policies="config-server-policy" \
    token_ttl=1h \
    period=1h \
    token_max_ttl=0

echo "Vault setup inside Docker complete!"

ROLE_ID=$(vault read -field=role_id auth/approle/role/config-server-role/role-id)
SECRET_ID=$(vault write -field=secret_id -f auth/approle/role/config-server-role/secret-id)

mkdir -p /vault/file
printf 'SPRING_CLOUD_VAULT_APP_ROLE_ROLE_ID=%s\nSPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID=%s\n' "$ROLE_ID" "$SECRET_ID" > /vault/file/approle.env
printf 'SPRING_CLOUD_VAULT_APP_ROLE_ROLE_ID=%s\nSPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID=%s\n' "$ROLE_ID" "$SECRET_ID" > /vault/file/approle.properties
echo "AppRole credentials written to approle.env and approle.properties"