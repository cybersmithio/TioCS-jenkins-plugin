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
    private boolean useOnPrem;
    private Double HighCVSS;
    private String HighCVSSColour;
    private Integer NumOfVulns;
    private boolean malwareDetected;
    private String MalwareColour;
    private boolean DebugInfo;
    private String Workflow;
    private String ImageSize;
    private String ComplianceStatus;


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
        Double HighCVSS, boolean useOnPrem, Integer NumOfVulns, boolean malwareDetected, boolean DebugInfo,
        String Workflow, String ImageSize, String ComplianceStatus) {
        this.name = name;
        if ( !(ImageTag.equals("") ) ) {
            this.ImageTag=ImageTag;
        } else {
            this.ImageTag = "latest";
        }
        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.Workflow = Workflow;
        this.HighCVSS = HighCVSS;
        if ( Workflow.equals("Test")  ) {
            HighCVSSColour="";
            MalwareColour="";
        } else {
            if ( HighCVSS >= 10.0) {
                HighCVSSColour="#EE3333";
            } else if ( HighCVSS >= 7.0) {
                HighCVSSColour="#FA8304";
            } else if ( HighCVSS >= 4.0) {
                HighCVSSColour="#FCC326";
            } else if ( HighCVSS >= 0.1) {
                HighCVSSColour="#3FAD29";
            } else {
                HighCVSSColour="#357ABD";
            }
            if ( malwareDetected ) {
                return "#EE3333";
            } else {
                return "#3FAD29";
            }

        }

        this.useOnPrem = useOnPrem;
        this.NumOfVulns = NumOfVulns;
        this.DebugInfo = DebugInfo;
        this.ImageSize = ImageSize;
        this.ComplianceStatus = ComplianceStatus;

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

    public String getHighCVSS() {
        if ( Workflow.equals("Test")  ) {
            return "Not evaluated";
        }
        return HighCVSS.toString();
    }

    //That's right, I spelled colour the proper way.
    public String getHighCVSSColour() {
        return HighCVSSColour;
    }

    public String getNumOfVulns() {
        if ( Workflow.equals("Test")  ) {
            return "Not evaluated";
        }
        return NumOfVulns.toString();
    }

    public boolean getuseOnPrem() {
        return useOnPrem;
    }

    public String getImageSize() {
        return ImageSize;
    }

    public String getComplianceStatus() {
        return ComplianceStatus;
    }

    public String getMalwareDetected() {
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
        return MalwareColour;
    }

    public boolean getDebugInfo() {
        return DebugInfo;
    }

    @Override
    public String getIconFileName() {
        return "/plugin/TioCS/images/tenable-icon.png";
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
