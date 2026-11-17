
#!/usr/bin/env bash

set -euo pipefail

JOB_CLASS="com.example.MyFlinkJob"
JAR="flink-app.jar"
JOBMANAGER_URL="http://localhost:8081"

echo "================================"
echo " Building Flink application"
echo "================================"

mvn clean package -DskipTests

if [ ! -f "target/$JAR" ]; then
    echo "ERROR: JAR not found!"
    exit 1
fi

echo "================================"
echo " Starting Flink cluster"
echo "================================"

docker compose up -d

echo "Waiting for JobManager..."

for i in $(seq 1 60); do
    if curl -fsS "$JOBMANAGER_URL/overview" >/dev/null; then
        break
    fi

    if [ "$i" -eq 60 ]; then
        echo "ERROR: JobManager did not become ready"
        exit 1
    fi

    sleep 2
done

echo "================================"
echo " Submitting Flink job"
echo "================================"

docker compose exec -T jobmanager \
    flink run -d \
    -c "$JOB_CLASS" \
    "/opt/flink/usrlib/$JAR"

echo "================================"
echo " Deployment completed"
echo "================================"

echo "Flink Dashboard: $JOBMANAGER_URL"
