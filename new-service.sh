#!/usr/bin/env bash
# Stamp out one service from the template, using the ports, paths and
# database names from the architecture diagram.
#
#   ./new-service.sh learning-service
#   ./new-service.sh assessment-service
#
# Run with no arguments to see the list.
set -euo pipefail

# name              port  db             resource
SERVICES="
user-service        8101  userdb         users
academic-service    8102  academicdb     academic
schedule-service    8103  scheduledb     schedule
enrollment-service  8104  enrollmentdb   enrollment
learning-service    8105  learningdb     learning
assessment-service  8106  assessmentdb   assessment
payment-service     8107  paymentdb      payments
analytics-service   8108  analyticsdb    analytics
"

usage() {
  echo "usage: $0 <service-name>"
  echo
  echo "known services (from the architecture diagram):"
  echo "$SERVICES" | sed '/^$/d' | awk '{printf "  %-20s port %-6s db %-14s /api/%s\n", $1, $2, $3, $4}'
  exit 1
}

[ $# -eq 1 ] || usage
NAME="$1"

LINE=$(echo "$SERVICES" | awk -v n="$NAME" '$1==n {print; exit}')
[ -n "$LINE" ] || { echo "unknown service: $NAME"; echo; usage; }

PORT=$(echo "$LINE" | awk '{print $2}')
DB=$(echo "$LINE" | awk '{print $3}')
RESOURCE=$(echo "$LINE" | awk '{print $4}')

# learning-service -> LearningServiceApplication
CLASS=$(echo "$NAME" | awk -F- '{for(i=1;i<=NF;i++) printf toupper(substr($i,1,1)) substr($i,2); print "Application"}')

[ -e "$NAME" ] && { echo "$NAME already exists, refusing to overwrite"; exit 1; }

cp -r template "$NAME"
cd "$NAME"

mv src/main/java/edu/lms/service/__CLASS__.java "src/main/java/edu/lms/service/${CLASS}.java"

# perl rather than sed: macOS sed needs -i '' and GNU sed needs -i, and perl
# behaves the same on both.
find . -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name 'Dockerfile' -o -name '*.md' -o -name '*.sql' \) -print0 \
  | xargs -0 perl -pi \
      -e "s/__ARTIFACT__/${NAME}/g;" \
      -e "s/__CLASS__/${CLASS}/g;" \
      -e "s/__PORT__/${PORT}/g;" \
      -e "s/__DB__/${DB}/g;" \
      -e "s/__RESOURCE__/${RESOURCE}/g;"

# Fail loudly rather than handing Docker a broken file.
if grep -rq '__[A-Z]*__' . 2>/dev/null; then
  echo "ERROR: placeholders left unreplaced:"
  grep -rn '__[A-Z]*__' . || true
  exit 1
fi

echo "created ./${NAME}  (port ${PORT}, db ${DB}, /api/${RESOURCE})"
echo
echo "next:"
echo "  docker compose up --build ${NAME}"
echo "  curl localhost:${PORT}/actuator/health"
