package io.jenkins.plugins.TioCS;

import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class TioCSAction implements RunAction2 {

    private transient Run run;
    private String TioRepo;
    private String name;
    private String TioUsername;
    private String TioPassword;
    private String TioAccessKey;
    private String TioSecretKey;
    private Double FailCVSS;
    private boolean useOnPrem;
    private Double HighxCVSS;
    private Int NumOfVulns;


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

    public TioCSAction(String name, String TioRepo, String TioUsername, String TioPassword, String TioAccessKey,
        String TioSecretKey, Double FailCVSS,Double HighCVSS, boolean useOnPrem, Int NumOfVulns) {
        this.name = name;
        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.TioSecretKey = TioSecretKey;
        this.TioUsername = TioUsername;
        this.TioPassword = TioPassword;
        this.FailCVSS = FailCVSS;
        this.FailCVSS = HighCVSS;
        this.useOnPrem = useOnPrem;
        this.NumOfVulns = NumOfVulns

    }

    public String getName() {
        return name;
    }

    public String getTioRepo() {
        return TioRepo;
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

    public Double getNumOfVulns() {
        return NumOfVulns;
    }

    public boolean getuseOnPrem() {
        return useOnPrem;
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
