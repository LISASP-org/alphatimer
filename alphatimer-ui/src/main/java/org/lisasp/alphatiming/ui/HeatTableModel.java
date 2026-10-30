package org.lisasp.alphatiming.ui;

import org.lisasp.alphatiming.ui.common.HeatRows;
import org.lisasp.alphatiming.ui.common.I18n;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class HeatTableModel extends AbstractTableModel {

    private List<HeatRows.Row> rows = List.of();

    void setRows(List<HeatRows.Row> rows) {
        this.rows = rows;
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> I18n.get("heats.start");
            case 1 -> I18n.get("heats.finish");
            default -> I18n.get("heats.heat");
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        HeatRows.Row row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.start();
            case 1 -> row.finish();
            default -> row.heat();
        };
    }
}
