package io.jenkins.plugins.TioCS;

import com.tenable.io.api.TenableIoClient;
import com.tenable.io.api.TestBase;
import com.tenable.io.api.folders.FolderRef;
import com.tenable.io.api.scans.ScanActivity;
import com.tenable.io.api.scans.ScanRef;
import com.tenable.io.api.scans.interfaces.RunnableScan;
import com.tenable.io.api.scans.interfaces.ScanBaseOp;
import com.tenable.io.api.scans.models.*;
import com.tenable.io.api.workbenches.models.FilterAssetField;
import com.tenable.io.core.exceptions.TenableIoErrorCode;
import com.tenable.io.core.exceptions.TenableIoException;

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
    private String TioRepo;
    private boolean useOnPrem;
    private String TioAccessKey;
    private String TioSecretKey;

    @DataBoundConstructor
    public TioCSBuilder(String name, String TioRepo, String TioAccessKey, String TioSecretKey) {
        this.name = name;
        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.TioSecretKey = TioSecretKey;
    }

    public String getName() {
        return name;
    }

    public String getTioRepo() {
        return TioRepo;
    }

    public String getTioAccessKey() {
        return TioAccessKey;
    }

    public String getTioSecretKey() {
        return TioSecretKey;
    }

    public boolean isUseOnPrem() {
        return useOnPrem;
    }

    @DataBoundSetter
    public void setUseOnPrem(boolean useOnPrem) {
        this.useOnPrem = useOnPrem;
    }

    public void setTioRepo(String TioRepo) {
        this.TioRepo = TioRepo;
    }

    public void setTioAccessKey(String TioAccessKey) {
        this.TioAccessKey = TioAccessKey;
    }

    public void setTioSecretKey(String TioSecretKey) {
        this.TioSecretKey = TioSecretKey;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        run.addAction(new TioCSAction(name,TioRepo,TioAccessKey,TioSecretKey));
        if (useOnPrem) {
            listener.getLogger().println("Testing image " + name + " with on-premise inspector.  Results will go into Tenable.io repository "+TioRepo);
        } else {
            listener.getLogger().println("Testing image " + name + " by uploading directly to Tenable.io cloud.  Results will go into Tenable.io repository "+TioRepo);
            TenableIoClient client = new TenableIoClient( TioAccessKey, TioSecretKey );
        }
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {


        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter String TioRepo, @QueryParameter String TioAccessKey, @QueryParameter String TioSecretKey, @QueryParameter boolean useOnPrem)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingName());
            if (TioRepo.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioRepo());
            if (TioAccessKey.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioAccessKey());
            if (TioSecretKey.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioSecretKey());
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
