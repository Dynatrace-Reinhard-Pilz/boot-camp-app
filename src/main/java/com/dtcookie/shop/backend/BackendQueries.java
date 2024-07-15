package com.dtcookie.shop.backend;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.dtcookie.database.Database;

public class BackendQueries {

    public static void execute(String sql) throws SQLException, InterruptedException {
        long start = System.currentTimeMillis();
        try (Connection con = Database.getConnection(4, TimeUnit.SECONDS)) {	        	
            Thread.sleep(System.currentTimeMillis() - start);	            
            Objects.requireNonNull(con);
            try (Statement stmt = con.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    public static void executeUpdate(String sql) throws SQLException, InterruptedException {
        long start = System.currentTimeMillis();
        try (Connection con = Database.getConnection(4, TimeUnit.SECONDS)) {
        	Objects.requireNonNull(con);
            Thread.sleep(System.currentTimeMillis() - start);
            try (Statement stmt = con.createStatement()) {
                stmt.executeUpdate(sql);
            }
        }
    }
}
