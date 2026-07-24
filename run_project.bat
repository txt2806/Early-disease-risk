@echo off
title CardioCare Application Runner
echo =======================================================
echo   GIAI PHONG CONG 8080 (NEU DANG BI CHIEM DUNG)
echo =======================================================
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
    echo Phat hien tien trinh PID %%a dang chiem dung cong 8080. Dang dung tien trinh...
    taskkill /F /PID %%a >nul 2>&1
)
echo Giai phong cong hoan tat.
echo.
echo =======================================================
echo   DANG KHOI DONG CARDIO CARE DOCTOR PORTAL (NEW)
echo =======================================================
echo Vui long cho trong giay lat...
call mvnw.cmd clean spring-boot:run
pause
