package org.example;

import org.example.models.ConnectDB;
import java.sql.*;

public class Main {
    public static void main(String[] args) {
        ConnectDB db = new ConnectDB();
        Connection connection = db.getConnection();

        if (connection != null) {
            try {
                Statement statement = connection.createStatement();

                // Buat tabel scores
                String createScoresTable = "CREATE TABLE IF NOT EXISTS scores (" +
                        "level TEXT PRIMARY KEY," +
                        "best_time INTEGER)";
                statement.executeUpdate(createScoresTable);

                System.out.println("Tabel 'scores' sudah disiapkan.");

                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                db.closeConnection();
            }
        } else {
            System.out.println("Connection failed.");
        }
    }
}
