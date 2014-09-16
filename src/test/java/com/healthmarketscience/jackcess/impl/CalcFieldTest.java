/*
Copyright (c) 2014 James Ahlborn

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
USA
*/

package com.healthmarketscience.jackcess.impl;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.ColumnBuilder;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import static com.healthmarketscience.jackcess.DatabaseTest.*;
import com.healthmarketscience.jackcess.PropertyMap;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.TableBuilder;
import static com.healthmarketscience.jackcess.impl.JetFormatTest.*;
import junit.framework.TestCase;

/**
 *
 * @author James Ahlborn
 */
public class CalcFieldTest extends TestCase
{

  public CalcFieldTest(String name) throws Exception {
    super(name);
  }

  public void testCreateCalcField() throws Exception {

    ColumnBuilder cb = new ColumnBuilder("calc_data", DataType.TEXT)
      .setCalculatedInfo("[id] & \"_\" & [data]");

    try {
      cb.validate(JetFormat.VERSION_12);
      fail("IllegalArgumentException should have been thrown");
    } catch(IllegalArgumentException e) {
      assertTrue(e.getMessage().contains(""));
    }

    cb.validate(JetFormat.VERSION_14);

    for (final Database.FileFormat fileFormat : SUPPORTED_FILEFORMATS) {
      JetFormat format = DatabaseImpl.getFileFormatDetails(fileFormat).getFormat();
      if(!format.isSupportedCalculatedDataType(DataType.TEXT)) {
        continue;
      }

      Database db = create(fileFormat);

      Table t = new TableBuilder("Test")
        .putProperty("awesome_table", true)
        .addColumn(new ColumnBuilder("id", DataType.LONG)
                   .setAutoNumber(true))
        .addColumn(new ColumnBuilder("data", DataType.TEXT))
        .addColumn(new ColumnBuilder("calc_text", DataType.TEXT)
                   .setCalculatedInfo("[id] & \"_\" & [data]"))
        .addColumn(new ColumnBuilder("calc_memo", DataType.MEMO)
                   .setCalculatedInfo("[id] & \"_\" & [data]"))
        .addColumn(new ColumnBuilder("calc_bool", DataType.BOOLEAN)
                   .setCalculatedInfo("[id] > 0"))
        .addColumn(new ColumnBuilder("calc_long", DataType.LONG)
                   .setCalculatedInfo("[id] + 1"))
        .addColumn(new ColumnBuilder("calc_numeric", DataType.NUMERIC)
                   .setCalculatedInfo("[id] / 0.03"))
        .toTable(db);

      Column col = t.getColumn("calc_text");
      assertTrue(col.isCalculated());
      assertEquals("[id] & \"_\" & [data]", col.getProperties().getValue(
                       PropertyMap.EXPRESSION_PROP));
      assertEquals(DataType.TEXT.getValue(), 
                   col.getProperties().getValue(
                       PropertyMap.RESULT_TYPE_PROP));

      String longStr = createString(1000);
      BigDecimal bd1 = new BigDecimal("-1234.5678");
      BigDecimal bd2 = new BigDecimal("0.0234");

      t.addRow(Column.AUTO_NUMBER, "foo", "1_foo", longStr, true, 2, bd1);
      t.addRow(Column.AUTO_NUMBER, "bar", "2_bar", longStr, false, -37, bd2);

      List<? extends Map<String, Object>> expectedRows =
        createExpectedTable(
            createExpectedRow(
                "id", 1,
                "data", "foo",
                "calc_text", "1_foo",
                "calc_memo", longStr,
                "calc_bool", true,
                "calc_long", 2,
                "calc_numeric", bd1),
            createExpectedRow(
                "id", 2,
                "data", "bar",
                "calc_text", "2_bar",
                "calc_memo", longStr,
                "calc_bool", false,
                "calc_long", -37,
                "calc_numeric", bd2));

      assertTable(expectedRows, t);

      db.close();
    }
  }

  public void testReadCalcFields() throws Exception {

    for(TestDB testDB : TestDB.getSupportedForBasename(Basename.CALC_FIELD)) {
      Database db = open(testDB);
      Table t = db.getTable("Table1");

      List<String> rows = new ArrayList<String>();
      for(Row r : t) {
        rows.add(r.entrySet().toString());
      }

      List<String> expectedRows = Arrays.asList(
          "[ID=1, FirstName=Bruce, LastName=Wayne, LastFirst=Wayne, Bruce, City=Gotham, LastFirstLen=12, Salary=1000000.0000, MonthlySalary=83333.3333, IsRich=true, AllNames=Wayne, Bruce=Wayne, Bruce, WeeklySalary=19230.7692307692, SalaryTest=1000000.0000, BoolTest=true, Popularity=50.325000, DecimalTest=50.325000, FloatTest=2583.2092, BigNumTest=56505085819.424791296572280180]",
          "[ID=2, FirstName=Bart, LastName=Simpson, LastFirst=Simpson, Bart, City=Springfield, LastFirstLen=13, Salary=-1.0000, MonthlySalary=-0.0833, IsRich=false, AllNames=Simpson, Bart=Simpson, Bart, WeeklySalary=-0.0192307692307692, SalaryTest=-1.0000, BoolTest=true, Popularity=-36.222200, DecimalTest=-36.222200, FloatTest=0.0035889593, BigNumTest=-0.0784734499180612994241100748]",
          "[ID=3, FirstName=John, LastName=Doe, LastFirst=Doe, John, City=Nowhere, LastFirstLen=9, Salary=0.0000, MonthlySalary=0.0000, IsRich=false, AllNames=Doe, John=Doe, John, WeeklySalary=0, SalaryTest=0.0000, BoolTest=true, Popularity=0.012300, DecimalTest=0.012300, FloatTest=0.0, BigNumTest=0E-8]",
          "[ID=4, FirstName=Test, LastName=User, LastFirst=User, Test, City=Hockessin, LastFirstLen=10, Salary=100.0000, MonthlySalary=8.3333, IsRich=false, AllNames=User, Test=User, Test, WeeklySalary=1.92307692307692, SalaryTest=100.0000, BoolTest=true, Popularity=102030405060.654321, DecimalTest=102030405060.654321, FloatTest=1.27413E-10, BigNumTest=2.787019289824216980830E-7]");

      assertEquals(expectedRows, rows);

      db.close();
    }    
  }

}
