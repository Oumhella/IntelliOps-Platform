#!/bin/sh
set -e

if [ -f /vault/file/approle.env ]; then
  echo "Loading AppRole credentials..."
  export $(grep -v '^#' /vault/file/approle.env | xargs)
else
  echo "WARNING: approle.env not found, Vault auth will fail"
fi

exec java -jar app.jar