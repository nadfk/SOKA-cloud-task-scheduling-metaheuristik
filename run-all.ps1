# Script otomatis untuk menjalankan SEMUA eksperimen (BFO vs VNS 3 run) dan analisis otomatis
$baseDir = $PSScriptRoot
Set-Location $baseDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   SOKA - Full Benchmark Suite: BFO vs VNS (3 Runs)       " -ForegroundColor Cyan
Write-Host "   Evaluasi 5 Metrik: Makespan, Wait, Exec, TP, Imbalance" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Compile Java
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
    # Bersihkan file CSV lama di folder runs jika ada
    if (Test-Path "$baseDir\results\runs") {
        Remove-Item "$baseDir\results\runs\*.csv" -Force -ErrorAction SilentlyContinue
    } else {
        New-Item -ItemType Directory -Path "$baseDir\results\runs" -Force | Out-Null
    }

    # 3. Jalankan pengujian 3 run x 2 algoritma (BFO & VNS)
    Write-Host "`n[3/4] Memulai benchmark 3 Run BFO & VNS..." -ForegroundColor Yellow
    $algos = @("bfo", "vns")

    foreach ($r in 1..3) {
        Write-Host "`n>>> RUN $r dari 3 <<<" -ForegroundColor Magenta
        foreach ($al in $algos) {
            $csvFile = "results/runs/${al}_run$r.csv"
            Write-Host "`nMenjalankan $al (Run $r) -> $csvFile" -ForegroundColor Cyan
            java -cp scheduler TaskScheduler $al 10 $csvFile
        }
    }
    Write-Host "`n[OK] Seluruh benchmark selesai dijalankan!" -ForegroundColor Green
    Write-Host "File CSV tersimpan di folder results/runs/" -ForegroundColor Green

    # 4. Jalankan script analisis
    Write-Host "`n[4/4] Menganalisis hasil dan menghasilkan grafik..." -ForegroundColor Yellow
    python "$baseDir\scheduler\analyze.py" "$baseDir\results\runs" "$baseDir\results"

} finally {
    Write-Host "`n[*] Menghentikan worker background..." -ForegroundColor Gray
    foreach ($w in $workers) {
        if (-not $w.HasExited) {
            $w.Kill()
        }
    }
    Write-Host "[OK] Semua worker telah dihentikan." -ForegroundColor Green
}
