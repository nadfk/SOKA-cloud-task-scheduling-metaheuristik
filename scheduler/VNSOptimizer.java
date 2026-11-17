import java.util.*;

/**
 * Variable Neighborhood Search (VNS) untuk Cloud Task Scheduling.
 *
 * Mengoptimalkan alokasi task heterogen ke sejumlah worker
 * untuk meminimalkan Makespan dan Degree of Imbalance (DI).
 *
 * Struktur Lingkungan (Neighborhood Structures):
 * - N1 (Single Shift): Memindahkan 1 task ke worker lain.
 * - N2 (Pair Swap): Menukar worker dari 2 task yang berbeda.
 * - N3 (Two-Task Shift): Memindahkan 2 task sekaligus ke worker yang berbeda.
 *
 * Tahapan VNS:
 * 1. Shaking: Mengacak solusi saat ini menggunakan struktur lingkungan Nk.
 * 2. Local Search: Melakukan perbaikan lokal pada hasil shaking (Fokus N1).
 * 3. Neighborhood Change: Jika ada peningkatan, kembali ke N1 (k=1). Jika tidak, tingkatkan ukuran lingkungan (k = k+1).
 */
public class VNSOptimizer {

    public static class OptimizationResult {
        public final int[] schedule;
        public final double estMakespan;
        public final double estImbalance;
        public final long elapsedMs;

        public OptimizationResult(int[] schedule, double estMakespan, double estImbalance, long elapsedMs) {
            this.schedule = schedule;
            this.estMakespan = estMakespan;
            this.estImbalance = estImbalance;
            this.elapsedMs = elapsedMs;
        }
    }

    private final int maxIterations;
    private final int kMax;
    private final Random rand;

    public VNSOptimizer() {
        this(60, 3, 42);
    }

    public VNSOptimizer(int maxIterations, int kMax, long seed) {
        this.maxIterations = maxIterations;
        this.kMax = kMax;
        this.rand = new Random(seed);
    }

    /**
     * Menghitung Makespan dan Degree of Imbalance (DI) untuk sebuah solusi schedule.
     */
    public static double[] evaluateMetrics(int[] schedule, int[] workloads, int numWorkers) {
        double[] loads = new double[numWorkers];
        for (int i = 0; i < schedule.length; i++) {
            loads[schedule[i]] += workloads[i];
        }
        double maxLoad = 0;
        double minLoad = Double.MAX_VALUE;
        double sum = 0;
        for (double l : loads) {
            if (l > maxLoad) maxLoad = l;
            if (l < minLoad) minLoad = l;
            sum += l;
        }
        double avg = sum / numWorkers;
        double di = avg > 0 ? (maxLoad - minLoad) / avg : 0.0;
        return new double[]{maxLoad, di};
    }

    /**
     * Fitness function: bobot utama pada Makespan + penalti Degree of Imbalance.
     */
    public static double computeFitness(int[] schedule, int[] workloads, int numWorkers) {
        double[] m = evaluateMetrics(schedule, workloads, numWorkers);
        return m[0] + (0.15 * (m[0] * m[1]));
    }

    /**
     * Shaking step: Memilih solusi acak di lingkungan Nk.
     */
    private int[] shake(int[] current, int k, int numWorkers) {
        int[] neighbor = current.clone();
        int n = neighbor.length;

        switch (k) {
            case 1: // N1: Single Shift
                int t1 = rand.nextInt(n);
                int w1 = rand.nextInt(numWorkers);
                while (w1 == neighbor[t1] && numWorkers > 1) {
                    w1 = rand.nextInt(numWorkers);
                }
                neighbor[t1] = w1;
                break;

            case 2: // N2: Pair Swap
                int ta = rand.nextInt(n);
                int tb = rand.nextInt(n);
                int attempts = 0;
                while (neighbor[ta] == neighbor[tb] && attempts < 10) {
                    tb = rand.nextInt(n);
                    attempts++;
                }
                int tmp = neighbor[ta];
                neighbor[ta] = neighbor[tb];
                neighbor[tb] = tmp;
                break;

            case 3: // N3: Two-Task Shift
            default:
                int tA = rand.nextInt(n);
                int tB = rand.nextInt(n);
                neighbor[tA] = rand.nextInt(numWorkers);
                neighbor[tB] = rand.nextInt(numWorkers);
                break;
        }
        return neighbor;
    }

    /**
     * Local Search: Menjelajahi perbaikan lingkungan lokal (First Improvement / Best Improvement di N1).
     * Secara cerdas mencoba memindahkan task dari worker dengan beban tertinggi ke worker beban terendah.
     */
    private int[] localSearch(int[] initial, int[] workloads, int numWorkers) {
        int[] current = initial.clone();
        double currentFit = computeFitness(current, workloads, numWorkers);
        boolean improved = true;
        int maxSteps = 25;
        int step = 0;

        while (improved && step < maxSteps) {
            improved = false;
            step++;

            // Cari worker terberat dan teringan
            double[] loads = new double[numWorkers];
            for (int i = 0; i < current.length; i++) {
                loads[current[i]] += workloads[i];
            }
            int maxW = 0, minW = 0;
            for (int w = 1; w < numWorkers; w++) {
                if (loads[w] > loads[maxW]) maxW = w;
                if (loads[w] < loads[minW]) minW = w;
            }

            // Coba pindahkan task dari worker terberat ke worker teringan
            for (int i = 0; i < current.length; i++) {
                if (current[i] == maxW) {
                    int[] candidate = current.clone();
                    candidate[i] = minW;
                    double candFit = computeFitness(candidate, workloads, numWorkers);
                    if (candFit < currentFit) {
                        current = candidate;
                        currentFit = candFit;
                        improved = true;
                        break; // First improvement
                    }
                }
            }
        }
        return current;
    }

    /**
     * Jalankan optimasi VNS untuk mencari alokasi task terbaik.
     */
    public OptimizationResult optimize(int[] workloads, int numWorkers) {
        long startTime = System.currentTimeMillis();
        int numTasks = workloads.length;

        System.out.println("\n[VNS Simulation] Memulai Variable Neighborhood Search...");
        System.out.printf(Locale.US, "  -> Parameter: MaxIterations=%d, kMax=%d (N1: Shift, N2: Swap, N3: Two-Shift)%n",
                maxIterations, kMax);

        // Solusi awal: Round Robin sederhana
        int[] current = new int[numTasks];
        for (int i = 0; i < numTasks; i++) {
            current[i] = i % numWorkers;
        }
        double currentFitness = computeFitness(current, workloads, numWorkers);

        int[] bestSchedule = current.clone();
        double bestFitness = currentFitness;

        for (int iter = 1; iter <= maxIterations; iter++) {
            int k = 1;
            while (k <= kMax) {
                // 1. Shaking
                int[] shaken = shake(current, k, numWorkers);

                // 2. Local Search
                int[] localOpt = localSearch(shaken, workloads, numWorkers);
                double localFitness = computeFitness(localOpt, workloads, numWorkers);

                // 3. Neighborhood Change
                if (localFitness < currentFitness) {
                    current = localOpt;
                    currentFitness = localFitness;
                    if (currentFitness < bestFitness) {
                        bestFitness = currentFitness;
                        bestSchedule = current.clone();
                    }
                    k = 1; // Kembali ke lingkungan terkecil (N1)
                } else {
                    k++;   // Ganti ke struktur lingkungan berikutnya
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double[] metrics = evaluateMetrics(bestSchedule, workloads, numWorkers);
        System.out.printf(Locale.US, "[VNS Simulation Selesai] Waktu komputasi: %d ms | Best Est. Makespan: %.1f | Est. Imbalance: %.4f%n",
                elapsed, metrics[0], metrics[1]);

        return new OptimizationResult(bestSchedule, metrics[0], metrics[1], elapsed);
    }
}
