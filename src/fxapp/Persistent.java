package fxapp;

import javax.xml.crypto.Data;
import java.lang.reflect.ParameterizedType;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Persistent<M> {
    private String tableName;
    private List<DataColumn> columns;
    private DatabaseManager dbManager;
    private Reviver<M> reviver;
    private Class<M> type;

    public Persistent(Class<M> type, DatabaseManager dbManager, String tableName, Reviver<M> reviver) {
        this.type = type;
        this.tableName = tableName;
        columns = new LinkedList<>();
        this.dbManager = dbManager;
        this.reviver = reviver;
    }

    public void addColumn(String schema, Function<M, ?> property) {
        columns.add(new DataColumn(schema, property));
    }

    public void init() {
        try(Connection conn = dbManager.getConnection()) {
            PreparedStatement checkTableExists
                    = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=(?);");
            checkTableExists.setString(1, tableName);
            ResultSet rs = checkTableExists.executeQuery();
            if (!rs.next()) {
                conn.createStatement().executeUpdate(
                        "create table " + tableName + "(" + getSQLStrings((DataColumn col) -> col.schema) + ");");
            }
            checkTableExists.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Class<M> getType() {
        return type;
    }

    private String getSQLStrings(Function<DataColumn, String> mapper) {
        return String.join(", ", columns.stream()
                    .map(mapper)
                    .collect(Collectors.toList()));
    }

    public void store(M model) throws SQLException {
        try (Connection conn = dbManager.getConnection()) {

            try (PreparedStatement prep
                         = conn.prepareStatement("insert into users values ("
                    + getSQLStrings((DataColumn col) -> "?") + ")")) {
                int index = 1;
                for (DataColumn column : columns) {
                    prep.setObject(index, column.property.apply(model));
                    index++;
                }
                prep.addBatch();
                prep.executeBatch();
            }
        }
    }

    public List<M> retrieve(String columnName, Object data) throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            PreparedStatement prep = conn.prepareStatement("select * from " + tableName + " WHERE " + columnName + "=(?)");
            prep.setObject(1, data);
            ResultSet model = prep.executeQuery();
            List<M> resultant = new LinkedList<>();
            while(model.next()) {
                resultant.add(reviver.make(model));
            }
            return resultant;
        }
    }

    public M retrieveOne(String columnName, Object data) throws SQLException {
        List<M> all = retrieve(columnName, data);
        if (all.size() == 0) {
            return null;
        } else {
            return all.get(0);
        }
    }

    public class DataColumn {
        private String schema;

        private Function<? super M, ?> property;
        public DataColumn(String schema, Function<? super M, ?> property) {
            this.schema = schema;
            this.property = property;
        }
    }

    @FunctionalInterface
    public interface Reviver<N> {
        N make(ResultSet modelRow) throws SQLException;
    }
}