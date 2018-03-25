import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestApp {

    private TestApp() {}

    public static void main(String[] args) {

        //String host = (args.length < 1) ? null : args[0]; // TODO always local host?
        try {
            Registry registry = LocateRegistry.getRegistry();
            RMITesting stub = (RMITesting) registry.lookup(args[0]);
            stub.remoteBackup("hello2", 1);
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}