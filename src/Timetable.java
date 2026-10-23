import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/**
 * CSC201 Task 2 -- shared timetable (auxiliary class).
 *
 * Reads stations.csv and connections.csv once and keeps them as flat int
 * arrays, plus the label() and hhmmss() helpers every task prints with. Each
 * task class gets it through Timetable.load(), so the files are only read
 * once per run even though CommandRunner builds three task objects.
 *
 * Notation used in the complexity comments of every task:
 *   S = stations (444), C = connections (230,227), T = services (8,497),
 *   R = routes (29).
 *
 * Indexes built here:
 *   - stations are numbered 0..S-1 in lexicographic order of station_id, so
 *     "ascending index" is exactly the "ascending station_id" order the
 *     output rules ask for
 *   - routes are numbered 0..R-1 in lexicographic order of their label
 *   - runs: an adjacency list per service, its connections in the order the
 *     train runs them
 *   - departures: an adjacency list per station, its departures in time order
 *   - timeOrder: every connection in departure order
 * Each adjacency list is an ArrayList while it is being built, then copied
 * to an int[] so it can be sorted and binary searched.
 * Loading is O(C log C) time (the sorts) and O(S + C + T) space.
 */
final class Timetable {

    static final int TRANSFER_SECONDS = 180;
    static final int NONE = -1;

    private static Timetable cached;

    // stations
    final int numStations;
    final String[] ids;      // station index -> station_id
    final String[] names;    // station index -> station_name
    private final HashMap<String, Integer> indexOf = new HashMap<>();

    // routes
    final String[] routeLabels;   // route index -> label, lexicographic

    // connections, one array per column, indexed by connection number
    final int numConnections;
    final int[] from, to, dep, arr, route;
    final int[] service;          // dense service index 0..T-1
    final int[] posInService;     // 0 for the first hop of a run, 1 for the next...
    int maxDeparture;

    // services
    final int numServices;
    final int[] serviceId;        // dense index -> run id from the file

    // runs[s] = connections of service s, in the order the train runs them
    final int[][] runs;
    // departures[u] = connections leaving station u, in departure time order
    final int[][] departures;
    // every connection ordered by departure (ties: arrival, then position along its run)
    final int[] timeOrder;

    /** Loads the dataset the first time it is asked for, then hands back the same copy. */
    static Timetable load(String stationsFile, String connectionsFile) {
        if (cached == null) {
            try {
                cached = new Timetable(stationsFile, connectionsFile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return cached;
    }

    private Timetable(String stationsFile, String connectionsFile) throws IOException {
        // stations: the last four columns never contain commas, so anything
        // between the id and those columns is the name
        TreeSet<String> sortedIds = new TreeSet<>();
        HashMap<String, String> nameOf = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new FileReader(stationsFile))) {
            in.readLine();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(",");
                StringBuilder name = new StringBuilder(p[1]);
                for (int i = 2; i < p.length - 4; i++) name.append(',').append(p[i]);
                sortedIds.add(p[0].trim());
                nameOf.put(p[0].trim(), name.toString().trim());
            }
        }
        numStations = sortedIds.size();
        ids = sortedIds.toArray(new String[0]);
        names = new String[numStations];
        for (int i = 0; i < numStations; i++) {
            indexOf.put(ids[i], i);
            names[i] = nameOf.get(ids[i]);
        }

        // connections: read the raw rows first, then number routes and services
        int cap = 1024;   // grows by doubling, like an array list
        int[] f = new int[cap], t = new int[cap], d = new int[cap], a = new int[cap], svcRaw = new int[cap];
        String[] routeRaw = new String[cap];
        int n = 0;
        try (BufferedReader in = new BufferedReader(new FileReader(connectionsFile))) {
            in.readLine();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(",");
                Integer u = indexOf.get(p[0].trim());
                Integer v = indexOf.get(p[1].trim());
                if (u == null || v == null) continue;   // unknown station, not part of the network
                if (n == f.length) {
                    f = Arrays.copyOf(f, n * 2);
                    t = Arrays.copyOf(t, n * 2);
                    d = Arrays.copyOf(d, n * 2);
                    a = Arrays.copyOf(a, n * 2);
                    svcRaw = Arrays.copyOf(svcRaw, n * 2);
                    routeRaw = Arrays.copyOf(routeRaw, n * 2);
                }
                f[n] = u;
                t[n] = v;
                d[n] = Integer.parseInt(p[2].trim());
                a[n] = Integer.parseInt(p[3].trim());
                routeRaw[n] = p[4].trim();
                svcRaw[n] = Integer.parseInt(p[5].trim());
                n++;
            }
        }
        numConnections = n;
        from = Arrays.copyOf(f, n);
        to = Arrays.copyOf(t, n);
        dep = Arrays.copyOf(d, n);
        arr = Arrays.copyOf(a, n);
        for (int c = 0; c < n; c++) maxDeparture = Math.max(maxDeparture, dep[c]);

        // routes numbered in label order, so a route index sorts the same way its label does
        TreeSet<String> labels = new TreeSet<>();
        for (int c = 0; c < n; c++) labels.add(routeRaw[c]);
        routeLabels = labels.toArray(new String[0]);
        HashMap<String, Integer> routeIndex = new HashMap<>();
        for (int r = 0; r < routeLabels.length; r++) routeIndex.put(routeLabels[r], r);
        route = new int[n];
        for (int c = 0; c < n; c++) route[c] = routeIndex.get(routeRaw[c]);

        // services numbered densely so per-service state can live in plain arrays
        HashMap<Integer, Integer> serviceIndex = new HashMap<>();
        service = new int[n];
        int[] runIds = new int[16];
        for (int c = 0; c < n; c++) {
            Integer s = serviceIndex.get(svcRaw[c]);
            if (s == null) {
                s = serviceIndex.size();
                serviceIndex.put(svcRaw[c], s);
                if (s == runIds.length) runIds = Arrays.copyOf(runIds, runIds.length * 2);
                runIds[s] = svcRaw[c];
            }
            service[c] = s;
        }
        numServices = serviceIndex.size();
        serviceId = Arrays.copyOf(runIds, numServices);

        // runs: one adjacency list per service. Rows are added in file order,
        // then each list is sorted by departure time (arrival breaks a tie)
        List<List<Integer>> runLists = new ArrayList<>();
        for (int i = 0; i < numServices; i++) runLists.add(new ArrayList<>());
        for (int c = 0; c < n; c++) runLists.get(service[c]).add(c);
        runs = new int[numServices][];
        posInService = new int[n];
        for (int i = 0; i < numServices; i++) {
            runs[i] = sortedArray(runLists.get(i), (x, y) -> dep[x] != dep[y]
                    ? Integer.compare(dep[x], dep[y]) : Integer.compare(arr[x], arr[y]));
            for (int pos = 0; pos < runs[i].length; pos++) posInService[runs[i][pos]] = pos;
        }

        // departures: one adjacency list per station, sorted by departure time
        List<List<Integer>> departLists = new ArrayList<>();
        for (int u = 0; u < numStations; u++) departLists.add(new ArrayList<>());
        for (int c = 0; c < n; c++) departLists.get(from[c]).add(c);
        departures = new int[numStations][];
        for (int u = 0; u < numStations; u++) {
            departures[u] = sortedArray(departLists.get(u), (x, y) -> Integer.compare(dep[x], dep[y]));
        }

        // timeOrder: every connection in departure order; the run position
        // tie-break keeps a train's own hops in order even if two of them ever
        // shared the same times
        List<Integer> all = new ArrayList<>();
        for (int c = 0; c < n; c++) all.add(c);
        timeOrder = sortedArray(all, (x, y) -> dep[x] != dep[y] ? Integer.compare(dep[x], dep[y])
                : arr[x] != arr[y] ? Integer.compare(arr[x], arr[y])
                : Integer.compare(posInService[x], posInService[y]));
    }

    /** Sorts a list of connection numbers with the given order and copies it to an int[]. */
    private static int[] sortedArray(List<Integer> list, Comparator<Integer> order) {
        list.sort(order);
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    /** Station index for an id, or NONE when the id is not in stations.csv. */
    int indexOf(String stationId) {
        Integer i = indexOf.get(stationId);
        return i == null ? NONE : i;
    }

    /** The standard message for an id that is not in stations.csv. */
    static void printUnknown(String stationId) {
        System.out.printf("Unknown station '%s'.\n", stationId);
    }

    /** "Times Sq-42 St (127)" */
    String label(int station) {
        return names[station] + " (" + ids[station] + ")";
    }

    /** 88410 -> "24:33:30"; the hour is never wrapped back to 00. */
    static String hhmmss(int t) {
        return String.format("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60);
    }

    /**
     * Binary search: the first position i in a whose time key[a[i]] is >= target,
     * or a.length if there is none. a must be sorted by key. O(log n).
     */
    static int firstAtOrAfter(int[] a, int[] key, int target) {
        int low = 0, high = a.length;
        while (low < high) {
            int mid = (low + high) / 2;
            if (key[a[mid]] < target) low = mid + 1;
            else high = mid;
        }
        return low;
    }
}
