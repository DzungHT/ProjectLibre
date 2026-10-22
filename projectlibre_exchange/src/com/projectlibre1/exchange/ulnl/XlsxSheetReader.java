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
package com.projectlibre1.exchange.ulnl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Minimal reader for the cell values of an .xlsx sheet (Office Open XML).
 * Formula cells return their cached value. No styles, dates or merged cells.
 * The POI version bundled with ProjectLibre has no OOXML support, hence this class.
 */
public class XlsxSheetReader {
	private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"; //$NON-NLS-1$
	private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"; //$NON-NLS-1$

	private final File file;
	private final Map<String, String> sheetPaths = new LinkedHashMap<String, String>(); // sheet name -> zip entry
	private List<String> sharedStrings;

	public XlsxSheetReader(File file) throws IOException {
		this.file = file;
		ZipFile zip = new ZipFile(file);
		try {
			readWorkbook(zip);
		} finally {
			zip.close();
		}
	}

	public List<String> getSheetNames() {
		return new ArrayList<String>(sheetPaths.keySet());
	}

	/**
	 * @return row number (1 based) -> column letters -> value. Empty cells are absent.
	 */
	public Map<Integer, Map<String, String>> readSheet(String sheetName) throws IOException {
		String path = sheetPaths.get(sheetName);
		if (path == null)
			throw new IOException("Sheet not found: " + sheetName); //$NON-NLS-1$
		ZipFile zip = new ZipFile(file);
		try {
			if (sharedStrings == null)
				sharedStrings = readSharedStrings(zip);
			Document doc = parse(zip, path);
			Map<Integer, Map<String, String>> rows = new TreeMap<Integer, Map<String, String>>();
			NodeList rowList = doc.getElementsByTagNameNS(MAIN_NS, "row"); //$NON-NLS-1$
			int rowNumber = 0;
			for (int i = 0; i < rowList.getLength(); i++) {
				Element row = (Element) rowList.item(i);
				String r = row.getAttribute("r"); //$NON-NLS-1$
				rowNumber = r.length() > 0 ? Integer.parseInt(r) : rowNumber + 1;
				Map<String, String> cells = new HashMap<String, String>();
				int columnIndex = 0;
				for (Node n = row.getFirstChild(); n != null; n = n.getNextSibling()) {
					if (!(n instanceof Element) || !"c".equals(n.getLocalName())) //$NON-NLS-1$
						continue;
					Element cell = (Element) n;
					String ref = cell.getAttribute("r"); //$NON-NLS-1$
					String column = ref.length() > 0 ? ref.replaceAll("[0-9]", "") : columnName(columnIndex); //$NON-NLS-1$ //$NON-NLS-2$
					columnIndex = columnIndex(column) + 1;
					String value = cellValue(cell);
					if (value != null && value.length() > 0)
						cells.put(column, value);
				}
				if (!cells.isEmpty())
					rows.put(rowNumber, cells);
			}
			return rows;
		} finally {
			zip.close();
		}
	}

	private String cellValue(Element cell) {
		String type = cell.getAttribute("t"); //$NON-NLS-1$
		if ("inlineStr".equals(type)) { //$NON-NLS-1$
			Element is = child(cell, "is"); //$NON-NLS-1$
			return is == null ? null : text(is);
		}
		Element v = child(cell, "v"); //$NON-NLS-1$
		if (v == null)
			return null;
		String value = v.getTextContent();
		if ("s".equals(type)) { //$NON-NLS-1$
			int index = Integer.parseInt(value.trim());
			return index < sharedStrings.size() ? sharedStrings.get(index) : null;
		}
		if ("e".equals(type)) // error such as #N/A //$NON-NLS-1$
			return null;
		return value;
	}

	private void readWorkbook(ZipFile zip) throws IOException {
		Map<String, String> targets = new HashMap<String, String>();
		Document rels = parse(zip, "xl/_rels/workbook.xml.rels"); //$NON-NLS-1$
		NodeList relList = rels.getElementsByTagNameNS("*", "Relationship"); //$NON-NLS-1$ //$NON-NLS-2$
		for (int i = 0; i < relList.getLength(); i++) {
			Element rel = (Element) relList.item(i);
			String target = rel.getAttribute("Target"); //$NON-NLS-1$
			target = target.startsWith("/") ? target.substring(1) : "xl/" + target; //$NON-NLS-1$ //$NON-NLS-2$
			targets.put(rel.getAttribute("Id"), target); //$NON-NLS-1$
		}
		Document workbook = parse(zip, "xl/workbook.xml"); //$NON-NLS-1$
		NodeList sheets = workbook.getElementsByTagNameNS(MAIN_NS, "sheet"); //$NON-NLS-1$
		for (int i = 0; i < sheets.getLength(); i++) {
			Element sheet = (Element) sheets.item(i);
			String path = targets.get(sheet.getAttributeNS(REL_NS, "id")); //$NON-NLS-1$
			if (path != null)
				sheetPaths.put(sheet.getAttribute("name"), path); //$NON-NLS-1$
		}
	}

	private static List<String> readSharedStrings(ZipFile zip) throws IOException {
		List<String> strings = new ArrayList<String>();
		if (zip.getEntry("xl/sharedStrings.xml") == null) //$NON-NLS-1$
			return strings;
		Document doc = parse(zip, "xl/sharedStrings.xml"); //$NON-NLS-1$
		NodeList items = doc.getElementsByTagNameNS(MAIN_NS, "si"); //$NON-NLS-1$
		for (int i = 0; i < items.getLength(); i++)
			strings.add(text((Element) items.item(i)));
		return strings;
	}

	/** Concatenates the <t> runs, skipping phonetic runs (<rPh>) */
	private static String text(Element parent) {
		StringBuilder b = new StringBuilder();
		NodeList ts = parent.getElementsByTagNameNS(MAIN_NS, "t"); //$NON-NLS-1$
		for (int i = 0; i < ts.getLength(); i++) {
			Node t = ts.item(i);
			if (!"rPh".equals(t.getParentNode().getLocalName())) //$NON-NLS-1$
				b.append(t.getTextContent());
		}
		return b.toString();
	}

	private static Element child(Element parent, String localName) {
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
			if (n instanceof Element && localName.equals(n.getLocalName()))
				return (Element) n;
		return null;
	}

	private static Document parse(ZipFile zip, String path) throws IOException {
		ZipEntry entry = zip.getEntry(path);
		if (entry == null)
			throw new IOException("Missing " + path + " in " + zip.getName()); //$NON-NLS-1$ //$NON-NLS-2$
		InputStream in = zip.getInputStream(entry);
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(in);
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException("Cannot read " + path + ": " + e.getMessage(), e); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			in.close();
		}
	}

	/** 0 -> A, 25 -> Z, 26 -> AA */
	public static String columnName(int index) {
		StringBuilder b = new StringBuilder();
		for (int i = index + 1; i > 0; i = (i - 1) / 26)
			b.insert(0, (char) ('A' + (i - 1) % 26));
		return b.toString();
	}

	/** A -> 0, AA -> 26 */
	public static int columnIndex(String column) {
		int index = 0;
		for (int i = 0; i < column.length(); i++)
			index = index * 26 + (Character.toUpperCase(column.charAt(i)) - 'A' + 1);
		return index - 1;
	}
}
