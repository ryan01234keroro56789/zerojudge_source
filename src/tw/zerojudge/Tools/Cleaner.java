package tw.zerojudge.Tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import tw.jiangsir.Utils.Exceptions.AccessException;
import tw.jiangsir.Utils.Tools.FileTools;
import tw.zerojudge.Utils.XMLParser;

public class Cleaner {

	Logger logger = Logger.getAnonymousLogger();

	public class FileNameSelector implements FilenameFilter {
		String regex;

		public FileNameSelector(String regex) {
			this.regex += regex;
		}

		public boolean accept(File dir, String name) {
			return name.matches(regex);
		}
	}

	private Document doc_webxml = null;
	private Document doc_contextxml = null;

	static String PATH_BASE = "./../ZeroJudge_BETA/";
	static String PATH_CONSOLE = PATH_BASE + "/CONSOLE/";
	static String PATH_TESTDATA = PATH_CONSOLE + "/testdata/";
	static String PATH_BIN = PATH_CONSOLE + "/bin/";
	static String PATH_SPECIAL = PATH_CONSOLE + "/Special/";
	private String PATH_WEBXML = PATH_BASE + "/WEB-INF/web.xml";
	private String PATH_CONTEXTXML = PATH_BASE + "/META-INF/context.xml";

	private File BASE_DIR;
	private File CONSOLE_DIR;
	private File TESTDATA_DIR;
	private File BIN_DIR;
	private File SPECIAL_DIR;
	private File WEBXML;
	private File CONTEXTXML;

	public Cleaner(File BASE_DIR) {
		if (!BASE_DIR.isDirectory()) {
			return;
		}
		this.BASE_DIR = BASE_DIR;
		this.CONSOLE_DIR = new File(BASE_DIR, "/WebContent/CONSOLE");
		this.TESTDATA_DIR = new File(CONSOLE_DIR, "Testdata");
		this.BIN_DIR = new File(CONSOLE_DIR, "Bin");
		this.SPECIAL_DIR = new File(CONSOLE_DIR, "Special");
		this.WEBXML = new File(BASE_DIR + "/WebContent/WEB-INF", "web.xml");
		this.CONTEXTXML = new File(BASE_DIR + "/WebContent/META-INF", "context.xml");
	}

	public void run() {
		this.cleanJavaSource();
		this.cleanXml();
		this.initContextxml();
		this.cleanJSP();
		this.deleteFiles();

	}

	private void deleteFiles() {

		System.out.println("???????????? *.zip ?????????!");
		ArrayList<File> zips = new FileTools().findFiles(this.BASE_DIR, ".*\\.zip$");
		for (int i = 0; i < zips.size(); i++) {
			this.delFiles(zips.get(i).toString());
		}

	}

	private void cleanJavaSource() {
		File src = new File(BASE_DIR, "/src/");
		ArrayList<File> javas = new FileTools().findFiles(src, ".*\\.java$");
		System.out.println(src.getPath() + " ?????? " + javas.size() + " ??? .java ???");
		for (File file : javas) {
			if (file.getName().endsWith(this.getClass().getSimpleName() + ".java")) {
				continue;
			}
			this.cleanJAVA(file.getPath());
		}
		System.out.println("????????????!!");

	}

	private void cleanXml() {
		System.out.println("???????????? *.xml ???????????????????(Y/n)");
		// if ("Y".equals(cin.next())) {
		ArrayList<File> xmls = new FileTools().findFiles(BASE_DIR, ".*\\.xml$");
		System.out.println("????????? " + xmls.size() + " ??? .xml ???");
		for (int i = 0; i < xmls.size(); i++) {
			this.cleanLines(xmls.get(i).toString());
		}
		ArrayList<File> filelist2 = new FileTools()
				.findFiles(new File(BASE_DIR, "/WebContent/META-INF/"), ".*\\.xml$");
		for (int i = 0; i < filelist2.size(); i++) {
			this.cleanLines(filelist2.get(i).toString());
		}
	}

	/**
	 * ????????? context.xml, ??? ???????????????,?????????
	 * 
	 * @throws AccessException
	 */
	private void initContextxml() {
		// System.out.println("????????? context.xml");
		// XMLParser parser = new XMLParser(BASE_DIR);
		// parser.setContextParam_docBase("zerojudge");
		// parser.setContextParam("Valve", "allow", ".*");
		// parser.setContextParam("Valve", "deny", "192.168.192.168");
		// parser.setContextParam("Resource", "username", "root");
		// parser.setContextParam("Resource", "password", "1234");
		// parser.setDBHost("127.0.0.1");
		// parser.setDBName("zerojudge");
		// try {
		// parser.writeContextxml();
		// } catch (AccessException e) {
		// e.printStackTrace();
		// }
	}

	/**
	 * ??? web.xml ???????????????
	 * 
	 * @throws AccessException
	 * 
	 */
	private void initWebxml() {
		XMLParser parser = new XMLParser(BASE_DIR);
		parser.setInitParam("TITLE_IMAGE", "images/title.png");
		parser.setInitParam("TITLE", "Your Site's Name!");
		parser.setInitParam("HEADER", "An Online Judge System For Beginners");
		parser.setInitParam("MAX_IP_CONNECTION", "1000");
		parser.setInitParam("MANAGER_IP", "*.*.*.*");
		parser.setInitParam("MANAGER_MAIL", "somebody@gmail.com");
		parser.setInitParam("SYSTEM_MAIL", "somebody@gmail.com");
		parser.setInitParam("SYSTEM_MAIL_PASSWORD", "password");
		try {
			parser.writetoWebxml();
		} catch (AccessException e) {
			e.printStackTrace();
		}
	}

	private void cleanJSP() {
		System.out.println("???????????? *.jsp ???????(Y/n)");
		// if ("Y".equals(cin.next())) {
		ArrayList<File> jsps = new FileTools().findFiles(BASE_DIR, ".*\\.jsp$");
		System.out.println("????????? " + jsps.size() + " ??? .jsp ???");
		for (int i = 0; i < jsps.size(); i++) {
			this.cleanLines(jsps.get(i).toString());
		}
	}

	/**
	 * ???????????? ?????? ????????? ??? .cpp ???
	 * 
	 * @param filename
	 * @param data
	 */
	public void createfile(String filename, String data) {
		BufferedWriter outfile = null;
		FileOutputStream fos = null;
		OutputStreamWriter writer = null;
		try {
			fos = new FileOutputStream(filename);
			writer = new OutputStreamWriter(fos, "utf-8");
			writer.write(new String(data.getBytes("UTF-8"), "UTF-8"));
			writer.flush();
			writer.close();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void filecopy(String from, String to) {
		if (!new File(from).isFile()) {
			return;
		}
		FileInputStream fis;
		FileOutputStream fos;
		try {
			fis = new FileInputStream(new File(from));
			fos = new FileOutputStream(new File(to));
			byte fileBuffer[] = new byte[512];
			int fileIdx = -1;
			while ((fileIdx = fis.read(fileBuffer)) != -1) {
				fos.write(fileBuffer, 0, fileIdx);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void filecopy_OLD(String from, String to) {
		if (!new File(from).isFile()) {
			return;
		}
		BufferedReader breader = null;
		FileInputStream fis = null;
		FileOutputStream fos = null;
		OutputStreamWriter writer = null;
		try {
			fis = new FileInputStream(from);
			fos = new FileOutputStream(to);
			breader = new BufferedReader(new InputStreamReader(fis, "utf-8"));
			writer = new OutputStreamWriter(fos, "utf-8");
			String line;
			try {
				while ((line = breader.readLine()) != null)
					writer.write(new String(line.getBytes("UTF-8"), "UTF-8") + "\n");
				writer.flush();
				writer.close();
				breader.close();
				fis.close();
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

	}

	/**
	 * ??????????????????????????????, ????????????
	 */
	public ArrayList<File> getFiles(File path, String regex) {
		File[] files = path.listFiles();
		ArrayList<File> fileList = new ArrayList<File>();
		for (int i = 0; i < files.length; i++) {
			// ???????????????
			if (!files[i].isDirectory() && files[i].toString().matches(regex)) { // ???????????????
				fileList.add(files[i]);
			}
		}
		return fileList;
	}

	/**
	 * ?????? <!-- qx ..... --> ??? <%-- qx ..... --%> ?????? <!-- cleaner BEGIN --> <!--
	 * cleaner END -->
	 * 
	 */
	private void cleanLines(String path) {
		BufferedReader breader = null;
		StringBuffer text = new StringBuffer(50000);
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(path);
			breader = new BufferedReader(new InputStreamReader(fis, "utf-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		String line = null;
		// ArrayList list = new ArrayList();
		try {
			while ((line = breader.readLine()) != null) {
				if (line.matches(".*<!--.*cleaner DELETE_FILE.*-->.*")) {
					breader.close();
					fis.close();
					this.delFiles(path);
					return;
				}
				// System.out.println(line);
				if (line.matches(".*<!--.*qx.*")) {
					String lines = line;
					while (!lines.matches(".*-->.*")) {
						lines += breader.readLine();
					}
					// System.out.println(path.substring(path.lastIndexOf("\\")
					// + 1) + ":?????? "
					// + new String(lines.getBytes("utf-8"), "UTF-8"));
					// logger.info(path.substring(path.lastIndexOf("\\") + 1) +
					// ":?????? "
					// + new String(lines.getBytes("utf-8"), "UTF-8"));
					line = lines.replaceAll("<!--.*qx.*-->.*", "");
					if (line.trim().equals("")) {
						continue;
					}
				}
				if (line.matches(".*<%--.*qx.*")) {
					String lines = line;
					while (!lines.matches(".*--%>.*")) {
						lines += breader.readLine();
					}
					// logger.info(path.substring(path.lastIndexOf("\\") + 1) +
					// ":?????? "
					// + new String(lines.getBytes("utf-8"), "UTF-8"));
					line = lines.replaceAll("<%--.*--%>.*", "");
					if (line.trim().equals("")) {
						continue;
					}
				}
				if (line.matches(".*<!--.*cleaner BEGIN.*-->.*")
						|| line.matches(".*<!--.*Cleaner BEGIN.*-->.*")) {
					String lines = line;
					while (!(lines.matches(".*<!--.*cleaner END.*-->.*")
							|| lines.matches(".*<!--.*Cleaner END.*-->.*"))) {
						lines += breader.readLine();
					}
					// logger.info(path.substring(path.lastIndexOf("\\") + 1) +
					// ":?????? "
					// + new String(lines.getBytes("utf-8"), "UTF-8"));
					line = "";
				}
				text.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String text2 = text.toString();
		try {
			text2 = new String(text2.getBytes("UTF-8"), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		// System.out.println(text2.toString());
		createfile(path, text2.toString());

	}

	public void setContextParam_ReplaceByXMLParser(String attribute, String key, String value) {
		if ("".equals(value)) {
			return;
		}
		SAXBuilder builder = new SAXBuilder();
		try {
			if (this.doc_contextxml == null) {
				this.doc_contextxml = builder.build(new File(this.PATH_CONTEXTXML));
			}
		} catch (JDOMException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// Get the root element
		Element root = this.doc_contextxml.getRootElement();
		// Print servlet information
		List children = root.getChildren();
		Iterator i = children.iterator();
		while (i.hasNext()) {
			Element child = (Element) i.next();
			if (attribute.equals(child.getName())) {
				child.setAttribute(key, value);
				return;
			}
		}
	}

	public String getContextParam_ReplaceByXMLParser(String attribute, String key) {
		SAXBuilder builder = new SAXBuilder();
		try {
			if (this.doc_contextxml == null) {
				this.doc_contextxml = builder.build(new File(this.PATH_CONTEXTXML));
			}
		} catch (JDOMException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// Get the root element
		Element root = this.doc_contextxml.getRootElement();
		// Print servlet information
		List children = root.getChildren();
		Iterator i = children.iterator();

		while (i.hasNext()) {
			Element child = (Element) i.next();
			System.out.println("child.getName()=" + child.getName());
			if (attribute.equals(child.getName())) {
				return child.getAttributeValue(key);
			}
		}
		return null;
	}

	/**
	 * ?????? web.xml ?????????
	 * 
	 * @param name
	 * @return
	 */
	public String getInitParam_ReplaceByXMLParser(String name) {
		SAXBuilder builder = new SAXBuilder();
		try {
			if (this.doc_webxml == null) {
				this.doc_webxml = builder.build(new File(this.PATH_WEBXML));
			}
		} catch (JDOMException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// Get the root element
		Element root = this.doc_webxml.getRootElement();
		// Print servlet information
		List children = root.getChildren();
		Iterator i = children.iterator();
		while (i.hasNext()) {
			Element child = (Element) i.next();
			if ("context-param".equals(child.getName())) {
				Element param_name = (Element) child.getChildren().get(0);
				Element param_value = (Element) child.getChildren().get(1);
				if (name.equals(param_name.getValue())) {
					return param_value.getText().trim();
				}
			}
		}
		return null;
	}

	/**
	 * ?????? web.xml context-param
	 * 
	 * @param name
	 * @param value
	 */
	public void setInitParam_ReplaceByXMLParser(String name, String value) {
		if ("".equals(value)) {
			return;
		}
		SAXBuilder builder = new SAXBuilder();
		try {
			if (this.doc_webxml == null) {
				this.doc_webxml = builder.build(new File(this.PATH_WEBXML));
			}
		} catch (JDOMException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		// Get the root element
		Element root = this.doc_webxml.getRootElement();
		// Print servlet information
		List children = root.getChildren();
		Iterator i = children.iterator();
		while (i.hasNext()) {
			Element child = (Element) i.next();
			if ("context-param".equals(child.getName())) {
				Element param_name = (Element) child.getChildren().get(0);
				Element param_value = (Element) child.getChildren().get(1);
				if (name.equals(param_name.getValue())) {
					param_value.setText(value);
					return;
				}
			}
		}
	}

	public void writetoWebxml_ReplaceByXMLParser() {
		XMLOutputter outp = new XMLOutputter();
		FileOutputStream fo = null;

		try {
			fo = new FileOutputStream(this.PATH_WEBXML);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		try {
			outp.output(this.doc_webxml, fo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeContextxml_ReplaceByXMLParser() {
		XMLOutputter outp = new XMLOutputter();
		FileOutputStream fo = null;

		try {
			fo = new FileOutputStream(this.PATH_CONTEXTXML);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		try {
			outp.output(this.doc_contextxml, fo);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void cleanMyProperties() {
		// ConfigDAO systemConfigDao = new ConfigDAO(new
		// File(Cleaner.PATH_BASE));
		// systemConfigDao.removeProperty("DOWNLOAD_ZEROJUDGE");
		// systemConfigDao.removeProperty("EXCLUSIVE_SCHOOL");
		// systemConfigDao.removeProperty("SAVETMPFILE");
		// systemConfigDao.removeProperty("MAIL_SERVRE");
		// systemConfigDao.removeProperty("MAILSERVER_PASSWORD");
		// systemConfigDao.removeProperty("SYSTEM_MONITOR_ACCOUNT");
		// systemConfigDao.removeProperty("SYSTEM_MONITOR");
		// systemConfigDao.removeProperty("SYSTEM_MONITOR_IP");
		// systemConfigDao.removeProperty("CANDIDATE_MANAGER");
		// systemConfigDao.removeProperty("BIN_PATH");
		//
		// systemConfigDao.setProperty("CONSOLE_PATH", "/tmp/CONSOLE/");
		// systemConfigDao.setProperty("LANGUAGES", "C, C++");
		// systemConfigDao.setProperty("MANAGER_IP", "*");
	}

	/**
	 * ?????? .java ???????????? package
	 * 
	 */
	public void cleanPackage(String path) {
		BufferedReader breader = null;
		StringBuffer text = new StringBuffer(50000);
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(path);
			breader = new BufferedReader(new InputStreamReader(fis, "utf-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		String line = null;
		// ArrayList list = new ArrayList();
		try {

			while ((line = breader.readLine()) != null) {
				if (line.matches("^package .*")) {
					line = line.replaceAll("package .*", "");
					if (line.trim().equals("")) {
						continue;
					}
				}

				text.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String text2 = text.toString();
		try {
			text2 = new String(text2.getBytes("UTF-8"), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		// System.out.println(text2.toString());
		createfile(path, text2.toString());
	}

	public String readFile(String path) {
		BufferedReader breader = null;
		StringBuffer text = new StringBuffer(50000);
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(path);
			breader = new BufferedReader(new InputStreamReader(fis, "utf-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		String line = null;
		// ArrayList list = new ArrayList();
		try {

			while ((line = breader.readLine()) != null) {
				text.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String text2 = text.toString();
		try {
			text2 = new String(text2.getBytes("UTF-8"), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return text2;
	}

	/**
	 * ?????? .java ????????? // ??????, ??? System.out.print ??????
	 * 
	 */
	public void cleanJAVA(String path) {
		System.out.println("cleanJava: " + path);
		BufferedReader breader = null;
		StringBuffer text = new StringBuffer(50000);
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(path);
			breader = new BufferedReader(new InputStreamReader(fis, "utf-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		String line = null;
		// ArrayList list = new ArrayList();
		try {

			while ((line = breader.readLine()) != null) {
				if (line.matches(".*/\\*.*cleaner DELETE_FILE.*\\*/.*")) {
					// .*/\\*.*cleaner BEGIN.*\\*/.*
					breader.close();
					fis.close();
					this.delFiles(path);
					return;
				}
				// TODO ??????????????????????????????
				// ????????????????????? /* */
				// line = line.replaceAll("/\\* .*\\*/", "");
				// ??????????????? /* */
				// if (line.matches(".*/\\*[ ].*")) {
				// String line2 = "";
				// while ((line2 = breader.readLine()) != null) {
				// System.out.println("!!! ?????? /* */ !!!!");
				// System.out.println("!!! line2=" + line2 + " !!!!");
				//
				// line += line2;
				// if (line2.contains(".*\\*/.*")) {
				// break;
				// }
				// }
				// line = line.replaceAll("/\\*.*\\*/", "");
				// }

				// System.out.println(line);
				// qx ???????????????????????????, ?????????????????? // ?????????
				// && !line.matches(".*\"[^\"]*?//[^\"]*?\".*")
				// qx ???????????? // ????????????????????? ??????

				if (line.matches(".*// .*")) {
					// logger.info(path + ":?????? " + new
					// String(line.getBytes("utf-8"), "UTF-8"));
					line = line.replaceAll("// .*", "");
					if (line.trim().equals("")) {
						continue;
					}
				}
				if (line.matches(".*System.out.print.*")) {
					String lines = line;
					while (!lines.matches(".*\\);")) {
						lines += breader.readLine();
					}
					// logger.info(path.substring(path.lastIndexOf("\\") + 1) +
					// ":?????? "
					// + new String(lines.getBytes("utf-8"), "UTF-8"));
					line = lines.replaceAll("System.out.print.*\\);", "");
					if (line.trim().equals("")) {
						continue;
					}
				} // list.add(line);
				if (line.matches(".*/\\*.*cleaner BEGIN.*\\*/.*")) {
					String lines = line;
					while (!lines.matches(".*/\\*.*cleaner END.*\\*/.*")) {
						lines += breader.readLine();
					}
					// logger.info(path.substring(path.lastIndexOf("\\") + 1) +
					// ":?????? "
					// + new String(lines.getBytes("utf-8"), "UTF-8"));
					line = "";
				}
				text.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String text2 = text.toString();
		try {
			text2 = new String(text2.getBytes("UTF-8"), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		// System.out.println(text2.toString());
		createfile(path, text2.toString());

	}

	/**
	 * ?????? JavaScript ????????????
	 * 
	 * @param path
	 */
	private void cleanJS(String path) {

	}

	/**
	 * ????????????????????? ????????????: ZeroJudge_Dev -> ZeroJudge <br>
	 * ex: native2ascii.bat ???????????????
	 * 
	 */
	public void cleanFile(String path) {
		BufferedReader breader = null;
		StringBuffer text = new StringBuffer(50000);
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(path);
			breader = new BufferedReader(new InputStreamReader(fis, "utf-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		String line = null;
		// ArrayList list = new ArrayList();
		try {
			while ((line = breader.readLine()) != null) {
				if (line.matches(".*ZeroJudge_Dev.*")) {
					String lines = line;
					line = lines.replaceAll("ZeroJudge_Dev", "ZeroJudge");
					if (line.trim().equals("")) {
						continue;
					}
				} // list.add(line);
				text.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String text2 = text.toString();
		try {
			text2 = new String(text2.getBytes("UTF-8"), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		// System.out.println(text2.toString());
		createfile(path, text2.toString());
	}

	/**
	 * ?????? .css ????????? / * * / ????????? ????????????
	 */
	private void cleanCSS(String path) {
		BufferedReader breader = null;
		StringBuffer text = new StringBuffer(50000);
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(path);
			breader = new BufferedReader(new InputStreamReader(fis, "utf-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		String line = null;
		// ArrayList list = new ArrayList();
		try {
			while ((line = breader.readLine()) != null) {
				if (line.matches(".*/\\*")) {
					String lines = line;
					while (!lines.matches(".*\\*/")) {
						lines += breader.readLine();
					}
					// logger.info(lines + ":?????? " + new
					// String(lines.getBytes("utf-8"), "UTF-8"));
					line = lines.replaceAll("/\\*.*\\*/", "");
					if (line.trim().equals("")) {
						continue;
					}
				} // list.add(line);
				text.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String text2 = text.toString();
		try {
			text2 = new String(text2.getBytes("UTF-8"), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		// System.out.println(text2.toString());
		createfile(path, text2.toString());
	}

	/**
	 * ????????????, ??????????????????, ??????????????????????????????????????? <br>
	 * ?????????????????????
	 * 
	 * @param path
	 */
	public void delFiles(String path) {
		File f = new File(path);
		if (f.isDirectory()) {
			File[] files = f.listFiles();
			System.out.println("f.getPath()=" + f.getPath());
			for (int i = 0; i < files.length; i++) {
				delFiles(files[i].getPath());
			}
		} else if (f.exists()) {
			// System.out.println("Deleting " + f.getName() + "...");
			if (f.delete()) {
				System.out.println("?????? " + path + "????????????");
				// cin.next();
			} else {
				System.out.println(path + " delFiles ??????????????????");
				cin.next();
			}
		}
	}

	/**
	 * ??????????????????
	 * 
	 * @param path
	 */
	public void delDIR(String path) {
		File file = new File(path);
		File[] files = file.listFiles();
		if (files == null) {
			return;
		}
		for (int i = 0; i < files.length; i++) {
			// ???????????????
			if (files[i].isDirectory()) { // ???????????????
				delDIR(files[i].toString());
			} else {
				files[i].delete();
			}
		}
		file.delete();
	}

	/**
	 * ?????? a001 ?????????
	 * 
	 * @param path
	 */
	private void delTestData(String path) {
		File f = new File(path);
		File[] files = new File(path).listFiles();
		System.out.println("f.getPath()=" + f.getPath());
		for (int i = 0; i < files.length; i++) {
			if (files[i].exists() && !files[i].isDirectory()
					&& !files[i].getPath().matches(".*a001.*\\.in$")
					&& !files[i].getPath().matches(".*a001.*\\.out$")) {
				if (files[i].delete()) {
					System.out.println("?????? " + files[i].getPath() + "??????");
					// cin.next();
				} else {
					System.out.println(files[i].getPath() + " ??????????????????");
					cin.next();
				}
			}
		}
	}

	/**
	 * ???????????????????????????????????? ??? VBANNER_1.jsp ????????????
	 * 
	 */
	private void releaseVersion() {
		this.filecopy(this.BASE_DIR + "VBANNER_DEFAULT.jsp", this.BASE_DIR + "VBANNER_1.jsp");
		this.filecopy(this.BASE_DIR + "VBANNER_DEFAULT.jsp", this.BASE_DIR + "VBANNER_2.jsp");
		this.filecopy(this.BASE_DIR + "VBANNER_DEFAULT.jsp", this.BASE_DIR + "VBANNER_3.jsp");
		this.delFiles(this.BASE_DIR + "images/DSC0025.jpg");
		this.delFiles(this.BASE_DIR + "images/DSC0025_smaller.jpg");
		this.delFiles(this.BASE_DIR + "images/DSC0027.jpg");
		this.delFiles(this.BASE_DIR + "images/DSC0027_smaller.jpg");
	}

	public static Scanner cin = new Scanner(System.in);

	public static void main(String[] args) {
		System.out.println("args.length=" + args.length);
		if (args.length == 1 && new File(args[0]).exists()) {
			System.out.println("args[0]=" + args[0]);
			new Cleaner(new File(args[0])).run();
		} else {
			System.out.println("?????????????????????");
			System.out.println("1. " + System.getProperty("user.dir"));
			// System.out.println("2. " + Cleaner.class.getClassLoader()
			// .getResource("/").getPath());
			int choose = Integer.parseInt(cin.next());
			if (choose == 1) {
				new Cleaner(new File(System.getProperty("user.dir"))).run();
			} else if (choose == 2) {
				new Cleaner(new File(Cleaner.class.getClassLoader().getResource("/").getPath()))
						.run();
			} else {
			}
		}
	}
}