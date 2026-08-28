/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.didvc.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC audit-log store (PostgreSQL-compatible; H2 for tests). Append-only:
 * the table grants no update/delete in production, and the hash chain
 * detects any mutation regardless.
 */
public class JdbcAuditLogStore implements AuditLogStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcAuditLogStore.class);

    private static final String DDL = "CREATE TABLE IF NOT EXISTS didvc_audit_log ("
            + "seq BIGINT PRIMARY KEY,"
            + "prev_hash VARCHAR(64) NOT NULL,"
            + "event_type VARCHAR(128) NOT NULL,"
            + "actor VARCHAR(255),"
            + "subject_ref VARCHAR(512),"
            + "payload TEXT,"
            + "created_at BIGINT NOT NULL,"
            + "hash VARCHAR(64) NOT NULL)";

    private final DataSource dataSource;

    public JdbcAuditLogStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates the audit table if it does not exist.
     */
    public void init() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(DDL);
        }
    }

    @Override
    public long nextSeq() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(seq), 0) + 1 FROM didvc_audit_log")) {
            return rs.next() ? rs.getLong(1) : 1L;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read audit log sequence", e);
        }
    }

    @Override
    public AuditRecord readLast() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM didvc_audit_log ORDER BY seq DESC LIMIT 1")) {
            return rs.next() ? map(rs) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read audit log", e);
        }
    }

    @Override
    public void persist(AuditRecord record) {
        String sql = "INSERT INTO didvc_audit_log "
                + "(seq, prev_hash, event_type, actor, subject_ref, payload, created_at, hash) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, record.getSeq());
            statement.setString(2, record.getPrevHash());
            statement.setString(3, record.getEventType());
            statement.setString(4, record.getActor());
            statement.setString(5, record.getSubjectRef());
            statement.setString(6, record.getPayload());
            statement.setLong(7, record.getCreatedAt());
            statement.setString(8, record.getHash());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warn("Failed to persist audit record {}", record.getSeq(), e);
            throw new IllegalStateException("Failed to persist audit record " + record.getSeq(), e);
        }
    }

    @Override
    public List<AuditRecord> readAll() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM didvc_audit_log ORDER BY seq")) {
            List<AuditRecord> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read audit log", e);
        }
    }

    private AuditRecord map(ResultSet rs) throws SQLException {
        AuditRecord record = new AuditRecord();
        record.setSeq(rs.getLong("seq"));
        record.setPrevHash(rs.getString("prev_hash"));
        record.setEventType(rs.getString("event_type"));
        record.setActor(rs.getString("actor"));
        record.setSubjectRef(rs.getString("subject_ref"));
        record.setPayload(rs.getString("payload"));
        record.setCreatedAt(rs.getLong("created_at"));
        record.setHash(rs.getString("hash"));
        return record;
    }
}
