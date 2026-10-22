/*******************************************************************************
 * The contents of this file are subject to the Common Public Attribution License 
 * Version 1.0 (the "License"); you may not use this file except in compliance with 
 * the License. You may obtain a copy of the License at 
 * http://www.projectlibre.com/license . The License is based on the Mozilla Public 
 * License Version 1.1 but Sections 14 and 15 have been added to cover use of 
 * software over a computer network and provide for limited attribution for the 
 * Original Developer. In addition, Exhibit A has been modified to be consistent 
 * with Exhibit B. 
 *
 * Software distributed under the License is distributed on an "AS IS" basis, 
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for the 
 * specific language governing rights and limitations under the License. The 
 * Original Code is ProjectLibre. The Original Developer is the Initial Developer 
 * and is ProjectLibre Inc. All portions of the code written by ProjectLibre are 
 * Copyright (c) 2012-2019. All Rights Reserved. All portions of the code written by 
 * ProjectLibre are Copyright (c) 2012-2019. All Rights Reserved. Contributor 
 * ProjectLibre, Inc.
 *
 * Alternatively, the contents of this file may be used under the terms of the 
 * ProjectLibre End-User License Agreement (the ProjectLibre License) in which case 
 * the provisions of the ProjectLibre License are applicable instead of those above. 
 * If you wish to allow use of your version of this file only under the terms of the 
 * ProjectLibre License and not to allow others to use your version of this file 
 * under the CPAL, indicate your decision by deleting the provisions above and 
 * replace them with the notice and other provisions required by the ProjectLibre 
 * License. If you do not delete the provisions above, a recipient may use your 
 * version of this file under either the CPAL or the ProjectLibre Licenses. 
 *
 *
 * [NOTE: The text of this Exhibit A may differ slightly from the text of the notices 
 * in the Source Code files of the Original Code. You should use the text of this 
 * Exhibit A rather than the text found in the Original Code Source Code for Your 
 * Modifications.] 
 *
 * EXHIBIT B. Attribution Information for ProjectLibre required
 *
 * Attribution Copyright Notice: Copyright (c) 2012-2019, ProjectLibre, Inc.
 * Attribution Phrase (not exceeding 10 words): 
 * ProjectLibre, open source project management software.
 * Attribution URL: http://www.projectlibre.com
 * Graphic Image as provided in the Covered Code as file: projectlibre-logo.png with 
 * alternatives listed on http://www.projectlibre.com/logo 
 *
 * Display of Attribution Information is required in Larger Works which are defined 
 * in the CPAL as a work which combines Covered Code or portions thereof with code 
 * not governed by the terms of the CPAL. However, in addition to the other notice 
 * obligations, all copies of the Covered Code in Executable and Source Code form 
 * distributed must, as a form of attribution of the original author, include on 
 * each user interface screen the "ProjectLibre" logo visible to all users. 
 * The ProjectLibre logo should be located horizontally aligned with the menu bar 
 * and left justified on the top left of the screen adjacent to the File menu. The 
 * logo must be at least 144 x 31 pixels. When users click on the "ProjectLibre" 
 * logo it must direct them back to http://www.projectlibre.com. 
 *******************************************************************************/
package com.projectlibre1.exchange.wbsexcel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.projectlibre1.exchange.ulnl.XlsxSheetReader;

/**
 * Minimal writer of a one sheet .xlsx (Office Open XML), counterpart of {@link XlsxSheetReader}.
 * All the cells are inline strings with the Text number format, so that what is typed in Excel
 * stays text (a typed date is not turned into a serial number).
 * Rows are added in order, then {@link #write(File)} writes the file.
 */
public class XlsxSheetWriter {
	public static final int MAX_INDENT = 15;

	// cellXfs: 0 default, 1 header, then editable / read only cells for each indent
	private static final int STYLE_HEADER = 1;
	private static final int STYLE_FIRST_CELL = 2;

	private final String sheetName;
	private final List<Row> rows = new ArrayList<Row>();
	private double[] columnWidths = new double[0];
	private int frozenRows;
	private int autoFilterRow; // 1 based, 0 for none

	private static class Row {
		final String[] values;
		final int[] styles;
		final boolean hidden;
		Row(String[] values, int[] styles, boolean hidden) {
			this.values = values;
			this.styles = styles;
			this.hidden = hidden;
		}
	}

	public XlsxSheetWriter(String sheetName) {
		this.sheetName = sheetName;
	}

	/** Style of a data cell */
	public static int cellStyle(boolean readOnly, int indent) {
		indent = Math.max(0, Math.min(MAX_INDENT, indent));
		return STYLE_FIRST_CELL + 2 * indent + (readOnly ? 1 : 0);
	}

	public void addHiddenRow(String[] values) {
		rows.add(new Row(values, null, true));
	}

	public void addHeaderRow(String[] values) {
		int[] styles = new int[values.length];
		Arrays.fill(styles, STYLE_HEADER);
		rows.add(new Row(values, styles, false));
	}

	/** @param styles from {@link #cellStyle(boolean, int)} */
	public void addRow(String[] values, int[] styles) {
		rows.add(new Row(values, styles, false));
	}

	public int getRowCount() {
		return rows.size();
	}

	public void setColumnWidths(double[] columnWidths) {
		this.columnWidths = columnWidths;
	}

	/** Rows kept visible at the top when scrolling */
	public void setFrozenRows(int frozenRows) {
		this.frozenRows = frozenRows;
	}

	/** @param row 1 based header row of the filter */
	public void setAutoFilterRow(int row) {
		this.autoFilterRow = row;
	}

	public void write(File file) throws IOException {
		ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file));
		try {
			Writer w = new BufferedWriter(new OutputStreamWriter(zip, "UTF-8")); //$NON-NLS-1$
			entry(zip, w, "[Content_Types].xml", //$NON-NLS-1$
				"<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" //$NON-NLS-1$
				+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" //$NON-NLS-1$
				+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" //$NON-NLS-1$
				+ "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" //$NON-NLS-1$
				+ "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" //$NON-NLS-1$
				+ "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" //$NON-NLS-1$
				+ "</Types>"); //$NON-NLS-1$
			entry(zip, w, "_rels/.rels", //$NON-NLS-1$
				"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" //$NON-NLS-1$
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" //$NON-NLS-1$
				+ "</Relationships>"); //$NON-NLS-1$
			entry(zip, w, "xl/_rels/workbook.xml.rels", //$NON-NLS-1$
				"<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" //$NON-NLS-1$
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" //$NON-NLS-1$
				+ "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" //$NON-NLS-1$
				+ "</Relationships>"); //$NON-NLS-1$
			entry(zip, w, "xl/workbook.xml", workbookXml()); //$NON-NLS-1$
			entry(zip, w, "xl/styles.xml", stylesXml()); //$NON-NLS-1$
			zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml")); //$NON-NLS-1$
			writeSheet(w);
			w.flush();
			zip.closeEntry();
			zip.finish();
		} finally {
			zip.close();
		}
	}

	private static void entry(ZipOutputStream zip, Writer w, String path, String xml) throws IOException {
		zip.putNextEntry(new ZipEntry(path));
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"); //$NON-NLS-1$
		w.write(xml);
		w.flush();
		zip.closeEntry();
	}

	private int columnCount() {
		int count = columnWidths.length;
		for (Row row : rows)
			count = Math.max(count, row.values.length);
		return Math.max(1, count);
	}

	private String filterRange() {
		String last = XlsxSheetReader.columnName(columnCount() - 1);
		return "$A$" + autoFilterRow + ":$" + last + "$" + Math.max(autoFilterRow, rows.size()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private String workbookXml() {
		StringBuilder b = new StringBuilder();
		b.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"); //$NON-NLS-1$
		b.append("<sheets><sheet name=\"").append(escape(sheetName)).append("\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"); //$NON-NLS-1$ //$NON-NLS-2$
		if (autoFilterRow > 0)
			b.append("<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">'") //$NON-NLS-1$
				.append(escape(sheetName.replace("'", "''"))).append("'!").append(filterRange()).append("</definedName></definedNames>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		b.append("</workbook>"); //$NON-NLS-1$
		return b.toString();
	}

	private static String stylesXml() {
		StringBuilder b = new StringBuilder();
		b.append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"); //$NON-NLS-1$
		b.append("<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"); //$NON-NLS-1$
		b.append("<fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"); //$NON-NLS-1$
		b.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDDEBF7\"/><bgColor indexed=\"64\"/></patternFill></fill>"); // header //$NON-NLS-1$
		b.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9D9D9\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"); // read only //$NON-NLS-1$
		b.append("<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"); //$NON-NLS-1$
		b.append("<border><left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left><right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"); //$NON-NLS-1$
		b.append("<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top><bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom><diagonal/></border></borders>"); //$NON-NLS-1$
		b.append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"); //$NON-NLS-1$
		int count = STYLE_FIRST_CELL + 2 * (MAX_INDENT + 1);
		b.append("<cellXfs count=\"").append(count).append("\">"); //$NON-NLS-1$ //$NON-NLS-2$
		b.append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"); //$NON-NLS-1$
		b.append("<xf numFmtId=\"49\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"); //$NON-NLS-1$
		for (int indent = 0; indent <= MAX_INDENT; indent++) {
			for (int readOnly = 0; readOnly < 2; readOnly++) {
				b.append("<xf numFmtId=\"49\" fontId=\"0\" fillId=\"").append(readOnly == 1 ? 3 : 0) //$NON-NLS-1$
					.append("\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFill=\"1\" applyBorder=\"1\""); //$NON-NLS-1$
				if (indent > 0)
					b.append(" applyAlignment=\"1\"><alignment indent=\"").append(indent).append("\"/></xf>"); //$NON-NLS-1$ //$NON-NLS-2$
				else
					b.append("/>"); //$NON-NLS-1$
			}
		}
		b.append("</cellXfs>"); //$NON-NLS-1$
		b.append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"); //$NON-NLS-1$
		b.append("</styleSheet>"); //$NON-NLS-1$
		return b.toString();
	}

	private void writeSheet(Writer w) throws IOException {
		w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"); //$NON-NLS-1$
		w.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"); //$NON-NLS-1$
		w.write("<sheetViews><sheetView workbookViewId=\"0\">"); //$NON-NLS-1$
		if (frozenRows > 0)
			w.write("<pane ySplit=\"" + frozenRows + "\" topLeftCell=\"A" + (frozenRows + 1) + "\" activePane=\"bottomLeft\" state=\"frozen\"/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		w.write("</sheetView></sheetViews>"); //$NON-NLS-1$
		w.write("<sheetFormatPr defaultRowHeight=\"15\"/>"); //$NON-NLS-1$
		if (columnWidths.length > 0) {
			w.write("<cols>"); //$NON-NLS-1$
			for (int c = 0; c < columnWidths.length; c++)
				w.write("<col min=\"" + (c + 1) + "\" max=\"" + (c + 1) + "\" width=\"" + columnWidths[c] + "\" customWidth=\"1\"/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			w.write("</cols>"); //$NON-NLS-1$
		}
		w.write("<sheetData>"); //$NON-NLS-1$
		for (int r = 0; r < rows.size(); r++) {
			Row row = rows.get(r);
			w.write("<row r=\"" + (r + 1) + "\"" + (row.hidden ? " hidden=\"1\"" : "") + ">"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			for (int c = 0; c < row.values.length; c++) {
				String value = row.values[c];
				int style = row.styles == null ? 0 : row.styles[c];
				if ((value == null || value.length() == 0) && style == 0)
					continue;
				w.write("<c r=\"" + XlsxSheetReader.columnName(c) + (r + 1) + "\""); //$NON-NLS-1$ //$NON-NLS-2$
				if (style != 0)
					w.write(" s=\"" + style + "\""); //$NON-NLS-1$ //$NON-NLS-2$
				if (value == null || value.length() == 0) {
					w.write("/>"); //$NON-NLS-1$
				} else {
					w.write(" t=\"inlineStr\"><is><t xml:space=\"preserve\">"); //$NON-NLS-1$
					w.write(escape(value));
					w.write("</t></is></c>"); //$NON-NLS-1$
				}
			}
			w.write("</row>"); //$NON-NLS-1$
		}
		w.write("</sheetData>"); //$NON-NLS-1$
		if (autoFilterRow > 0)
			w.write("<autoFilter ref=\"" + filterRange().replace("$", "") + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		w.write("</worksheet>"); //$NON-NLS-1$
	}

	/** XML escape, dropping the control characters XML 1.0 cannot hold */
	static String escape(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			switch (ch) {
			case '&': b.append("&amp;"); break; //$NON-NLS-1$
			case '<': b.append("&lt;"); break; //$NON-NLS-1$
			case '>': b.append("&gt;"); break; //$NON-NLS-1$
			case '"': b.append("&quot;"); break; //$NON-NLS-1$
			default:
				if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r')
					b.append(ch);
			}
		}
		return b.toString();
	}
}
