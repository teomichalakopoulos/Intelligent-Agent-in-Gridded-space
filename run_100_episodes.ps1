# run_100_episodes.ps1
# Script to run the agent 100 times and calculate the average reward

$ErrorActionPreference = "Continue"

# Change to the project directory
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location $projectDir

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Agent Benchmark - 100 Episodes"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$totalEpisodes = 100
$successCount = 0

# Clear the rewards file at the start (new file: episode_rewards.txt with append)
$rewardsFile = Join-Path $projectDir "episode_rewards.txt"
if (Test-Path $rewardsFile) {
    Remove-Item $rewardsFile -Force
}

# Build once before running
Write-Host "Building project..." -ForegroundColor Yellow
gradle classes -q 2>$null
Write-Host "Build complete." -ForegroundColor Green
Write-Host ""

for ($i = 1; $i -le $totalEpisodes; $i++) {
    Write-Host -NoNewline "Running episode $i of $totalEpisodes... "
    
    # Run the MAS with auto-exit enabled (environment appends reward to episode_rewards.txt)
    gradle runBenchmark -q 2>&1 | Out-Null
    
    # Check if the reward was recorded
    if (Test-Path $rewardsFile) {
        $lineCount = (Get-Content $rewardsFile | Measure-Object -Line).Lines
        if ($lineCount -ge $i) {
            $lastReward = (Get-Content $rewardsFile -Tail 1).Trim()
            Write-Host "Reward: $lastReward" -ForegroundColor Green
            $successCount++
        } else {
            Write-Host "Failed to record reward" -ForegroundColor Red
        }
    } else {
        Write-Host "Rewards file not found" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESULTS"
Write-Host "========================================" -ForegroundColor Cyan

# Read all rewards from the file
if (Test-Path $rewardsFile) {
    $rewards = Get-Content $rewardsFile | Where-Object { $_ -match '\S' } | ForEach-Object { [double]$_.Trim() }
    
    if ($rewards.Count -gt 0) {
        $avgReward = ($rewards | Measure-Object -Average).Average
        $minReward = ($rewards | Measure-Object -Minimum).Minimum
        $maxReward = ($rewards | Measure-Object -Maximum).Maximum
        $stdDev = 0
        if ($rewards.Count -gt 1) {
            $sumSquares = ($rewards | ForEach-Object { ($_ - $avgReward) * ($_ - $avgReward) } | Measure-Object -Sum).Sum
            $stdDev = [Math]::Sqrt($sumSquares / ($rewards.Count - 1))
        }
        
        Write-Host ""
        Write-Host "Episodes completed: $($rewards.Count) / $totalEpisodes" -ForegroundColor White
        Write-Host ""
        Write-Host "Average Reward:  $([Math]::Round($avgReward, 4))" -ForegroundColor Green
        Write-Host "Min Reward:      $([Math]::Round($minReward, 4))" -ForegroundColor Yellow
        Write-Host "Max Reward:      $([Math]::Round($maxReward, 4))" -ForegroundColor Yellow
        Write-Host "Std Deviation:   $([Math]::Round($stdDev, 4))" -ForegroundColor Yellow
        Write-Host ""
        
        # Save summary to results file
        $resultsFile = "benchmark_results.txt"
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        @"
Benchmark Results - $timestamp
======================================
Episodes completed: $($rewards.Count) / $totalEpisodes
Average Reward:  $([Math]::Round($avgReward, 4))
Min Reward:      $([Math]::Round($minReward, 4))
Max Reward:      $([Math]::Round($maxReward, 4))
Std Deviation:   $([Math]::Round($stdDev, 4))

Individual rewards saved in: episode_rewards.txt
"@ | Out-File -FilePath $resultsFile -Encoding UTF8
        
        Write-Host "Results saved to: $resultsFile" -ForegroundColor Cyan
        Write-Host "All rewards in: episode_rewards.txt" -ForegroundColor Cyan
    } else {
        Write-Host "No rewards recorded in file!" -ForegroundColor Red
    }
} else {
    Write-Host "No rewards file found!" -ForegroundColor Red
}

Write-Host ""
Write-Host "Benchmark completed!" -ForegroundColor Cyan
