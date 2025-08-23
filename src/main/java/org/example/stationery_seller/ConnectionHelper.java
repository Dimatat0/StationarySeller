package org.example.stationery_seller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionHelper {
    private static ConnectionHelper instance;

    private final String url;
    private final String user;
    private final String password;

    private ConnectionHelper(String url, String user, String password){
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static synchronized void init(String url, String user, String password) {
        if (instance == null) {
            instance = new ConnectionHelper(url, user, password);
        }
    }

    public static ConnectionHelper getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConnectionHelper is not initialized. Call init() first.");
        }
        return instance;
    }

    public Connection getConnection() throws SQLException{
            return DriverManager.getConnection(url, user, password);
    }


}
