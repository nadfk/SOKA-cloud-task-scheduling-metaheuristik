# Script otomatis untuk menjalankan Simulasi Demo BFO / VNS
param (
    [string]$Algorithm = "bfo",
    [int]$Tasks = 10
)

$baseDir = $PSScriptRoot
Set-Location $baseDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SOKA - Metaheuristic Cloud Task Scheduling Demo        " -ForegroundColor Cyan
Write-Host "   Algoritma: BFO (Bacterial Foraging) | VNS              " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Compile Java files
Write-Host "`n[1/4] Mengompilasi kode Java..." -ForegroundColor Yellow
javac -encoding UTF-8 scheduler/BFOOptimizer.java scheduler/VNSOptimizer.java scheduler/TaskScheduler.java
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Kompilasi Java gagal!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Kompilasi berhasil." -ForegroundColor Green

# 2. Menjalankan 4 worker di background
Write-Host "`n[2/4] Menjalankan 4 Worker Python (Port 9001 - 9004)..." -ForegroundColor Yellow
$workers = @()
foreach ($i in 1..4) {
    $port = 9000 + $i
    $wId = "worker-$i"
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "python"
    $psi.Arguments = "`"$baseDir\worker\worker.py`" $wId $port"
    $psi.WorkingDirectory = $baseDir
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    $workers += $p
}

Start-Sleep -Seconds 2

try {
    # 3. Cek Health tiap worker
    Write-Host "`n[3/4] Memeriksa status kesehatan Worker..." -ForegroundColor Yellow
    foreach ($i in 1..4) {
        $port = 9000 + $i
        try {
            $resp = Invoke-RestMethod -Uri "http://127.0.0.1:$port/health" -TimeoutSec 3 -ErrorAction Stop
            Write-Host "  -> Worker $i (Port $port): ONLINE ($($resp.status))" -ForegroundColor Green
        } catch {
            Write-Host "  -> Worker $i (Port $port): GAGAL MERESPON" -ForegroundColor Red
        }
    }

    # 4. Jalankan TaskScheduler
    Write-Host "`n[4/4] Menjalankan Scheduler (Algoritma: $Algorithm, Tasks: $Tasks)..." -ForegroundColor Yellow
    Write-Host "----------------------------------------------------------"
    java -cp scheduler TaskScheduler $Algorithm $Tasks
    Write-Host "----------------------------------------------------------"

} finally {
    # Bersihkan background process worker
    Write-Host "`n[*] Menghentikan proses Worker background..." -ForegroundColor Gray
    foreach ($w in $workers) {
        if (-not $w.HasExited) {
            $w.Kill()
        }
    }
    Write-Host "[OK] Semua worker telah dihentikan dengan aman." -ForegroundColor Green
}

Write-Host "`nSelesai! Anda juga dapat mengganti algoritma dengan parameter:" -ForegroundColor Cyan
Write-Host ".\run-demo.ps1 -Algorithm bfo" -ForegroundColor White
Write-Host ".\run-demo.ps1 -Algorithm vns" -ForegroundColor White
