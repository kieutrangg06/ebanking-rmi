@echo off
chcp 65001 >nul
title e-Banking RMI Client
cd /d "%~dp0"

echo ===============================================================================
echo                 KHOI DONG EBANKING RMI CLIENT (GIAO DIEN SO)
echo ===============================================================================
echo.

REM Kiem tra Java
where java >nul 2>&1
if %errorLevel% neq 0 (
    if exist "C:\Users\kieut\.jdks\jbr-17.0.14\bin\java.exe" (
        set "JAVA_CMD=C:\Users\kieut\.jdks\jbr-17.0.14\bin\java.exe"
    ) else if exist "C:\Program Files\Java\jdk-17\bin\java.exe" (
        set "JAVA_CMD=C:\Program Files\Java\jdk-17\bin\java.exe"
    ) else (
        echo [LOI] Khong tim thay Java JDK 17+ tren he thong. Vui long cai dat Java!
        pause
        exit /b 1
    )
) else (
    set "JAVA_CMD=java"
)

REM Kiem tra file JAR
if exist "target\ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar" (
    set "JAR_PATH=target\ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar"
) else (
    echo [THONG BAO] Dang bien dich du an sang file JAR...
    call "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" package -DskipTests
    set "JAR_PATH=target\ebanking-rmi-1.0-SNAPSHOT-jar-with-dependencies.jar"
)

echo Dang khoi dong Client giao dien hien dai...
start "" "%JAVA_CMD%" -cp "%JAR_PATH%" client.ClientMain
