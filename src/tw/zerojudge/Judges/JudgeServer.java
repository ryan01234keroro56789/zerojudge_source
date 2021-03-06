package tw.zerojudge.Judges;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.text.DecimalFormat;
import java.util.TreeSet;
import java.util.logging.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import tw.jiangsir.Utils.Exceptions.AccessException;
import tw.jiangsir.Utils.Exceptions.DataException;
import tw.jiangsir.Utils.Scopes.ApplicationScope;
import tw.zerojudge.Configs.AppConfig;
import tw.zerojudge.DAOs.ContestDAO;
import tw.zerojudge.DAOs.LogDAO;
import tw.zerojudge.DAOs.ProblemService;
import tw.zerojudge.DAOs.SolutionService;
import tw.zerojudge.DAOs.TestcodeDAO;
import tw.zerojudge.DAOs.UserService;
import tw.zerojudge.DAOs.VClassStudentDAO;
import tw.zerojudge.Factories.JudgeFactory;
import tw.zerojudge.Factories.UserFactory;
import tw.zerojudge.Judges.ServerOutput.JUDGEMENT;
import tw.zerojudge.Objects.Problemid;
import tw.zerojudge.Tables.Log;
import tw.zerojudge.Tables.Problem;
import tw.zerojudge.Tables.Solution;
import tw.zerojudge.Tables.Testcode;
import tw.zerojudge.Tables.VClassStudent;
import tw.zerojudge.Utils.*;

public class JudgeServer {

	ObjectMapper mapper = new ObjectMapper(); 

	Logger logger = Logger.getLogger(this.getClass().getName());

	public JudgeServer() {
	}

	/**
	 * Producer
	 */
	public synchronized void addJudgeObject(JudgeObject judgeObject) {

		JudgeFactory.addToQueue(judgeObject);
		notify();
	}

	private URLConnection sendServerInput(URL url, JudgeObject judgeObject) throws DataException {
		URLConnection conn;
		OutputStreamWriter wr;
		try {
			conn = url.openConnection();
			conn.setDoOutput(true);
			conn.setReadTimeout(500 * 1000);
			wr = new OutputStreamWriter(conn.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			throw new DataException("???????????????????????????????????????????????????????????????????????????????????????", e);
		}
		ServerInput serverinput = new ServerInput();
		AppConfig appConfig = ApplicationScope.getAppConfig();
		serverinput.setServername(appConfig.getServerConfig().getServername());
		if (judgeObject.getPriority() == JudgeObject.PRIORITY.Prejudge) {
			serverinput.setCode(judgeObject.getProblem().getSamplecode());
			serverinput.setLanguage(judgeObject.getProblem().getLanguage().getName());
			serverinput.setCodename("code_" + judgeObject.getProblem().getProblemid());
		} else if (judgeObject.getPriority() == JudgeObject.PRIORITY.TESTCODE) {
			serverinput.setCode(judgeObject.getSolution().getCode());
			serverinput.setLanguage(judgeObject.getSolution().getLanguage().getName());
			serverinput.setCodename("code_" + judgeObject.getSolution().getId());
		} else if (judgeObject.getPriority() == JudgeObject.PRIORITY.Submit
				|| judgeObject.getPriority() == JudgeObject.PRIORITY.Rejudge) {
			serverinput.setCode(judgeObject.getSolution().getCode());
			serverinput.setLanguage(judgeObject.getSolution().getLanguage().getName());
			serverinput.setCodename("code_" + judgeObject.getSolution().getId());
		} else if (judgeObject.getPriority() == JudgeObject.PRIORITY.Testjudge) {
			serverinput.setCode(judgeObject.getSolution().getCode());
			serverinput.setLanguage(judgeObject.getSolution().getLanguage().getName());
			serverinput.setCodename("code_" + judgeObject.getSolution().getId());
			serverinput.setTestjudge_indata(judgeObject.getProblem().getSampleinput());
			serverinput.setTestjudge_outdata(judgeObject.getProblem().getSampleoutput());
		}
		serverinput.setPriority(judgeObject.getPriority());
		serverinput.setProblemid(judgeObject.getProblem().getProblemid().toString());
		serverinput.setMode(judgeObject.getProblem().getJudgemode());
		serverinput.setMemorylimit(judgeObject.getProblem().getMemorylimit());
		serverinput.setScores(judgeObject.getProblem().getScores());
		serverinput.setTimelimits(judgeObject.getProblem().getTimelimits());
		serverinput.setSession_account(judgeObject.getSession_account());
		serverinput.setSolutionid(judgeObject.getSolution().getId());
		serverinput.setErrmsg_visible(judgeObject.getProblem().getWa_visible());

		Problemid problemid = judgeObject.getProblem().getProblemid();
		String[] testfiles = new String[judgeObject.getProblem().getTestfilelength()];
		for (int i = 0; i < testfiles.length; i++) {
			if (serverinput.getPriority() == JudgeObject.PRIORITY.Testjudge) {
				testfiles[i] = judgeObject.getProblem().getTESTJUDGE_FILENAME(judgeObject.getSolution().getId(), i);
			} else {
				testfiles[i] = problemid + "/" + judgeObject.getProblem().getTESTDATA_FILENAME(i);
			}
		}
		serverinput.setTestfiles(testfiles);

		if (!appConfig.getIsEnableCompiler(serverinput.getLanguage())) {
			throw new DataException("??????????????? ???????????????????????? " + serverinput.getLanguage() + " ???");
		}
		String input;
		try {
			input = new DES().encrypt(mapper.writeValueAsString(serverinput));
			wr.write("input=" + input);
			wr.flush();
			wr.close();
			return conn;
		} catch (JsonGenerationException e1) {
			e1.printStackTrace();
			throw new DataException("???????????? (ServerInput) ??? JSON ???????????????", e1);
		} catch (JsonMappingException e1) {
			e1.printStackTrace();
			throw new DataException("???????????? (ServerInput) ??? JSON ???????????????", e1);
		} catch (InvalidKeyException e1) {
			throw new DataException("?????????????????????????????? 8 ??????(" + InvalidKeyException.class.getSimpleName() + ": "
					+ e1.getLocalizedMessage() + ")????????????????????????", e1);
		} catch (InvalidAlgorithmParameterException e1) {
			throw new DataException("??????????????????(" + InvalidKeyException.class.getSimpleName() + ": "
					+ e1.getLocalizedMessage() + ")????????????????????????", e1);
		} catch (Exception e1) {
			e1.printStackTrace();
			throw new DataException("???????????????????????????????????????????????????", e1);
		}
	}

	/**
	 * ????????????????????????
	 * 
	 * @return
	 * @throws AccessException
	 */
	private ServerOutput[] readServerOutputs(URLConnection conn) throws DataException {
		ServerOutput[] serverOutputs = null;

		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		String outputString = "";
		BufferedReader in = null;
		long start = System.currentTimeMillis();
		try {
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuffer lines = new StringBuffer(10000);
			String line;
			while ((line = in.readLine()) != null) {
				lines.append(line);
			}
			outputString = new DES().decrypt(lines.toString());
		} catch (Exception e) {
			e.printStackTrace();
			serverOutputs = new ServerOutput[] { new ServerOutput() };
			serverOutputs[0].setJudgement(JUDGEMENT.SE);
			serverOutputs[0].setReason(ServerOutput.REASON.SYSTEMERROR);

			if (e instanceof java.io.IOException) {
				String hint = e.getLocalizedMessage();

				if (e instanceof java.net.SocketTimeoutException) {
					serverOutputs[0].setHint(
							"????????????????????????(" + hint + ")??????????????????" + (System.currentTimeMillis() - start) / 1000 + "s");
				} else {
					serverOutputs[0].setHint(
							"????????????(" + e.getClass().getName() + ")???" + hint.replaceAll(conn.getURL().toString(), ""));
				}
			} else if (e instanceof DataException) {
				serverOutputs[0].setHint("???????????????????????????????????????????????????????????????????????????" + e.getLocalizedMessage());
			} else {
				serverOutputs[0].setHint("??????????????????????????????" + e.getLocalizedMessage());
			}
			return serverOutputs;
		}
		try {
			serverOutputs = mapper.readValue(new String(outputString.getBytes("UTF-8"), "UTF-8"), ServerOutput[].class);
			return serverOutputs;
		} catch (JsonParseException e) {
			e.printStackTrace();
			throw new DataException("???????????? (ServerInput) ????????????????????????????????????????????????????????????\n" + e.getLocalizedMessage(), e);
		} catch (JsonMappingException e) {
			e.printStackTrace();
			throw new DataException("???????????? (ServerInput) ????????????????????????????????????????????????????????????\n" + e.getLocalizedMessage(), e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new DataException("???????????? (ServerInput) ?????????????????????\n" + e.getLocalizedMessage(), e);
		}

	}

	/**
	 * Consummer
	 * 
	 * @throws DataException
	 * 
	 */
	public synchronized void doJudge() throws DataException {
		if (JudgeFactory.getJudgeQueue().size() == 0) {
			try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		logger.info("doJudge wakeup!!");
		logger.info("JudgeObjectQueue size=" + JudgeFactory.getJudgeQueue().size() + "");
		JudgeObject judgeObject = JudgeFactory.removeFromQueue();
		logger.info("JudgeObjectQueue size=" + JudgeFactory.getJudgeQueue().size() + "");
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		ServerOutput[] serverOutputs;
		AppConfig appConfig = ApplicationScope.getAppConfig();
		try {

			logger.info("doJudge: appConfig.getServerUrl()=" + appConfig.getServerUrl());
			URLConnection conn = this.sendServerInput(appConfig.getServerUrl(), judgeObject);

			serverOutputs = this.readServerOutputs(conn);
			if (serverOutputs[0].getReason() == ServerOutput.REASON.TESTDATA_NOT_FOUND) {
				new ProblemService().rsyncTestdataByProblem(judgeObject.getProblem(), UserFactory.getNullOnlineUser());
				conn = this.sendServerInput(appConfig.getServerUrl(), judgeObject);
				serverOutputs = this.readServerOutputs(conn);
			}
			if (serverOutputs[0].getReason() == ServerOutput.REASON.SPECIAL_JUDGE_NOT_FOUND) {
				new ProblemService().rsyncSpecialJudge(judgeObject.getProblem(), UserFactory.getNullOnlineUser());
				conn = this.sendServerInput(appConfig.getServerUrl(), judgeObject);
				serverOutputs = this.readServerOutputs(conn);
			}

		} catch (DataException e) {
			e.printStackTrace();
			serverOutputs = new ServerOutput[1];
			ServerOutput serverOutput = new ServerOutput();
			serverOutput.setJudgement(ServerOutput.JUDGEMENT.SE);
			serverOutput.setReason(e.getLocalizedMessage());
			serverOutput.setHint(e.getLocalizedMessage());
			serverOutputs[0] = serverOutput;
			summaryServerOutput(judgeObject.getPriority(), judgeObject.getProblem(), judgeObject.getSolution(),
					serverOutputs);
			notify();
			throw new DataException(e); 
		}
		summaryServerOutput(judgeObject.getPriority(), judgeObject.getProblem(), judgeObject.getSolution(),
				serverOutputs);
		notify();
	}

	/**
	 * ??? ServerOutput ?????????????????????????????????
	 * 
	 * @param priority
	 * @param problem
	 * @param solution
	 * @param serverOutputs
	 * @throws DataException
	 */
	public synchronized void summaryServerOutput(JudgeObject.PRIORITY priority, Problem problem, Solution solution,
			ServerOutput[] serverOutputs) {
		ServerOutput summary = new ServerOutput();

		if (priority == JudgeObject.PRIORITY.Prejudge) {
			this.fromPrejudge(problem, summary, serverOutputs);
		} else if (priority == JudgeObject.PRIORITY.TESTCODE) {
			this.fromTestcode(solution, summary, serverOutputs);
		} else if (priority == JudgeObject.PRIORITY.Submit || priority == JudgeObject.PRIORITY.Testjudge
				|| priority == JudgeObject.PRIORITY.Rejudge) {
			this.fromSubmision(solution, serverOutputs);
		}

		/**
		 * solution ??? open ????????? rebuilt
		 */
		if (solution.getVisible() == Solution.VISIBLE.open) {
			rebuiltUser(priority, solution);
		}

		if (solution.getContestid() != 0) { 
			ContestDAO contestsDAO = new ContestDAO();
			contestsDAO.updateContestRanking(solution.getUserid(), solution.getContestid());
		}
		if (solution.getVclassid() > 0 && summary.getJudgement() == ServerOutput.JUDGEMENT.AC) {
			VClassStudentDAO vclassstudentDao = new VClassStudentDAO();
			VClassStudent student = vclassstudentDao.getStudent(solution.getVclassid(), solution.getUserid());
			TreeSet<Problemid> aclist = student.getVclassaclist();
			aclist.add(solution.getProblem().getProblemid());
			student.setVclassaclist(aclist);
			vclassstudentDao.update(student);
		}

	}

	private void rebuiltUser(JudgeObject.PRIORITY priority, Solution solution) {
		if (priority == JudgeObject.PRIORITY.Submit || priority == JudgeObject.PRIORITY.Rejudge
				|| priority == JudgeObject.PRIORITY.MANUALJUDGE) {
			new UserService().rebuiltUserStatisticByDataBase(new UserService().getUserById(solution.getUserid()));
		}
	}

	/**
	 * ??????????????? SubmitCode 
	 */
	private void fromSubmision(Solution solution, ServerOutput[] serverOutputs) {
		ServerOutput.JUDGEMENT judgement = null;
		long timeusage = 0;
		int memoryusage = 0;
		int scores = 0;
		boolean isAC = true;
		int count = 0;
		for (ServerOutput serverOutput : serverOutputs) {
			if (serverOutput == null) {
				continue;
			}
			count++;
			judgement = serverOutput.getJudgement();
			if (judgement != ServerOutput.JUDGEMENT.AC) {
				isAC = false;
			}
			timeusage = Math.max(timeusage, serverOutput.getTimeusage());
			memoryusage = Math.max(memoryusage, serverOutput.getMemoryusage());
			if (serverOutput.getJudgement() == ServerOutput.JUDGEMENT.AC) {
				scores += serverOutput.getScore();
			}
		}
		if (count > 1) {
			solution.setJudgement(isAC ? ServerOutput.JUDGEMENT.AC : ServerOutput.JUDGEMENT.NA);
		} else {
			solution.setJudgement(judgement);
		}

		solution.setServeroutputs(serverOutputs);
		solution.setTimeusage(timeusage);
		solution.setMemoryusage(memoryusage);
		solution.setScore(scores);
		new SolutionService().update(solution);
	}

	/**
	 * ??????????????? ????????????????????????????????????????????????
	 */
	private void fromTestcode(Solution solution, ServerOutput summary, ServerOutput[] serverOutputs) {
		Testcode testcode = new TestcodeDAO().getTestcodeById(solution.getId());
		testcode.setActual_status(summary.getJudgement().name());
		try {
			testcode.setActual_detail(mapper.writeValueAsString(serverOutputs));
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		new TestcodeDAO().update(testcode);
		return;
	}

	/**
	 * ??????????????? Problem ?????????
	 */
	private void fromPrejudge(Problem problem, ServerOutput summary, ServerOutput[] serverOutputs) {
		ServerOutput.JUDGEMENT judgement = ServerOutput.JUDGEMENT.Waiting;
		long timeusage = 0;
		int memoryusage = 0;
		boolean isAC = true;
		int count = 0;
		for (ServerOutput serverOutput : serverOutputs) {
			if (serverOutput == null) {
				continue;
			}
			count++;
			judgement = serverOutput.getJudgement();
			if (judgement != ServerOutput.JUDGEMENT.AC) {
				isAC = false;
			}
			timeusage = Math.max(timeusage, serverOutput.getTimeusage());
			memoryusage = Math.max(memoryusage, serverOutput.getMemoryusage());
		}
		if (count > 1) {
			problem.setPrejudgement(isAC ? ServerOutput.JUDGEMENT.AC : ServerOutput.JUDGEMENT.NA);
		} else {
			problem.setPrejudgement(judgement);
		}
		try {
			problem.setServeroutputs(mapper.writeValueAsString(serverOutputs));
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		new ProblemService().update(problem);
		Log log = new Log();
		log.setUri(this.getClass().getName());
		log.setSession_account(summary.getSession_account());
		log.setTabid(Log.TABID.WARNING);
		log.setMessage(problem.getProblemid() + ". " + problem.getTitle() + "(owner:" + problem.getOwner()
				+ ") ???????????? -- " + problem.getPrejudgement());
		log.setStacktrace("?????????\n" + problem.getPrejudgement() + "(" + problem.getSummary() + ") : "
				+ problem.getServeroutputs() + "\n??????????????????\n" + problem.getSamplecode());
		new LogDAO().insert(log);
		return;
	}

	/**
	 * ??? timeusage ????????????????????????
	 * 
	 * @param timeusage
	 * @return
	 */
	public static String parseTimeusage(Long timeusage) {
		return timeusage < 1000 ? timeusage + "ms" : new DecimalFormat("######.#").format(timeusage / 1000.0) + "s";
	}

	/**
	 * ??? memoryusage ????????????????????????
	 * 
	 * @param memoryusage
	 * @return
	 */
	public static String parseMemoryusage(Integer memoryusage) {
		return memoryusage < 1000 ? memoryusage + "KB"
				: new DecimalFormat("######.#").format((int) memoryusage / 1000.0) + "MB";
	}

	public boolean isThreadActive() {
		return Thread.currentThread().isAlive();
	}

}
