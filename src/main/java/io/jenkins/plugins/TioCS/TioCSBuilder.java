package io.jenkins.plugins.TioCS;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

public class TioCSBuilder extends Builder implements SimpleBuildStep {

    private final String imagename;
    private boolean useOnPrem;

    @DataBoundConstructor
    public TioCSBuilder(String imagename) {
        this.imagename = imagename;
    }

    public String getImageName() {
        return imagename;
    }

    public boolean useOnPrem() {
        return useOnPrem;
    }

    @DataBoundSetter
    public void useOnPrem(boolean useOnPrem) {
        this.useOnPrem = useOnPrem;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        run.addAction(new TioCSAction(imagename));
        if (useOnPrem) {
            listener.getLogger().println("Testing image " + imagename + " with on-premise inspector.");
        } else {
            listener.getLogger().println("Testing image " + imagename + " by uploading directly to Tenable.io cloud.");
        }
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckImageName(@QueryParameter String value, @QueryParameter boolean useOnPrem)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingImageName());
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.TioCSBuilder_DescriptorImpl_DisplayName();
        }

    }

}
