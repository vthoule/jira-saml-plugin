package com.bitium.jira.logout;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.JiraVelocityUtils;
import com.atlassian.jira.util.velocity.VelocityRequestContextFactory;
import com.atlassian.jira.web.ExecutingHttpRequest;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.velocity.VelocityManager;
import com.bitium.jira.config.SAMLJiraConfig;
import com.bitium.saml.SAMLStatusCodesProvider.StatusCodesExplained;
import com.bitium.saml.servlet.SsoLoginServlet;

public class SAMLLoggedOut extends JiraWebActionSupport {
	private static final long serialVersionUID = 1L;

	private SAMLJiraConfig saml2Config;

	private final VelocityManager velocityManager;
	private final JiraAuthenticationContext authenticationContext;
	private final ApplicationProperties applicationProperties;

	public SAMLLoggedOut(final SAMLJiraConfig saml2Config, final JiraAuthenticationContext authenticationContext, final VelocityManager _velocityManager, final ApplicationProperties _applicationProperties) {
		this.saml2Config = saml2Config;
		this.authenticationContext = authenticationContext;
		this.velocityManager = _velocityManager;
		this.applicationProperties = _applicationProperties;
	}

	public String getContent() {
		Map<String, Object> contextParameters = new HashMap<String, Object>();
		Map<String, Object> createVelocityParams = createVelocityParams(contextParameters);
		createVelocityParams.put("statusCodesExplained", getStatusCodesExplained());
		String content = velocityManager.getEncodedBodyForContent(saml2Config.getLoggedOutPageTemplate(), saml2Config.getBaseUrl(), createVelocityParams);
		return content;
	}
	
	private StatusCodesExplained getStatusCodesExplained() {
		HttpServletRequest httpServletRequest = ExecutingHttpRequest.get();
		HttpSession session = httpServletRequest.getSession();
		StatusCodesExplained statusCodesExplained = (StatusCodesExplained)session.getAttribute(SsoLoginServlet.SAML_STATUSCODES_EXPLAINED_KEY); 

		if (statusCodesExplained != null) {
			Object[] args = new Object[]{
					statusCodesExplained.getKeyOfstatusCode1stLevel(),
					statusCodesExplained.getMsgCode1stlevel(),
					statusCodesExplained.getKeyOfstatusCode2ndLevel(),
					statusCodesExplained.getMsgCode2ndlevel()};
			String errorMessage = getText("saml2.statusCode.erroMessage", args);
			statusCodesExplained.setMsgError(errorMessage);
		}
		return statusCodesExplained;
	}

	public String doExecute() throws Exception {
		return "success";
	}

	protected Map<String, Object> createVelocityParams(final Map<String, Object> startingParams) {
		final Map<String, Object> params = getDefaultVelocityParams(startingParams, authenticationContext);
		final Map<String, Object> result = new HashMap<String, Object>();
		if (!params.containsKey("i18n")) {
			result.put("i18n", getI18nBean());
		}
		result.putAll(params);
		return result;
	}

	private String getBaseUrl() {
		return getVelocityRequestContextFactory().getJiraVelocityRequestContext().getBaseUrl();
	}

	private VelocityRequestContextFactory getVelocityRequestContextFactory() {
		// This would probably be better injected, but you would have to update all the subclasses' constructors
		return ComponentAccessor.getComponentOfType(VelocityRequestContextFactory.class);
	}

	protected I18nHelper getI18nBean() {
		return authenticationContext.getI18nHelper();
	}

	public Map<String, Object> getDefaultVelocityParams(Map<String, Object> startingParams, JiraAuthenticationContext authenticationContext) {
		return JiraVelocityUtils.getDefaultVelocityParams(startingParams, authenticationContext);
	}
}
