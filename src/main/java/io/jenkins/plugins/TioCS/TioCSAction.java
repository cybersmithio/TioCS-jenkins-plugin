package io.jenkins.plugins.TioCS;

import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class TioCSAction implements RunAction2 {

    private transient Run run;
    private String TioRepo;
    private String name;

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

    public TioCSAction(String name, String TioRepo, String TioUsername, String TioPassword, String TioAccessKey, String TioSecretKey) {
        this.name = name;
        this.TioRepo = TioRepo;
    }

    public String getName() {
        return name;
    }

    public String getTioRepo() {
        return TioRepo;
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
