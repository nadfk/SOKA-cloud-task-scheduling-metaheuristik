#!/usr/bin/env python3
"""
Analisis Hasil Benchmark Task Scheduling Cloud: BFO vs VNS
Membaca file CSV hasil run, menghitung 5 metrik utama:
1. Makespan (s)
2. Waiting Time (s)
3. Execution Time (s)
4. Throughput (task/s)
5. Degree of Imbalance (DI)

Menghasilkan:
- results/summary_metrics.csv
- results/chart_makespan.png
- results/chart_waiting_time.png
- results/chart_exec_time.png
- results/chart_throughput.png
- results/chart_imbalance.png
- results/chart_distribution.png
- results/dashboard.html
"""
import sys, glob, os, csv, json, re
from collections import defaultdict

src = sys.argv[1] if len(sys.argv) > 1 else "results/runs"
dst = sys.argv[2] if len(sys.argv) > 2 else "results"
os.makedirs(dst, exist_ok=True)

csv_files = sorted(glob.glob(os.path.join(src, "*.csv")))
if not csv_files:
    print(f"[WARN] Tidak ada file CSV ditemukan di {src}")
    sys.exit(0)

algo_runs = defaultdict(list)
algo_tasks = defaultdict(list)
algo_workers = defaultdict(lambda: defaultdict(int))

for f in csv_files:
    lines = open(f, encoding="utf-8").read().strip().splitlines()
    summ_lines = [l for l in lines if l.startswith("#SUMMARY")]
    if not summ_lines:
        continue
    summ = summ_lines[0].split(",")
    algo = summ[1]
    num_tasks = int(summ[2])
    makespan = float(summ[3])

    # Parse rows
    reader = csv.DictReader([l for l in lines if not l.startswith("#")])
    completed_tasks = []
    worker_times = defaultdict(float)

    for r in reader:
        if r.get("status") == "completed":
            completed_tasks.append(r)
            exec_s = float(r.get("exec_seconds", 0))
            w_id = r.get("worker", "unknown")
            worker_times[w_id] += exec_s
            algo_workers[algo][w_id] += 1

    if not completed_tasks:
        continue

    # Metrik 1: Makespan
    ms = makespan

    # Metrik 2: Waiting Time
    wait_times = [float(r.get("waiting_time", 0)) for r in completed_tasks]
    avg_wait = sum(wait_times) / len(wait_times)

    # Metrik 3: Execution Time
    exec_times = [float(r.get("exec_seconds", 0)) for r in completed_tasks]
    avg_exec = sum(exec_times) / len(exec_times)

    # Metrik 4: Throughput
    tp = len(completed_tasks) / ms if ms > 0 else 0.0

    # Metrik 5: Degree of Imbalance = (T_max - T_min) / T_avg
    if worker_times:
        t_max = max(worker_times.values())
        t_min = min(worker_times.values())
        t_avg = sum(worker_times.values()) / len(worker_times)
        di = (t_max - t_min) / t_avg if t_avg > 0 else 0.0
    else:
        di = 0.0

    algo_runs[algo].append({
        "makespan_s": ms,
        "waiting_time_s": avg_wait,
        "exec_time_s": avg_exec,
        "throughput": tp,
        "degree_of_imbalance": di,
        "completed": len(completed_tasks),
        "tasks": num_tasks
    })
    algo_tasks[algo].append([float(r.get("e2e_seconds", 0)) for r in completed_tasks])

# Hitung Rata-rata per Algoritma
summary_rows = []
algo_averages = {}

for algo, runs in algo_runs.items():
    n_runs = len(runs)
    avg_entry = {
        "algorithm": algo,
        "runs": n_runs,
        "tasks": runs[0]["tasks"],
        "makespan_s": round(sum(r["makespan_s"] for r in runs) / n_runs, 4),
        "waiting_time_s": round(sum(r["waiting_time_s"] for r in runs) / n_runs, 4),
        "exec_time_s": round(sum(r["exec_time_s"] for r in runs) / n_runs, 4),
        "throughput": round(sum(r["throughput"] for r in runs) / n_runs, 4),
        "degree_of_imbalance": round(sum(r["degree_of_imbalance"] for r in runs) / n_runs, 4),
        "completed": round(sum(r["completed"] for r in runs) / n_runs, 1)
    }
    summary_rows.append(avg_entry)
    algo_averages[algo] = avg_entry

# Simpan summary_metrics.csv
summary_csv_path = os.path.join(dst, "summary_metrics.csv")
with open(summary_csv_path, "w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=[
        "algorithm", "runs", "tasks", "makespan_s", "waiting_time_s", "exec_time_s", "throughput", "degree_of_imbalance", "completed"
    ])
    writer.writeheader()
    writer.writerows(summary_rows)

print("\n=========================================================================================")
print("                   RINGKASAN HASIL EKSPERIMEN: 5 METRIK EVALUASI                         ")
print("=========================================================================================")
print(f"{'Algoritma':<12} | {'Makespan (s)':<13} | {'Avg Wait (s)':<13} | {'Avg Exec (s)':<13} | {'Throughput':<12} | {'Degree of Imbalance':<18}")
print("-" * 89)
for r in summary_rows:
    print(f"{r['algorithm'].upper():<12} | {r['makespan_s']:<13.4f} | {r['waiting_time_s']:<13.4f} | {r['exec_time_s']:<13.4f} | {r['throughput']:<12.4f} | {r['degree_of_imbalance']:<18.4f}")
print("=========================================================================================\n")

# ---- Pembuatan Grafik PNG menggunakan Pillow (Bawaan Python Environment) ----
try:
    from PIL import Image, ImageDraw, ImageFont

    def draw_bar_chart(title, subtitle, metric_key, fname, unit, lower_is_better=True):
        img_w, img_h = 700, 440
        img = Image.new("RGB", (img_w, img_h), color="#ffffff")
        draw = ImageDraw.Draw(img)

        # Header background
        draw.rectangle([0, 0, img_w, 65], fill="#1e293b")
        draw.text((25, 14), title, fill="#ffffff")
        draw.text((25, 38), f"{subtitle} ({'Lebih kecil lebih baik' if lower_is_better else 'Lebih besar lebih baik'})", fill="#94a3b8")

        # Layout chart
        chart_top = 100
        chart_bottom = 360
        chart_left = 90
        chart_right = 640
        chart_h = chart_bottom - chart_top

        # Sumbu Y & Gridlines
        algos = list(algo_averages.keys())
        values = [algo_averages[a][metric_key] for a in algos]
        max_val = max(values) if values else 1.0
        if max_val == 0: max_val = 1.0
        y_max = max_val * 1.25

        for i in range(5):
            y_ratio = i / 4.0
            y_pos = chart_bottom - y_ratio * chart_h
            val_label = f"{(y_ratio * y_max):.3f}"
            draw.line([(chart_left, y_pos), (chart_right, y_pos)], fill="#f1f5f9", width=1)
            draw.text((chart_left - 65, y_pos - 8), val_label, fill="#64748b")

        # Garis Sumbu
        draw.line([(chart_left, chart_bottom), (chart_right, chart_bottom)], fill="#334155", width=2)
        draw.line([(chart_left, chart_top), (chart_left, chart_bottom)], fill="#334155", width=2)

        # Batang Bar
        n_bars = len(algos)
        slot_w = (chart_right - chart_left) / n_bars
        bar_w = min(100, slot_w * 0.5)

        colors = {
            "bfo": ("#10b981", "#059669"),
            "vns": ("#3b82f6", "#2563eb"),
            "roundrobin": ("#a855f7", "#7e22ce")
        }

        for idx, a in enumerate(algos):
            val = algo_averages[a][metric_key]
            bar_height = (val / y_max) * chart_h
            cx = chart_left + (idx + 0.5) * slot_w
            bx1 = cx - bar_w / 2
            bx2 = cx + bar_w / 2
            by1 = chart_bottom - bar_height
            by2 = chart_bottom

            c_fill, c_outline = colors.get(a.lower(), ("#0ea5e9", "#0284c7"))
            draw.rectangle([bx1, by1, bx2, by2], fill=c_fill, outline=c_outline, width=2)

            # Label nilai di atas bar
            val_str = f"{val:.4f} {unit}"
            draw.text((bx1, by1 - 22), val_str, fill="#0f172a")

            # Label nama algoritma di bawah sumbu
            draw.text((cx - 16, chart_bottom + 12), a.upper(), fill="#1e293b")

        img.save(os.path.join(dst, fname))

    draw_bar_chart("1. Evaluasi Makespan", "Total waktu eksekusi batch", "makespan_s", "chart_makespan.png", "s", True)
    draw_bar_chart("2. Average Waiting Time", "Rata-rata waktu tunggu antrian", "waiting_time_s", "chart_waiting_time.png", "s", True)
    draw_bar_chart("3. Average Execution Time", "Rata-rata waktu komputasi task", "exec_time_s", "chart_exec_time.png", "s", True)
    draw_bar_chart("4. Throughput", "Jumlah task selesai per detik", "throughput", "chart_throughput.png", "task/s", False)
    draw_bar_chart("5. Degree of Imbalance (DI)", "Keseimbangan pembagian beban worker", "degree_of_imbalance", "chart_imbalance.png", "DI", True)

    # Grafik Distribusi Worker
    dist_img = Image.new("RGB", (700, 400), color="#ffffff")
    d_draw = ImageDraw.Draw(dist_img)
    d_draw.rectangle([0, 0, 700, 65], fill="#1e293b")
    d_draw.text((25, 14), "Distribusi Alokasi Task per Worker", fill="#ffffff")
    d_draw.text((25, 38), "Jumlah total task yang dieksekusi oleh masing-masing worker", fill="#94a3b8")

    all_workers = sorted(list({w for d in algo_workers.values() for w in d.keys()}))
    w_colors = ["#38bdf8", "#818cf8", "#c084fc", "#f472b6"]

    d_chart_bottom = 330
    d_chart_top = 100
    d_chart_left = 80
    d_chart_right = 650
    d_chart_h = d_chart_bottom - d_chart_top

    algos = list(algo_workers.keys())
    slot_w = (d_chart_right - d_chart_left) / len(algos)
    bar_w = 70

    for idx, a in enumerate(algos):
        cx = d_chart_left + (idx + 0.5) * slot_w
        total_tasks = sum(algo_workers[a].values())
        cur_y = d_chart_bottom

        for w_idx, w in enumerate(all_workers):
            cnt = algo_workers[a].get(w, 0)
            if total_tasks > 0 and cnt > 0:
                seg_h = (cnt / total_tasks) * d_chart_h
                d_draw.rectangle([cx - bar_w/2, cur_y - seg_h, cx + bar_w/2, cur_y], fill=w_colors[w_idx % len(w_colors)], outline="#ffffff")
                d_draw.text((cx - 10, cur_y - seg_h / 2 - 6), f"{w.split('-')[-1]}:{cnt}", fill="#1e293b")
                cur_y -= seg_h

        d_draw.text((cx - 16, d_chart_bottom + 12), a.upper(), fill="#1e293b")

    # Legend
    leg_x = 150
    for w_idx, w in enumerate(all_workers):
        d_draw.rectangle([leg_x, 365, leg_x + 14, 379], fill=w_colors[w_idx % len(w_colors)])
        d_draw.text((leg_x + 20, 365), w, fill="#334155")
        leg_x += 120

    dist_img.save(os.path.join(dst, "chart_distribution.png"))
    print(f"[OK] 6 Grafik PNG visualisasi berhasil dibuat di folder: {dst}")

except Exception as e:
    print(f"[WARN] Gagal membuat grafik PNG via Pillow: {e}")

# ---- Update Dashboard HTML ----
tpl_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboard_template.html")
if os.path.exists(tpl_path):
    data = {
        "meta": {"tasks": summary_rows[0]["tasks"] if summary_rows else 10, "runs": len(csv_files) // max(1, len(algo_runs)), "taskLabel": "rata-rata semua run"},
        "metrics": summary_rows,
        "runs": {a: [round(r["makespan_s"], 4) for r in runs] for a, runs in algo_runs.items()},
        "tasks": {a: [round(sum(col)/len(col), 4) for col in zip(*algo_tasks[a])] for a in algo_tasks if algo_tasks[a]},
        "dist": {a: dict(algo_workers[a]) for a in algo_workers}
    }
    html = open(tpl_path, encoding="utf-8").read()
    block = "/*DATA*/\nconst DATA = " + json.dumps(data) + ";\n/*END*/"
    html = re.sub(r"/\*DATA\*/.*?/\*END\*/", lambda m: block, html, flags=re.S)
    open(os.path.join(dst, "dashboard.html"), "w", encoding="utf-8").write(html)
    print(f"[OK] Dashboard HTML berhasil diperbarui di: {os.path.join(dst, 'dashboard.html')}")
    
    # Sinkronkan juga ke dashboard/index.html utama
    main_dash = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "dashboard", "index.html")
    if os.path.exists(os.path.dirname(main_dash)):
        open(main_dash, "w", encoding="utf-8").write(html)
        print(f"[OK] Dashboard utama disinkronkan di: {main_dash}")
