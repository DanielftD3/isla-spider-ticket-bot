#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -f target/isla-spider-ticket-bot.jar ]; then
    jar=target/isla-spider-ticket-bot.jar
elif [ -f isla-spider-ticket-bot.jar ]; then
    jar=isla-spider-ticket-bot.jar
else
    echo "Falta el JAR. Compila el proyecto o sube el artefacto de GitHub Actions." >&2
    exit 1
fi
exec java -Xms32m -Xmx128m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -Xss512k -jar "$jar"
