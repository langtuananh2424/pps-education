@echo off
setlocal EnableExtensions
chcp 65001 >nul
title Tai backup DB + media PPS tu server

rem ============================================================
rem  Chay tren LAPTOP Windows (khong phai server). Dat file nay vao
rem  thu muc chua backup (VD D:\pps-db-backups\) roi bam dup.
rem
rem  1. DB: tai cac ban backup DB DA MA HOA (.gpg) tu server ve thu
rem     muc chua file nay.
rem  2. Media (anh/audio MinIO production): tai /mnt/pps-backup/media/current
rem     ve thu muc "media" canh file nay, MA HOA ngay tren laptop bang
rem     rclone crypt (ten file + noi dung) - hoi mat khau moi lan chay
rem     (Google Password Manager, muc pps-media-backup-crypt). De trong
rem     = bo qua media.
rem
rem  Chi chay duoc khi laptop o mang noi bo trung tam (192.168.100.x).
rem  Chi tai file moi, khong xoa ban cu. Cau hinh 1 lan: SSH key
rem  %USERPROFILE%\.ssh\pps_backup_pull (xem deploy/README.md muc 11, 11b).
rem  Ban goc trong repo: deploy/laptop/tai-backup.cmd.
rem ============================================================

set "SERVER=192.168.100.90"
set "KEY=%USERPROFILE%\.ssh\pps_backup_pull"
set "KNOWN=%USERPROFILE%\.ssh\known_hosts"
rem Khai bao ket noi SFTP ngay trong lenh - KHONG phu thuoc file rclone.conf.
set "SFTP=:sftp,host=%SERVER%,user=pps-backup-pull,shell_type=none,key_file='%KEY%',known_hosts_file='%KNOWN%'"
set "REMOTE=%SFTP%:/opt/pps-education/backups/encrypted"
set "MEDIA_SRC=%SFTP%:/mnt/pps-backup/media/current"
set "DEST=%~dp0"
if "%DEST:~-1%"=="\" set "DEST=%DEST:~0,-1%"
set "MEDIA_DEST=%DEST%\media"
set "CRYPT=:crypt,remote='%MEDIA_DEST%':"
set "CHECK_FILE=.pps-crypt-check"
set "CHECK_TEXT=pps-media-backup-ok"
set "MEDIA_RC=0"

rem --- Tim rclone.exe: uu tien PATH, sau do thu muc cai cua winget ---
set "RCLONE="
for /f "delims=" %%R in ('where rclone 2^>nul') do if not defined RCLONE set "RCLONE=%%R"
if not defined RCLONE (
  for /d %%P in ("%LOCALAPPDATA%\Microsoft\WinGet\Packages\Rclone.Rclone_*") do (
    for /d %%V in ("%%P\rclone-*") do if exist "%%V\rclone.exe" set "RCLONE=%%V\rclone.exe"
  )
)
if not defined RCLONE (
  echo [LOI] Khong tim thay rclone. Cai bang lenh: winget install Rclone.Rclone
  goto :end_fail
)

rem --- SSH key rieng cho user chi-doc pps-backup-pull ---
if not exist "%KEY%" (
  echo [LOI] Chua co SSH key "%KEY%".
  echo       Tao bang lenh ^(PowerShell^):
  echo         ssh-keygen -t ed25519 -f $env:USERPROFILE\.ssh\pps_backup_pull -N '""' -C "pps-backup-pull@laptop"
  echo       roi dan noi dung file .pub vao server: /home/pps-backup-pull/.ssh/authorized_keys
  goto :end_fail
)
findstr /c:"%SERVER% " "%KNOWN%" >nul 2>&1 || (
  echo [LOI] Chua tin cay server %SERVER% trong "%KNOWN%".
  echo       Chay 1 lan roi go yes:  ssh -i "%KEY%" pps-backup-pull@%SERVER%
  goto :end_fail
)

rem --- Kiem tra dang o mang trung tam (server tra loi cong 22) ---
powershell -NoProfile -Command "exit [int](-not (Test-NetConnection %SERVER% -Port 22 -InformationLevel Quiet -WarningAction SilentlyContinue))"
if errorlevel 1 (
  echo [LOI] Khong ket noi duoc %SERVER%:22.
  echo       Laptop co dang o mang noi bo trung tam ^(IP 192.168.100.x^) khong?
  goto :end_fail
)

echo === 1/2 Backup DB (da ma hoa GPG) ===
echo Dang tai tu %SERVER% ve "%DEST%" ...
echo.
"%RCLONE%" copy "%REMOTE%" "%DEST%" --contimeout 15s --log-level ERROR --progress --stats-one-line
if errorlevel 1 (
  echo.
  echo [LOI] rclone bao loi - xem thong bao phia tren.
  goto :end_fail
)

echo.
echo Xong. Ban moi nhat cua tung he thong:
for %%S in (production staging ppsvn) do (
  set "NEWEST="
  for /f "delims=" %%F in ('dir /b /o-d "%DEST%\%%S\daily\*.gpg" 2^>nul') do if not defined NEWEST (
    set "NEWEST=%%F"
    echo   %%S: %%F
  )
)
echo.
echo Luu y: day la ban MA HOA - can passphrase trong Google Password Manager
echo        ^(muc pps-db-backup^) de giai ma. Xem deploy/README.md muc 11.

rem ============================================================
rem  2/2 Media - rclone crypt tren laptop
rem ============================================================
echo.
echo === 2/2 Backup media production (anh/audio) - ma hoa tren laptop ===
set "FIRST_RUN=1"
if exist "%MEDIA_DEST%\" (
  for /f %%X in ('dir /b /a "%MEDIA_DEST%" 2^>nul') do set "FIRST_RUN=0"
)
if "%FIRST_RUN%"=="1" (
  echo Lan dau: thu muc "%MEDIA_DEST%" chua co du lieu - se TAO khoa ma hoa moi.
  echo Dung mat khau DAI, luu vao Google Password Manager muc pps-media-backup-crypt
  echo TRUOC khi tiep tuc. Mat mat khau = KHONG doc lai duoc ban media tren laptop.
)

rem Doc mat khau an ^(khong hien tren man hinh^), doi sang dang "obscure" cua
rem rclone ngay trong PowerShell - mat khau goc khong nam trong bien moi truong
rem hay file nao. Lan dau phai go 2 lan cho khop.
set "RCLONE_CRYPT_PASSWORD="
for /f "usebackq delims=" %%O in (`powershell -NoProfile -Command "function Read-Pw($m){$p=Read-Host $m -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($p))}; $a=Read-Pw 'Mat khau ma hoa media (de trong = bo qua)'; if(-not $a){exit 0}; if('%FIRST_RUN%' -eq '1'){ if($a.Length -lt 12){[Console]::Error.WriteLine('Mat khau qua ngan (can >= 12 ky tu).'); exit 1}; $b=Read-Pw 'Go lai mat khau'; if($a -cne $b){[Console]::Error.WriteLine('Hai lan go khong khop.'); exit 1} }; $a | & $env:RCLONE obscure -"`) do set "RCLONE_CRYPT_PASSWORD=%%O"
if not defined RCLONE_CRYPT_PASSWORD (
  echo Bo qua media ^(khong nhap mat khau hoac mat khau khong hop le^).
  set "MEDIA_RC=2"
  goto :summary
)

if "%FIRST_RUN%"=="1" goto :media_init

rem Kiem tra mat khau bang file canary: sai mat khau thi rclone khong giai ma
rem duoc ten file -> dung lai, tranh tron du lieu ma hoa bang 2 khoa khac nhau.
"%RCLONE%" cat "%CRYPT%%CHECK_FILE%" --log-level ERROR 2>nul | findstr /x /c:"%CHECK_TEXT%" >nul
if errorlevel 1 (
  echo [LOI] Sai mat khau ma hoa media ^(khong giai ma duoc file kiem tra^) - KHONG tai.
  set "MEDIA_RC=1"
  goto :summary
)
goto :media_copy

:media_init
mkdir "%MEDIA_DEST%" 2>nul
echo %CHECK_TEXT%| "%RCLONE%" rcat "%CRYPT%%CHECK_FILE%" --log-level ERROR
if errorlevel 1 (
  echo [LOI] Khong tao duoc file kiem tra trong "%MEDIA_DEST%".
  set "MEDIA_RC=1"
  goto :summary
)

:media_copy
echo Dang tai media tu %SERVER% ^(ma hoa vao "%MEDIA_DEST%"^) ...
"%RCLONE%" copy "%MEDIA_SRC%" "%CRYPT%" --contimeout 15s --log-level ERROR --progress --stats-one-line
if errorlevel 1 (
  echo.
  echo [LOI] rclone bao loi khi tai media. Neu la "permission denied": server chua
  echo       chay backup-media.sh ban moi ^(cap quyen doc current/ cho group pps-backup^).
  set "MEDIA_RC=1"
  goto :summary
)
echo.
echo Xong media. So file ^(da ma hoa^) tren laptop:
"%RCLONE%" size "%CRYPT%" --exclude "/%CHECK_FILE%" --log-level ERROR

:summary
set "RCLONE_CRYPT_PASSWORD="
echo.
if "%MEDIA_RC%"=="0" echo Ket qua: DB OK, media OK.
if "%MEDIA_RC%"=="1" echo Ket qua: DB OK, media LOI - xem thong bao phia tren.
if "%MEDIA_RC%"=="2" echo Ket qua: DB OK, media BO QUA.
echo Xem/khoi phuc media: deploy/README.md muc 11b ^(giai ma bang rclone crypt^).
set "RC=%MEDIA_RC%"
if "%RC%"=="2" set "RC=0"
goto :end

:end_fail
set "RC=1"

:end
set "RCLONE_CRYPT_PASSWORD="
echo.
rem Bam dup chay file -> giu cua so de doc ket qua; chay tu cmd co san thi khong dung.
echo %cmdcmdline% | find /i "%~0" >nul && pause
exit /b %RC%
