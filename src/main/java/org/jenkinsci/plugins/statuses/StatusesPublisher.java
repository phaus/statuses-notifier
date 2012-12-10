/**
 * StatusesPublisher 09.12.2012
 *
 * @author Philipp Haussleiter
 *
 */
package org.jenkinsci.plugins.statuses;

import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Mailer;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class StatusesPublisher extends Notifier {

    private static final List<String> VALUES_REPLACED_WITH_NULL = Arrays.asList("", "(Default)", "(System Default)");
    private static final Logger LOGGER = Logger.getLogger(StatusesPublisher.class.getName());
    private final String recipients;

    @DataBoundConstructor
    public StatusesPublisher(String recipients) {
        this.recipients = recipients;
    }

    public String getRecipients() {
        return recipients;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    public String createStatusMessage(AbstractBuild<?, ?> build) {
        String projectName = build.getProject().getName();
        String result = build.getResult().toString();
        StringBuilder sb = new StringBuilder();
        sb.append(recipients).append(" ");
        sb.append("Project: #").append(projectName).append(" has Status ").append(result);
        if (((DescriptorImpl) getDescriptor()).isIncludeUrl()) {
            String absoluteBuildURL = ((DescriptorImpl) getDescriptor()).getUrl() + build.getUrl();
            sb.append(" ").append(absoluteBuildURL);
        }
        return sb.toString();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        if (shouldSend(build)) {
            try {
                String newStatus = createStatusMessage(build);
                ((DescriptorImpl) getDescriptor()).sendStatus(newStatus);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unable to send tweet.", e);
            }
        }
        return true;
    }

    /**
     * Determine if this build results should be send. Uses the local settings
     * if they are provided, otherwise the global settings.
     *
     * @param build the Build object
     * @return true if we should send this build result
     */
    protected boolean shouldSend(AbstractBuild<?, ?> build) {
        if (((DescriptorImpl) getDescriptor()).onlyOnFailureOrRecovery) {
            return isFailureOrRecovery(build);
        } else {
            return true;
        }
    }

    /**
     * Determine if this build represents a failure or recovery. A build failure
     * includes both failed and unstable builds. A recovery is defined as a
     * successful build that follows a build that was not successful. Always
     * returns false for aborted builds.
     *
     * @param build the Build object
     * @return true if this build represents a recovery or failure
     */
    protected boolean isFailureOrRecovery(AbstractBuild<?, ?> build) {
        if (build.getResult() == Result.FAILURE || build.getResult() == Result.UNSTABLE) {
            return true;
        } else if (build.getResult() == Result.SUCCESS) {
            AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
            if (previousBuild != null && previousBuild.getResult() != Result.SUCCESS) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public String serverUrl;
        public boolean checkSSL;
        public String user;
        public String pass;
        public boolean onlyOnFailureOrRecovery;
        public boolean includeUrl;
        public String hudsonUrl;

        public DescriptorImpl() {
            super(StatusesPublisher.class);
            load();
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public void setCheckSSL(boolean checkSSL) {
            this.checkSSL = checkSSL;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public void setPass(String pass) {
            this.pass = pass;
        }

        public boolean isOnlyOnFailureOrRecovery() {
            return onlyOnFailureOrRecovery;
        }

        public void setOnlyOnFailureOrRecovery(boolean onlyOnFailureOrRecovery) {
            this.onlyOnFailureOrRecovery = onlyOnFailureOrRecovery;
        }

        public boolean isIncludeUrl() {
            return includeUrl;
        }

        public void setIncludeUrl(boolean includeUrl) {
            this.includeUrl = includeUrl;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public String getUser() {
            return user;
        }

        public String getPass() {
            return pass;
        }

        public boolean isCheckSSL() {
            return checkSSL;
        }

        public String getUrl() {
            return hudsonUrl;
        }

        public FormValidation doTestConnection(
                @QueryParameter("serverUrl") final String serverUrl,
                @QueryParameter("checkSSL") final boolean checkSSL,
                @QueryParameter("user") final String user,
                @QueryParameter("pass") final String pass) throws IOException, ServletException {
            try {
                System.out.println("calling " + serverUrl + " with user: " + user);
                HttpClient client = getClient(user, pass);
                GetMethod httpGet = new GetMethod(serverUrl);
                httpGet.setDoAuthentication(true);
                int status = client.executeMethod(httpGet);
                if (status == HttpStatus.SC_OK) {
                    return FormValidation.ok("Success");
                } else {
                    return FormValidation.error("Error: " + status);
                }
            } catch (Exception e) {
                return FormValidation.error("Client error : " + e.getMessage());
            }
        }

        private HttpClient getClient(String user, String pass) {
            HttpClient client = new HttpClient();
            client.getState().setCredentials(
                    // TODO we should try to get all values for specific AuthScope
                    // new AuthScope("www.verisign.com", 443, "realm"),
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(user, pass));
            return client;
        }

        public void sendStatus(String message) {
            try {
                LOGGER.log(Level.INFO, "Attempting to update status to: {0}", message);
                HttpClient client = getClient(user, pass);
                PostMethod httpPost = new PostMethod(serverUrl);
                httpPost.setParameter("text", message);
                client.executeMethod(httpPost);
            } catch (HttpException ex) {
                Logger.getLogger(StatusesPublisher.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(StatusesPublisher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // set the booleans to false as defaults
            includeUrl = false;
            onlyOnFailureOrRecovery = true;
            hudsonUrl = Mailer.descriptor().getUrl();
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            if (hudsonUrl == null) {
                // if Hudson URL is not configured yet, infer some default
                hudsonUrl = Functions.inferHudsonURL(req);
                save();
            }
            return super.newInstance(req, formData);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> type) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Statuses Notifier";
        }
    }
}
