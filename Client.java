package medicareRawand;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.function.Function;


public class MedicareClient {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1527;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        printWelcomeBanner();

        
        int id = promptForInput(scanner, "Enter Patient ID: ", 
                BillingValidator::isIdValid, Integer::parseInt);
        
        String date = promptForInput(scanner, "Enter Date (YYYY-MM-DD): ", 
                BillingValidator::isDateValid, s -> s);
        
        String type = promptForInput(scanner, "Enter Visit Type (Inpatient/Outpatient/Emergency): ", 
                BillingValidator::isTypeValid, s -> s);

        System.out.println("\n[CATALOG] CONS100 (Consultation), LAB210 (Lab), IMG330 (Imaging), US400 (Ultrasound), MRI700 (MRI)");
        String service = promptForInput(scanner, "Enter Service Code: ", 
                BillingValidator::isServiceValid, String::toUpperCase);

        
        BillingService serviceConnector = new BillingService(SERVER_HOST, SERVER_PORT);
        serviceConnector.submitRequest(id, date, type, service);
        
        scanner.close();
    }

    private static void printWelcomeBanner() {
        
        System.out.println("$$$    MEDICARE OMAN - BILLING INTERFACE v3.0      $$$");
        
    }

    private static <T> T promptForInput(Scanner sc, String msg, Predicate<String> validator, Function<String, T> transformer) {
        while (true) {
            System.out.print(msg);
            String input = sc.nextLine().trim();
            if (validator.test(input)) {
                return transformer.apply(input);
            }
            System.out.println(" [!] Invalid input format. Please try again.");
        }
    }
}


class BillingService {
    private final String host;
    private final int port;

    public BillingService(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void submitRequest(int id, String date, String type, String code) {
        System.out.println("\n[SYSTEM] Connecting to secure gateway...");

        try (Socket socket = new Socket(host, port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {


            out.writeInt(id);
            out.writeUTF(date);
            out.writeUTF(type);
            out.writeUTF(code);
            out.flush();


            if (in.readBoolean()) {
                displayInvoice(in, id, date, type, code);
            } else {
                System.err.println("\n[ERROR] Patient Record #" + id + " not found in the database.");
            }

        } catch (ConnectException e) {
            System.err.println("[OFFLINE] Server unreachable. Ensure the Billing Server is running.");
        } catch (IOException e) {
            System.err.println("[FATAL] Communication error: " + e.getMessage());
        }
    }

    private void displayInvoice(DataInputStream in, int id, String date, String type, String code) throws IOException {
        String name = in.readUTF();
        int age = in.readInt();
        String plan = in.readUTF();
        double base = in.readDouble();
        double insDisc = in.readDouble();
        double loyDisc = in.readDouble();
        double tax = in.readDouble();
        double total = in.readDouble();

        System.out.println("\n" + "=".repeat(48));
        System.out.println("            OMAN MEDICAL INVOICE             ");
        System.out.println("=".repeat(48));
        System.out.printf(" Patient: %-20s  PID: %d\n", name, id);
        System.out.printf(" Age:     %-20d  Plan: %s\n", age, plan);
        System.out.printf(" Type:    %-20s  Date: %s\n", type, date);
        System.out.println("-".repeat(48));
        System.out.printf(" Service Provided:       %s\n", code);
        System.out.printf(" Base Fee:               %10.2f OMR\n", base);
        System.out.printf(" Insurance Coverage:    -%10.2f OMR\n", insDisc);
        System.out.printf(" Loyalty Discount:      -%10.2f OMR\n", loyDisc);
        System.out.printf(" Healthcare Surcharge:  +%10.2f OMR\n", tax);
        System.out.println("-".repeat(48));
        System.out.printf(" TOTAL AMOUNT DUE:       %10.2f OMR\n", total);
        System.out.println("=".repeat(48));
        System.out.println("[SUCCESS] Transaction processed successfully.");
    }
}


class BillingValidator {
    public static boolean isIdValid(String id) {
        return id.matches("\\d+") && Integer.parseInt(id) > 0;
    }

    public static boolean isDateValid(String date) {
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    public static boolean isTypeValid(String type) {
        String t = type.toLowerCase();
        return t.equals("inpatient") || t.equals("outpatient") || t.equals("emergency");
    }

    public static boolean isServiceValid(String code) {
        return code.toUpperCase().matches("CONS100|LAB210|IMG330|US400|MRI700");
    }
}
