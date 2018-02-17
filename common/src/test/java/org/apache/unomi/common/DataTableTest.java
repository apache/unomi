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

import org.junit.Test;
import static org.junit.Assert.*;

public class DataTableTest {

    @Test
    public void testTableSorting() {
        DataTable dataTable = new DataTable();

        dataTable.addRow("Row1", 1, 2, 1);
        dataTable.addRow("Row2", 3, 2, 2);
        dataTable.addRow("Row3", 2, 1, 1);

        dataTable.sort(new DataTable.SortCriteria(0, DataTable.SortOrder.ASCENDING));
        assertEquals("Row 1 should be first", "Row1", dataTable.getRows().get(0).getData(0));
        assertEquals("Row 2 should be second", "Row2", dataTable.getRows().get(1).getData(0));
        assertEquals("Row 3 should be third", "Row3", dataTable.getRows().get(2).getData(0));

        dataTable.sort(new DataTable.SortCriteria(1, DataTable.SortOrder.ASCENDING));
        assertEquals("Row 1 should be first", "Row1", dataTable.getRows().get(0).getData(0));
        assertEquals("Row 3 should be second", "Row3", dataTable.getRows().get(1).getData(0));
        assertEquals("Row 2 should be third", "Row2", dataTable.getRows().get(2).getData(0));

        dataTable.sort(new DataTable.SortCriteria(2, DataTable.SortOrder.ASCENDING),
                new DataTable.SortCriteria(3, DataTable.SortOrder.DESCENDING));
        assertEquals("Row 3 should be first", "Row3", dataTable.getRows().get(0).getData(0));
        assertEquals("Row 2 should be second", "Row2", dataTable.getRows().get(1).getData(0));
        assertEquals("Row 1 should be third", "Row1", dataTable.getRows().get(2).getData(0));

        System.out.println("CSV version of data table:");
        System.out.println(dataTable.toCSV("Row name", "\"Value\", 1", "Value 2", "Value 3"));
    }

    @Test
    public void testNonSquareTable() {
        DataTable dataTable = new DataTable();
        dataTable.addRow("Row1", 1, 2, 1, "Value");
        dataTable.addRow("Row2", 3, 2, 2);

        Comparable cellData = dataTable.getRows().get(1).getData(4);
        assertEquals("Excepted cell data is empty cell", DataTable.EMPTY_CELL, cellData);

        dataTable.sort(new DataTable.SortCriteria(4, DataTable.SortOrder.ASCENDING));
        assertEquals("Row 2 should be first", "Row2", dataTable.getRows().get(0).getData(0));

        dataTable.sort(new DataTable.SortCriteria(4, DataTable.SortOrder.DESCENDING));
        assertEquals("Row 1 should be first", "Row1", dataTable.getRows().get(0).getData(0));
    }
}
