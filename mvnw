#!/usr/bin/env bash
# Minimal Maven launcher for this project. Downloaded distributions are SHA-512 checked.
set -euo pipefail
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi
if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then
  echo "Maven is not installed and neither curl nor wget is available to bootstrap it." >&2
  exit 1
fi

DEFAULT_VERSION="3.10.0"
VERSION="${MAVEN_VERSION:-$DEFAULT_VERSION}"
if [[ "$VERSION" == "3.10.0" ]]; then
  EXPECTED_SHA512="908b1501bfb420bf7c8affb855534a9c407fd6099367bfb9f2f2dcb8e9799102bffb84518cde74c679bd76870247c6528683abdd620581bffa90f95d92d175aa"
elif [[ "$VERSION" == "3.9.16" ]]; then
  EXPECTED_SHA512="831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6"
else
  EXPECTED_SHA512="${MAVEN_SHA512:-}"
  if [[ -z "$EXPECTED_SHA512" ]]; then
    echo "Custom MAVEN_VERSION requires MAVEN_SHA512; refusing to install an unverified Maven archive." >&2
    exit 1
  fi
fi

BASE="${HOME}/.cache/coala-maven"
DIST="${BASE}/apache-maven-${VERSION}"
if [[ ! -x "${DIST}/bin/mvn" ]]; then
  mkdir -p "${BASE}"
  ARCHIVE="${BASE}/apache-maven-${VERSION}-bin.tar.gz"
  URLS=(
    "https://dlcdn.apache.org/maven/maven-3/${VERSION}/binaries/apache-maven-${VERSION}-bin.tar.gz"
    "https://archive.apache.org/dist/maven/maven-3/${VERSION}/binaries/apache-maven-${VERSION}-bin.tar.gz"
  )
  downloaded=0
  for url in "${URLS[@]}"; do
    rm -f "${ARCHIVE}"
    echo "Downloading Maven ${VERSION} from ${url} ..." >&2
    if command -v curl >/dev/null 2>&1; then
      if curl --fail --location --silent --show-error "$url" -o "${ARCHIVE}"; then downloaded=1; break; fi
    else
      if wget -q "$url" -O "${ARCHIVE}"; then downloaded=1; break; fi
    fi
  done
  if [[ "$downloaded" -ne 1 ]]; then
    echo "Could not download Maven ${VERSION}. Install Maven 3.10.x or allow network access, then retry." >&2
    rm -f "${ARCHIVE}"
    exit 1
  fi

  if command -v sha512sum >/dev/null 2>&1; then
    ACTUAL_SHA512="$(sha512sum "${ARCHIVE}" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA512="$(shasum -a 512 "${ARCHIVE}" | awk '{print $1}')"
  else
    echo "A SHA-512 utility (sha512sum or shasum) is required to verify Maven." >&2
    rm -f "${ARCHIVE}"
    exit 1
  fi
  if [[ "${ACTUAL_SHA512,,}" != "${EXPECTED_SHA512,,}" ]]; then
    echo "Maven archive SHA-512 mismatch; refusing to extract it." >&2
    rm -f "${ARCHIVE}"
    exit 1
  fi
  tar -xzf "${ARCHIVE}" -C "${BASE}"
  rm -f "${ARCHIVE}"
fi
exec "${DIST}/bin/mvn" "$@"
