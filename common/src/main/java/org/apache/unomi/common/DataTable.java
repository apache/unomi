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
package org.apache.unomi.common;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An in memory table structure for storing data and performing operations such as sorting it, or generating JSON or
 * CSV outputs.
 */
public class DataTable {

    List<Row> rows = new ArrayList<>();
    int maxColumns = 0;

    public static final EmptyCell EMPTY_CELL = new EmptyCell();

    /**
     * Creates an empty data table.
     */
    public DataTable() {
    }

    /**
     * Returns the rows stored in this table.
     *
     * @return the table rows
     */
    public List<Row> getRows() {
        return rows;
    }

    /**
     * Appends a row built from the given cell values.
     *
     * @param rowData cell values for the new row
     */
    public void addRow(Comparable... rowData) {
        if (rowData == null) {
            return;
        }
        if (rowData.length > maxColumns) {
            maxColumns = rowData.length;
        }
        Row row = new Row();
        for (Comparable dataObject : rowData) {
            row.addData(dataObject);
        }
        rows.add(row);
    }

    /**
     * Returns the maximum number of columns across all rows.
     *
     * @return the maximum column count
     */
    public int getMaxColumns() {
        return maxColumns;
    }

    /**
     * Sort order used when comparing row values.
     */
    public static enum SortOrder {
        ASCENDING,
        DESCENDING;
    }

    /**
     * Defines a column index and sort order for table sorting.
     */
    public static class SortCriteria {
        Integer columnIndex;
        SortOrder sortOrder;

        /**
         * Creates a sort criterion for one column.
         *
         * @param columnIndex zero-based column index
         * @param sortOrder sort direction for the column
         */
        public SortCriteria(Integer columnIndex, SortOrder sortOrder) {
            this.columnIndex = columnIndex;
            this.sortOrder = sortOrder;
        }
    }

    /**
     * One row of comparable cell values in a {@link DataTable}.
     */
    public class Row {
        List<Comparable> rowData = new ArrayList<>();

        /**
         * Appends a cell value to this row.
         *
         * @param data the cell value to add
         */
        public void addData(Comparable data) {
            rowData.add(data);
        }

        /**
         * Returns the cell value at the given index.
         *
         * @param index zero-based column index
         * @return the cell value, or {@link #EMPTY_CELL} when the row has no value at that index
         */
        public Comparable getData(int index) {
            if (index >= maxColumns) {
                throw new ArrayIndexOutOfBoundsException("Index on row data (" + index + ") is larger than max columns (" + maxColumns + ")");
            }
            if (index >= rowData.size()) {
                return EMPTY_CELL;
            }
            return rowData.get(index);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("[");
            sb.append(rowData);
            sb.append(']');
            return sb.toString();
        }
    }

    /**
     * Sorts table rows using the given criteria in order.
     *
     * @param sortCriterias column sort criteria applied left to right
     */
    public void sort(SortCriteria... sortCriterias) {
        rows.sort(new Comparator<Row>() {
            @Override
            public int compare(Row row1, Row row2) {
                int i = 0;
                while (i < sortCriterias.length) {
                    Comparable row1Data = row1.getData(sortCriterias[i].columnIndex);
                    Comparable row2Data = row2.getData(sortCriterias[i].columnIndex);
                    if (row1Data == EMPTY_CELL && row2Data != EMPTY_CELL) {
                        if (sortCriterias[i].sortOrder == SortOrder.ASCENDING) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                    if (row2Data == EMPTY_CELL && row1Data != EMPTY_CELL) {
                        if (sortCriterias[i].sortOrder == SortOrder.ASCENDING) {
                            return 1;
                        } else {
                            return -1;
                        }
                    }
                    int rowComparison = row1Data.compareTo(row2Data);
                    if (rowComparison == 0) {
                        // rows are equal on this criteria, let's go to the next criteria if it exists
                        if (i < sortCriterias.length) {
                            i++;
                        } else {
                            return 0;
                        }
                    } else {
                        if (sortCriterias[i].sortOrder == SortOrder.ASCENDING) {
                            return rowComparison;
                        } else {
                            return -rowComparison;
                        }
                    }
                }
                return 0;
            }
        });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[");
        sb.append(rows);
        sb.append(']');
        return sb.toString();
    }

    /**
     * Placeholder value used for missing cells when sorting or exporting.
     */
    public static class EmptyCell implements Comparable {
        @Override
        public int compareTo(Object o) {
            if (o instanceof EmptyCell) {
                return 0;
            }
            return -1;
        }

        @Override
        public String toString() {
            return "";
        }
    }

    /**
     * Serializes the table to CSV text.
     *
     * @param headers optional CSV header row
     * @return CSV representation of the table
     */
    public String toCSV(String... headers) {
        StringBuilder stringBuilder = new StringBuilder();
        CSVFormat csvFormat = CSVFormat.DEFAULT;
        if (headers != null && headers.length > 0) {
            csvFormat = CSVFormat.DEFAULT.withHeader(headers);
        }
        try {
            CSVPrinter printer = csvFormat.print(stringBuilder);
            for (Row row : rows) {
                List<String> values = new ArrayList<>();
                for (int i = 0; i < maxColumns; i++) {
                    values.add(row.getData(i).toString());
                }
                printer.printRecord(values.toArray(new String[values.size()]));
            }
            printer.close();
        } catch (IOException e) {
            e.printStackTrace(); // this will probably never happen as we are writing to a String.
        }
        return stringBuilder.toString();
    }

}
