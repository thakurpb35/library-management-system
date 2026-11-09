package library.ui;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A read-only table model that shows a list of objects, one column per getter. */
final class ListTableModel<T> extends AbstractTableModel {

    private final List<String> names = new ArrayList<>();
    private final List<Class<?>> types = new ArrayList<>();
    private final List<Function<T, Object>> getters = new ArrayList<>();
    private List<T> rows = List.of();

    ListTableModel<T> col(String name, Class<?> type, Function<T, Object> getter) {
        names.add(name);
        types.add(type);
        getters.add(getter);
        return this;
    }

    void setRows(List<T> newRows) {
        rows = List.copyOf(newRows);
        fireTableDataChanged();
    }

    T rowAt(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return names.size();
    }

    @Override
    public String getColumnName(int column) {
        return names.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return types.get(column);
    }

    @Override
    public Object getValueAt(int row, int column) {
        return getters.get(column).apply(rows.get(row));
    }
}
