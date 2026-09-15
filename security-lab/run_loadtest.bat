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
REM     run_loadtest.bat reflex-writing  -> UC-23b: cham AI phan "viet" (Video phan xa)
REM     run_loadtest.bat reflex-speaking -> UC-23b: cham AI phan "noi" (chay SAU reflex-writing)
REM
REM  Credential: KHONG hardcode trong file nay (repo public).
REM  Dat truoc bang bien moi truong, hoac de script tu hoi:
REM     set TEST_USERNAME=loadtest_user
REM     set TEST_PASSWORD=...
REM
REM  Bien co the doi (dat truoc khi chay):
REM     TARGET_URL   mac dinh https://admin-staging.ppsvietnam.edu.vn
REM     RPS_PEAK     mac dinh 800  (chi dung o mode capacity)
REM     VU_PEAK      mac dinh 1000 (chi dung o mode browse; reflex-* dung mac dinh rieng 50)
REM     SKIP_WRITES  dat =1 de tat hoan toan phan ghi du lieu
REM
REM  Rieng reflex-writing/reflex-speaking BAT BUOC them:
REM     REFLEX_ASSIGNMENT_ID   id lan giao Video phan xa TEST rieng
REM     REFLEX_QUESTION_IDS    danh sach id cau hoi, vd "501,502,503"
REM     REFLEX_AUDIO_URL       (chi reflex-speaking) link audio that da upload san
REM  TEST_USERNAME/TEST_PASSWORD phai la 1 tai khoan HOC SINH test rieng, KHONG
REM  dung tai khoan/bo Video phan xa dang giao that cho lop hoc sinh that -- xem
REM  ghi chu "LUU Y DU LIEU" trong loadtest_reflex_ai_grading.js.
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
REM QUAN TRONG: VU_PEAK/RPS_PEAK la BIEN MOI TRUONG THAT (set /set) - k6
REM (tien trinh con) doc duoc ngay ca khi KHONG truyen qua "-e", vi k6 tu
REM doc __ENV tu process environment ke thua, khong chi tu "-e". Truoc day
REM set mac dinh VU_PEAK=1000 O DAY (dung chung cho moi mode) lam "ri" gia
REM tri nay sang ca reflex-writing/reflex-speaking du khong -e VU_PEAK cho
REM 2 mode do - ghi de mat default rieng (50) trong loadtest_reflex_ai_
REM grading.js, khien VU dinh thanh 1000 ngoai y muon. Chi dat default o
REM DUNG label mode can no (:browse), khong dat chung o day nua.
if "%RPS_PEAK%"=="" set "RPS_PEAK=800"

REM Nhan thoi gian cho file ket qua. Dung PowerShell thay vi %DATE%/%TIME%
REM vi 2 bien do phu thuoc dinh dang vung/mien, de sinh ten file hong.
for /f "delims=" %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%t"

if not exist "results" mkdir "results"
set "SUMMARY=results\k6-%MODE_ARG%-%TS%.json"

set "COMMON=-e TARGET_URL=%TARGET_URL% -e TEST_USERNAME=%TEST_USERNAME% -e TEST_PASSWORD=%TEST_PASSWORD%"
if not "%SKIP_WRITES%"=="" set "COMMON=%COMMON% -e SKIP_WRITES=%SKIP_WRITES%"
if not "%BYPASS_CACHE%"=="" set "COMMON=%COMMON% -e BYPASS_CACHE=%BYPASS_CACHE%"
if not "%REFLEX_ASSIGNMENT_ID%"=="" set "COMMON=%COMMON% -e REFLEX_ASSIGNMENT_ID=%REFLEX_ASSIGNMENT_ID%"
if not "%REFLEX_QUESTION_IDS%"=="" set "COMMON=%COMMON% -e REFLEX_QUESTION_IDS=%REFLEX_QUESTION_IDS%"
if not "%REFLEX_AUDIO_URL%"=="" set "COMMON=%COMMON% -e REFLEX_AUDIO_URL=%REFLEX_AUDIO_URL%"

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
if /i "%MODE_ARG%"=="reflex-writing" goto :reflex_writing
if /i "%MODE_ARG%"=="reflex-speaking" goto :reflex_speaking

echo [LOI] Mode khong hop le: %MODE_ARG%
echo       Chon: smoke ^| capacity ^| browse ^| login ^| reflex-writing ^| reflex-speaking
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
if "%VU_PEAK%"=="" set "VU_PEAK=1000"
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
:reflex_writing
if "%REFLEX_ASSIGNMENT_ID%"=="" goto :no_reflex_params
if "%REFLEX_QUESTION_IDS%"=="" goto :no_reflex_params
REM VU_PEAK o day CHI duoc forward neu ban tu dat truoc (mac dinh 50 nam
REM trong loadtest_reflex_ai_grading.js, KHONG dat default 1000 o day -
REM xem ghi chu o dau file). Neu phien PowerShell nay TRUOC DO da tung
REM chay "browse" va co dat $env:VU_PEAK, gia tri cu se con "ri" sang day -
REM chay `$env:VU_PEAK = $null` de xoa neu khong chac chan.
set "REFLEX_EXTRA="
if not "%VU_PEAK%"=="" set "REFLEX_EXTRA=-e VU_PEAK=%VU_PEAK%"
echo UC-23b - cham AI phan VIET (Video phan xa). TEST_USERNAME phai la
echo tai khoan HOC SINH test rieng (khong dung tai khoan/bo video that).
echo.
k6 run %COMMON% %REFLEX_EXTRA% -e STEP=writing --summary-export "%SUMMARY%" loadtest_reflex_ai_grading.js
goto :done

REM ---------------------------------------------------------------------
:reflex_speaking
if "%REFLEX_ASSIGNMENT_ID%"=="" goto :no_reflex_params
if "%REFLEX_QUESTION_IDS%"=="" goto :no_reflex_params
if "%REFLEX_AUDIO_URL%"=="" goto :no_reflex_params
set "REFLEX_EXTRA="
if not "%VU_PEAK%"=="" set "REFLEX_EXTRA=-e VU_PEAK=%VU_PEAK%"
echo UC-23b - cham AI phan NOI (Video phan xa). Chay SAU reflex-writing -
echo combo (hoc sinh, cau hoi) chua dat writing se bi 400 (dem rieng, khong
echo tinh la loi that - xem script).
echo.
k6 run %COMMON% %REFLEX_EXTRA% -e STEP=speaking --summary-export "%SUMMARY%" loadtest_reflex_ai_grading.js
goto :done

REM ---------------------------------------------------------------------
:no_reflex_params
echo.
echo [LOI] Thieu tham so cho reflex-writing/reflex-speaking - khong chay.
echo       Dat truoc bang bien moi truong, vi du:
echo           set REFLEX_ASSIGNMENT_ID=123
echo           set REFLEX_QUESTION_IDS=501,502,503
echo           set REFLEX_AUDIO_URL=https://files-staging.ppsvietnam.edu.vn/pps-media/... (chi reflex-speaking)
exit /b 1

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
if /i "%MODE_ARG:~0,6%"=="reflex" (
    echo Luu y: reflex-writing/reflex-speaking GHI DE tien trinh that cua
    echo combo hoc sinh/cau hoi test - khong tao ban ghi rieng de don bang
    echo SQL nhu leads/device_tokens. Xem LOADTEST.md muc "Load test rieng
    echo cho cham AI Video phan xa".
) else (
    echo Nho don du lieu test sau khi chay - xem LOADTEST.md muc "Don du lieu".
)
endlocal & exit /b %EXITCODE%
