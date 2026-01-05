package medicareRawand;

import java.io.*;
import java.net.*;
import java.sql.*;

public class BillingServer {
    private static final int PORT = 1527;
    
    
    private static final String DB_URL = "jdbc:mysql://localhost:1527/medicare_db";
    private static final String DB_USER = "adminReem";
    private static final String DB_PASS = "admin";

    public static void main(String[] args) {
        System.out.println("___________________________________________");
        System.out.println("   MEDICARE OMAN - BILLING SERVER v2.6    ");
        System.out.println("____________________________________________");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[STATUS] Server online. Listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[INFO] Incoming request from: " + clientSocket.getInetAddress());
                
                
                new Thread(new BillingTask(clientSocket, DB_URL, DB_USER, DB_PASS)).start();
            }
        } catch (IOException e) {
            System.err.println("[CRITICAL] Server failure: " + e.getMessage());
        }
    }
}


class BillingTask implements Runnable {
    private final Socket socket;
    private final String dbUrl, dbUser, dbPass;

    public BillingTask(Socket socket, String dbUrl, String dbUser, String dbPass) {
        this.socket = socket;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPass = dbPass;
    }

    @Override
    public void run() {
        try (
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
            socket 
                
        ) {
            
            int pId = in.readInt();
            String visitDate = in.readUTF();
            String pType = in.readUTF();
            String serviceCode = in.readUTF();

            
            String sql = "SELECT name, age, insurance_plan FROM Patient WHERE PID = ?";
            try (PreparedStatement fetchStmt = conn.prepareStatement(sql)) {
                fetchStmt.setInt(1, pId);
                ResultSet rs = fetchStmt.executeQuery();

                if (rs.next()) {
                    out.writeBoolean(true); 

                    String name = rs.getString("name");
                    int age = rs.getInt("age");
                    String plan = rs.getString("insurance_plan");

                    
                    double finalTotal = calculateBill(serviceCode, plan, pType);

                    
                    saveBill(conn, pId, visitDate, finalTotal);

                    
                    out.writeUTF(name);
                    out.writeInt(age);
                    out.writeUTF(plan);
                    out.writeDouble(finalTotal);
                    out.flush();

                    System.out.printf("[SUCCESS] Processed PID %d | Total: %.2f OMR%n", pId, finalTotal);
                } else {
                    out.writeBoolean(false); 
                    
                    System.out.println("[DENIED] PID " + pId + " not found in system.");
                }
            }
        } catch (Exception e) {
            System.err.println("[TASK ERROR] Interaction failed: " + e.getMessage());
        }
    }

    private double calculateBill(String code, String plan, String type) {
        double base = getServiceFee(code);
        double planPct = getPlanDiscountPct(plan);
        double fixedDisc = getFixedLoyaltyDiscount(plan);
        double surchargePct = getSurchargeRate(type);

        
        double afterPlanDisc = base * (1 - (planPct / 100));
        double afterFixed = Math.max(0, afterPlanDisc - fixedDisc);
        double surchargeAmt = afterFixed * (surchargePct / 100);
        
        return afterFixed + surchargeAmt;
    }

    private void saveBill(Connection conn, int pid, String date, double amount) throws SQLException {
        String logQuery = "INSERT INTO Bill (PID, date, amount) VALUES (?, ?, ?)";
        try (PreparedStatement logStmt = conn.prepareStatement(logQuery)) {
            logStmt.setInt(1, pid);
            logStmt.setDate(2, Date.valueOf(date));
            logStmt.setDouble(3, amount);
            logStmt.executeUpdate();
        }
    }

    // --- Pricing Logic Methods ---

    private double getServiceFee(String code) {
        return switch (code.toUpperCase()) {
            case "CONS100" -> 12.0;
            case "LAB210"  -> 8.5;
            case "IMG330"  -> 25.0;
            case "US400"   -> 35.0;
            case "MRI700"  -> 180.0;
            default -> 0.0;
        };
    }

    private double getPlanDiscountPct(String plan) {
        return switch (plan.toLowerCase()) {
            case "premium"  -> 15.0;
            case "standard" -> 10.0;
            default -> 0.0;
        };
    }

    private double getFixedLoyaltyDiscount(String plan) {
        return switch (plan.toLowerCase()) {
            case "premium"  -> 5.0;
            case "standard" -> 8.0;
            case "basic"    -> 10.0;
            default -> 0.0;
        };
    }

    private double getSurchargeRate(String type) {
        return switch (type.toLowerCase()) {
            case "inpatient" -> 5.0;
            case "emergency" -> 15.0;
            default -> 0.0;
        };
    }
}

