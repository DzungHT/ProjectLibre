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
package com.projectlibre1.pm.resource.labor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

import com.projectlibre1.preference.ConfigurationFile;

/**
 * CSV files of the catalogs (labor categories, skills), kept in the configuration directory of the user so that
 * they are shared by all the projects. Read as UTF-8, or in the encoding of the system when it is not UTF-8.
 * Written as UTF-8. Values are separated by commas, and quoted when they have a comma or a quote.
 */
class CatalogFile {
	private static final Charset UTF8 = Charset.forName("UTF-8"); //$NON-NLS-1$

	private CatalogFile() {
	}

	/** File in the configuration directory, ~/.projectlibre unless there is already a ProjectLibre one */
	static File get(String name) {
		File dir = ConfigurationFile.getConfDir();
		if (dir == null)
			dir = new File(System.getProperty("user.home"), ".projectlibre"); //$NON-NLS-1$ //$NON-NLS-2$
		return new File(dir, name);
	}

	/** Rows of a CSV file, without the empty lines */
	static List<String[]> read(File file) throws IOException {
		InputStream in = new FileInputStream(file);
		try {
			return read(in);
		} finally {
			in.close();
		}
	}
	static List<String[]> read(InputStream in) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		for (int n; (n = in.read(buffer)) > 0;)
			bytes.write(buffer, 0, n);
		String text;
		try {
			text = UTF8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
		} catch (CharacterCodingException e) {
			text = new String(bytes.toByteArray(), Charset.defaultCharset());
		}
		if (text.length() > 0 && text.charAt(0) == 0xFEFF) // byte order mark
			text = text.substring(1);
		List<String[]> rows = new ArrayList<String[]>();
		for (String line : text.split("\r\n|\r|\n")) { //$NON-NLS-1$
			if (line.trim().length() > 0)
				rows.add(split(line));
		}
		return rows;
	}
	private static String[] split(String line) {
		List<String> values = new ArrayList<String>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quoted) {
				if (c != '"')
					value.append(c);
				else if (i + 1 < line.length() && line.charAt(i + 1) == '"')
					value.append(line.charAt(++i));
				else
					quoted = false;
			} else if (c == '"')
				quoted = true;
			else if (c == ',') {
				values.add(value.toString().trim());
				value.setLength(0);
			} else
				value.append(c);
		}
		values.add(value.toString().trim());
		return values.toArray(new String[values.size()]);
	}

	static void write(File file, List<String[]> rows) throws IOException {
		File dir = file.getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs())
			throw new IOException("Cannot create the directory " + dir); //$NON-NLS-1$
		Writer out = new OutputStreamWriter(new FileOutputStream(file), UTF8);
		try {
			for (String[] row : rows) {
				for (int i = 0; i < row.length; i++) {
					if (i > 0)
						out.write(',');
					out.write(quote(row[i]));
				}
				out.write("\r\n"); //$NON-NLS-1$
			}
		} finally {
			out.close();
		}
	}
	private static String quote(String value) {
		if (value == null)
			return ""; //$NON-NLS-1$
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0)
			return value;
		return '"' + value.replace("\"", "\"\"") + '"'; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Index of a column from the names of the header row, ignoring case, -1 if missing */
	static int column(String[] header, String name) {
		for (int i = 0; i < header.length; i++)
			if (header[i].equalsIgnoreCase(name))
				return i;
		return -1;
	}
	static String value(String[] row, int column) {
		return column < 0 || column >= row.length ? "" : row[column]; //$NON-NLS-1$
	}
}
