@echo off
chcp 65001 >nul
title Kiem Tra Va Dong Cong Tuong Lua Windows - eBanking RMI
echo ===============================================================================
echo          KIỂM TRA VÀ ĐÓNG CÁC CỔNG TƯỜNG LỬA WINDOWS (1097, 1098, 1099)
echo ===============================================================================
echo.

REM Kiem tra quyen Administrator
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [CẢNH BÁO] Để đóng được cổng tường lửa, bạn cần chạy file này bằng quyền Admin!
    echo Hãy click chuột phải vào file này và chọn: "Run as administrator"
    echo.
    echo Đang thực hiện kiểm tra trạng thái cổng hiện tại...
    echo.
)

echo [1] KIỂM TRA CÁC CỔNG RMI ĐANG LẮNG NGHE (LISTENING TRÊN MÁY):
netstat -ano | findstr /R ":1097 :1098 :1099 :3306"
if %errorlevel% neq 0 (
    echo     -> Hiện không có cổng 1097, 1098, 1099 nào đang bị chiếm dụng.
)
echo.

echo [2] KIỂM TRA CÁC RULE TƯỜNG LỬA ĐÃ TẠO:
netsh advfirewall firewall show rule name="eBanking RMI Registry (1099)" >nul 2>&1
if %errorlevel% equ 0 (
    echo     [x] Cổng 1099 (Registry) : ĐANG MỞ
) else (
    echo     [ ] Cổng 1099 (Registry) : CHƯA MỞ (ĐANG ĐÓNG)
)

netsh advfirewall firewall show rule name="eBanking RMI Service (1098)" >nul 2>&1
if %errorlevel% equ 0 (
    echo     [x] Cổng 1098 (Service)  : ĐANG MỞ
) else (
    echo     [ ] Cổng 1098 (Service)  : CHƯA MỞ (ĐANG ĐÓNG)
)

netsh advfirewall firewall show rule name="eBanking RMI Client Callback (1097)" >nul 2>&1
if %errorlevel% equ 0 (
    echo     [x] Cổng 1097 (Callback) : ĐANG MỞ
) else (
    echo     [ ] Cổng 1097 (Callback) : CHƯA MỞ (ĐANG ĐÓNG)
)
echo.

echo ===============================================================================
echo BẠN CÓ MUỐN ĐÓNG VÀ XÓA TẤT CẢ CÁC CỔNG TRÊN KHỎI TƯỜNG LỬA KHÔNG?
echo (Nhấn Y để ĐÓNG TOÀN BỘ, hoặc phím bất kỳ để thoát)
echo ===============================================================================
set /p choice="Lựa chọn của bạn (Y/N): "
if /i "%choice%" neq "Y" (
    echo Đã hủy thao tác.
    pause
    exit /b 0
)

echo.
echo Đang xóa các Rule và đóng toàn bộ cổng trên tường lửa...
netsh advfirewall firewall delete rule name="eBanking RMI Registry (1099)" >nul 2>&1
netsh advfirewall firewall delete rule name="eBanking RMI Service (1098)" >nul 2>&1
netsh advfirewall firewall delete rule name="eBanking RMI Client Callback (1097)" >nul 2>&1
netsh advfirewall firewall delete rule name=all protocol=tcp localport=1097 >nul 2>&1
netsh advfirewall firewall delete rule name=all protocol=tcp localport=1098 >nul 2>&1
netsh advfirewall firewall delete rule name=all protocol=tcp localport=1099 >nul 2>&1

echo.
echo [THÀNH CÔNG] Đã đóng và xóa sạch toàn bộ các rule tường lửa cho cổng 1097, 1098, 1099!
echo.
pause
