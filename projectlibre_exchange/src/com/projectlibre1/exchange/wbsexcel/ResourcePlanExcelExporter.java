package com.projectlibre1.exchange.wbsexcel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.projectlibre1.pm.resource.labor.LaborCatalog;
import com.projectlibre1.pm.resource.plan.ResourcePlan;

/** Copies the company's workbook and replaces its two sample people with the current plan. */
public final class ResourcePlanExcelExporter {
	private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
	private static final String TEMPLATE = "resource-plan-template.xlsx";
	private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

	private ResourcePlanExcelExporter() {}

	public static void export(ResourcePlan plan, File destination) throws IOException {
		InputStream template = ResourcePlanExcelExporter.class.getResourceAsStream(TEMPLATE);
		if (template == null)
			throw new IOException("Resource Plan Excel template is missing"); //$NON-NLS-1$
		File parent = destination.getAbsoluteFile().getParentFile();
		File temporary = File.createTempFile("resource-plan-", ".xlsx", parent); //$NON-NLS-1$ //$NON-NLS-2$
		boolean done = false;
		try {
			try (InputStream source = template; ZipInputStream zipIn = new ZipInputStream(source);
					ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(temporary.toPath()))) {
				ZipEntry entry;
				boolean sheetFound = false;
				while ((entry = zipIn.getNextEntry()) != null) {
					zipOut.putNextEntry(new ZipEntry(entry.getName()));
					if ("xl/worksheets/sheet1.xml".equals(entry.getName())) { //$NON-NLS-1$
						writeSheet(zipIn, zipOut, plan);
						sheetFound = true;
					} else {
						byte[] buffer = new byte[8192];
						int count;
						while ((count = zipIn.read(buffer)) != -1)
							zipOut.write(buffer, 0, count);
					}
					zipOut.closeEntry();
					zipIn.closeEntry();
				}
				if (!sheetFound)
					throw new IOException("Resource Plan Excel template has no Sheet1"); //$NON-NLS-1$
			}
			Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
			done = true;
		} finally {
			if (!done)
				Files.deleteIfExists(temporary.toPath());
		}
	}

	private static void writeSheet(InputStream input, ZipOutputStream output, ResourcePlan plan) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //$NON-NLS-1$
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			ByteArrayOutputStream sheetBytes = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int count;
			while ((count = input.read(buffer)) != -1)
				sheetBytes.write(buffer, 0, count);
			Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(sheetBytes.toByteArray()));
			Element root = document.getDocumentElement();
			Element data = (Element) root.getElementsByTagNameNS(NS, "sheetData").item(0); //$NON-NLS-1$
			for (Node node = data.getLastChild(); node != null;) {
				Node previous = node.getPreviousSibling();
				if (node instanceof Element && "row".equals(node.getLocalName()) //$NON-NLS-1$
						&& Integer.parseInt(((Element) node).getAttribute("r")) >= 3) //$NON-NLS-1$
					data.removeChild(node);
				node = previous;
			}
			int row = 3;
			for (ResourcePlan.Line line : plan.getLines()) {
				int people = line.isNamed() ? 1 : line.getPeople();
				for (int i = 0; i < people; i++)
					addPerson(document, data, row++, line);
			}
			((Element) root.getElementsByTagNameNS(NS, "dimension").item(0)).setAttribute("ref", "A1:I" + Math.max(2, row - 1)); //$NON-NLS-1$ //$NON-NLS-2$
			Element validations = (Element) root.getElementsByTagNameNS(NS, "dataValidations").item(0); //$NON-NLS-1$
			if (validations != null) {
				for (Node node = validations.getFirstChild(); node != null; node = node.getNextSibling()) {
					if (!(node instanceof Element) || !"dataValidation".equals(node.getLocalName())) //$NON-NLS-1$
						continue;
					Element validation = (Element) node;
					String range = validation.getAttribute("sqref"); //$NON-NLS-1$
					if (range.startsWith("H3:")) //$NON-NLS-1$
						validation.setAttribute("sqref", "H3:H" + Math.max(3, row - 1)); //$NON-NLS-1$ //$NON-NLS-2$
					else if (range.startsWith("C3:")) //$NON-NLS-1$
						validation.setAttribute("sqref", "C3:C" + Math.max(3, row - 1)); //$NON-NLS-1$ //$NON-NLS-2$
					else if (range.startsWith("D1:")) { //$NON-NLS-1$
						Element formula = (Element) validation.getElementsByTagNameNS(NS, "formula1").item(0); //$NON-NLS-1$
						formula.setTextContent("\"Intern, F, J, J+, M, M+, S\""); //$NON-NLS-1$
					}
				}
			}
			TransformerFactory transformers = TransformerFactory.newInstance();
			transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			javax.xml.transform.Transformer transformer = transformers.newTransformer();
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8"); //$NON-NLS-1$
			transformer.transform(new DOMSource(document), new StreamResult(output));
		} catch (Exception e) {
			throw new IOException("Cannot write Resource Plan Excel sheet", e); //$NON-NLS-1$
		}
	}

	private static void addPerson(Document document, Element data, int number, ResourcePlan.Line line) {
		Element row = document.createElementNS(NS, "row"); //$NON-NLS-1$
		row.setAttribute("r", Integer.toString(number)); //$NON-NLS-1$
		data.appendChild(row);
		text(document, row, "A", number, "", 4); //$NON-NLS-1$ //$NON-NLS-2$
		text(document, row, "B", number, "", 2); //$NON-NLS-1$ //$NON-NLS-2$
		text(document, row, "C", number, line.getRole(), 2); //$NON-NLS-1$
		text(document, row, "D", number, line.getLevel() < 0 ? "" : LaborCatalog.levelAbbreviation(line.getLevel()), 1); //$NON-NLS-1$ //$NON-NLS-2$
		number(document, row, "E", number, excelDate(line.getFrom()), 9); //$NON-NLS-1$
		number(document, row, "F", number, excelDate(line.getTo() - 1), 9); //$NON-NLS-1$
		number(document, row, "G", number, line.getAllocation() * 100, 7); //$NON-NLS-1$
		text(document, row, "H", number, "Không onsite", 2); //$NON-NLS-1$ //$NON-NLS-2$
		text(document, row, "I", number, "", 1); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static long excelDate(long millis) {
		LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
		return ChronoUnit.DAYS.between(EXCEL_EPOCH, date);
	}

	private static void text(Document document, Element row, String column, int number, String value, int style) {
		Element cell = cell(document, row, column, number, style);
		cell.setAttribute("t", "inlineStr"); //$NON-NLS-1$ //$NON-NLS-2$
		Element inline = document.createElementNS(NS, "is"); //$NON-NLS-1$
		Element content = document.createElementNS(NS, "t"); //$NON-NLS-1$
		content.setTextContent(value == null ? "" : value); //$NON-NLS-1$
		inline.appendChild(content);
		cell.appendChild(inline);
	}

	private static void number(Document document, Element row, String column, int number, double value, int style) {
		Element cell = cell(document, row, column, number, style);
		Element content = document.createElementNS(NS, "v"); //$NON-NLS-1$
		content.setTextContent(Double.toString(value));
		cell.appendChild(content);
	}

	private static Element cell(Document document, Element row, String column, int number, int style) {
		Element cell = document.createElementNS(NS, "c"); //$NON-NLS-1$
		cell.setAttribute("r", column + number); //$NON-NLS-1$
		cell.setAttribute("s", Integer.toString(style)); //$NON-NLS-1$
		row.appendChild(cell);
		return cell;
	}
}
