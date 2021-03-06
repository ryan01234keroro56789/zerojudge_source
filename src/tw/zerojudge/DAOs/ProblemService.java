package tw.zerojudge.DAOs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpSession;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.OutputDocument;
import net.htmlparser.jericho.Source;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import tw.jiangsir.Utils.Exceptions.AccessException;
import tw.jiangsir.Utils.Exceptions.Alert;
import tw.jiangsir.Utils.Exceptions.DataException;
import tw.jiangsir.Utils.Scopes.ApplicationScope;
import tw.jiangsir.Utils.Tools.FileTools;
import tw.jiangsir.Utils.Tools.RunCommand;
import tw.jiangsir.Utils.Tools.StringTool;
import tw.zerojudge.Configs.AppConfig;
import tw.zerojudge.Factories.JudgeFactory;
import tw.zerojudge.Factories.ProblemFactory;
import tw.zerojudge.Factories.UserFactory;
import tw.zerojudge.JsonObjects.ExportProblem;
import tw.zerojudge.JsonObjects.Problemtab;
import tw.zerojudge.Judges.JudgeObject;
import tw.zerojudge.Judges.JudgeProducer;
import tw.zerojudge.Judges.JudgeServer;
import tw.zerojudge.Judges.ServerInput;
import tw.zerojudge.Judges.ServerOutput;
import tw.zerojudge.Objects.Language;
import tw.zerojudge.Objects.Problemid;
import tw.zerojudge.Servlets.ShowImageServlet;
import tw.zerojudge.Tables.Log;
import tw.zerojudge.Tables.OnlineUser;
import tw.zerojudge.Tables.Problem;
import tw.zerojudge.Tables.Problemimage;
import tw.zerojudge.Tables.Solution;
import tw.zerojudge.Tables.User;

public class ProblemService {
	public static Hashtable<Integer, Problem> HashProblems = new Hashtable<Integer, Problem>();
	public static Hashtable<Problemid, Integer> HashProblemids = new Hashtable<Problemid, Integer>();
	public static Hashtable<String, TreeSet<Problemid>> HashProblemtabProblemidset = new Hashtable<String, TreeSet<Problemid>>();
	private TreeSet<Problemid> allProblemids = new TreeSet<Problemid>();
	Logger logger = Logger.getAnonymousLogger();

	public int insert(Problem problem) {
		int pid = new ProblemDAO().insert(problem);
		if (pid == 0) {
			throw new DataException("??????????????????(" + problem.getTitle() + ")???");
		}
		return pid;
	}

	public int update(Problem problem) {
		Problem oldProblem = new ProblemDAO().getProblemByPid(problem.getId());
		int result = new ProblemDAO().update(problem);
		HashProblems.put(problem.getId(), problem);
		HashProblemids.put(problem.getProblemid(), problem.getId());
		HashProblemtabProblemidset.clear();
		this.getProblemtabProblemidset();
		if ((oldProblem.getDisplay() == Problem.DISPLAY.open && problem.getDisplay() != Problem.DISPLAY.open)
				|| (oldProblem.getDisplay() != Problem.DISPLAY.open && problem.getDisplay() == Problem.DISPLAY.open)) {
			new UserService().rebuiltUserStatisticByProblem(problem.getProblemid());
		}
		return result;
	}

	public int updateProblemSettings(Problem problem) {
		int result = new ProblemDAO().updateProblemSettings_PSTMT(problem);
		if (problem.getDisplay() == Problem.DISPLAY.open) {
			ApplicationScope.getOpenedProblemidSet().add(problem.getProblemid());
		} else {
			ApplicationScope.getOpenedProblemidSet().remove(problem.getProblemid());
		}
		HashProblemtabProblemidset.clear();
		this.getProblemtabProblemidset();
		return result;
	}


	public boolean delete(int id) {
		return new ProblemDAO().delete(id);
	}

	/**
	 * ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????? <br/>
	 * ???????????????????????????????????????
	 * 
	 * @deprecated
	 * @param problemid
	 * @return
	 */
	public boolean delete(String problemid) {
		return false;
	}

	public Problem getProblemByPid(int pid) {
		if (!HashProblems.containsKey(pid)) {
			Problem problem = new ProblemDAO().getProblemByPid(pid);
			HashProblems.put(pid, problem);
		}

		return HashProblems.get(pid);
	}

	public Problem getProblemByProblemid(Problemid problemid) {
		int pid = 0;
		if (HashProblemids.containsKey(problemid)) {
			pid = HashProblemids.get(problemid);
		} else {
			pid = (int) new ProblemDAO().getIdByProblemid(problemid);
			HashProblemids.put(problemid, (int) pid);
		}
		return this.getProblemByPid(pid);
	}

	public int getOpenedProblemCount() {
		return new ProblemDAO().getCountOfOpenedProblem();
	}

	public TreeSet<Problemid> getOpenedProblemidSet() {
		TreeMap<String, Object> fields = new TreeMap<String, Object>();
		fields.put("display", Problem.DISPLAY.open.name());
		return new ProblemDAO().getProblemidSetByFields(fields, null, 0);
	}


	/**
	 * ???????????? last_solutionid ??????????????? problemid <br>
	 * ?????? ?????????????????? recount ???????????????/?????? ??????
	 * 
	 * @param solutionid
	 */
	public void recountProblemidAfter_LastSolutionid() {
		for (Problemid problemid : new SolutionDAO().getProblemidAfter_LastSolutionid()) {
			this.recountProblem_UserCount(problemid);
		}

	}

	public Hashtable<Integer, Problem> getHashProblems() {
		return HashProblems;
	}

	public int getCountByAllProblems() {
		return new ProblemDAO().getCountByFields(null);
	}


	public int getLastPageByFields(TreeMap<String, Object> fields) {
		int count = new ProblemDAO().getCountByFields(fields);
		int pagesize = ApplicationScope.getAppConfig().getPageSize();
		return count % pagesize == 0 ? count / pagesize : (count / pagesize) + 1;
	}

	public ArrayList<Problem> getAllProblems() {
		return new ProblemDAO().getAllProblems();
	}

	/**
	 * ?????? OnlineUser ????????? Insert ??????????????????????????? InsertProblem, ??????????????????
	 * 
	 * @return
	 */
	public Problem getUnfinishedProblem(OnlineUser onlineUser) {
		TreeMap<String, Object> fields = new TreeMap<String, Object>();
		fields.put("ownerid", onlineUser.getId());
		fields.put("display", Problem.DISPLAY.unfinished.name());
		for (Problem problem : new ProblemDAO().getProblemByFields(fields, null, 0)) {
			return problem;
		}
		return ProblemFactory.getNullproblem();
	}


	public Hashtable<String, TreeSet<Problemid>> getProblemtabProblemidset() {
		if (ProblemService.HashProblemtabProblemidset.size() == 0) {
			AppConfig appConfig = ApplicationScope.getAppConfig();
			for (Problemtab problemtab : appConfig.getProblemtabs()) {
				ProblemService.HashProblemtabProblemidset.put(problemtab.getId(),
						this.getProblemidSetByTabidDisplay(problemtab.getId(), Problem.DISPLAY.open));
			}
		}
		return HashProblemtabProblemidset;
	}

	private TreeSet<Problemid> getProblemidSetByTabidDisplay(String tabid, Problem.DISPLAY display) {
		TreeMap<String, Object> fields = new TreeMap<String, Object>();
		fields.put("tabid", tabid);
		fields.put("display", display.name());
		return new ProblemDAO().getProblemidSetByFields(fields, null, 0);
	}

	public boolean isexitProblemid(Problemid problemid) {
		TreeMap<String, Object> fields = new TreeMap<String, Object>();
		fields.put("problemid", problemid.toString());
		return new ProblemDAO().getCountByFields(fields) > 0;
	}

	public ArrayList<Problem> getLastestProblems(int number) {
		ArrayList<Problem> problems = new ProblemDAO().getLastestProblems(number);
		return problems;
	}

	/**
	 * ???????????????????????? problemid
	 * 
	 * @param prefix
	 * @return
	 */
	//
	//
	//

	/**
	 * ????????????????????? problemid
	 * 
	 * @return
	 */
	public Problemid createNextProblemid() {
		if (allProblemids.size() == 0) {
			allProblemids = new ProblemDAO().getAllProblemids();
		}

		for (char prefix = ApplicationScope.getAppConfig().getProblemid_prefix().charAt(0); prefix <= 'z'; prefix++) {
			for (int serial = 1; serial < 1000; serial++) {
				Problemid next = new Problemid(prefix + new DecimalFormat("000").format(serial));
				if (!allProblemids.contains(next)) {
					return next;
				}
			}
		}
		return null;
	}

	public Problem ImportProblemJson(OnlineUser onlineUser, File file)
			throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper(); 

		AppConfig appConfig = ApplicationScope.getAppConfig();
		Problem problem = new Problem();
		ExportProblem exportProblem = mapper.readValue(file, ExportProblem.class);
		Problemid nextproblemid = this.createNextProblemid();

		problem.setProblemid(nextproblemid);
		problem.setTitle(exportProblem.getTitle());
		problem.setTimelimits(exportProblem.getTimelimits());
		ArrayList<Problemtab> tabs = appConfig.getProblemtabs();
		problem.setTabid(tabs.get(0).getId());
		problem.setMemorylimit(exportProblem.getMemorylimit());
		problem.setBackgrounds(exportProblem.getBackgrounds());
		problem.setLocale(exportProblem.getLocale());
		problem.setContent(exportProblem.getContent());
		problem.setTheinput(exportProblem.getTheinput());
		problem.setTheoutput(exportProblem.getTheoutput());
		problem.setSampleinput(exportProblem.getSampleinput());
		problem.setSampleoutput(exportProblem.getSampleoutput());
		problem.setHint(exportProblem.getHint());
		problem.setLanguage(exportProblem.getLanguage());
		problem.setScores(exportProblem.getScores());
		problem.setSamplecode(exportProblem.getSamplecode());
		problem.setComment(exportProblem.getComment());
		problem.setTestfilelength(
				Math.min(exportProblem.getTestinfiles().size(), exportProblem.getTestoutfiles().size()));

		problem.setJudgemode(exportProblem.getJudgemode());
		problem.setDifficulty(exportProblem.getDifficulty());
		problem.setOwnerid(onlineUser.getId());
		problem.setReference(exportProblem.getReference());
		problem.setSortable(exportProblem.getSortable());
		problem.setKeywords(exportProblem.getKeywords());
		problem.setInserttime(exportProblem.getInserttime());
		problem.setUpdatetime(exportProblem.getUpdatetime());
		problem.setWa_visible(exportProblem.getErrmsg_visible());

		problem.setSpecialjudge_language(exportProblem.getSpecialjudge_language());
		problem.setSpecialjudge_code(exportProblem.getSpecialjudge_code());
		if (!"".equals(problem.getSpecialjudge_code().trim())) {
			File SpecialJudgeFile = problem.getSpecialJudgeFile();
			FileTools.writeStringToFile(SpecialJudgeFile, problem.getSpecialjudge_code(), appConfig.getRsyncAccount());
		}

		problem.setDisplay(Problem.DISPLAY.hide);

		ProblemimageDAO imageDao = new ProblemimageDAO();
		Base64 base64 = new Base64();

		List<Problemimage> images = exportProblem.getProblemimages();
		for (Problemimage image : images) {
			int oldid = image.getId();
			image.setProblemid(problem.getProblemid().toString());
			image.setFile(new ByteArrayInputStream(base64.decode(image.getFilebytes())));
			int imageid = imageDao.insert(image);
			problem.setContent(this.replaceImageForImport(oldid, imageid, problem.getContent()));
			problem.setTheinput(this.replaceImageForImport(oldid, imageid, problem.getTheinput()));
			problem.setTheoutput(this.replaceImageForImport(oldid, imageid, problem.getTheoutput()));
			problem.setHint(this.replaceImageForImport(oldid, imageid, problem.getHint()));
		}

		int testfilelength = problem.getTestfilelength();
		for (int i = 0; i < testfilelength; i++) {
			try {
				this.doCreateTestfile(problem, i, exportProblem.getTestinfiles().get(i),
						exportProblem.getTestoutfiles().get(i));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		int newpid = insert(problem);

		Problem newproblem = getProblemByPid(newpid);

		return newproblem;
	}

	/**
	 * ?????????????????? img ????????? #id (id>0)??? ????????????????????????????????? imageid
	 * 
	 * @param html
	 * @return
	 */

	/**
	 * ???????????????????????? imgsrc #id ????????? ShowImage?id=xx
	 * 
	 * @param id
	 * @param html
	 * @return
	 */
	private String replaceImageForImport(int oldid, int imageid, String html) {
		Source source = new Source(html);
		OutputDocument document = new OutputDocument(source);
		List<Element> imglist = source.getAllElements(HTMLElementName.IMG);
		String urlpattern = ShowImageServlet.class.getAnnotation(WebServlet.class).urlPatterns()[0];
		urlpattern = urlpattern.substring(1);
		for (Element img : imglist) {
			String src = img.getAttributeValue("src");
			if (src.equals(urlpattern + "?id=" + oldid)) {
				document.replace(img.getAttributes().get("src").getValueSegment(), urlpattern + "?id=" + imageid);
			}
		}
		return document.toString();
	}

	/**
	 * ??????????????????????????????
	 * 
	 * @throws AccessException
	 */
	public void insertInitProblems() {
		if (this.getCountByAllProblems() > 0) {
			return;
		}
		ObjectMapper mapper = new ObjectMapper(); 

		AppConfig appConfig = ApplicationScope.getAppConfig();
		ArrayList<Problemtab> problemtabs = appConfig.getProblemtabs();

		Problem problem = new Problem();
		problem.setProblemid(new Problemid("a001"));
		problem.setTitle("??????");

		problem.setTestfilelength(4);

		problem.setScores(problem.autoScoring(problem.getTestfilelength()));
		double[] timelimits = new double[problem.getTestfilelength()];
		Arrays.fill(timelimits, 1);
		problem.setTimelimits(timelimits);

		problem.setLanguage(new Language("C", "c"));
		problem.setTabid(problemtabs.get(0).getId());
		problem.setMemorylimit(64);
		problem.setBackgrounds(StringTool.String2TreeSet("[????????????]"));
		problem.setLocale(Problem.locale_zh_TW);
		problem.setContent("<p>????????????????????????????????????????????? <br />" + "?????????????????????????????????????????????????????????????????????????????????</p>");
		problem.setTheinput("???????????????");
		problem.setTheoutput("?????????????????????");
		String[] sampleinputs = new String[] { "world", "C++", "Taiwan" };
		try {
			problem.setSampleinput(mapper.writeValueAsString(sampleinputs));
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String[] sampleoutputs = new String[] { "hello, world", "hello, C++", "hello, Taiwan" };
		try {
			problem.setSampleoutput(mapper.writeValueAsString(sampleoutputs));
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		problem.setHint("<p>????????????????????????????????????????????? <a href=\"UserGuide.jsp#Samplecode\">" + "???????????????</a></p>");
		problem.setSamplecode("");
		problem.setJudgemode(ServerInput.MODE.Tolerant);
		problem.setDifficulty(1);
		problem.setOwnerid(1);
		problem.setReference("Brian Kernighan");
		problem.setInserttime(new Timestamp(System.currentTimeMillis()));
		problem.setUpdatetime(new Timestamp(System.currentTimeMillis()));
		problem.setDisplay(Problem.DISPLAY.open);
		this.insert(problem);
	}

	/**
	 * ????????? problem ??????????????????????????? db ??????
	 * 
	 * @param problem
	 * @return
	 */
	public Problem downloadProblemAttachfiles(Problem problem, int serverport) {
		problem.setContent(this.downloadImage(problem.getProblemid(), problem.getContent(), serverport));
		problem.setContent(this.downloadAudio(problem.getProblemid(), problem.getContent(), serverport));
		problem.setTheinput(this.downloadImage(problem.getProblemid(), problem.getTheinput(), serverport));
		problem.setTheinput(this.downloadAudio(problem.getProblemid(), problem.getTheinput(), serverport));
		problem.setTheoutput(this.downloadImage(problem.getProblemid(), problem.getTheoutput(), serverport));
		problem.setTheoutput(this.downloadAudio(problem.getProblemid(), problem.getTheoutput(), serverport));
		problem.setHint(this.downloadImage(problem.getProblemid(), problem.getHint(), serverport));
		problem.setHint(this.downloadAudio(problem.getProblemid(), problem.getHint(), serverport));
		return problem;
	}

	/**
	 * ??????????????? html ???????????? img ??????????????????????????????
	 * 
	 * @param html
	 * @return
	 */
	private String downloadImage(Problemid problemid, String html, int serverport) {
		Source source = new Source(html);
		OutputDocument document = new OutputDocument(source);
		List<Element> imglist = source.getAllElements(HTMLElementName.IMG);
		String src = null;
		for (Element img : imglist) {
			src = img.getAttributeValue("src");
			String urlpattern = ShowImageServlet.class.getAnnotation(WebServlet.class).urlPatterns()[0];
			urlpattern = urlpattern.substring(1);
			if (src.startsWith(urlpattern)) {
				continue;
			}

			File file = new File(ApplicationScope.getSystemTmp(), System.currentTimeMillis() + "");
			URL url;
			try {
				if (!src.startsWith("http")) {
					url = new URL("http://127.0.0.1:" + serverport + "/"
							+ (ApplicationScope.getAppRoot().getName().equals("ROOT") ? ""
									: ApplicationScope.getAppRoot().getName() + "/")
							+ src);
				} else {
					url = new URL(src);
				}
				FileUtils.copyURLToFile(url, file);

				int max_MB = 6;
				if (file.length() > max_MB * 1024 * 1024) { 
					throw new DataException("?????????????????????????????????(" + max_MB + " MB)");
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
				continue;
			} catch (Exception e) {
				e.printStackTrace();
				new LogDAO().insert(new Log(problemid + ": " + src + " ???????????????", this.getClass(), e));
				continue;
			}

			try {
				Problemimage problemimage = new Problemimage();
				problemimage.setProblemid(problemid.toString());
				problemimage.setFilename(url.getFile());
				problemimage.setFiletype(url.openConnection().getContentType());
				problemimage.setFilesize(0); 
				problemimage.setFile(new FileInputStream(file));
				problemimage.setDescript(url.toString());
				int problemimageid = new ProblemimageDAO().insert(problemimage);
				document.replace(img.getAttributes().get("src").getValueSegment(),
						urlpattern + "?id=" + problemimageid);
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
		}
		return document.toString();
	}

	/**
	 * ?????? autio ??? src ?????????<br/>
	 * ?????? src ??????????????????
	 * 
	 * @return
	 */
	private String getAudioSrc(Element audio) {
		String src = audio.getAttributeValue("src");
		if (src != null) {
			return src;
		}

		Element audio_source = audio.getFirstElement(HTMLElementName.SOURCE);
		if (audio_source != null) {
			return audio_source.getAttributeValue("src");
		}
		return "";
	}

	/**
	 * ????????????????????? url ?????????
	 * 
	 * @return
	 */
	private void replaceAudioSrc(OutputDocument document, Element audio, String urlpattern, int id) {
		String src = audio.getAttributeValue("src");
		if (src != null) {
			document.replace(audio.getAttributes().get("src").getValueSegment(), urlpattern + "?id=" + id);
			return;
		}
		Element audio_source = audio.getFirstElement(HTMLElementName.SOURCE);
		if (audio_source != null) {
			document.replace(audio_source.getAttributes().get("src").getValueSegment(), urlpattern + "?id=" + id);
			return;
		}
	}

	/**
	 * ??????????????? html ???????????? audio ??????????????????????????????
	 * 
	 * @param html
	 * @return
	 */
	private String downloadAudio(Problemid problemid, String html, int serverport) {
		Source source = new Source(html);
		OutputDocument document = new OutputDocument(source);
		List<Element> audios = source.getAllElements(HTMLElementName.AUDIO);
		String src = null;
		for (Element audio : audios) {
			src = this.getAudioSrc(audio);
			String urlpattern = ShowImageServlet.class.getAnnotation(WebServlet.class).urlPatterns()[0];
			urlpattern = urlpattern.substring(1);
			if (src == null || src.startsWith(urlpattern)) {
				continue;
			}

			File file = new File(ApplicationScope.getSystemTmp(), System.currentTimeMillis() + "");
			URL url;
			try {
				if (!src.startsWith("http")) {
					url = new URL("http://127.0.0.1:" + serverport + "/"
							+ (ApplicationScope.getAppRoot().getName().equals("ROOT") ? ""
									: ApplicationScope.getAppRoot().getName() + "/")
							+ src);
				} else {
					url = new URL(src);
				}
				FileUtils.copyURLToFile(url, file);

				int max_MB = 6;
				if (file.length() > max_MB * 1024 * 1024) { 
					throw new DataException("?????????????????????????????????(" + max_MB + " MB)");
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
				continue;
			} catch (Exception e) {
				e.printStackTrace();
				new LogDAO().insert(new Log(problemid + ": " + src + " ???????????????", this.getClass(), e));
				continue;
			}

			try {
				Problemimage problemimage = new Problemimage();
				problemimage.setProblemid(problemid.toString());
				problemimage.setFilename(url.getFile());
				problemimage.setFiletype(url.openConnection().getContentType());
				problemimage.setFilesize(0); 
				problemimage.setFile(new FileInputStream(file));
				problemimage.setDescript(url.toString());
				int problemimageid = new ProblemimageDAO().insert(problemimage);
				this.replaceAudioSrc(document, audio, urlpattern, problemimageid);
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			}
		}
		return document.toString();
	}

	/**
	 * ???????????????????????????????????????????????????????????????...???
	 * 
	 * @param onlineUser
	 * @param page
	 * @return
	 */
	public ArrayList<Problem> getMyProblems(OnlineUser onlineUser, int page) {
		TreeSet<String> rules = new TreeSet<String>();
		if (onlineUser.isNullUser()) {
			return new ArrayList<Problem>();
		} else {
			rules.add("display!='" + Problem.DISPLAY.deprecated.name() + "'");
			rules.add("display!='" + Problem.DISPLAY.unfinished.name() + "'");
			rules.add("ownerid=" + onlineUser.getId());
		}
		return new ProblemDAO().getProblemsByRules(rules, "updatetime DESC", page);
	}

	/**
	 * ?????? ???????????????????????????????????????????????? EditProblems ??? <br>
	 * ?????? problem ?????? deprecated ???????????????<br>
	 * 
	 * @param rules
	 *            rules ??? AND ??????
	 * @param orderby
	 *            ?????? null ???????????????
	 * @param page
	 *            page==0 ??????????????????
	 * @return
	 */
	public ArrayList<Problem> getEditableProblems(OnlineUser onlineUser, TreeSet<String> rules, String orderby,
			int page) {
		if (onlineUser.isNullUser()) {
			return new ArrayList<Problem>();
		} else if (onlineUser.getIsDEBUGGER()) {
		} else if (onlineUser.getIsMANAGER()) {
			rules.add("display!='" + Problem.DISPLAY.deprecated.name() + "'");
		} else {
			rules.add("display!='" + Problem.DISPLAY.deprecated.name() + "'");
			rules.add("ownerid=" + onlineUser.getId());
		}
		rules.add("display!='" + Problem.DISPLAY.unfinished.name() + "'");
		return new ProblemDAO().getProblemsByRules(rules, orderby, page);
	}

	/**
	 * 
	 * @param onlineUser
	 * @param page
	 * @return
	 */
	public ArrayList<Problem> getSpecialProblems(OnlineUser onlineUser, int page) {
		TreeMap<String, Object> fields = new TreeMap<String, Object>();
		if (onlineUser.isNullUser()) {
			return new ArrayList<Problem>();
		} else if (onlineUser.getIsDEBUGGER()) {
		} else if (onlineUser.getIsMANAGER()) {
			fields.put("display!", Problem.DISPLAY.deprecated.name());
			fields.put("display!", Problem.DISPLAY.unfinished.name());
		} else {
			fields.put("display!", Problem.DISPLAY.deprecated.name());
			fields.put("display!", Problem.DISPLAY.unfinished.name());
			fields.put("ownerid", onlineUser.getId());

		}
		fields.put("judgemode", "Special");
		return new ProblemDAO().getProblemByFields(fields, "updatetime DESC", page);
	}

	/**
	 * 
	 * @param onlineUser
	 * @param page
	 * @return
	 */
	public ArrayList<Problem> getVerifyingProblems(OnlineUser onlineUser, int page) {
		TreeMap<String, Object> fields = new TreeMap<String, Object>();
		if (onlineUser.isNullUser()) {
			return new ArrayList<Problem>();
		} else if (onlineUser.getIsDEBUGGER()) {
		} else {
			fields.put("display!", Problem.DISPLAY.deprecated.name());
			fields.put("display!", Problem.DISPLAY.unfinished.name());
			fields.put("ownerid", onlineUser.getId());

		}
		fields.put("display", Problem.DISPLAY.verifying.name());
		return new ProblemDAO().getProblemByFields(fields, "updatetime DESC", page);
	}

	/**
	 * ???????????????????????????????????????????????????????????????...???
	 * 
	 * @param onlineUser
	 * @param rules
	 * @param orderby
	 * @param page
	 * @return
	 */
	public ArrayList<Problem> getNotopenProblems(OnlineUser onlineUser, int page) {

		TreeSet<String> rules = new TreeSet<String>();
		if (onlineUser.isNullUser()) {
			return new ArrayList<Problem>();
		} else if (onlineUser.getIsDEBUGGER()) {
		} else if (onlineUser.getIsMANAGER()) {
			rules.add("display!='" + Problem.DISPLAY.deprecated.name() + "'");
			rules.add("display!='" + Problem.DISPLAY.open.name() + "'");
			rules.add("display!='" + Problem.DISPLAY.unfinished.name() + "'");
		} else {
			rules.add("display!='" + Problem.DISPLAY.deprecated.name() + "'");
			rules.add("display!='" + Problem.DISPLAY.open.name() + "'");
			rules.add("display!='" + Problem.DISPLAY.unfinished.name() + "'");
			rules.add("ownerid=" + onlineUser.getId());
		}
		return new ProblemDAO().getProblemsByRules(rules, "updatetime DESC", page);

	}

	/**
	 * ?????????????????????
	 * 
	 * @param session
	 * @param orderby
	 * @return
	 */
	public ArrayList<Problem> getAllProlems(OnlineUser onlineUser, String orderby) {
		if (onlineUser.getIsDEBUGGER()) {
			ArrayList<Problem> problems = new ProblemDAO().getProblemByFields(new TreeMap<String, Object>(), orderby,
					0);
			return problems;
		} else {
			return new ArrayList<Problem>();
		}
	}

	/**
	 * ??????????????? Rule ???????????????????????? problemBean ????????? Open ??????
	 * 
	 * @param rules
	 * @param orderby
	 * @param page
	 * @return
	 */
	public ArrayList<Problem> getOpenProblemsByRules(TreeSet<String> rules, String orderby, int page) {
		rules.add("display='" + Problem.DISPLAY.open.name() + "'");
		return new ProblemDAO().getProblemsByRules(rules, orderby, page);
	}

	public ArrayList<Problem> getOpenProblemsByFields(TreeMap<String, Object> fields, String orderby, int page) {
		fields.put("display", Problem.DISPLAY.open.name());
		return new ProblemDAO().getProblemByFields(fields, orderby, page);
	}


	/**
	 * ???????????????????????????????????????????????????
	 * 
	 * @return
	 */
	public int getCountByProblemSubmissions(Problem problem) {
		TreeMap<String, Object> rules = new TreeMap<String, Object>();
		rules.put("pid", problem.getId());
		rules.put("visible", Solution.VISIBLE.open.getValue());
		return new SolutionDAO().executeCount(rules);
	}

	public LinkedHashMap<User, Integer> getTopAuthors(int topn) {
		return new ProblemDAO().getTopAuthors(topn);
	}


	/**
	 * ??????????????????????????????
	 * 
	 * @param session_account
	 * @param problemid
	 * @throws DataException
	 */
	public void doPreJudge(OnlineUser onlineUser, Problem problem) {
		ServerOutput[] serverOutputs;
		JudgeObject judgeObject;
		try {
			judgeObject = new JudgeObject(JudgeObject.PRIORITY.Prejudge, new Solution(), problem);
		} catch (Exception e) {
			serverOutputs = new ServerOutput[1];
			ServerOutput serverOutput = new ServerOutput();
			serverOutput.setSession_account(onlineUser.getAccount());
			serverOutput.setJudgement(ServerOutput.JUDGEMENT.SE);
			serverOutput.setReason(ServerOutput.REASON.SYSTEMERROR);
			serverOutput.setHint("???????????????????????????" + e.getLocalizedMessage());
			serverOutputs[0] = serverOutput;
			new JudgeServer().summaryServerOutput(JudgeObject.PRIORITY.Prejudge, problem, null, serverOutputs);
			e.printStackTrace();
			return;
		}
		JudgeProducer judgeProducer = new JudgeProducer(JudgeFactory.getJudgeServer(), judgeObject);
		Thread judgeProducerThread = new Thread(judgeProducer);
		judgeProducerThread.start();
	}

	/**
	 * ??????????????????????????????,??????????????????????????????
	 * 
	 * @param index
	 *            ???????????????????????? 0 ??????
	 * @param infiledata
	 *            ???????????????
	 * @param outfiledata
	 *            ???????????????
	 * @throws IOException
	 */
	public void doCreateTestfile(Problem problem, int index, String infiledata, String outfiledata) throws IOException {
		AppConfig appConfig = ApplicationScope.getAppConfig();
		String rsyncaccount = appConfig.getRsyncAccount();

		File infile = new File(appConfig.getTestdataPath(problem.getProblemid()),
				problem.getTESTDATA_FILENAME(index) + ".in");
		FileTools.writeStringToFile(infile, infiledata, rsyncaccount);

		File outfile = new File(appConfig.getTestdataPath(problem.getProblemid()),
				problem.getTESTDATA_FILENAME(index) + ".out");
		FileTools.writeStringToFile(outfile, outfiledata, rsyncaccount);
	}

	/**
	 * ?????? problem.timelimits ???????????? index
	 * 
	 * @return
	 */
	public double[] removeTimelimitsIndex(double[] timelimits, int index) {
		double[] newtimelimits = new double[timelimits.length - 1];
		for (int i = 0; i < newtimelimits.length; i++) {
			if (i >= index) {
				newtimelimits[i] = timelimits[i + 1];
			} else {
				newtimelimits[i] = timelimits[i];
			}
		}
		return newtimelimits;
	}

	/**
	 * ?????? problem.scores ???????????? index
	 * 
	 * @return
	 */
	public int[] removeScoresIndex(int[] scores, int index) {
		int[] newscores = new int[scores.length - 1];
		for (int i = 0; i < newscores.length; i++) {
			if (i >= index) {
				newscores[i] = scores[i + 1];
			} else {
				newscores[i] = scores[i];
			}
		}
		return newscores;
	}

	/**
	 * ???????????? index ?????????
	 * 
	 * @param index
	 * @throws DataException
	 * @throws AccessException
	 */
	public void doDeleteTestdataPair(Problem problem, int index) {

		AppConfig appConfig = ApplicationScope.getAppConfig();
		File filein = new File(appConfig.getTestdataPath(problem.getProblemid()),
				problem.getTESTDATA_FILENAME(index) + ".in");
		File fileout = new File(appConfig.getTestdataPath(problem.getProblemid()),
				problem.getTESTDATA_FILENAME(index) + ".out");
		int testfilelength = problem.getTestdataPairs().size();
		int[] scores = problem.getScores();
		double[] timelimits = problem.getTimelimits();
		if (filein.exists() && fileout.exists()) {
			problem.getTestdataPairs().remove(index);

			filein.delete();
			fileout.delete();

			for (int i = index; i < testfilelength; i++) {
				new File(appConfig.getTestdataPath(problem.getProblemid()), problem.getTESTDATA_FILENAME(i + 1) + ".in")
						.renameTo(new File(appConfig.getTestdataPath(problem.getProblemid()),
								problem.getTESTDATA_FILENAME(i) + ".in"));
				new File(appConfig.getTestdataPath(problem.getProblemid()),
						problem.getTESTDATA_FILENAME(i + 1) + ".out")
								.renameTo(new File(appConfig.getTestdataPath(problem.getProblemid()),
										problem.getTESTDATA_FILENAME(i) + ".out"));
			}

			problem.setTestfilelength(testfilelength - 1);
			problem.setTimelimits(this.removeTimelimitsIndex(timelimits, index));
			problem.setScores(this.removeScoresIndex(scores, index));
			problem.getTestdataPairs().remove(index);
		}
		this.update(problem);
		this.rsyncTestdataByProblem(problem, UserFactory.getNullOnlineUser());
	}

	/**
	 * ??????????????????????????????????????????
	 */
	public void rsyncAllTestdatas() {
		AppConfig appConfig = ApplicationScope.getAppConfig();
		File testdataPath = new File(appConfig.getTestdataPath() + File.separator);
		if (!testdataPath.exists()) {
			ArrayList<String> debugs = new ArrayList<String>();
			debugs.add(testdataPath.toString() + "?????????");
			throw new DataException("??????????????????(" + testdataPath.getName() + ")?????????????????????????????????????????????????????????????????????", debugs);
		}


		File serverTestdataPath = appConfig.getServerConfig().getTestdataPath();

		new RunCommand().exec(RunCommand.Command.whoami.toString());


		String rsync = "sudo -u " + appConfig.getRsyncAccount() + " ";
		rsync += "rsync -av --delete -e \"ssh -p " + appConfig.getServerConfig().getSshport() + "\" "
				+ appConfig.getTestdataPath() + File.separator + " " + appConfig.getServerConfig().getRsyncAccount()
				+ "@" + appConfig.getServerUrl().getHost() + ":" + serverTestdataPath;

		RunCommand runCommand = new RunCommand();
		runCommand.exec(rsync);

		if (runCommand.getErrStream().size() != 0) {
			throw new DataException("??????????????????????????????(rsyncAllTestdatas)????????????????????????\n", runCommand.getErrStream());
		}

	}

	/**
	 * ??? Webapp ??????????????? push ??? ???????????? ??? rsync ?????????
	 * 
	 * @param problem
	 */
	public void rsyncTestdataByProblem(Problem problem, OnlineUser onlineUser) {
		AppConfig appConfig = ApplicationScope.getAppConfig();
		File testfile = appConfig.getTestdataPath(problem.getProblemid());
		if (!testfile.exists()) {
			ArrayList<String> debugs = new ArrayList<String>();
			debugs.add(testfile.getPath() + "?????????");
			logger.info("??????????????????????????????????????????????????????????????????????????????????????????(" + testfile + ")");
			throw new DataException("??????????????????????????????????????????????????????????????????????????????????????????(" + testfile.getName() + ")", debugs);
		}


		File serverTestdataPath = appConfig.getServerConfig().getTestdataPath();

		String rsync = "";
		rsync += RunCommand.Command.sudo + " -u " + appConfig.getRsyncAccount() + " ";
		rsync += RunCommand.Command.rsync + " -av --delete -e \"" + RunCommand.Command.ssh + " -p "
				+ appConfig.getServerConfig().getSshport() + "\" " + appConfig.getTestdataPath(problem.getProblemid())
				+ " ";
		InetAddress address;
		try {
			address = InetAddress.getByName(appConfig.getServerUrl().getHost());
			if (!address.isLoopbackAddress()) {
				rsync += appConfig.getServerConfig().getRsyncAccount() + "@" + appConfig.getServerUrl().getHost() + ":";
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		rsync += serverTestdataPath;

		RunCommand runCommand = new RunCommand();
		runCommand.exec(rsync);

		if (runCommand.getErrStream().size() != 0) {
			RunCommand whoami = new RunCommand();
			whoami.exec(RunCommand.Command.whoami.name());

			ArrayList<String> debugs = new ArrayList<String>();
			debugs.addAll(whoami.getOutStream());
			debugs.add(rsync);
			debugs.addAll(runCommand.getErrStream());
			logger.warning("debugs rsync=" + rsync);
			logger.warning("debugs errStream=" + runCommand.getErrStream());
			String title = "??????????????????????????????(rsyncTestdataByProblem)????????????????????????";
			Alert alert = new Alert(Alert.TYPE.DATAERROR, title, "", "", null, debugs, onlineUser);
			throw new DataException(alert);
		}
	}

	/**
	 * ??? SpecialJudge ???????????????????????? JudgeServer
	 */
	public void rsyncSpecialJudge(Problem problem, OnlineUser onlineUser) {
		AppConfig appConfig = ApplicationScope.getAppConfig();

		File specialjudge = appConfig.getSpecialPath(problem.getProblemid());
		if (!specialjudge.exists()) {
			FileTools.forceMkdir(specialjudge, appConfig.getRsyncAccount());
		}

		if (!specialjudge.exists()) {
			ArrayList<String> debugs = new ArrayList<String>();
			debugs.add(specialjudge.getPath() + "?????????");
			throw new DataException("???????????????Special Judge ????????????????????????????????????????????????????????????????????????????????????(" + specialjudge.getName() + ")",
					debugs);
		}

		String rsync = RunCommand.Command.sudo + " -u " + appConfig.getRsyncAccount() + " ";
		rsync += RunCommand.Command.rsync + " --chmod=Du=rwx,Dg=rwx,Fu=rw,Fg=rw -av --delete -e \""
				+ RunCommand.Command.ssh + " -p " + appConfig.getServerConfig().getSshport() + "\" "
				+ appConfig.getSpecialPath(problem.getProblemid()) + " ";
		InetAddress address;
		try {
			address = InetAddress.getByName(appConfig.getServerUrl().getHost());
			if (!address.isLoopbackAddress()) {
				rsync += appConfig.getServerConfig().getRsyncAccount() + "@" + appConfig.getServerUrl().getHost() + ":";
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		rsync += appConfig.getServerConfig().getSpecialPath();

		RunCommand runCommand = new RunCommand();
		runCommand.exec(rsync);

		if (runCommand.getErrStream().size() != 0) {
			RunCommand whoami = new RunCommand();
			whoami.execWhoami();

			ArrayList<String> debugs = new ArrayList<String>();
			debugs.addAll(whoami.getOutStream());
			debugs.add(rsync);
			debugs.addAll(runCommand.getErrStream());
			String title = "???????????????????????????Special Judge?????????????????????????????????";
			String subtitle = "";
			String content = "";
			Alert alert = new Alert(Alert.TYPE.DATAERROR, title, subtitle, content, null, debugs, onlineUser);
			throw new DataException(alert);
		}

	}

	/**
	 * ??? TreeSet ???????????? problems ??????????????? keyrowd
	 * 
	 * @return
	 */
	public TreeSet<String> getSuggestKeywords(HttpSession session, String servletPath) {
		OnlineUser onlineUser = UserFactory.getOnlineUser(session);

		TreeSet<String> keywords = new TreeSet<String>();
		TreeSet<String> rules = new TreeSet<String>();
		if (onlineUser.isNullUser()) {
			return new TreeSet<String>();
		} else {
			rules.add("display!='" + Problem.DISPLAY.deprecated.name() + "'");
			rules.add("ownerid=" + onlineUser.getId());
		}
		rules.add("display!='" + Problem.DISPLAY.unfinished.name() + "'");
		rules.add("keywords!='" + new TreeSet<String>().toString() + "' AND keywords!=''");

		for (Problem problem : new ProblemDAO().getProblemsByRules(rules, null, 0)) {
			keywords.addAll(problem.getKeywords());
		}
		return keywords;
	}

	/**
	 * ??? TreeSet ???????????? problems ??????????????? background
	 * 
	 * @return
	 */
	public TreeSet<String> getSuggestBackgrounds(HttpSession session, String servletPath) {
		OnlineUser onlineUser = UserFactory.getOnlineUser(session);
		TreeSet<String> rules = new TreeSet<String>();
		if (onlineUser.isNullUser()) {
			return new TreeSet<String>();
		} else {
			rules.add("display!='" + Problem.DISPLAY.deprecated.name() + "'");
			rules.add("ownerid=" + onlineUser.getId());
		}
		rules.add("display!='" + Problem.DISPLAY.unfinished.name() + "'");
		rules.add("backgrounds!='" + new TreeSet<String>().toString() + "' AND backgrounds!=''");

		TreeSet<String> backgrounds = new TreeSet<String>();
		for (Problem problem : new ProblemDAO().getProblemsByRules(rules, null, 0)) {
			backgrounds.addAll(problem.getBackgrounds());
		}
		return backgrounds;
	}

	/**
	 * ????????????????????????????????????
	 * 
	 * @param tab_search
	 * @param difficulty
	 * @param keyword
	 * @param background
	 * @param searchword
	 * @param author
	 * @param locale
	 * @return
	 */
	public TreeSet<String> getSearchRules(String tag, String[] tab_search, String[] difficulty, String keyword,
			String background, String searchword, String locale, String ownerid) {
		tag = StringEscapeUtils.escapeSql(tag);
		keyword = StringEscapeUtils.escapeSql(keyword);
		background = StringEscapeUtils.escapeSql(background);
		searchword = StringEscapeUtils.escapeSql(searchword);
		locale = StringEscapeUtils.escapeSql(locale);

		TreeSet<String> rules = new TreeSet<String>();
		if (tag != null && !"".equals(tag.trim())) {
			if (tag.length() > 50) {
				throw new DataException("tag ?????????");
			}
			rules.add("keywords LIKE '%" + tag + "%' OR backgrounds LIKE '%" + tag + "%' OR reference LIKE '%" + tag
					+ "%'");
			return rules;
		}
		if (ownerid != null && ownerid.trim().matches("[0-9]+")) {
			rules.add("ownerid=" + Integer.parseInt(ownerid.trim()));
		}

		if (searchword != null && !"".equals(searchword)) {
			if (searchword.length() > 30) {
				throw new DataException("searchword ?????????(" + searchword + ")");
			}
			rules.add("problemid='" + searchword + "' OR title LIKE '%" + searchword + "%' OR backgrounds LIKE '%"
					+ searchword + "%' OR content LIKE '%" + searchword + "%' OR theinput LIKE '%" + searchword
					+ "%' OR theoutput LIKE '%" + searchword + "%' OR hint LIKE '%" + searchword
					+ "%' OR reference LIKE '%" + searchword + "%' OR keywords LIKE '%" + searchword + "%'");
		}

		if (tab_search != null && tab_search.length > 0) {
			String tab_rule = "tabid='" + tab_search[0] + "'";
			for (int i = 1; i < tab_search.length; i++) {
				tab_rule += " OR tabid='" + tab_search[i] + "'";
			}
			rules.add(tab_rule);
		}
		if (difficulty != null && difficulty.length > 0) {
			String difficulty_rule = "difficulty=" + difficulty[0];
			for (int i = 1; i < difficulty.length; i++) {
				difficulty_rule += " OR difficulty=" + difficulty[i];
			}
			rules.add(difficulty_rule);
		}
		if (keyword != null && !"".equals(keyword)) {
			if (keyword.length() > 30) {
				throw new DataException("keyword ?????????");
			}
			rules.add("keywords LIKE '%" + keyword + "%' OR backgrounds LIKE '%" + keyword + "%' OR reference LIKE '%"
					+ keyword + "%'");
		}
		if (background != null && !"".equals(background)) {
			if (background.length() > 30) {
				throw new DataException("background ?????????");
			}

			rules.add("backgrounds LIKE '%" + background + "%'");
		}

		if (locale != null && !"".equals(locale)) {
			rules.add("locale ='" + locale + "'");
		}


		if (rules.size() == 0) {
			throw new DataException("??????????????????????????????????????????");
		}

		return rules;
	}

	/**
	 * ????????????????????????????????? ????????????/????????????
	 * 
	 */
	public void recountAllProblem_UserCount() {
		ProblemDAO problemDao = new ProblemDAO();
		for (Problem problem : this.getAllProblems()) {
			problemDao.recountProblem_UserCount(problem);
		}
	}

	/**
	 * ????????????????????? ????????????/????????????(?????????????????????)
	 * 
	 * @param problem
	 */
	public void recountProblem_UserCount(Problemid problemid) {
		new ProblemDAO().recountProblem_UserCount(this.getProblemByProblemid(problemid));
	}

	/**
	 * ????????????????????? ????????????/????????????(?????????????????????)
	 * 
	 * @param problem
	 */
	public void recountProblem_UserCount(Problem problem) {
		new ProblemDAO().recountProblem_UserCount(problem);
	}

}
