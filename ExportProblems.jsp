<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@ page isELIgnored="false"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<jsp:include page="include/CommonHead.jsp" />
<script type="text/javascript"
	src="./jscripts/jquery.timeout.interval.idle.js"></script>
<script type="text/javascript" src="./jscripts/json2.js"></script>
<!-- <script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/js/jquery-ui-1.7.3.custom.min.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/ui.core.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/ui.tabs.js"></script>
<link type="text/css"
	href="./jscripts/jquery-ui-1.7.3/css/ui-lightness/jquery-ui-1.7.3.custom.css"
	rel="Stylesheet" />
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.core.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.widget.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.mouse.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.button.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.draggable.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.position.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.resizable.js"></script>
<script type="text/javascript"
	src="./jscripts/jquery-ui-1.7.3/development-bundle/ui/jquery.ui.dialog.js"></script>
 -->
<script type="text/javascript" src="EditProblems.js"></script>
</head>
<c:set var="tab" value="${tab}" />
<fmt:setLocale value="${sessionScope.session_locale}" />
<fmt:setBundle basename="resource" />
<%-- <jsp:useBean id="problemBean" class="tw.zerojudge.Beans.ProblemBean" /><jsp:setProperty
	name="problemBean" property="session_account"
	value="${sessionScope.onlineUser.account}" />
 --%>
<body id="${tab}">
	<jsp:include page="include/Header.jsp" />
	<div id="showdetail_dialog"
		style="cursor: default; padding: 10px; display: none;">
		<div id="jsondetail" style="text-align: left">
			<span id="line1">??? <span id="testlength"></span>?????????(<span
				id="score"></span>)???
			</span> <span id="judgement"></span> <span id="info"></span><br /> <span
				id="reason"></span>
			<pre id="hint"></pre>
		</div>
		<br />
	</div>
	<div id="rejudge_dialog" problemid=""
		style="display: none; cursor: default; padding: 20px; margin: auto;">
		<h2>
			???????????????????????????????????????????????????????????? <span id="submitnum"></span> ???<br /> <br />
			???????????????????????????
		</h2>
		<br />
	</div>
	<div class="content_individual">
		<ul id="ui-bottom">
			<li><a
				href="InsertProblem?locale=${sessionScope.session_locale}">????????????</a></li>

			<form name="form2" method="post" action=""
				style="margin: 0px; display: inline;" onsubmit="checkForm(this);">
				??????????????? <input name="searchword" type="text" value="" size="10" />
			</form>

			<span id="advsearch" class="FakeLink">????????????</span>
			<c:if test="${sessionScope.onlineUser.isDEBUGGER}">
				<span class="DEBUGGEROnly">
					<li class="DEBUGGEROnly"><a href="#" id="ImportProblems">*
							????????????</a></li>
					<li class="DEBUGGEROnly"><a href="./ExportProblems"
						id="ExportProblems">* ????????????</a></li>
				</span>
			</c:if>
		</ul>
		<hr />
		<ul id="tabmenu">
			<li class="tab10"><a href="./EditProblems?tabid=MYPROBLEM">??????????????????</a></li>
			<c:forEach var="tab" items="${tabs}" varStatus="varstatus">
				<li class="tab0${varstatus.count-1}"><a
					href="./EditProblems?tabid=${tab.id}">${tab.name}</a></li>
			</c:forEach>
			<c:if
				test="${sessionScope.onlineUser.isDEBUGGER }">
				<li class="tab13"><a href="./EditProblems?tabid=VERIFYING">?????????</a></li>
			</c:if>
			<c:if test="${sessionScope.onlineUser.isDEBUGGER}">
				<li class="tab11"><a href="./EditProblems?tabid=NOTOPEN">????????????</a></li>
			</c:if>
		</ul>
		<c:choose>
			<c:when test="${fn:length(problems)!=0}">
				<table width="100%" align="center">
					<tr>
						<th width="6%"><input type="checkbox" name="checkbox"
							value="checkbox" title="??????" /> ??????</th>
						<th></th>
						<th>??????</th>
						<th width="4%">&nbsp;</th>
						<th width="12%">&nbsp;</th>
						<th width="6%">&nbsp;</th>
						<th width="12%">??????</th>
					</tr>
					<c:forEach var="problem" items="${problems}" varStatus="varstatus">
						<jsp:setProperty name="problemBean" property="problemid"
							value="${problem.problemid}" />
						<tr problemid="${problem.problemid}">
							<td id="problemid"><input type="checkbox" name="checkbox"
								value="checkbox" /> ${problem.problemid}</td>
							<td><c:if
									test="${sessionScope.onlineUser.isDEBUGGER}">
									<select id="problemdisplay" name="problemdisplay"
										problemdisplay="${problem.display}"
										problemid="${problem.problemid}">
										<option value="---">---</option>
										<option value="open">?????????</option>
										<option value="verifying">?????????</option>
										<option value="practice">?????????</option>
										<option value="hide">??????</option>
									</select>
								</c:if></td>
							<td><c:set var="problem" value="${problem}" scope="request" />
								<jsp:include page="include/div/ProblemDisplay.jsp" /> <a
								href="ShowProblem?problemid=${problem.problemid}">${fn:escapeXml(problem.title)}</a>
								<span style="font-size: small; color: #AAAAAA"
								title="[????????????] -- ??????">[${fn:escapeXml(problem.backgrounds)}]
									-- ${fn:escapeXml(problem.reference)} (??????: <span id="owner">${problem.owner}</span>)
							</span> <c:if test="${problemBean.isOutsideImage==true}">
									<img src="images/block_user.png" title="????????????????????????" />
								</c:if></td>
							<td>&nbsp;</td>
							<td id="td_prejudge">&nbsp;</td>
							<td style="font-size: smaller">&nbsp;</td>
							<td><a href="#"> <img src="./images/save.png" style="height: 1.2em;"
									title="????????????" id="exportProblem" /></a></td>
						</tr>
					</c:forEach>
				</table>
			</c:when>
			<c:otherwise>
				<div>
					<fmt:message key="NO_DATA" />
				</div>
			</c:otherwise>
		</c:choose>
		<br />
	</div>
	<jsp:include page="include/Footer.jsp" />
</body>
</html>
