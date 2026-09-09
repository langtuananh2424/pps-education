@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul 2>&1

REM ===================================================================
REM  Chay load test PPS Education tu may Windows.
REM
REM  Cach dung:
REM     run_loadtest.bat                 -> smoke test (1 VU, 1 vong)
REM     run_loadtest.bat smoke
REM     run_loadtest.bat capacity        -> tim tran (ramping-arrival-rate)
REM     run_loadtest.bat browse          -> mo phong nguoi dung that
REM     run_loadtest.bat login           -> chi rieng luong auth
REM
REM  Credential: KHONG hardcode trong file nay (repo public).
REM  Dat truoc bang bien moi truong, hoac de script tu hoi:
REM     set TEST_USERNAME=loadtest_user
REM     set TEST_PASSWORD=...
REM
REM  Bien co the doi (dat truoc khi chay):
REM     TARGET_URL   mac dinh https://admin-staging.ppsvietnam.edu.vn
REM     RPS_PEAK     mac dinh 800  (chi dung o mode capacity)
REM     VU_PEAK      mac dinh 1000 (chi dung o mode browse)
REM     SKIP_WRITES  dat =1 de tat hoan toan phan ghi du lieu
REM ===================================================================

cd /d "%~dp0"

set "MODE_ARG=%~1"
if "%MODE_ARG%"=="" set "MODE_ARG=smoke"

REM --- k6 da cai chua ---------------------------------------------------
where k6 >nul 2>&1
if errorlevel 1 (
    echo [LOI] Khong tim thay k6 trong PATH.
    echo       Cai bang:  winget install k6 --source winget
    echo       Hoac xem:  https://k6.io/docs/get-started/installation/
    exit /b 1
)

REM --- Muc tieu ---------------------------------------------------------
if "%TARGET_URL%"=="" set "TARGET_URL=https://admin-staging.ppsvietnam.edu.vn"

REM --- Credential -------------------------------------------------------
if "%TEST_USERNAME%"=="" (
    set /p "TEST_USERNAME=Tai khoan test: "
)
if "%TEST_PASSWORD%"=="" (
    REM Doc mat khau an ky tu qua PowerShell - `set /p` cua cmd hien ro mat khau
    for /f "delims=" %%p in ('powershell -NoProfile -Command "$s=Read-Host -AsSecureString 'Mat khau'; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"') do set "TEST_PASSWORD=%%p"
)
if "%TEST_USERNAME%"=="" goto :no_cred
if "%TEST_PASSWORD%"=="" goto :no_cred

REM --- Mac dinh cho tung mode ------------------------------------------
if "%RPS_PEAK%"=="" set "RPS_PEAK=800"
if "%VU_PEAK%"=="" set "VU_PEAK=1000"

REM Nhan thoi gian cho file ket qua. Dung PowerShell thay vi %DATE%/%TIME%
REM vi 2 bien do phu thuoc dinh dang vung/mien, de sinh ten file hong.
for /f "delims=" %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%t"

if not exist "results" mkdir "results"
set "SUMMARY=results\k6-%MODE_ARG%-%TS%.json"

set "COMMON=-e TARGET_URL=%TARGET_URL% -e TEST_USERNAME=%TEST_USERNAME% -e TEST_PASSWORD=%TEST_PASSWORD%"
if not "%SKIP_WRITES%"=="" set "COMMON=%COMMON% -e SKIP_WRITES=%SKIP_WRITES%"
if not "%BYPASS_CACHE%"=="" set "COMMON=%COMMON% -e BYPASS_CACHE=%BYPASS_CACHE%"

echo.
echo ============================================================
echo   Mode      : %MODE_ARG%
echo   Target    : %TARGET_URL%
echo   Tai khoan : %TEST_USERNAME%
echo   Ket qua   : %SUMMARY%
echo ============================================================
echo.

if /i "%MODE_ARG%"=="smoke" goto :smoke
if /i "%MODE_ARG%"=="capacity" goto :capacity
if /i "%MODE_ARG%"=="browse" goto :browse
if /i "%MODE_ARG%"=="login" goto :login

echo [LOI] Mode khong hop le: %MODE_ARG%
echo       Chon: smoke ^| capacity ^| browse ^| login
exit /b 1

REM ---------------------------------------------------------------------
:smoke
echo Smoke test - kiem tra dang nhap va endpoint truoc khi chay bai dai.
echo Neu buoc nay hong thi KHONG chay tiep, sua loi truoc da.
echo.
REM Dung -e MODE=smoke, KHONG dung "--vus 1 --iterations 1": co CLI do ghi de
REM toan bo khoi `scenarios` roi roi ve executor mac dinh, ma executor mac dinh
REM goi ham `export default` - script khong co -> loi "function 'default' not
REM found in exports".
k6 run %COMMON% -e MODE=smoke loadtest_api_suite.js
goto :done

REM ---------------------------------------------------------------------
:capacity
echo TIM TRAN - ep du RPS bat ke server co kip tra loi hay khong.
echo Thoi luong ~17 phut. Ctrl-C de dung som.
echo.
echo Doc ket qua:
echo   dropped_iterations ^> 0  -^> MAY BAN khong phat du tai (khong phai server yeu)
echo   loi + p95 vot len       -^> do la tran that
echo   toi %RPS_PEAK% RPS van ~0%% loi -^> chua cham tran, tang RPS_PEAK
echo.
k6 run %COMMON% -e MODE=capacity -e RPS_PEAK=%RPS_PEAK% --summary-export "%SUMMARY%" loadtest_api_suite.js
goto :done

REM ---------------------------------------------------------------------
:browse
echo MO PHONG NGUOI DUNG - ramping-vus, co think-time.
echo Luu y: che do nay KHONG tim duoc tran (server cham lai thi tai tu co lai).
echo Muon biet gioi han thi dung: run_loadtest.bat capacity
echo.
k6 run %COMMON% -e MODE=browse -e VU_PEAK=%VU_PEAK% --summary-export "%SUMMARY%" loadtest_api_suite.js
goto :done

REM ---------------------------------------------------------------------
:login
echo Chi do rieng luong auth: login -^> me -^> refresh -^> logout.
echo.
k6 run %COMMON% --summary-export "%SUMMARY%" loadtest_login_flow.js
goto :done

REM ---------------------------------------------------------------------
:no_cred
echo.
echo [LOI] Thieu tai khoan hoac mat khau - khong chay.
echo       Dat bien moi truong truoc khi chay, vi du:
echo           set TEST_USERNAME=loadtest_user
echo           set TEST_PASSWORD=matkhau
exit /b 1

REM ---------------------------------------------------------------------
:done
set "EXITCODE=%ERRORLEVEL%"
echo.
if exist "%SUMMARY%" echo Summary da luu: %SUMMARY%

REM k6 tra ve 99 khi threshold bi vuot. O mode capacity thi dieu do la BINH
REM THUONG va la muc dich cua bai test (ta co y day qua diem gay), nen khong
REM coi la that bai cua script.
if "%EXITCODE%"=="99" (
    echo.
    echo Ghi chu: k6 thoat voi ma 99 = co threshold bi vuot.
    if /i "%MODE_ARG%"=="capacity" (
        echo Voi mode capacity day la ket qua MONG DOI - da day qua diem gay.
    ) else (
        echo Xem dong threshold mau do o tren de biet nguong nao khong dat.
    )
)

echo.
echo Nho don du lieu test sau khi chay - xem LOADTEST.md muc "Don du lieu".
endlocal & exit /b %EXITCODE%
