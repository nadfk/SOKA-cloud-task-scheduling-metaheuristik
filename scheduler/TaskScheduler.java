import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

/**
 * Task Scheduler Metaheuristik untuk Cloud Computing.
 * Algoritma: BFO (Bacterial Foraging Optimization) & VNS (Variable Neighborhood Search)
 *
 * Dataset beban task: Heterogen kelipatan 100 (100, 200, 300, ..., 1000).
 * Skala komputasi: workload * 1000 iterasi hash SHA-256 (presisi komputasi nyata).
 *
 * Evaluasi 5 Metrik Utama:
 * 1. Makespan (detik)
 * 2. Waiting Time (detik)
 * 3. Execution Time (detik)
 * 4. Throughput (task/detik)
 * 5. Degree of Imbalance (DI)
 *
 * Mode Eksekusi:
 * - HTTP Worker Cluster: Jika worker python aktif di port 9001-9004.
 * - Virtual Worker Cluster: Eksekutor multi-threading in-process dengan antrian per-worker
 *   (memastikan simulasi waiting time dan makespan tetap berjalan presisi dalam lingkungan apapun).
 */
public class TaskScheduler {

    static class WorkerSpec {
        final String id;
        final String baseUrl;
        final double costPerM;
        final AtomicInteger inflight = new AtomicInteger();

        WorkerSpec(String id, String baseUrl, double costPerM) {
            this.id = id;
            this.baseUrl = baseUrl;
            this.costPerM = costPerM;
        }

        double cost(int iterations) {
            return iterations / 1_000_000.0 * costPerM;
        }
    }

    static final List<WorkerSpec> WORKER_SPECS = List.of(
            new WorkerSpec("worker-1", "http://127.0.0.1:9001", 0.10),
            new WorkerSpec("worker-2", "http://127.0.0.1:9002", 0.08),
            new WorkerSpec("worker-3", "http://127.0.0.1:9003", 0.12),
            new WorkerSpec("worker-4", "http://127.0.0.1:9004", 0.09)
    );

    private static HttpClient httpClientInstance = null;
    private static boolean httpClientInitialized = false;

    private static synchronized HttpClient getHttpClient() {
        if (!httpClientInitialized) {
            httpClientInitialized = true;
            try {
                httpClientInstance = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2)).build();
            } catch (Throwable t) {
                httpClientInstance = null;
            }
        }
        return httpClientInstance;
    }

    static boolean checkHttpHealth(WorkerSpec w) {
        HttpClient client = getHttpClient();
        if (client == null) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(w.baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        String algo = args.length > 0 ? args[0].toLowerCase() : "bfo";
        int numTasks = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        String csvPath = args.length > 2 ? args[2] : null;

        System.out.println("==========================================================================");
        System.out.println("     CLOUD TASK SCHEDULING SIMULATION: BFO vs VNS METAHEURISTICS          ");
        System.out.println("==========================================================================");
        System.out.printf(Locale.US, "Algoritma Pembanding : %s%n", algo.toUpperCase());
        System.out.printf(Locale.US, "Jumlah Task          : %d task%n", numTasks);

        // 1. Cek ketersediaan Worker (HTTP vs Virtual Cluster)
        List<WorkerSpec> onlineHttpWorkers = new ArrayList<>();
        for (WorkerSpec w : WORKER_SPECS) {
            if (checkHttpHealth(w)) onlineHttpWorkers.add(w);
        }

        boolean useHttp = !onlineHttpWorkers.isEmpty();
        List<WorkerSpec> activeWorkers = useHttp ? onlineHttpWorkers : WORKER_SPECS;
        int numWorkers = activeWorkers.size();

        if (useHttp) {
            System.out.printf(Locale.US, "Cluster Mode         : HTTP Network Worker (%d node online)%n", numWorkers);
        } else {
            System.out.printf(Locale.US, "Cluster Mode         : High-Precision Virtual Worker Cluster (%d worker queues)%n", numWorkers);
        }

        // 2. Dataset task heterogen kelipatan 100 (100, 200, 300, ..., 1000)
        int[] workloads = new int[numTasks];
        for (int i = 0; i < numTasks; i++) {
            workloads[i] = ((i % 10) + 1) * 100;
        }

        System.out.println("\n[Dataset Task] Beban Heterogen (Kelipatan 100):");
        for (int i = 0; i < numTasks; i++) {
            System.out.printf("  task-%03d : Workload = %4d units | Iterasi SHA-256 = %,d%n",
                    i + 1, workloads[i], workloads[i] * 1000);
        }

        // 3. Eksekusi Optimasi Scheduling (BFO / VNS / Baseline)
        int[] schedule = new int[numTasks];
        long optTimeMs = 0;

        long seed = System.nanoTime();
        if (algo.equals("bfo")) {
            BFOOptimizer bfo = new BFOOptimizer(12, 15, 4, 3, 2, 0.25, seed);
            BFOOptimizer.OptimizationResult res = bfo.optimize(workloads, numWorkers);
            schedule = res.schedule;
            optTimeMs = res.elapsedMs;
        } else if (algo.equals("vns")) {
            VNSOptimizer vns = new VNSOptimizer(60, 3, seed);
            VNSOptimizer.OptimizationResult res = vns.optimize(workloads, numWorkers);
            schedule = res.schedule;
            optTimeMs = res.elapsedMs;
        } else {
            System.out.println("\n[Baseline] Menggunakan Round Robin allocation...");
            for (int i = 0; i < numTasks; i++) schedule[i] = i % numWorkers;
        }

        System.out.println("\n[Hasil Penjadwalan] Pemetaan Solusi ke Worker:");
        for (int i = 0; i < numTasks; i++) {
            System.out.printf("  task-%03d (Workload: %4d) ---> %s%n",
                    i + 1, workloads[i], activeWorkers.get(schedule[i]).id);
        }

        // 4. Eksekusi Simulasi Komputasi & Pengukuran Metrik
        List<String> rows = new ArrayList<>();
        rows.add("algorithm,task_id,worker,status,workload,exec_seconds,waiting_time,e2e_seconds,start_offset,est_cost");

        Map<String, Double> workerTotalExecTimes = new TreeMap<>();
        for (WorkerSpec w : activeWorkers) workerTotalExecTimes.put(w.id, 0.0);

        List<TaskResult> taskResults = new ArrayList<>();
        long globalStart = System.nanoTime();

        if (useHttp) {
            // Eksekusi via HTTP Cluster
            ExecutorService pool = Executors.newFixedThreadPool(numTasks);
            List<Future<TaskResult>> futures = new ArrayList<>();
            HttpClient client = getHttpClient();

            for (int i = 0; i < numTasks; i++) {
                final int idx = i;
                final String tid = String.format("task-%03d", i + 1);
                final int load = workloads[idx];
                final int iters = load * 1000;
                final WorkerSpec worker = activeWorkers.get(schedule[idx]);

                futures.add(pool.submit(() -> {
                    long sendTime = System.nanoTime();
                    String body = "{\"task_id\":\"" + tid + "\",\"iterations\":" + iters + "}";
                    double execSec = 0.0;
                    String status = "failed";

                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(worker.baseUrl + "/run"))
                                .timeout(Duration.ofSeconds(300))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() == 200) {
                            status = "completed";
                            Matcher m = Pattern.compile("\"execution_seconds\":\\s*([0-9.eE-]+)").matcher(resp.body());
                            if (m.find()) execSec = Double.parseDouble(m.group(1));
                        }
                    } catch (Exception e) {
                        status = "failed";
                    }

                    double e2e = (System.nanoTime() - sendTime) / 1e9;
                    double startRel = (sendTime - globalStart) / 1e9;
                    double wait = Math.max(0.0, e2e - execSec);
                    return new TaskResult(algo, tid, worker.id, status, load, execSec, wait, e2e, startRel, worker.cost(iters));
                }));
            }

            for (Future<TaskResult> f : futures) taskResults.add(f.get());
            pool.shutdown();

        } else {
            // Eksekusi via High-Precision Virtual Worker Cluster (1 thread worker per queue)
            ExecutorService[] workerThreads = new ExecutorService[numWorkers];
            for (int w = 0; w < numWorkers; w++) {
                workerThreads[w] = Executors.newSingleThreadExecutor();
            }

            List<Future<TaskResult>> futures = new ArrayList<>();
            for (int i = 0; i < numTasks; i++) {
                final int idx = i;
                final String tid = String.format("task-%03d", i + 1);
                final int load = workloads[idx];
                final int iters = load * 1000;
                final int workerIdx = schedule[idx];
                final WorkerSpec worker = activeWorkers.get(workerIdx);
                final long taskSubmitTime = System.nanoTime();

                futures.add(workerThreads[workerIdx].submit(() -> {
                    long execStartTime = System.nanoTime();
                    double waitSec = (execStartTime - taskSubmitTime) / 1e9;

                    // Komputasi SHA-256 asli sejumlah iterasi
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    for (int it = 0; it < iters; it++) {
                        md.update((tid + ":" + it).getBytes());
                    }
                    long execEndTime = System.nanoTime();

                    double execSec = (execEndTime - execStartTime) / 1e9;
                    double e2eSec = (execEndTime - taskSubmitTime) / 1e9;
                    double startRel = (taskSubmitTime - globalStart) / 1e9;

                    return new TaskResult(algo, tid, worker.id, "completed", load, execSec, waitSec, e2eSec, startRel, worker.cost(iters));
                }));
            }

            for (Future<TaskResult> f : futures) taskResults.add(f.get());
            for (ExecutorService es : workerThreads) es.shutdown();
        }

        double makespan = (System.nanoTime() - globalStart) / 1e9;

        // 5. Hitung 5 Metrik Evaluasi
        System.out.println("\nLog Eksekusi Tiap Task:");
        System.out.println("-----------------------------------------------------------------------------------------------");
        System.out.println("Task ID   | Worker   | Workload | Exec Time (s) | Waiting Time (s) | E2E Time (s) | Status");
        System.out.println("-----------------------------------------------------------------------------------------------");

        double sumWait = 0.0;
        double sumExec = 0.0;
        int completed = 0;

        for (TaskResult tr : taskResults) {
            rows.add(tr.toCsvRow());
            if ("completed".equals(tr.status)) {
                completed++;
                sumWait += tr.waitingTime;
                sumExec += tr.execTime;
                workerTotalExecTimes.put(tr.workerId, workerTotalExecTimes.get(tr.workerId) + tr.execTime);
            }
            System.out.printf("%-9s | %-8s | %-8d | %-13.4f | %-16.4f | %-12.4f | %s%n",
                    tr.taskId, tr.workerId, tr.workload, tr.execTime, tr.waitingTime, tr.e2eTime, tr.status);
        }

        double avgWait = completed > 0 ? (sumWait / completed) : 0.0;
        double avgExec = completed > 0 ? (sumExec / completed) : 0.0;
        double throughput = makespan > 0 ? (completed / makespan) : 0.0;

        // Degree of Imbalance (DI) = (T_max - T_min) / T_avg
        double maxBusy = 0;
        double minBusy = Double.MAX_VALUE;
        double sumBusy = 0;
        for (double b : workerTotalExecTimes.values()) {
            if (b > maxBusy) maxBusy = b;
            if (b < minBusy) minBusy = b;
            sumBusy += b;
        }
        double avgBusy = numWorkers > 0 ? (sumBusy / numWorkers) : 0.0;
        double degreeOfImbalance = avgBusy > 0 ? (maxBusy - minBusy) / avgBusy : 0.0;

        System.out.println("==========================================================================");
        System.out.println("                 HASIL PERBANDINGAN EVALUASI (5 METRIK)                   ");
        System.out.println("==========================================================================");
        System.out.printf(Locale.US, "1. Makespan             : %.4f detik%n", makespan);
        System.out.printf(Locale.US, "2. Avg Waiting Time     : %.4f detik%n", avgWait);
        System.out.printf(Locale.US, "3. Avg Execution Time   : %.4f detik%n", avgExec);
        System.out.printf(Locale.US, "4. Throughput           : %.4f task/detik%n", throughput);
        System.out.printf(Locale.US, "5. Degree of Imbalance  : %.4f (makin mendekati 0 = makin seimbang)%n", degreeOfImbalance);
        System.out.println("--------------------------------------------------------------------------");
        System.out.printf(Locale.US, "Waktu Optimasi (%s)  : %d ms%n", algo.toUpperCase(), optTimeMs);
        System.out.printf(Locale.US, "Total Task Sukses       : %d / %d%n", completed, numTasks);
        System.out.println("Beban Total Tiap Worker : " + workerTotalExecTimes);
        System.out.println("==========================================================================");

        // #SUMMARY,algo,tasks,makespan,avg_wait,avg_exec,throughput,degree_of_imbalance,workers
        rows.add(String.format(Locale.US, "#SUMMARY,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d",
                algo, numTasks, makespan, avgWait, avgExec, throughput, degreeOfImbalance, numWorkers));

        if (csvPath != null) {
            Path p = Path.of(csvPath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.write(p, rows);
            System.out.println("\n[Output] File hasil benchmark tersimpan di: " + csvPath);
        }
    }

    static class TaskResult {
        final String algo, taskId, workerId, status;
        final int workload;
        final double execTime, waitingTime, e2eTime, startRel, cost;

        TaskResult(String algo, String taskId, String workerId, String status, int workload,
                   double execTime, double waitingTime, double e2eTime, double startRel, double cost) {
            this.algo = algo;
            this.taskId = taskId;
            this.workerId = workerId;
            this.status = status;
            this.workload = workload;
            this.execTime = execTime;
            this.waitingTime = waitingTime;
            this.e2eTime = e2eTime;
            this.startRel = startRel;
            this.cost = cost;
        }

        String toCsvRow() {
            return String.format(Locale.US, "%s,%s,%s,%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f",
                    algo, taskId, workerId, status, workload, execTime, waitingTime, e2eTime, startRel, cost);
        }
    }
}
