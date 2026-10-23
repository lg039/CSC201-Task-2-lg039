/**
 * CSC201 Task 2 -- Task 1: Station Activity.
 *
 * Implement both subtasks in this class. You may add auxiliary classes,
 * but they must be invoked from here.
 *
 * Reminders from the task sheet:
 *  - Stations are printed as name followed by id in brackets, e.g.
 *    "Times Sq-42 St (127)". Write yourself a label(stationId) helper.
 *  - Any method handed a station id that is not in stations.csv prints
 *      System.out.printf("Unknown station '%s'.\n", stationId);
 *    and does nothing else.
 *  - Ranked lists print at most the first ten items; the count in the
 *    template line is still the full tier size.
 */
public class StationActivity {

    static final String CONNECTIONS = "dataset/connections.csv";
    static final String STATIONS = "dataset/stations.csv";

    public StationActivity() {
        // TODO: load the data you need.
    }

    /** Task 1.1 -- report the k-th busiest tier of stations by departures. */
    public void getStationRank(int k) {
        // TODO: implement.
        // Output templates (copy exactly):
        //   System.out.printf("Rank %d: %d station(s) with %d departure(s).\n", k, stations.size(), count);
        //   System.out.println("Station(s) include: " + stations.toString());
        // If the rank does not exist:
        //   System.out.printf("Rank %d does not exist.\n", k);
    }

    /** Task 1.2 -- report the busiest hour bucket at one station. */
    public void getBusiestHour(String stationId) {
        // TODO: implement.
        // Output template (copy exactly):
        //   System.out.printf("Busiest hour at %s is %02d:00-%02d:00 with %d departure(s).\n",
        //                     label(stationId), hour, hour + 1, count);
    }

    /**
     * Runs the commands in the command file that belong to this task:
     *     java -cp bin StationActivity            (reads tests/commands.txt)
     *     java -cp bin StationActivity other.txt
     */
    public static void main(String[] args) {
        CommandRunner.run(CommandRunner.fileFrom(args), "StationActivity");
    }
}
