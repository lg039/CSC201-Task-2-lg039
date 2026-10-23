/**
 * CSC201 Task 2 -- Task 3: Earliest Arrival Journey Planning.
 *
 * Task 3.2 must be implemented twice, by two genuinely different algorithms,
 * as getItineraryM1 and getItineraryM2. Both print in exactly the same
 * templates. Each announces itself first with its own "[getItineraryM1]" or
 * "[getItineraryM2]" line, so the two can be told apart in a transcript.
 * getItinerary calls both in turn, so a single run exercises each of them;
 * the test cases call it.
 *
 * Times are seconds after midnight of the service day and are NOT wrapped at
 * 24:00 -- an arrival of 88410 prints as 24:33:30. Write yourself a
 * hhmmss(t) helper alongside label(stationId).
 */
public class JourneyPlanner {

    static final String CONNECTIONS = "dataset/connections.csv";
    static final String STATIONS = "dataset/stations.csv";
    static final String QUERIES = "tests/queries.csv";
    static final int TRANSFER_SECONDS = 180;

    public JourneyPlanner() {
        // TODO: load and organise the data.
    }

    /** Task 3.1 -- stations exactly `hops` edges away, times ignored. */
    public void getStationsAtHops(String stationId, int hops) {
        // TODO: implement.
        // Output template (copy exactly):
        //   System.out.printf("Total number of stations exactly %d hop(s) from %s: %d\n",
        //                     hops, label(stationId), count);
    }

    /** Task 3.2, algorithm 1 of 2. */
    public void getItineraryM1(String fromStationId, String toStationId, int departAfter) {
        System.out.println("[getItineraryM1]"); // DO NOT remove this statement
        // TODO: implement.
        // Output templates (copy exactly):
        //   System.out.printf("Itinerary from %s to %s departing %s: %d leg(s), %d transfer(s), arriving %s.\n",
        //                     label(fromStationId), label(toStationId), hhmmss(departAfter),
        //                     legs.size(), legs.size() - 1, hhmmss(arrival));
        //   // then one line per leg, using that leg's own boarding and alighting stations
        //   System.out.printf("  Leg %d: route %s service %d depart %s %s arrive %s %s\n",
        //                     i, leg.route, leg.service, label(leg.from), hhmmss(leg.depart),
        //                     label(leg.to), hhmmss(leg.arrive));
        // Same origin and destination:
        //   System.out.printf("Already at %s at %s; no journey required.\n",
        //                     label(fromStationId), hhmmss(departAfter));
        // No journey exists:
        //   System.out.printf("No journey from %s to %s departing at or after %s.\n",
        //                     label(fromStationId), label(toStationId), hhmmss(departAfter));
    }

    /** Task 3.2, algorithm 2 of 2 -- a genuinely different algorithm, same templates. */
    public void getItineraryM2(String fromStationId, String toStationId, int departAfter) {
        System.out.println("[getItineraryM2]"); // DO NOT remove this statement
        // TODO: implement.
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

    /**
     * Runs the commands in the command file that belong to this task:
     *     java -cp bin JourneyPlanner             (reads tests/commands.txt)
     *     java -cp bin JourneyPlanner other.txt
     */
    public static void main(String[] args) {
        CommandRunner.run(CommandRunner.fileFrom(args), "JourneyPlanner");
    }
}
