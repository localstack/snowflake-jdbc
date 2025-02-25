#!/bin/bash -e

# scripts used to check if all dependencies are shaded into snowflake internal path

set -o pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

if jar tvf $DIR/../target/snowflake-jdbc-fips.jar  | awk '{print $8}' | grep -v -E "/$" | grep -v -E "^(net|com)/snowflake" | grep -v -E "(com|net)/\$" | grep -v -E "^META-INF" | grep -v -E "^iso3166_" | grep -v -E "^mozilla" | grep -v -E "^com/sun/jna" | grep -v com/sun/ | grep -v mime.types | grep -v -E "^com/github/luben/zstd/" | grep -v -E "^aix/" | grep -v -E "^darwin/" | grep -v -E "^freebsd/" | grep -v -E "^linux/" | grep -v -E "^win/"; then
  echo "[ERROR] JDBC jar includes class not under the snowflake namespace"
  exit 1
fi
