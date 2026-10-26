import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * CSC201 Task 2 -- Task 3.2 comparison (auxiliary class, run from
 * JourneyPlanner.main with --compare).
 *
 *  1. Agreement: runs M1 and M2 on every row of tests/queries.csv and checks
 *     the earliest arrival is identical on every row.
 *  2. Validity: replays every journey either version returns against the raw
 *     timetable, independently of how it was found, and checks a passenger
 *     could really take it.
 *  3. Timing: times each version over the whole batch (arrival AND journey,
 *     printing excluded) several times with System.nanoTime(), and reports
 *     the work counters that explain the times.
 */
final class ItineraryComparison {

    private static final int TIMED_BATCHES = 15;

    static void run(JourneyPlanner planner, String queriesFile) {
        Timetable tt = Timetable.load(JourneyPlanner.STATIONS, JourneyPlanner.CONNECTIONS);
        int[][] queries;
        try {
            queries = readQueries(tt, queriesFile);
        } catch (IOException e) {
            System.out.println("Cannot read '" + queriesFile + "': " + e.getMessage());
            return;
        }
        int q = queries.length;
        System.out.println("Queries: " + q);

        // 1 + 2: agreement and validity
        int agree = 0, noJourney = 0, sameJourney = 0, sameLegCount = 0, invalid = 0, crossMidnight = 0;
        int legsM1 = 0, legsM2 = 0;
        for (int[] row : queries) {
            int src = row[0], dst = row[1], t = row[2];
            JourneyPlanner.Journey a = src == dst ? null : planner.solveM1(src, dst, t);
            JourneyPlanner.Journey b = src == dst ? null : planner.solveM2(src, dst, t);
            int arrA = a == null ? -1 : a.arrival;
            int arrB = b == null ? -1 : b.arrival;
            if (arrA == arrB) agree++;
            else System.out.printf("DISAGREE %s -> %s at %d: M1 %d, M2 %d%n", tt.ids[src], tt.ids[dst], t, arrA, arrB);
            if (a == null && b == null) {
                noJourney++;
                continue;
            }
            for (JourneyPlanner.Journey j : new JourneyPlanner.Journey[] {a, b}) {
                String why = j == null ? "missing" : validate(tt, src, dst, t, j);
                if (why != null) {
                    invalid++;
                    System.out.printf("INVALID %s -> %s at %d: %s%n", tt.ids[src], tt.ids[dst], t, why);
                }
            }
            if (a == null || b == null) continue;
            legsM1 += a.legs.size();
            legsM2 += b.legs.size();
            if (a.legs.size() == b.legs.size()) sameLegCount++;
            if (sameLegs(a, b)) sameJourney++;
            if (a.arrival >= 86400) crossMidnight++;
        }
        int found = q - noJourney;
        System.out.printf("Arrival agrees on %d / %d queries%n", agree, q);
        System.out.printf("No journey: %d, journey found: %d (%d arrive after midnight)%n", noJourney, found, crossMidnight);
        System.out.printf("Invalid journeys printed: %d%n", invalid);
        System.out.printf("Identical journeys: %d / %d; same number of legs: %d / %d%n", sameJourney, found, sameLegCount, found);
        System.out.printf("Average legs: M1 %.2f, M2 %.2f%n", (double) legsM1 / found, (double) legsM2 / found);

        // 3: timing. The two versions take turns batch by batch, so neither
        // one always runs on a "fresher" machine. The first batches are slower
        // while Java is still compiling the code, so the median is reported
        // alongside the average.
        double[] msM1 = new double[TIMED_BATCHES], msM2 = new double[TIMED_BATCHES];
        for (int r = 0; r < TIMED_BATCHES; r++) {
            msM1[r] = batch(planner, queries, true);
            msM2[r] = batch(planner, queries, false);
        }
        planner.scannedM1 = planner.ridesM1 = planner.heapPushesM1 = planner.scannedM2 = 0;
        batch(planner, queries, true);
        batch(planner, queries, false);

        System.out.printf("%nTiming over %d batches of %d queries:%n", TIMED_BATCHES, q);
        report("M1 Dijkstra      ", msM1, q);
        report("M2 Connection scan", msM2, q);
        System.out.printf("%nWork per query (average):%n");
        System.out.printf("  M1: %.0f departures scanned, %.0f hops ridden, %.0f heap pushes%n",
                (double) planner.scannedM1 / q, (double) planner.ridesM1 / q, (double) planner.heapPushesM1 / q);
        System.out.printf("  M2: %.0f connections scanned (of %d)%n", (double) planner.scannedM2 / q, tt.numConnections);
    }

    /** Solves the whole batch with one version and returns the elapsed milliseconds. */
    private static double batch(JourneyPlanner planner, int[][] queries, boolean useM1) {
        long start = System.nanoTime();
        for (int[] row : queries) {
            if (row[0] == row[1]) continue;
            if (useM1) planner.solveM1(row[0], row[1], row[2]);
            else planner.solveM2(row[0], row[1], row[2]);
        }
        return (System.nanoTime() - start) / 1e6;
    }

    private static void report(String name, double[] ms, int q) {
        double[] sorted = ms.clone();
        Arrays.sort(sorted);
        double mean = 0;
        for (double m : ms) mean += m;
        mean /= ms.length;
        System.out.printf("  %s  average %7.1f ms   median %7.1f ms   (%.1f us/query, median)%n",
                name, mean, sorted[sorted.length / 2], sorted[sorted.length / 2] * 1000 / q);
    }

    private static boolean sameLegs(JourneyPlanner.Journey a, JourneyPlanner.Journey b) {
        if (a.legs.size() != b.legs.size()) return false;
        for (int i = 0; i < a.legs.size(); i++) {
            JourneyPlanner.Leg x = a.legs.get(i), y = b.legs.get(i);
            if (x.service != y.service || x.from != y.from || x.to != y.to) return false;
        }
        return true;
    }

    /**
     * Replays a journey against the raw runs. Returns null if a passenger
     * could take it, otherwise the first rule it breaks:
     *   - leg 1 starts at the origin, at or after departAfter
     *   - every other leg starts where the last ended, on a DIFFERENT service,
     *     at least 180 s after the last arrival
     *   - each leg really is that service, calling at its from-station at the
     *     printed time and at its to-station later at the printed time
     *   - the last leg ends at the destination at the reported arrival
     */
    static String validate(Timetable tt, int src, int dst, int departAfter, JourneyPlanner.Journey j) {
        if (j.legs.isEmpty()) return "no legs";
        int at = src, time = departAfter, prevService = Integer.MIN_VALUE;
        for (int i = 0; i < j.legs.size(); i++) {
            JourneyPlanner.Leg leg = j.legs.get(i);
            if (leg.from != at) return "leg " + (i + 1) + " starts away from where the last one ended";
            int earliest = i == 0 ? departAfter : time + Timetable.TRANSFER_SECONDS;
            if (leg.depart < earliest) return "leg " + (i + 1) + " leaves before the passenger can board";
            if (leg.service == prevService) return "legs " + i + " and " + (i + 1) + " are the same service";
            if (!onTimetable(tt, leg)) return "leg " + (i + 1) + " is not on the timetable";
            at = leg.to;
            time = leg.arrive;
            prevService = leg.service;
        }
        if (at != dst) return "journey ends away from the destination";
        if (time != j.arrival) return "reported arrival differs from the last leg";
        return null;
    }

    private static HashMap<Integer, Integer> denseService;

    /** True when service leg.service leaves leg.from at leg.depart and later reaches leg.to at leg.arrive. */
    private static boolean onTimetable(Timetable tt, JourneyPlanner.Leg leg) {
        if (denseService == null) {
            denseService = new HashMap<>();
            for (int s = 0; s < tt.numServices; s++) denseService.put(tt.serviceId[s], s);
        }
        Integer s = denseService.get(leg.service);
        if (s == null) return false;
        boolean aboard = false;
        for (int c : tt.runs[s]) {
            if (!tt.routeLabels[tt.route[c]].equals(leg.route)) return false;
            if (!aboard && tt.from[c] == leg.from && tt.dep[c] == leg.depart) aboard = true;
            if (aboard && tt.to[c] == leg.to && tt.arr[c] == leg.arrive) return true;
        }
        return false;
    }

    private static int[][] readQueries(Timetable tt, String file) throws IOException {
        List<int[]> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            in.readLine();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(",");
                int src = tt.indexOf(p[0].trim()), dst = tt.indexOf(p[1].trim());
                if (src == Timetable.NONE || dst == Timetable.NONE) continue;
                rows.add(new int[] {src, dst, Integer.parseInt(p[2].trim())});
            }
        }
        return rows.toArray(new int[0][]);
    }

    private ItineraryComparison() { }
}
