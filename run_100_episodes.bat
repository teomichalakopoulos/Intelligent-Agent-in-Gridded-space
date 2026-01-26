@echo off
REM run_100_episodes.bat - Run the agent 100 times and get the average reward
setlocal enabledelayedexpansion

echo ========================================
echo   Agent Benchmark - 100 Episodes
echo ========================================
echo.

set TOTAL_EPISODES=100
set SUCCESS_COUNT=0
set SUM=0

REM Build once
echo Building project...
gradle classes -q 2>nul
echo Build complete.
echo.

REM Create temp file for rewards
set REWARDS_FILE=%TEMP%\rewards_%RANDOM%.txt
echo. > "%REWARDS_FILE%"

for /L %%i in (1,1,%TOTAL_EPISODES%) do (
    echo Running episode %%i of %TOTAL_EPISODES%...
    
    REM Run and capture output to temp file
    set TEMP_OUT=%TEMP%\episode_%%i.txt
    gradle run -q > "!TEMP_OUT!" 2>&1
    
    REM Extract reward using findstr
    for /f "tokens=5 delims=: " %%r in ('findstr /C:"Episode completed with reward" "!TEMP_OUT!"') do (
        set REWARD=%%r
        REM Remove the comma if present
        set REWARD=!REWARD:,=!
        echo Episode %%i reward: !REWARD!
        echo !REWARD! >> "%REWARDS_FILE%"
        set /a SUCCESS_COUNT+=1
    )
    
    del "!TEMP_OUT!" 2>nul
)

echo.
echo ========================================
echo   RESULTS
echo ========================================
echo.
echo Episodes completed: %SUCCESS_COUNT% / %TOTAL_EPISODES%
echo.

REM Use PowerShell to calculate average (batch can't handle decimals well)
powershell -Command "$rewards = Get-Content '%REWARDS_FILE%' | Where-Object { $_ -match '\d' } | ForEach-Object { [double]$_ }; if ($rewards.Count -gt 0) { $avg = ($rewards | Measure-Object -Average).Average; Write-Host ('Average Reward: ' + [Math]::Round($avg, 4)) -ForegroundColor Green }"

del "%REWARDS_FILE%" 2>nul

echo.
echo Benchmark completed!
pause
