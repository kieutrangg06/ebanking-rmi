@echo off
chcp 65001 >nul
title Cau Hinh Tuong Lua Windows Cho eBanking RMI Client
echo ===============================================================================
echo     MO CONG TUONG LUA WINDOWS CHO MAY KHACH RMI CLIENT (CALLBACK PORT 1097)
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

echo Dang mo Inbound Rule cho cong RMI Client Callback (TCP 1097)...
netsh advfirewall firewall delete rule name="eBanking RMI Client Callback (1097)" >nul 2>&1
netsh advfirewall firewall add rule name="eBanking RMI Client Callback (1097)" dir=in action=allow protocol=TCP localport=1097 profile=any

echo.
echo ===============================================================================
echo [THANH CONG] Da mo cong tuong lua TCP 1097 tren May Khach (Client)!
echo Server gio day co the ban Callback ve may nay (thong bao bien dong so du,
echo nhan tien, lai suat, hoac lenh khoa/kick tu Admin) ma khong bi chan.
echo ===============================================================================
echo.
pause
