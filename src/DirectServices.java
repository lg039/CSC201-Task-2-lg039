import java.util.Arrays;

/**
 * CSC201 Task 2 -- Task 2: Direct Services.
 *
 * A direct service from u to v exists when one service calls at u and later
 * calls at v. A run stopping at m stations gives m(m-1)/2 of them, so the
 * whole timetable has about 3.9 million, far more than the 230k rows. They are
 * never stored one by one. Each is folded, the moment it is found, into a
 * fixed-size summary for its ordered pair: count, min time, max time, and
 * which routes serve it. A query is then one array lookup.
 *
 * Notation: S stations, C connections, T services, R routes,
 * D = direct services (sum over runs of m(m-1)/2), P = pairs with at least one.
 */
public class DirectServices {

    static final String CONNECTIONS = "dataset/connections.csv";
    static final String STATIONS = "dataset/stations.csv";

    private final Timetable tt;
    private final int n;

    // Pair (u, v) lives at [u][v] in S x S tables (197,136 cells for 444
    // stations, ~5 MB). A HashMap keyed by the pair would only hold the 27,487
    // pairs that are used, but a 2D array needs no hashing and no key objects
    // during the 3.9M updates, and the memory is still small.
    private final int[][] count;
    private final int[][] minTime;
    private final int[][] maxTime;
    private final boolean[][][] onRoute;   // onRoute[u][v][r]: route r serves the pair (made when first used)
    private final int[][] lastService;     // last service counted for the pair, so each service counts once

    private long totalDirect;
    private int pairsServed;

    public DirectServices() {
        tt = Timetable.load(STATIONS, CONNECTIONS);
        n = tt.numStations;
        count = new int[n][n];
        minTime = new int[n][n];
        maxTime = new int[n][n];
        onRoute = new boolean[n][n][];
        lastService = new int[n][n];
        for (int[] row : lastService) Arrays.fill(row, Timetable.NONE);

        // For every run, board at hop i and alight at the end of hop j (j >= i).
        // Time O(D + C): constant work per direct service. Extra space O(S^2),
        // independent of D -- materialising the 3.9M direct services as
        // objects instead would need hundreds of MB to answer the same queries.
        for (int s = 0; s < tt.numServices; s++) {
            int[] run = tt.runs[s];
            for (int i = 0; i < run.length; i++) {
                int board = run[i];
                int u = tt.from[board];
                for (int j = i; j < run.length; j++) {
                    int alight = run[j];
                    int v = tt.to[alight];
                    if (v == u) continue;                // a run passing back through u is not a journey
                    if (lastService[u][v] == s) continue; // this service already counted for the pair
                    lastService[u][v] = s;

                    int ride = tt.arr[alight] - tt.dep[board];
                    if (count[u][v] == 0) {
                        minTime[u][v] = ride;
                        maxTime[u][v] = ride;
                        onRoute[u][v] = new boolean[tt.routeLabels.length];
                        pairsServed++;
                    } else {
                        if (ride < minTime[u][v]) minTime[u][v] = ride;
                        if (ride > maxTime[u][v]) maxTime[u][v] = ride;
                    }
                    count[u][v]++;
                    onRoute[u][v][tt.route[board]] = true;
                    totalDirect++;
                }
            }
        }
    }

    /** Task 2 -- direct services between one ordered pair of stations. O(1) plus O(R) to list routes. */
    public void getDirectServices(String fromStationId, String toStationId) {
        // arguments are checked left to right, first unknown one only
        int u = tt.indexOf(fromStationId);
        if (u == Timetable.NONE) { Timetable.printUnknown(fromStationId); return; }
        int v = tt.indexOf(toStationId);
        if (v == Timetable.NONE) { Timetable.printUnknown(toStationId); return; }

        if (count[u][v] == 0) {   // includes u == v, which is never counted
            System.out.printf("No direct service from %s to %s.\n", tt.label(u), tt.label(v));
            return;
        }
        System.out.printf("Direct service %s -> %s: %d service(s) on route(s) %s, min %ds, max %ds.\n",
                tt.label(u), tt.label(v), count[u][v], routeList(onRoute[u][v]), minTime[u][v], maxTime[u][v]);
    }

    /** Route indices are in label order, so walking r upwards lists the routes lexicographically. */
    private String routeList(boolean[] routes) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < routes.length; r++) {
            if (!routes[r]) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(tt.routeLabels[r]);
        }
        return sb.toString();
    }

    /** Prints the totals and the pairs with the widest spread between fastest and slowest service. */
    private void printStats(double buildMs) {
        System.out.println("Ordered pairs with a direct service: " + pairsServed);
        System.out.println("Direct services in total: " + totalDirect);
        System.out.printf("Built in %.1f ms%n", buildMs);

        // widest absolute spread, and widest relative to the fastest (min >= 5 min, to skip trivial hops)
        int wu = -1, wv = -1;   // widest max - min
        int ru = -1, rv = -1;   // widest max / min
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                if (count[u][v] == 0) continue;
                int spread = maxTime[u][v] - minTime[u][v];
                if (wu < 0 || spread > maxTime[wu][wv] - minTime[wu][wv]) { wu = u; wv = v; }
                if (minTime[u][v] >= 300 && (ru < 0
                        || (double) maxTime[u][v] / minTime[u][v] > (double) maxTime[ru][rv] / minTime[ru][rv])) {
                    ru = u;
                    rv = v;
                }
            }
        }
        System.out.print("Widest spread (max - min): ");
        getDirectServices(tt.ids[wu], tt.ids[wv]);
        System.out.print("Widest spread (max / min): ");
        getDirectServices(tt.ids[ru], tt.ids[rv]);
    }

    /**
     * Runs the commands in the command file that belong to this task:
     *     java -cp bin DirectServices             (reads tests/commands.txt)
     *     java -cp bin DirectServices other.txt
     *     java -cp bin DirectServices --stats     (totals, build time, widest spread, for the report)
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--stats")) {
            Timetable.load(STATIONS, CONNECTIONS);
            long t0 = System.nanoTime();
            DirectServices task = new DirectServices();
            long t1 = System.nanoTime();
            task.printStats((t1 - t0) / 1e6);
            return;
        }
        CommandRunner.run(CommandRunner.fileFrom(args), "DirectServices");
    }
}
