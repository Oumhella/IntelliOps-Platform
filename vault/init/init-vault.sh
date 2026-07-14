#!/bin/sh
set -e

export VAULT_ADDR="http://vault:8200"

echo "Waiting for Vault to start..."
until wget -qO- "$VAULT_ADDR/v1/sys/init" 2>&1 | grep -q '"initialized":'; do
    sleep 1
done

IS_INITIALIZED=$(wget -qO- "$VAULT_ADDR/v1/sys/init" | grep -o '"initialized":[a-z]*' | cut -d: -f2)

if [ "$IS_INITIALIZED" = "false" ]; then
    echo "Initializing Vault..."
    vault operator init -key-shares=1 -key-threshold=1 -format=json > /tmp/vault-init.json

    FLAT=$(tr -d '\n' < /tmp/vault-init.json)

    UNKEY=$(echo "$FLAT" | grep -o '"unseal_keys_b64": *\[ *"[^"]*"' | cut -d'"' -f4)
    ROOT_TOKEN=$(echo "$FLAT" | grep -o '"root_token": *"[^"]*"' | cut -d'"' -f4)

    vault operator unseal "$UNKEY"
else
    echo "Vault already initialized."
    ROOT_TOKEN="$VAULT_LOCAL_ROOT_TOKEN"
    vault operator unseal "$VAULT_LOCAL_UNKEY" || true
fi

export VAULT_TOKEN="$ROOT_TOKEN"

# Activation des moteurs
vault secrets enable -path=secret kv-v2 || true
vault auth enable approle || true

# Injection des secrets (ex: JWT et Base de données)
vault kv put secret/application JWT_SECRET="mon_super_secret_jwt" JWT_EXPIRATION="86400"
vault kv put secret/user-service spring.datasource.username="root" spring.datasource.password="root"

# Configuration de la politique et du rôle
cat <<EOF > /tmp/config-server-policy.hcl
path "secret/data/*" { capabilities = ["read"] }
EOF
vault policy write config-server-policy /tmp/config-server-policy.hcl
vault write auth/approle/role/config-server-role token_policies="config-server-policy" token_ttl=1h token_max_ttl=4h

echo "Vault setup inside Docker complete!"

ROLE_ID=$(vault read -field=role_id auth/approle/role/config-server-role/role-id)
SECRET_ID=$(vault write -field=secret_id -f auth/approle/role/config-server-role/secret-id)

mkdir -p /vault/file
printf 'SPRING_CLOUD_VAULT_APP_ROLE_ROLE_ID=%s\nSPRING_CLOUD_VAULT_APP_ROLE_SECRET_ID=%s\n' "$ROLE_ID" "$SECRET_ID" > /vault/file/approle.env
echo "AppRole credentials written to approle.env"