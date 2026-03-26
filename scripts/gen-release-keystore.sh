#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
keystore_dir="$root_dir/keystore"
keystore_path="$keystore_dir/release.jks"
env_file="$root_dir/.env.release-signing"

mkdir -p "$keystore_dir"

if [[ -f "$keystore_path" ]]; then
  echo "Keystore already exists: $keystore_path"
  echo "Refusing to overwrite."
  exit 1
fi

if command -v openssl >/dev/null 2>&1; then
  store_pass="$(openssl rand -base64 24 | tr -d '\n' | tr '/+' 'ab')"
  key_pass="$(openssl rand -base64 24 | tr -d '\n' | tr '/+' 'cd')"
else
  store_pass="$(head -c 24 /dev/urandom | base64 | tr -d '\n' | tr '/+' 'ab')"
  key_pass="$(head -c 24 /dev/urandom | base64 | tr -d '\n' | tr '/+' 'cd')"
fi

key_alias="release"
dname="${RELEASE_DNAME:-CN=Cognautic,O=Cognautic,C=US}"

keytool -genkeypair \
  -keystore "$keystore_path" \
  -storepass "$store_pass" \
  -keypass "$key_pass" \
  -alias "$key_alias" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "$dname" \
  -storetype JKS

cat >"$env_file" <<EOF
# Load with: set -a; source .env.release-signing; set +a
RELEASE_STORE_FILE=$keystore_path
RELEASE_STORE_PASSWORD=$store_pass
RELEASE_KEY_ALIAS=$key_alias
RELEASE_KEY_PASSWORD=$key_pass
EOF

chmod 600 "$env_file" || true

echo "Created keystore: $keystore_path"
echo "Wrote env file:  $env_file"
echo ""
echo "Next:"
echo "  set -a; source .env.release-signing; set +a"
echo "  ./gradlew assembleRelease"

