# SOKA: Cloud Task Scheduling Metaheuristics (BFO vs VNS)

Simulasi dan perbandingan performa algoritma penjadwalan komputasi awan berbasis metaheuristik:
1. **Bacterial Foraging Optimization (BFO)** — *Swarm-based intelligence*
2. **Variable Neighborhood Search (VNS)** — *Trajectory-based neighborhood search*

Dataset yang digunakan adalah beban task **heterogen kelipatan 100** (100, 200, 300, ..., 1000 unit beban) yang dialokasikan ke 4 simpul worker.

---

## 5 Metrik Evaluasi Kinerja

1. **Makespan (detik):** Total durasi waktu sejak batch dikirim hingga seluruh task selesai dieksekusi.
2. **Waiting Time (detik):** Rata-rata waktu tunggu antrian task sebelum mulai dieksekusi oleh worker.
3. **Execution Time (detik):** Rata-rata waktu komputasi nyata pada worker node.
4. **Throughput (task/detik):** Jumlah task yang berhasil diselesaikan per satuan waktu.
5. **Degree of Imbalance (DI):** Derajat variasi/ketidakseimbangan beban antar worker:
   $$DI = \frac{T_{\max} - T_{\min}}{T_{\text{avg}}}$$
   *(Makin kecil / mendekati 0 = distribusi beban makin merata).*

---

## Ringkasan Hasil Perbandingan

| Metrik Evaluasi | BFO | VNS | Analisis / Keunggulan |
|---|---|---|---|
| **Makespan (s)** | 0.2975 s | **0.2903 s** | **VNS lebih cepat** karena local search terarah langsung memangkas bottleneck worker. |
| **Throughput (task/s)** | 33.88 | **35.22** | **VNS lebih tinggi**, berbanding lurus dengan efisiensi makespan. |
| **Degree of Imbalance (DI)** | **0.1223** | 0.2600 | **BFO jauh lebih seimbang**, pemerataan beban antar worker lebih optimal. |
| **Waiting Time (s)** | **0.1151 s** | 0.1191 s | **BFO lebih rendah**, karena beban terbagi rata sehingga antrian antartask lebih pendek. |
| **Execution Time (s)** | 0.1103 s | 0.1005 s | Relatif seimbang sesuai beban total komputasi. |
| **Waktu Optimasi** | 8 - 10 ms | **1 - 2 ms** | VNS instan; BFO mengevaluasi multi-generasi bakteri. |

---

## Struktur Repositori

```text
SOKA-clod-task-scheduling-metaheuristik/
├── scheduler/
│   ├── BFOOptimizer.java       # Modul independen Bacterial Foraging Optimization
│   ├── VNSOptimizer.java       # Modul independen Variable Neighborhood Search
│   ├── TaskScheduler.java      # Dispatcher utama & pengukur 5 metrik
│   ├── analyze.py              # Script analisis CSV -> Grafik PNG & summary
│   └── dashboard_template.html # Template HTML visualisasi interaktif
├── worker/
│   └── worker.py               # Server HTTP node worker (port 9001 - 9004)
├── results/
│   ├── runs/                   # File CSV data per-run (bfo_run*.csv, vns_run*.csv)
│   ├── summary_metrics.csv     # Rangkuman 5 metrik rata-rata
│   ├── chart_makespan.png      # Grafik batang Makespan
│   ├── chart_waiting_time.png  # Grafik batang Waiting Time
│   ├── chart_exec_time.png     # Grafik batang Execution Time
│   ├── chart_throughput.png    # Grafik batang Throughput
│   ├── chart_imbalance.png     # Grafik batang Degree of Imbalance
│   └── chart_distribution.png  # Grafik distribusi task per worker
├── dashboard/
│   └── index.html              # Dashboard visualisasi interaktif
├── run-demo.ps1                # Script demo cepat 1 algoritma
├── run-demo.bat                # Shortcut batch runner
├── run-all.ps1                 # Script benchmark penuh (3 runs x 2 algoritma)
└── LAPORAN.md                  # Dokumen laporan akademik komprehensif
```

---

## Cara Menjalankan

### 1. Menjalankan Demo Cepat
Di terminal PowerShell:
```powershell
# Menjalankan simulasi BFO
.\run-demo.ps1 -Algorithm bfo -Tasks 10

# Menjalankan simulasi VNS
.\run-demo.ps1 -Algorithm vns -Tasks 10
```

### 2. Menjalankan Full Benchmark Suite (3 Runs & Visualisasi Otomatis)
```powershell
.\run-all.ps1
```

### 3. Membuka Dashboard Visualisasi
Buka berkas `dashboard/index.html` langsung di peramban web (browser) untuk melihat dashboard interaktif.
