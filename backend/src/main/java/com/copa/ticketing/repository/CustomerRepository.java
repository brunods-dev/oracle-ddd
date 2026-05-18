package com.copa.ticketing.repository;

import com.copa.ticketing.domain.Customer;
import com.copa.ticketing.util.DocumentNumbers;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

public class CustomerRepository {

    private final DataSource ds;

    public CustomerRepository(DataSource ds) {
        this.ds = ds;
    }

    public Optional<Customer> findByDocument(String documentType, String documentNumber) throws SQLException {
        documentNumber = DocumentNumbers.normalize(documentType, documentNumber);
        String sql = """
                SELECT id, full_name, email, document_type, document_number, phone
                FROM customers
                WHERE document_type = ? AND document_number = ?
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, documentType);
            ps.setString(2, documentNumber);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        }
        return Optional.empty();
    }

    public Optional<Customer> findByEmail(String email) throws SQLException {
        String sql = """
                SELECT id, full_name, email, document_type, document_number, phone
                FROM customers WHERE email = ?
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(map(rs));
        }
        return Optional.empty();
    }

    public Customer upsert(String fullName, String email, String documentType, String documentNumber, String phone) throws SQLException {
        documentNumber = DocumentNumbers.normalize(documentType, documentNumber);
        Optional<Customer> byDocument = findByDocument(documentType, documentNumber);
        if (byDocument.isPresent()) {
            return updateProfile(byDocument.get().id(), fullName, email, documentType, documentNumber, phone);
        }

        Optional<Customer> byEmail = findByEmail(email);
        if (byEmail.isPresent()) {
            Customer existing = byEmail.get();
            updateContact(existing.id(), fullName, phone);
            return new Customer(existing.id(), fullName, existing.email(),
                    existing.documentType(), existing.documentNumber(), phone);
        }

        String sql = """
                INSERT INTO customers (full_name, email, document_type, document_number, phone, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW(), NOW())
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, documentType);
            ps.setString(4, documentNumber);
            ps.setString(5, phone);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            long id = keys.getLong(1);
            return new Customer(id, fullName, email, documentType, documentNumber, phone);
        }
    }

    private Customer updateProfile(long id, String fullName, String email, String documentType,
                                   String documentNumber, String phone) throws SQLException {
        String sql = """
                UPDATE customers
                SET full_name = ?, email = ?, document_type = ?, document_number = ?,
                    phone = ?, updated_at = NOW()
                WHERE id = ?
                """;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, documentType);
            ps.setString(4, documentNumber);
            ps.setString(5, phone);
            ps.setLong(6, id);
            ps.executeUpdate();
        }
        return new Customer(id, fullName, email, documentType, documentNumber, phone);
    }

    private void updateContact(long id, String fullName, String phone) throws SQLException {
        String sql = "UPDATE customers SET full_name = ?, phone = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    private Customer map(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("document_type"),
                rs.getString("document_number"),
                rs.getString("phone")
        );
    }
}
