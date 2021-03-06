package tw.zerojudge.Utils.GoogleLogin;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import tw.jiangsir.Utils.Exceptions.Alert;
import tw.jiangsir.Utils.Exceptions.DataException;
import tw.jiangsir.Utils.Scopes.ApplicationScope;
import tw.jiangsir.Utils.Scopes.SessionScope;
import tw.zerojudge.Configs.AppConfig;
import tw.zerojudge.DAOs.UserService;
import tw.zerojudge.Tables.OnlineUser;
import tw.zerojudge.Tables.User;
import tw.zerojudge.Tables.User.AUTHHOST;

/**
 * Servlet implementation class OAuth2CallbackServlet
 */
@WebServlet(urlPatterns = { "/callbackGoogleLogin" })
public class CallbackGoogleLoginServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private ObjectMapper mapper = new ObjectMapper(); 

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		String code = request.getParameter("code");
		Verifier verifier = new Verifier(code);
		AppConfig appConfig = ApplicationScope.getAppConfig();
		String apiKey = appConfig.getClient_id(); 
		String apiSecret = appConfig.getClient_secret();
		String redirect_uri = request.getScheme() + "://" + request.getServerName()
				+ (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort())
				+ request.getContextPath()
				+ CallbackGoogleLoginServlet.class.getAnnotation(WebServlet.class).urlPatterns()[0];

		OAuthService service = new ServiceBuilder().provider(Google2Api.class).apiKey(apiKey).apiSecret(apiSecret)
				.callback(redirect_uri)
				.scope("https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email")
				.build();

		Token accessToken;
		try {
			accessToken = service.getAccessToken(null, verifier);
		} catch (Exception e) {
			e.printStackTrace();
			Alert alert = new Alert(e);
			alert.setTitle("????????? Google ??????????????????????????????");
			alert.setSubtitle(
					"???????????????????????????????????????????????????????????????????????????<br>" + "????????????????????????????????????<h2><a href=\"Login\">????????????</a></h2>???????????????????????????????????????");
			throw new DataException(alert);
		}

		OAuthRequest req = new OAuthRequest(Verb.GET, "https://www.googleapis.com/oauth2/v1/userinfo?alt=json");
		service.signRequest(accessToken, req);
		Response resp = req.send();
		String _sBody = resp.getBody();
		GoogleUser googleUser = mapper.readValue(_sBody, GoogleUser.class);

		OnlineUser onlineUser = new SessionScope(session).getOnlineUser();
		if (onlineUser == null || onlineUser.isNullOnlineUser()) { 
			User user = new UserService().getUserByGoogle(googleUser);
			if (user == null || user.isNullUser()) {
				Alert alert = new Alert();
				try {
					HashMap<String, URI> uris = new HashMap<String, URI>();
					uris.put("??? Google Account ????????????", new URI(
							"./" + InsertGoogleUserServlet.class.getAnnotation(WebServlet.class).urlPatterns()[0]));
					alert.setUris(uris);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				alert.setTitle("?????? Google Account (" + googleUser.getEmail() + ") ???????????????" + "?????????????????????"
						+ "???????????????????????????????????? Google ?????????????????????Google Account ????????????????????????");
				throw new DataException(alert);
			} else {
				onlineUser = new OnlineUser(session, user);
				this.doGoogleLogin(session, onlineUser, googleUser);
			}
		} else {
			this.doBindGoogleUser(session, onlineUser, googleUser);
		}
		new SessionScope(session).setGoogleUser(googleUser);

		response.sendRedirect("./");
	}

	public void doGoogleLogin(HttpSession session, OnlineUser onlineUser, GoogleUser googleUser) throws IOException {
		User user = new UserService().getUserById(onlineUser.getId());
		user.setAuthhost(AUTHHOST.Google);
		user.setEmail(googleUser.getEmail());
		user.setPicture(googleUser.getPicture());
		user.setPicture(googleUser.getPicture());
		URL url = new URL(googleUser.getPicture());
		try {
			user.setPicturetype(url.openConnection().getContentType());
		} catch (IOException e) {
			e.printStackTrace();
		}
		user.setPictureblob(IOUtils.toByteArray(url));
		if (!onlineUser.getJoinedContest().getIsRunning()) {
			user.setJoinedcontestidToNONE();
			onlineUser.setJoinedcontestidToNONE();
		}
		new UserService().update(user);

		onlineUser.setAuthhost(AUTHHOST.Google);
		onlineUser.setEmail(googleUser.getEmail());
		onlineUser.setPicture(googleUser.getPicture());
		new SessionScope(session).setOnlineUser(onlineUser);

	}

	/**
	 * ?????????????????? onlineUser ?????? ??????????????? GoogleUser
	 * 
	 * @param session
	 * @param onlineUser
	 * @param googleUser
	 * @throws IOException
	 */
	protected void doBindGoogleUser(HttpSession session, OnlineUser onlineUser, GoogleUser googleUser)
			throws IOException {
		User user = new UserService().getUserByGoogle(googleUser);
		if (user != null && !user.isNullUser() && onlineUser.getId().intValue() != user.getId().intValue()) {
			throw new DataException("????????????(" + googleUser.getEmail() + ")?????????????????????????????????????????????????????????");
		}
		this.doGoogleLogin(session, onlineUser, googleUser);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
	}

}
