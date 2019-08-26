package io.jenkins.plugins.TioCS;

import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class TioCSAction implements RunAction2 {

    private transient Run run;
    private String TioRepo;
    private String name;
    private String ImageTag;

    private String TioUsername;
    private String TioPassword;
    private String TioAccessKey;
    private String TioSecretKey;
    private Double FailCVSS;
    private boolean useOnPrem;
    private boolean FailMalware;
    private Double HighCVSS;
    private Integer NumOfVulns;
    private boolean malwareDetected;
    private boolean DebugInfo;
    private String Workflow;


    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    public Run getRun() {
        return run;
    }

    public TioCSAction(String name, String ImageTag, String TioRepo, String TioUsername, String TioPassword, String TioAccessKey,
        String TioSecretKey, Double FailCVSS,Double HighCVSS, boolean useOnPrem, Integer NumOfVulns, boolean FailMalware,
        boolean malwareDetected, boolean DebugInfo, String Workflow) {
        this.name = name;
        this.ImageTag = ImageTag;
        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.TioSecretKey = TioSecretKey;
        this.TioUsername = TioUsername;
        this.TioPassword = TioPassword;
        this.FailCVSS = FailCVSS;
        this.HighCVSS = HighCVSS;
        this.useOnPrem = useOnPrem;
        this.NumOfVulns = NumOfVulns;
        this.FailMalware = FailMalware;
        this.malwareDetected = malwareDetected;
        this.DebugInfo = DebugInfo;
        this.Workflow = Workflow;
    }

    public String getName() {
        return name;
    }

    public String getImageTag() {
        return ImageTag;
    }

    public String getTioRepo() {
        return TioRepo;
    }

    public String getWorkflow() {
        return Workflow;
    }

    public String getTioUsername() {
        return TioUsername;
    }

    public String getTioPassword() {
        return TioPassword;
    }

    public String getTioAccessKey() {
        return TioAccessKey;
    }

    public String getTioSecretKey() {
        return TioSecretKey;
    }

    public Double getFailCVSS() {
        return FailCVSS;
    }

    public Double getHighCVSS() {
        return HighCVSS;
    }

    public Integer getNumOfVulns() {
        return NumOfVulns;
    }

    public boolean getFailMalware() {
        return FailMalware;
    }

    public boolean getuseOnPrem() {
        return useOnPrem;
    }

    public boolean getmalwareDetected() {
        return malwareDetected;
    }

    public boolean getDebugInfo() {
        return DebugInfo;
    }

    @Override
    public String getIconFileName() {
        return "document.png";
    }

    @Override
    public String getDisplayName() {
        return "Tenable.io Container Security";
    }

    @Override
    public String getUrlName() {
        return "tiocs";
    }
}
