package org.example.models;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectDB {
    private Connection connection;

    public ConnectDB() {
        // Tambahkan busy_timeout ke URL
        String url = "jdbc:sqlite:mydb.db?busy_timeout=5000";
        try {
            connection = DriverManager.getConnection(url);
            System.out.println("Connection Successful");

            // Set PRAGMA tambahan
            Statement stmt = connection.createStatement();
            stmt.execute("PRAGMA busy_timeout = 5000"); // tunggu max 5 detik kalau terkunci
            stmt.execute("PRAGMA foreign_keys = ON"); // opsional
            stmt.close();

        } catch (SQLException e) {
            System.out.println("Error Connecting to Database");
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null) {
                System.out.println("Connection Closed");
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
