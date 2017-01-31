package com.bitium.jira.servlet;

import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.crowd.exception.GroupNotFoundException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.OperationNotPermittedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserDetails;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.seraph.auth.Authenticator;
import com.atlassian.seraph.auth.DefaultAuthenticator;
import com.atlassian.seraph.config.SecurityConfigFactory;
import com.bitium.jira.config.SAMLJiraConfig;
import com.bitium.saml.SAMLStatusCodesProvider;
import com.bitium.saml.config.SAMLConfig;
import com.bitium.saml.servlet.SsoLoginServlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;

public class SsoJiraLoginServlet extends SsoLoginServlet {

	public SsoJiraLoginServlet(SAMLConfig _saml2Config,  SAMLStatusCodesProvider _samlStatusCodesProvider) {
		this.saml2Config = _saml2Config;
		this.samlStatusCodesProvider = _samlStatusCodesProvider;
	}
	
	@Override
	protected String getText(String _key, Object _args) {
		I18nHelper i18nHelper = ComponentAccessor.getJiraAuthenticationContext().getI18nHelper();
		return i18nHelper.getText(_key, _args);
	}
	
	protected void authenticateUserAndLogin(HttpServletRequest request, HttpServletResponse response, String username) throws Exception {

		log.warn("Authenticating with username " + username);

		Authenticator authenticator = SecurityConfigFactory.getInstance().getAuthenticator();

		if (authenticator instanceof DefaultAuthenticator) {
			Method getUserMethod = DefaultAuthenticator.class.getDeclaredMethod("getUser", new Class[] { String.class });
			getUserMethod.setAccessible(true);
			Object userObject = getUserMethod.invoke(authenticator, new Object[] { username });

			// if not found, see if we're allowed to auto-create the user
			if (userObject == null) {
				userObject = tryCreateOrUpdateUser(username);
			}

			// If Auto-Create, we grant the user to Default Group (It may have been created for another application using JIRA as User Directory)
			if (userObject != null && userObject instanceof ApplicationUser) {
				if (saml2Config.getAutoCreateUserFlag()) {
					addUserToGroup((ApplicationUser)userObject);
				}
			}

			if (userObject != null && userObject instanceof ApplicationUser) {
				Boolean result = authoriseUserAndEstablishSession((DefaultAuthenticator) authenticator, userObject, request, response);

				if (result) {
					redirectToSuccessfulAuthLandingPage(request, response);
					return;
				}
			} else {
				log.error("No Usser found for " + username);
			}
		} else {
			log.error("Not able to instanciate an Authenticator");
		}

		redirectToLoginWithSAMLError(response, null, "user_not_found");
	}

	@Override
	protected Object tryCreateOrUpdateUser(String username) throws Exception {
		if (saml2Config.getAutoCreateUserFlag()) {
			log.warn("Creating user account for " + username);

			UserManager userManager = ComponentAccessor.getUserManager();

			String fullName = credential.getAttributeAsString("cn");
			String email = credential.getAttributeAsString("mail");
			UserDetails newUserDetails = new UserDetails(username, username).withEmail(email);
			ApplicationUser newUser = userManager.createUser(newUserDetails);

//			addUserToGroup(newUser);

			return newUser;
		} else {
			// not allowed to auto-create user
			log.error("User not found and auto-create disabled: " + username);
		}
		return null;
	}

	private void addUserToGroup(ApplicationUser newUser) throws GroupNotFoundException, UserNotFoundException, OperationNotPermittedException, OperationFailedException {
		GroupManager groupManager = ComponentAccessor.getGroupManager();
		String defaultGroup = saml2Config.getAutoCreateUserDefaultGroup();
		if (defaultGroup.isEmpty()) {
			defaultGroup = SAMLJiraConfig.DEFAULT_AUTOCREATE_USER_GROUP;
		}
		Group defaultJiraGroup = groupManager.getGroup(defaultGroup);
		if (defaultJiraGroup != null) {
			groupManager.addUserToGroup(newUser, defaultJiraGroup);
		}
	}

	@Override
	protected String getDashboardUrl() {
		return saml2Config.getBaseUrl() + "/secure/Dashboard.jspa";
	}

	@Override
	protected String getLoginFormUrl() {
		return saml2Config.getBaseUrl() + "/login.jsp";
	}
	
	@Override
	protected String getLogoutUrl() {
		return saml2Config.getBaseUrl() + "/Logout!default.jspa";
	}
	
	@Override
	protected String getLoggedOutUrl() {
		return saml2Config.getBaseUrl() + "/secure/SAMLLoggedOut.jspa";
	}
}
