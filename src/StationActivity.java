import java.util.ArrayList;
import java.util.List;

/**
 * CSC201 Task 2 -- Task 1: Station Activity.
 *
 * Everything is counted in the constructor with one pass over the
 * connections, and the rank tiers are built once from those counts. The two
 * query methods never look at the connections again; each is a lookup.
 *
 * Notation: S stations, C connections (see Timetable).
 */
public class StationActivity {

    static final String CONNECTIONS = "dataset/connections.csv";
    static final String STATIONS = "dataset/stations.csv";
    static final int MAX_LISTED = 10;

    private final Timetable tt;

    private final int[] departures;   // station -> departures across the day
    private final int[][] hourly;     // station -> departures in each hour bucket

    // tier k-1 is rank k: its departure count, and its stations in ascending id order
    private final int[] tierCount;
    private final int[][] tierStations;

    public StationActivity() {
        tt = Timetable.load(STATIONS, CONNECTIONS);
        int n = tt.numStations;

        // one pass over the connections fills both tables: O(C) time.
        // buckets run 0..28 here because the last departure is 28:00:00;
        // nothing is wrapped, so after-midnight trains land in 24..28
        int buckets = tt.maxDeparture / 3600 + 1;
        departures = new int[n];
        hourly = new int[n][buckets];
        for (int c = 0; c < tt.numConnections; c++) {
            departures[tt.from[c]]++;
            hourly[tt.from[c]][tt.dep[c] / 3600]++;
        }

        // Building the tiers: bucket-sort the stations by departure count.
        //   1. count how many stations have each departure value (0..max)
        //   2. walk the values from max down to 0; each non-empty value is a tier
        //   3. drop stations into their tier in index order, which is ascending
        //      station_id, so every tier comes out already sorted for printing
        // Cost: O(S + M) time and space, M = the largest count (4,008 here).
        //
        // Alternatives considered:
        //   - comparison sort of all S stations by (count desc, id): O(S log S)
        //     once. Also fine, but the counts are small integers, so bucket-sort
        //     needs no comparisons and gives the ties in id order for free.
        //   - per query, keep the k largest distinct counts in a min-heap of
        //     size k: O(S log k) per query, or quick-select over the distinct
        //     counts: O(S) expected per query. Either beats sorting for ONE
        //     query, but the command file asks many rank queries against the
        //     same counts, so paying O(S + M) once and answering each query in
        //     O(1) is cheaper overall (q queries: O(S + M + q) vs O(q S log k)).
        int max = 0;
        for (int u = 0; u < n; u++) max = Math.max(max, departures[u]);
        int[] stationsWithCount = new int[max + 1];
        for (int u = 0; u < n; u++) stationsWithCount[departures[u]]++;

        int tiers = 0;
        for (int v = 0; v <= max; v++) if (stationsWithCount[v] > 0) tiers++;
        tierCount = new int[tiers];
        tierStations = new int[tiers][];
        int[] tierOf = new int[max + 1];
        int k = 0;
        for (int v = max; v >= 0; v--) {
            if (stationsWithCount[v] == 0) continue;
            tierCount[k] = v;
            tierStations[k] = new int[stationsWithCount[v]];
            tierOf[v] = k++;
        }
        int[] filled = new int[tiers];
        for (int u = 0; u < n; u++) {
            int t = tierOf[departures[u]];
            tierStations[t][filled[t]++] = u;
        }
    }

    /** Task 1.1 -- report the k-th busiest tier of stations by departures. O(1) plus printing. */
    public void getStationRank(int k) {
        if (k < 1 || k > tierCount.length) {
            System.out.printf("Rank %d does not exist.\n", k);
            return;
        }
        int[] tier = tierStations[k - 1];
        int count = tierCount[k - 1];

        // print at most ten, but the count is the full tier size
        List<String> stations = new ArrayList<>();
        for (int i = 0; i < tier.length && i < MAX_LISTED; i++) stations.add(tt.label(tier[i]));
        System.out.printf("Rank %d: %d station(s) with %d departure(s).\n", k, tier.length, count);
        System.out.println("Station(s) include: " + stations.toString());
    }

    /** Task 1.2 -- report the busiest hour bucket at one station. O(29) per query. */
    public void getBusiestHour(String stationId) {
        int u = tt.indexOf(stationId);
        if (u == Timetable.NONE) {
            Timetable.printUnknown(stationId);
            return;
        }
        int[] hours = hourly[u];
        int hour = 0;
        for (int h = 1; h < hours.length; h++) {
            if (hours[h] > hours[hour]) hour = h;   // strictly greater keeps the earlier hour on a tie
        }
        System.out.printf("Busiest hour at %s is %02d:00-%02d:00 with %d departure(s).\n",
                tt.label(u), hour, hour + 1, hours[hour]);
    }

    /** Number of distinct departure-count tiers (171 on the frozen dataset). */
    int numberOfTiers() {
        return tierCount.length;
    }

    /**
     * Runs the commands in the command file that belong to this task:
     *     java -cp bin StationActivity            (reads tests/commands.txt)
     *     java -cp bin StationActivity other.txt
     *     java -cp bin StationActivity --stats    (tier count and build time, for the report)
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--stats")) {
            long t0 = System.nanoTime();
            Timetable.load(STATIONS, CONNECTIONS);
            long t1 = System.nanoTime();
            StationActivity task = new StationActivity();
            long t2 = System.nanoTime();
            System.out.printf("Load timetable: %.1f ms%n", (t1 - t0) / 1e6);
            System.out.printf("Count departures and build tiers: %.2f ms%n", (t2 - t1) / 1e6);
            System.out.println("Distinct departure-count tiers: " + task.numberOfTiers());
            return;
        }
        CommandRunner.run(CommandRunner.fileFrom(args), "StationActivity");
    }
}
