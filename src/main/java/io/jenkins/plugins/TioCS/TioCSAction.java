package io.jenkins.plugins.TioCS;

import hudson.model.Action;
import hudson.model.Run;
import jenkins.model.RunAction2;

import hudson.PluginWrapper;
import hudson.model.Hudson;
import hudson.PluginManager;


public class TioCSAction implements RunAction2 {

    private transient Run run;
    private String TioRepo;
    private String name;
    private String ImageTag;

    private String TioAccessKey;
    private Double FailCVSS;
    private boolean useOnPrem;
    private boolean FailMalware;
    private Double HighCVSS;
    private Integer NumOfVulns;
    private boolean malwareDetected;
    private boolean DebugInfo;
    private String Workflow;
    private String ImageSize;


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

    public TioCSAction(String name, String ImageTag, String TioRepo, String TioAccessKey,
        Double FailCVSS,Double HighCVSS, boolean useOnPrem, Integer NumOfVulns, boolean FailMalware,
        boolean malwareDetected, boolean DebugInfo, String Workflow, String ImageSize) {
        this.name = name;
        this.ImageTag = ImageTag;
        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.FailCVSS = FailCVSS;
        this.HighCVSS = HighCVSS;
        this.useOnPrem = useOnPrem;
        this.NumOfVulns = NumOfVulns;
        this.FailMalware = FailMalware;
        this.DebugInfo = DebugInfo;
        this.Workflow = Workflow;
        this.ImageSize = ImageSize;

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
        switch(Workflow) {
            case "Test":
                return("Test image only.");
            case "Evaluate":
                return("Evaluating test results only.");
            case "TestEvaluate":
                return("Test image and evaluate results.");
        }

        return "";
    }

    public String getTioAccessKey() {
        return TioAccessKey;
    }

    public Double getFailCVSS() {
        return FailCVSS;
    }

    public String getHighCVSS() {
        if ( Workflow.equals("Test")  ) {
            return "Not evaluated";
        }
        return HighCVSS.toString();
    }

    //That's right, I spelled colour the proper way.
    public String getHighCVSSColour() {
        if ( Workflow.equals("Test")  ) {
            return "";
        }
        if ( HighCVSS >= 10.0) {
            return "#EE3333";
        }
        if ( HighCVSS >= 7.0) {
            return "#FA8304";
        }
        if ( HighCVSS >= 4.0) {
            return "#FCC326";
        }
        if ( HighCVSS >= 0.1) {
            return "#3FAD29";
        }
        return "#357ABD";

    }


    public String getNumOfVulns() {
        if ( Workflow.equals("Test")  ) {
            return "Not evaluated";
        }
        return NumOfVulns.toString();
    }

    public boolean getFailMalware() {
        return FailMalware;
    }

    public boolean getuseOnPrem() {
        return useOnPrem;
    }

    public String getImageSize() {
        return ImageSize;
    }

    public String getmalwareDetected() {
        if ( Workflow.equals("Test")  ) {
            return "Not evaluated";
        }
        if ( malwareDetected ) {
            return "True";
        } else {
            return "False";
        }
    }

    public String getMalwareColour() {
        if ( Workflow.equals("Test")  ) {
            return "";
        }

        if ( malwareDetected ) {
            return "#EE3333";
        }
        return "#3FAD29";


    }


    public boolean getDebugInfo() {
        return DebugInfo;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/TioCS/images/tenable-icon.png";
        //return "/plugin/tiocs/images/24x24/tenable-icon.png";
        //return "/tenable-icon.png";
        //return "/jenkins/plugin/TioCS/tenable-icon.png";
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
