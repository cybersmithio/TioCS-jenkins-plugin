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

    private final String name;
    private final String tiorepo;
    private boolean useOnPrem;

    @DataBoundConstructor
    public TioCSBuilder(String name, String tiorepo) {
        this.name = name;
        this.tiorepo = tiorepo;
    }

    public String getName() {
        return name;
    }

    public String getTioRepo() {
        return tiorepo;
    }

    public boolean isUseOnPrem() {
        return useOnPrem;
    }

    @DataBoundSetter
    public void setUseOnPrem(boolean useOnPrem) {
        this.useOnPrem = useOnPrem;
    }

    public void setTioRepo(String tiorepo) {
        this.tiorepo = tiorepo;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        run.addAction(new TioCSAction(name));
        if (useOnPrem) {
            listener.getLogger().println("Testing image " + name + " with on-premise inspector.");
        } else {
            listener.getLogger().println("Testing image " + name + " by uploading directly to Tenable.io cloud.");
        }
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {


        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter boolean useOnPrem)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingName());
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
