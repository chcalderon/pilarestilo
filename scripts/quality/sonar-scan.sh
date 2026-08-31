#!/usr/bin/env bash
# Runs the whole codebase through the local SonarQube, coverage included.
#
# Local only: the `quality` compose profile is off by default and vps_deploy.sh strips it, so this
# never runs against production. See CLAUDE.md.
set -euo pipefail

SONAR_URL="${SONAR_URL:-http://localhost:9000}"
SONAR_NETWORK="${SONAR_NETWORK:-infra_pe_net}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "SONAR_TOKEN no definido. Genéralo en $SONAR_URL → My Account → Security." >&2
  exit 1
fi

scan_maven() {
  local dir="$1" key="$2" name="$3"
  echo "== $name"
  # verify first: without jacoco's xml report the gate's coverage condition has nothing to read,
  # and a project with no coverage passes it for the wrong reason.
  (cd "$REPO_ROOT/$dir" \
    && mvn -q clean verify \
    && mvn -q -DskipTests org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
         -Dsonar.host.url="$SONAR_URL" \
         -Dsonar.token="$SONAR_TOKEN" \
         -Dsonar.projectKey="$key" \
         -Dsonar.projectName="$name")
}

scan_maven backend pilarestilo-backend "PilarEstilo backend"
scan_maven services/notification-service pilarestilo-notification-service "PilarEstilo notification-service"

echo "== PilarEstilo frontend"
(cd "$REPO_ROOT/frontend" && npm run test:coverage --silent)

# -Xmx4g on purpose: the container's default heap dies partway through the Astro/TSX tree, and the
# failure reads as a scanner crash rather than as running out of memory.
# MSYS_NO_PATHCONV only here: Git Bash rewrites the container-side /usr/src into
# C:/Program Files/Git/usr/src and the mount lands nowhere. It cannot be exported for the whole
# script -- with it set, the mvn wrapper hands java a POSIX path and Maven dies on
# ClassNotFoundException: plexus.classworlds.launcher.Launcher. Unset on Linux, so it is inert.
MSYS_NO_PATHCONV=1 docker run --rm --network "$SONAR_NETWORK" \
  -e SONAR_HOST_URL="http://sonarqube:9000" \
  -e SONAR_TOKEN="$SONAR_TOKEN" \
  -e SONAR_SCANNER_OPTS="-Xmx4g" \
  -v "$REPO_ROOT/frontend:/usr/src" \
  sonarsource/sonar-scanner-cli \
  -Dsonar.projectKey=pilarestilo-frontend \
  -Dsonar.projectName="PilarEstilo frontend" \
  -Dsonar.sources=src \
  -Dsonar.tests=src \
  -Dsonar.test.inclusions="**/__tests__/**,**/*.test.ts,**/*.test.tsx" \
  -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info \
  -Dsonar.exclusions="**/node_modules/**,**/dist/**,**/.astro/**,**/e2e/**,**/coverage/**"

echo
echo "Resultados: $SONAR_URL/projects"
