$ErrorActionPreference = 'Stop'

# Local-only launcher: never inherits the remote .env database connection.
$java21 = 'C:\Program Files\Java\jdk-21'
if (-not (Test-Path "$java21\bin\java.exe")) {
    throw "JDK 21 not found at $java21"
}

$env:JAVA_HOME = $java21
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/ielts_center'
$env:DATABASE_USERNAME = 'ielts'
$env:DATABASE_PASSWORD = 'ielts'
$env:FLYWAY_OUT_OF_ORDER = 'false'
$env:PORT = '8081'

Write-Host "Starting backend on http://localhost:8081 with local PostgreSQL and Java 21..."
mvn spring-boot:run
