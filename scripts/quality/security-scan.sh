#!/usr/bin/env bash
# The security analysis SonarQube Community cannot do.
#
# Community Build has no taint analysis: it never follows a value from a request into a query, and
# says so itself in a banner. These three cover that gap, all free and all local:
#
#   Find Security Bugs  - bytecode-level taint for Java: SQL/HQL injection, traversal, crypto
#   Semgrep             - source-level rules across Java and TypeScript, OWASP ruleset
#   OSV-Scanner         - known CVEs in dependencies, which no edition of Sonar covers at all
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FAILED=0

echo "== Find Security Bugs (Java)"
for module in backend services/order-service services/inventory-service; do
  printf '   %-28s ' "$module"
  if (cd "$REPO_ROOT/$module" && mvn -B -q -P security -DskipTests -Djacoco.skip=true verify > /tmp/fsb.log 2>&1); then
    echo "sin hallazgos"
  else
    echo "HALLAZGOS:"
    grep -E "^\[ERROR\] (Low|Medium|High)" /tmp/fsb.log | sed 's/^/      /' | head -20
    FAILED=1
  fi
done

echo
echo "== Semgrep (Java + TypeScript)"
# --config=auto pulls the community rulesets, including the OWASP Top 10 packs.
docker run --rm -v "$REPO_ROOT:/src" returntocorp/semgrep semgrep scan \
  --config=p/java --config=p/typescript --config=p/owasp-top-ten \
  --exclude=node_modules --exclude=dist --exclude=target --exclude=coverage \
  --error --quiet /src || FAILED=1

echo
echo "== OSV-Scanner (dependencias)"
# Neither Community nor Developer Edition looks at CVEs in dependencies; this is the only tool here
# that answers "is a library we use known to be vulnerable".
docker run --rm -v "$REPO_ROOT:/src" ghcr.io/google/osv-scanner:latest scan source --recursive /src \
  || FAILED=1

echo
if [[ "$FAILED" == "0" ]]; then
  echo "Sin hallazgos."
else
  echo "Hay hallazgos arriba. Los descartes justificados van en config/spotbugs-noise.xml, con su razón."
fi
exit "$FAILED"
