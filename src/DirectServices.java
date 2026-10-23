/**
 * CSC201 Task 2 -- Task 2: Direct Services.
 *
 * A direct service from u to v exists when one service calls at u and later
 * calls at v: the passenger rides between them without transferring. See the
 * task sheet for the full definition before you start -- a single service
 * stopping at 30 stations creates a direct service for every ordered pair
 * among those stations, not just the adjacent ones.
 */
public class DirectServices {

    static final String CONNECTIONS = "dataset/connections.csv";
    static final String STATIONS = "dataset/stations.csv";

    public DirectServices() {
        // TODO: load and organise the data. Think about what enumerating
        // every pair costs in time and memory; report what you observe.
    }

    /** Task 2 -- direct services between one ordered pair of stations. */
    public void getDirectServices(String fromStationId, String toStationId) {
        // TODO: implement.
        // Output template (copy exactly):
        //   System.out.printf("Direct service %s -> %s: %d service(s) on route(s) %s, min %ds, max %ds.\n",
        //                     label(fromStationId), label(toStationId), count, routes, min, max);
        // Routes are listed in the order used by the routes column of stations.csv.
        // Where both stations exist but no single train runs between them:
        //   System.out.printf("No direct service from %s to %s.\n", label(fromStationId), label(toStationId));
    }

    /**
     * Runs the commands in the command file that belong to this task:
     *     java -cp bin DirectServices             (reads tests/commands.txt)
     *     java -cp bin DirectServices other.txt
     */
    public static void main(String[] args) {
        CommandRunner.run(CommandRunner.fileFrom(args), "DirectServices");
    }
}
