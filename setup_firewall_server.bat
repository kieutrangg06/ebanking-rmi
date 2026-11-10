@echo off
chcp 65001 >nul
title Cau Hinh Tuong Lua Windows Cho eBanking RMI Server
echo ===============================================================================
echo        MO CONG TUONG LUA WINDOWS CHO MAY CHU RMI SERVER (FIXED PORTS)
echo ===============================================================================
echo.
echo Kiem tra quyen Administrator...
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [LOI] Vui long Click chuot phai vao file nay va chon 'Run as administrator'!
    echo.
    pause
    exit /b 1
)

echo [1/2] Dang mo Inbound Rule cho cong RMI Registry (TCP 1099)...
netsh advfirewall firewall delete rule name="eBanking RMI Registry (1099)" >nul 2>&1
netsh advfirewall firewall add rule name="eBanking RMI Registry (1099)" dir=in action=allow protocol=TCP localport=1099 profile=any

echo [2/2] Dang mo Inbound Rule cho cong RMI Server Service (TCP 1098)...
netsh advfirewall firewall delete rule name="eBanking RMI Service (1098)" >nul 2>&1
netsh advfirewall firewall add rule name="eBanking RMI Service (1098)" dir=in action=allow protocol=TCP localport=1098 profile=any

echo.
echo ===============================================================================
echo [THANH CONG] Da mo cong tuong lua TCP 1098 va 1099 tren May Chu (Server)!
echo May Client tu may tinh khac gio day co the ket noi toi Server ma khong bi chan.
echo ===============================================================================
echo.
pause
