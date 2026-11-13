package com.nova.bank.config;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import java.sql.*;
/** Provision the named database before constructing the pool consumed by Flyway. */
@Configuration
public class DatabaseConfig {
 /** Create the database outside a transaction, tolerating a concurrent creator. */
 @Bean public DataSource dataSource(Environment env) throws SQLException {
  String base = env.getRequiredProperty("spring.datasource.url");
  String name = env.getRequiredProperty("nova.db-name");
  if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,62}")) throw new IllegalArgumentException("DB_NAME must be a safe PostgreSQL identifier");
  String user = env.getRequiredProperty("spring.datasource.username"), password = env.getRequiredProperty("spring.datasource.password");
  try (Connection c = DriverManager.getConnection(base, user, password)) {
   c.setAutoCommit(true);
   try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
    q.setString(1, name);
    try (ResultSet rows = q.executeQuery()) {
     if (!rows.next()) try (Statement s = c.createStatement()) { s.executeUpdate("CREATE DATABASE " + (char)34 + name + (char)34); }
     catch (SQLException e) { if (!"42P04".equals(e.getSQLState())) throw e; }
    }
   }
  }
  int query = base.indexOf('?'); String suffix = query < 0 ? "" : base.substring(query);
  String stem = query < 0 ? base : base.substring(0, query);
  HikariDataSource pool = new HikariDataSource();
  pool.setJdbcUrl(stem.substring(0, stem.lastIndexOf('/') + 1) + name + suffix);
  pool.setUsername(user); pool.setPassword(password); pool.setMaximumPoolSize(10); pool.setMinimumIdle(1);
  return pool;
 }
}
