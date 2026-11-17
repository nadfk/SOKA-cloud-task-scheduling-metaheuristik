import java.util.*;

/**
 * Bacterial Foraging Optimization (BFO) untuk Cloud Task Scheduling.
 *
 * Mengoptimalkan alokasi task heterogen ke sejumlah worker
 * untuk meminimalkan Makespan dan Degree of Imbalance (DI).
 *
 * Tahapan BFO:
 * 1. Chemotaxis (Tumble & Swim): Bakteri bergerak mencari solusi lokal yang lebih baik.
 * 2. Reproduction: Bakteri tersehat (fitness terbaik) menggandakan diri, yang buruk mati.
 * 3. Elimination-Dispersal: Bakteri dieliminasi secara acak untuk mencegah terjebak di local optima.
 */
public class BFOOptimizer {

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

    private static class Bacterium {
        int[] schedule;
        double fitness;
        double health;

        Bacterium(int[] schedule, double fitness) {
            this.schedule = schedule.clone();
            this.fitness = fitness;
            this.health = 0.0;
        }
    }

    // Parameter BFO
    private final int numBacteria;       // S
    private final int numChemotactic;    // Nc
    private final int swimLength;        // Ns
    private final int numReproduction;   // Nre
    private final int numElimination;    // Ned
    private final double pElimination;   // Ped
    private final Random rand;

    public BFOOptimizer() {
        this(12, 15, 4, 3, 2, 0.25, 42);
    }

    public BFOOptimizer(int numBacteria, int numChemotactic, int swimLength,
                        int numReproduction, int numElimination, double pElimination, long seed) {
        this.numBacteria = numBacteria;
        this.numChemotactic = numChemotactic;
        this.swimLength = swimLength;
        this.numReproduction = numReproduction;
        this.numElimination = numElimination;
        this.pElimination = pElimination;
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
        return new double[]{maxLoad, di}; // [makespan, degreeOfImbalance]
    }

    /**
     * Fitness function: bobot utama pada Makespan + penalti Degree of Imbalance.
     */
    public static double computeFitness(int[] schedule, int[] workloads, int numWorkers) {
        double[] m = evaluateMetrics(schedule, workloads, numWorkers);
        return m[0] + (0.15 * (m[0] * m[1]));
    }

    private int[] randomSchedule(int numTasks, int numWorkers) {
        int[] s = new int[numTasks];
        for (int i = 0; i < numTasks; i++) {
            s[i] = rand.nextInt(numWorkers);
        }
        return s;
    }

    /**
     * Jalankan optimasi BFO untuk mencari alokasi task terbaik.
     */
    public OptimizationResult optimize(int[] workloads, int numWorkers) {
        long startTime = System.currentTimeMillis();
        int numTasks = workloads.length;

        System.out.println("\n[BFO Simulation] Menginisialisasi " + numBacteria + " bakteri...");
        System.out.printf(Locale.US, "  -> Parameter: Nc=%d, Ns=%d, Nre=%d, Ned=%d, Ped=%.2f%n",
                numChemotactic, swimLength, numReproduction, numElimination, pElimination);

        // Inisialisasi populasi bakteri
        List<Bacterium> population = new ArrayList<>(numBacteria);
        for (int i = 0; i < numBacteria; i++) {
            int[] s = randomSchedule(numTasks, numWorkers);
            double fit = computeFitness(s, workloads, numWorkers);
            population.add(new Bacterium(s, fit));
        }

        Bacterium globalBest = population.get(0);
        for (Bacterium b : population) {
            if (b.fitness < globalBest.fitness) globalBest = new Bacterium(b.schedule, b.fitness);
        }

        // 1. Loop Elimination-Dispersal
        for (int l = 1; l <= numElimination; l++) {
            // 2. Loop Reproduction
            for (int k = 1; k <= numReproduction; k++) {
                // Reset health
                for (Bacterium b : population) b.health = 0.0;

                // 3. Loop Chemotaxis
                for (int j = 1; j <= numChemotactic; j++) {
                    for (Bacterium b : population) {
                        // Tumble: ubah 1 alokasi task secara acak
                        int taskIdx = rand.nextInt(numTasks);
                        int oldWorker = b.schedule[taskIdx];
                        int newWorker = rand.nextInt(numWorkers);
                        while (newWorker == oldWorker && numWorkers > 1) {
                            newWorker = rand.nextInt(numWorkers);
                        }

                        int[] candidate = b.schedule.clone();
                        candidate[taskIdx] = newWorker;
                        double candidateFitness = computeFitness(candidate, workloads, numWorkers);

                        // Swim: jika perbaikan didapat, lanjutkan eksplorasi ke arah tersebut
                        if (candidateFitness < b.fitness) {
                            b.schedule = candidate;
                            b.fitness = candidateFitness;

                            int swimStep = 0;
                            while (swimStep < swimLength) {
                                int t2 = rand.nextInt(numTasks);
                                int w2 = rand.nextInt(numWorkers);
                                int[] swimCandidate = b.schedule.clone();
                                swimCandidate[t2] = w2;
                                double swimFit = computeFitness(swimCandidate, workloads, numWorkers);
                                if (swimFit < b.fitness) {
                                    b.schedule = swimCandidate;
                                    b.fitness = swimFit;
                                    swimStep++;
                                } else {
                                    break;
                                }
                            }
                        }

                        b.health += b.fitness;
                        if (b.fitness < globalBest.fitness) {
                            globalBest = new Bacterium(b.schedule, b.fitness);
                        }
                    }
                }

                // Reproduction step: sortir berdasarkan akumulasi health (makin kecil makin sehat)
                population.sort(Comparator.comparingDouble(b -> b.health));
                int half = numBacteria / 2;
                for (int i = 0; i < half; i++) {
                    // Bakteri yang kurang sehat (indeks half..numBacteria-1) diganti klon dari bakteri sehat
                    population.set(half + i, new Bacterium(population.get(i).schedule, population.get(i).fitness));
                }
            }

            // Elimination-Dispersal step: eliminasi acak dengan probabilitas Ped
            for (int i = 0; i < population.size(); i++) {
                if (rand.nextDouble() < pElimination) {
                    int[] s = randomSchedule(numTasks, numWorkers);
                    population.set(i, new Bacterium(s, computeFitness(s, workloads, numWorkers)));
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double[] metrics = evaluateMetrics(globalBest.schedule, workloads, numWorkers);
        System.out.printf(Locale.US, "[BFO Simulation Selesai] Waktu komputasi: %d ms | Best Est. Makespan: %.1f | Est. Imbalance: %.4f%n",
                elapsed, metrics[0], metrics[1]);

        return new OptimizationResult(globalBest.schedule, metrics[0], metrics[1], elapsed);
    }
}
