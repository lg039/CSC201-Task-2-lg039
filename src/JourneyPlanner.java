import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * CSC201 Task 2 -- Task 3: Earliest Arrival Journey Planning.
 *
 * 3.1 getStationsAtHops: breadth-first search on the plain station graph.
 *
 * 3.2 getItinerary, solved by two genuinely different algorithms:
 *   M1 -- Dijkstra over stations. A priority queue hands out stations in order
 *         of the earliest clock time we can stand there; from each settled
 *         station we board any later departure and ride that train to the end
 *         of its run. Work is ordered by STATION, chosen by the heap.
 *   M2 -- Connection scan. The timetable is a DAG once every connection is a
 *         node: a connection can only feed one that departs later, so sorting
 *         by departure time is already a topological order. M2 reads the
 *         connections once in that order and relaxes each in O(1), like the
 *         DAG shortest-path method: no heap and no per-station lists. Work is
 *         ordered by TIME, fixed in advance by the sort.
 * Both return the full journey, and print through the same templates.
 *
 * Times are seconds after midnight and are NOT wrapped at 24:00.
 * Notation: S stations, C connections, T services, E distinct station edges.
 */
public class JourneyPlanner {

    static final String CONNECTIONS = "dataset/connections.csv";
    static final String STATIONS = "dataset/stations.csv";
    static final String QUERIES = "tests/queries.csv";
    static final int TRANSFER_SECONDS = Timetable.TRANSFER_SECONDS;
    private static final int INF = Integer.MAX_VALUE;

    /** The part of a journey spent aboard one service. */
    static final class Leg {
        final String route;
        final int service;            // run id as printed
        final int from, to;           // station indices
        final int depart, arrive;

        Leg(String route, int service, int from, int to, int depart, int arrive) {
            this.route = route;
            this.service = service;
            this.from = from;
            this.to = to;
            this.depart = depart;
            this.arrive = arrive;
        }
    }

    /** An earliest-arrival answer: the arrival time and one journey that achieves it. */
    static final class Journey {
        final int arrival;
        final List<Leg> legs;

        Journey(int arrival, List<Leg> legs) {
            this.arrival = arrival;
            this.legs = legs;
        }
    }

    /** A heap entry for M1: a station and the clock time we can be standing there. */
    private static final class Stop implements Comparable<Stop> {
        final int time, station;

        Stop(int time, int station) {
            this.time = time;
            this.station = station;
        }

        @Override
        public int compareTo(Stop other) {
            if (time != other.time) return Integer.compare(time, other.time);
            return Integer.compare(station, other.station);   // tie: lower station index first
        }
    }

    private final Timetable tt;

    // 3.1: adjacency list of the station graph, neighbours[u] = stations one edge from u
    private final int[][] neighbours;

    // work counters, read by ItineraryComparison to explain the timings
    long scannedM1, ridesM1, heapPushesM1, scannedM2;

    public JourneyPlanner() {
        tt = Timetable.load(STATIONS, CONNECTIONS);
        int n = tt.numStations;

        // collapse the 230k connections onto distinct station edges with an
        // adjacency matrix, then turn each row into a neighbour list: O(C + S^2)
        boolean[][] edge = new boolean[n][n];
        for (int c = 0; c < tt.numConnections; c++) edge[tt.from[c]][tt.to[c]] = true;
        neighbours = new int[n][];
        for (int u = 0; u < n; u++) {
            List<Integer> list = new ArrayList<>();
            for (int v = 0; v < n; v++) if (edge[u][v]) list.add(v);
            neighbours[u] = new int[list.size()];
            for (int i = 0; i < list.size(); i++) neighbours[u][i] = list.get(i);
        }
    }

    // ------------------------------------------------------------------ 3.1

    /**
     * Task 3.1 -- stations exactly `hops` edges away, times ignored.
     *
     * BFS settles stations layer by layer, so the layer a station first
     * appears in is its smallest hop count. A station already seen in an
     * earlier layer is NOT "exactly hops away" even if a longer path exists.
     * The search stops expanding at layer `hops`.
     * Time O(S + E), space O(S).
     */
    public void getStationsAtHops(String stationId, int hops) {
        int src = tt.indexOf(stationId);
        if (src == Timetable.NONE) {
            Timetable.printUnknown(stationId);
            return;
        }
        int n = tt.numStations;
        int[] dist = new int[n];
        Arrays.fill(dist, -1);
        int[] queue = new int[n];
        int head = 0, tail = 0;
        dist[src] = 0;
        queue[tail++] = src;
        int count = 0;
        while (head < tail) {
            int u = queue[head++];
            if (dist[u] == hops) {
                count++;
                continue;   // no need to look past the layer we are counting
            }
            for (int v : neighbours[u]) {
                if (dist[v] == -1) {
                    dist[v] = dist[u] + 1;
                    queue[tail++] = v;
                }
            }
        }
        System.out.printf("Total number of stations exactly %d hop(s) from %s: %d\n",
                hops, tt.label(src), count);
    }

    // ------------------------------------------------------------------ 3.2

    /** Task 3.2, algorithm 1 of 2: Dijkstra over stations. */
    public void getItineraryM1(String fromStationId, String toStationId, int departAfter) {
        System.out.println("[getItineraryM1]"); // DO NOT remove this statement
        plan(fromStationId, toStationId, departAfter, true);
    }

    /** Task 3.2, algorithm 2 of 2: connection scan in departure order. */
    public void getItineraryM2(String fromStationId, String toStationId, int departAfter) {
        System.out.println("[getItineraryM2]"); // DO NOT remove this statement
        plan(fromStationId, toStationId, departAfter, false);
    }

    /**
     * The method the test cases call. It runs both versions one after the
     * other, so a single run exercises both: M1 prints its itinerary first,
     * then M2 prints its own. The two must report the same arrival time.
     */
    public void getItinerary(String fromStationId, String toStationId, int departAfter) {
        getItineraryM1(fromStationId, toStationId, departAfter);
        getItineraryM2(fromStationId, toStationId, departAfter);
    }

    /** Argument checks and printing shared by both versions. */
    private void plan(String fromStationId, String toStationId, int departAfter, boolean useM1) {
        int src = tt.indexOf(fromStationId);
        if (src == Timetable.NONE) { Timetable.printUnknown(fromStationId); return; }
        int dst = tt.indexOf(toStationId);
        if (dst == Timetable.NONE) { Timetable.printUnknown(toStationId); return; }

        if (src == dst) {
            System.out.printf("Already at %s at %s; no journey required.\n",
                    tt.label(src), Timetable.hhmmss(departAfter));
            return;
        }
        Journey j = useM1 ? solveM1(src, dst, departAfter) : solveM2(src, dst, departAfter);
        if (j == null) {
            System.out.printf("No journey from %s to %s departing at or after %s.\n",
                    tt.label(src), tt.label(dst), Timetable.hhmmss(departAfter));
            return;
        }
        List<Leg> legs = j.legs;
        System.out.printf("Itinerary from %s to %s departing %s: %d leg(s), %d transfer(s), arriving %s.\n",
                tt.label(src), tt.label(dst), Timetable.hhmmss(departAfter),
                legs.size(), legs.size() - 1, Timetable.hhmmss(j.arrival));
        for (int i = 0; i < legs.size(); i++) {
            Leg leg = legs.get(i);
            System.out.printf("  Leg %d: route %s service %d depart %s %s arrive %s %s\n",
                    i + 1, leg.route, leg.service, tt.label(leg.from), Timetable.hhmmss(leg.depart),
                    tt.label(leg.to), Timetable.hhmmss(leg.arrive));
        }
    }

    /**
     * M1 -- Dijkstra over stations, keyed on earliest clock time.
     *
     * best[u] is the earliest time we can be standing at u. The heap pops the
     * unsettled station with the smallest best[], which is then final. From
     * a settled u we may board any departure at or after
     * ready = best[u] + 180 (just departAfter at the origin), found by binary
     * search in u's time-sorted departures. Boarding a train means riding it:
     * every later stop of that run is relaxed with its own arrival time, so
     * staying aboard costs nothing and never needs a transfer.
     *
     * Avoided work:
     *   - boardedPos[s] remembers the earliest point of run s we have boarded
     *     at; boarding s again further along can only repeat those arrivals,
     *     so it is skipped, and each hop of each run is ridden at most once
     *   - departures, and arrivals along a ride, at or after the best known
     *     arrival at the destination cannot improve it, so the scan stops
     *   - the search ends as soon as the destination is popped
     *
     * Time: each station is settled once; each departure is scanned at most
     * once (O(C)); each hop is ridden at most once (O(C)); each improvement
     * pushes one heap entry, O(log C) each. Plus O(S log C) for the binary
     * searches and O(S + T) to set up the arrays:
     *     O(S + T + C log C) worst case per query.
     * Space: O(S + T) for the arrays, O(C) for the heap in the worst case.
     */
    Journey solveM1(int src, int dst, int departAfter) {
        int n = tt.numStations;
        int[] best = new int[n];
        int[] boardConn = new int[n];    // connection we boarded on, for the leg that reached each station
        int[] alightConn = new int[n];   // connection we got off at the end of
        boolean[] settled = new boolean[n];
        int[] boardedPos = new int[tt.numServices];
        Arrays.fill(best, INF);
        Arrays.fill(boardedPos, INF);

        // the heap orders Stops by time, so poll() gives the earliest station
        PriorityQueue<Stop> heap = new PriorityQueue<>();
        best[src] = departAfter;
        heap.add(new Stop(departAfter, src));

        while (!heap.isEmpty()) {
            int u = heap.poll().station;
            if (settled[u]) continue;   // stale entry, a better one was already used
            settled[u] = true;
            if (u == dst) break;

            int ready = (u == src) ? departAfter : best[u] + TRANSFER_SECONDS;
            int[] leaving = tt.departures[u];
            for (int k = Timetable.firstAtOrAfter(leaving, tt.dep, ready); k < leaving.length; k++) {
                int c = leaving[k];
                scannedM1++;
                if (tt.dep[c] >= best[dst]) break;   // departures are in time order
                int s = tt.service[c];
                int p = tt.posInService[c];
                if (p >= boardedPos[s]) continue;    // already riding s from this stop or earlier

                // ride s from here up to where we boarded it before (or its last stop)
                int[] run = tt.runs[s];
                int stop = Math.min(boardedPos[s], run.length);
                boardedPos[s] = p;
                for (int q = p; q < stop; q++) {
                    int hop = run[q];
                    int a = tt.arr[hop];
                    ridesM1++;
                    if (a >= best[dst]) break;       // arrivals along a run only increase
                    int v = tt.to[hop];
                    if (a < best[v]) {
                        best[v] = a;
                        boardConn[v] = c;
                        alightConn[v] = hop;
                        heap.add(new Stop(a, v));
                        heapPushesM1++;
                    }
                }
            }
        }
        if (best[dst] == INF) return null;
        return buildJourney(src, dst, best[dst], boardConn, alightConn);
    }

    /**
     * M2 -- connection scan in departure order (shortest path on a DAG).
     *
     * Connections are visited in timeOrder, which is a topological order of
     * the "connection feeds connection" DAG: anything that could get us to
     * c.from in time for c has an earlier departure, so it was already
     * relaxed. A connection is usable if
     *   - we are already aboard its service (boardedAt[s] is set), or
     *   - we are at c.from by c.departure - 180 (by departAfter at the origin).
     * A usable connection relaxes best[c.to] in O(1).
     *
     * Avoided work: binary search jumps straight to the first departure at or
     * after departAfter, and the scan stops at the first departure at or
     * after the best known arrival at the destination -- nothing later can
     * improve it.
     *
     * Time: O(log C) to find the start, then O(1) per connection inside the
     * window [departAfter, arrival], plus O(S + T) to set up the arrays:
     *     O(S + T + C) worst case per query, with no log factor.
     * Space: O(S + T).
     */
    Journey solveM2(int src, int dst, int departAfter) {
        int n = tt.numStations;
        int[] best = new int[n];
        int[] boardConn = new int[n];
        int[] alightConn = new int[n];
        int[] boardedAt = new int[tt.numServices];   // connection where we got on each service
        Arrays.fill(best, INF);
        Arrays.fill(boardedAt, Timetable.NONE);
        best[src] = departAfter;

        int[] order = tt.timeOrder;
        int total = tt.numConnections;
        for (int k = Timetable.firstAtOrAfter(order, tt.dep, departAfter); k < total; k++) {
            int c = order[k];
            scannedM2++;
            if (tt.dep[c] >= best[dst]) break;
            int s = tt.service[c];
            if (boardedAt[s] == Timetable.NONE) {
                int u = tt.from[c];
                if (best[u] == INF) continue;
                int ready = (u == src) ? departAfter : best[u] + TRANSFER_SECONDS;
                if (tt.dep[c] < ready) continue;
                boardedAt[s] = c;
            }
            int v = tt.to[c];
            if (tt.arr[c] < best[v]) {
                best[v] = tt.arr[c];
                boardConn[v] = boardedAt[s];
                alightConn[v] = c;
            }
        }
        if (best[dst] == INF) return null;
        return buildJourney(src, dst, best[dst], boardConn, alightConn);
    }

    /**
     * Walks back from the destination. Each station remembers the single leg
     * that reached it (where it boarded, where it got off), so following the
     * boarding stations back to the origin gives the legs in reverse.
     * O(L) for L legs.
     */
    private Journey buildJourney(int src, int dst, int arrival, int[] boardConn, int[] alightConn) {
        List<Leg> legs = new ArrayList<>();
        int v = dst;
        while (v != src) {
            int b = boardConn[v], a = alightConn[v];
            legs.add(new Leg(tt.routeLabels[tt.route[b]], tt.serviceId[tt.service[b]],
                    tt.from[b], v, tt.dep[b], tt.arr[a]));
            v = tt.from[b];
        }
        Collections.reverse(legs);
        return new Journey(arrival, legs);
    }

    /**
     * Runs the commands in the command file that belong to this task:
     *     java -cp bin JourneyPlanner             (reads tests/commands.txt)
     *     java -cp bin JourneyPlanner other.txt
     *     java -cp bin JourneyPlanner --compare   (M1 vs M2 over tests/queries.csv, for the report)
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--compare")) {
            ItineraryComparison.run(new JourneyPlanner(), args.length > 1 ? args[1] : QUERIES);
            return;
        }
        CommandRunner.run(CommandRunner.fileFrom(args), "JourneyPlanner");
    }
}
