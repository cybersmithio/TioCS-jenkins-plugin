package io.jenkins.plugins.TioCS;

//Needed for Map class, when looking for all environment variables.
import java.util.HashMap;
import java.util.Map;

//Needed for dynamic HTML form list boxes
import hudson.util.ListBoxModel;

import java.net.URL;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

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

//TODO: Record the testing time duration
public class TioCSBuilder extends Builder implements SimpleBuildStep {

    private final String name;
    private String ImageTag;
    private String TioRepo;
    private boolean useOnPrem;
    private String TioAccessKey;
    private String TioSecretKey;
    private boolean DebugInfo;
    private String Workflow;

    @DataBoundConstructor
    //TODO need to validate input
    public TioCSBuilder(String name, String ImageTag, String TioRepo, String TioAccessKey, String TioSecretKey,
        boolean DebugInfo, String Workflow) {
        this.name = name;

        if ( !(ImageTag.equals("") ) ) {
            this.ImageTag=ImageTag;
        } else {
            this.ImageTag = "latest";
        }

        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.TioSecretKey = TioSecretKey;
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

    public String getTioAccessKey() {
        return TioAccessKey;
    }

    public String getTioSecretKey() {
        return TioSecretKey;
    }

    public boolean getDebugInfo() {
        return DebugInfo;
    }

    public boolean isUseOnPrem() {
        return useOnPrem;
    }
    public String getWorkflow() {
        return Workflow;
    }


    @DataBoundSetter
    public void setUseOnPrem(boolean useOnPrem) {
        this.useOnPrem = useOnPrem;
    }

    //TODO need to validate input
    public void setImageTag(String ImageTag) {
        if ( !(ImageTag.equals("") ) ) {
            this.ImageTag=ImageTag;
        } else {
            this.ImageTag = "latest";
        }
    }

    //TODO need to validate input
    public void setTioRepo(String TioRepo) {
        this.TioRepo = TioRepo;
    }

    //TODO need to validate input
    public void setTioAccessKey(String TioAccessKey) {
        this.TioAccessKey = TioAccessKey;
    }

    //TODO need to validate input
    public void setTioSecretKey(String TioSecretKey) {
        this.TioSecretKey = TioSecretKey;
    }

    public void setDebugInfo(boolean DebugInfo) {
        this.DebugInfo = DebugInfo;
    }

    public void setWorkflow(String Workflow) {
        this.Workflow = Workflow;
    }


    private String getCompliance(TaskListener listener) throws InterruptedException {
        listener.getLogger().println("Retrieving compliance report for container image" );

        boolean reportReady = false;
        Integer sleepPeriod=0;
        JSONObject responsejson = new JSONObject("{}");

        //This will immediately check for the compliance report and if it is not ready, wait 10 seconds for the report to generate and keep looping until the report is ready.
        while ( ! reportReady  ) {
            Thread.sleep(sleepPeriod);
            sleepPeriod=10000;

            listener.getLogger().println("Retrieving report of image " + name + " from Tenable.io API");
            String jsonstring="";
            try {
                URL myUrl = new URL("https://cloud.tenable.com/container-security/api/v1/compliancebyname?repo="+TioRepo+"&image="+name+"&tag="+ImageTag);
                HttpsURLConnection conn = (HttpsURLConnection)myUrl.openConnection();
                conn.setRequestProperty("x-apikeys","accessKey="+TioAccessKey+";secretKey="+TioSecretKey);
                conn.setRequestProperty("accept","application/json");

                InputStream is = conn.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);

                String inputLine;

                while ((inputLine = br.readLine()) != null) {
                    jsonstring=jsonstring+inputLine;
                }

                br.close();
            } catch (Exception e) {
                listener.getLogger().println("Error getting image report.  Tenable.io is likely still creating it.");
                reportReady=false;
                continue;
            }

            //See if the JSON string from Tenable.io is valid.  If not, it is likely the report is still generating.
            if ( DebugInfo ) {
                listener.getLogger().println("Attempting to parse JSON string into JSON object:"+jsonstring);
            }
            try {
                responsejson = new JSONObject(jsonstring);
            } catch (Exception e) {
                listener.getLogger().println("Didn't get any valid JSON back, so looks like report is still processing.");
                reportReady=false;
                continue;
            }

            //Check the JSON to see if the report is finished.  If the report is finished, only "status" will be in
            // the JSON.  Otherwise a "message" will exist.  If we see that, the report is likely not ready.
            if ( DebugInfo ) {
                listener.getLogger().println("DEBUG: JSON received:"+responsejson.toString());
            }
            try {
                String reportMessage = responsejson.getString("message");
                listener.getLogger().println("Report message:"+reportMessage);
                reportReady = false;
                continue;
            } catch (JSONException e) {
                listener.getLogger().println("No message value returned, so report should be complete.");
            } catch (Exception e) {
                listener.getLogger().println("Some other unknown exception getting reportMessage: "+e.toString());
                reportReady = false;
                continue;
            }

            try {
                String reportStatus = responsejson.getString("status");
                listener.getLogger().println("Report status:"+reportStatus);
                reportReady = true;
                return reportStatus;
            } catch (JSONException e) {
                listener.getLogger().println("No report status, so report is not ready");
                reportReady = false;
                continue;
            } catch (Exception e) {
                listener.getLogger().println("Some other unknown exception getting reportStatus: "+e.toString());
                reportReady = false;
                continue;
            }
        }

        return "";
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        Double highcvss=0.0;
        Integer NumOfVulns=0;
        boolean malwareDetected=false;
        String imagesize="";

        listener.getLogger().println("Working on image " + name + ":"+ImageTag+".");
        if ( DebugInfo ) {
            listener.getLogger().println("Showing debugging information as requested.");
        } else {
            listener.getLogger().println("Debugging information will be suppressed.");
        }
        if ( DebugInfo ) {
            Map<String, String> env = System.getenv();
            for (String envName : env.keySet()) {
                listener.getLogger().println("Environment variable list: " + envName+" = " +env.get(envName) );
            }
            //String envTioAccessKey=System.getenv("TIOACCESSKEY");
            //listener.getLogger().println("Environment variable: " + envTioAccessKey );
        }

        switch(Workflow) {
            case "Test":
                listener.getLogger().println("Only testing the image.");
                break;
            case "Evaluate":
                listener.getLogger().println("Only evaluating the image test results.");
                break;
            case "TestEvaluate":
                listener.getLogger().println("Both testing the image and evaluating the results.");
                break;
        }

        if ( Workflow.equals("TestEvaluate") || Workflow.equals("Test") ) {
            listener.getLogger().println("Starting image testing.  Results will go into Tenable.io repository "+TioRepo);
            listener.getLogger().println("Tenable.io API Access Key: " + TioAccessKey );


            //First, check if the image exists otherwise we need to stop the build since it will fail aways.
            listener.getLogger().println("Check if image exists.");
            try {
                listener.getLogger().println("docker images -q "+name+":"+ImageTag);

                Process process=new ProcessBuilder("docker", "images","-q",name+":"+ImageTag).start();
                StringBuilder output = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }
                int exitVal = process.waitFor();
                if (exitVal == 0) {
                    listener.getLogger().println("Success running external command:"+output);
                } else {
                    listener.getLogger().println("ERROR: Error running external command:"+output);
                    throw new SecurityException();
                }
                if ( output.length() < 12 ) {
                    listener.getLogger().println("Image does not exist");
                    throw new SecurityException();
                } else {
                    listener.getLogger().println("Image exists with ID "+output+", continuing with build");
                }
            } catch (IOException e) {
                listener.getLogger().println("IO Exception running external command");
            } catch (InterruptedException e) {
                listener.getLogger().println("Interrupted Exception running external command");
            }


            //Now record the image size (more for documentation purposes)
            listener.getLogger().println("Checking image size.");
            try {
                listener.getLogger().println("docker images --format {{.Size}} "+name+":"+ImageTag);
                Process process=new ProcessBuilder("docker", "images","--format","{{.Size}}",name+":"+ImageTag).start();
                StringBuilder output = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }
                int exitVal = process.waitFor();
                if (exitVal == 0) {
                    listener.getLogger().println("Success running external command:"+output);
                } else {
                    listener.getLogger().println("ERROR: Error running external command:"+output);
                    throw new SecurityException();
                }
                imagesize=output.toString();
                listener.getLogger().println("Image size is "+imagesize);
            } catch (IOException e) {
                listener.getLogger().println("IO Exception running external command");
            } catch (InterruptedException e) {
                listener.getLogger().println("Interrupted Exception running external command");
            }




            if (useOnPrem) {
                listener.getLogger().println("Testing with on-premise inspector.");

                listener.getLogger().println("Piping image into on-premise Tenable.io CS inspector ");
                try {
                    listener.getLogger().println("sh -c docker save "+name+":"+ImageTag+" | docker run -e TENABLE_ACCESS_KEY=$TENABLE_ACCESS_KEY"
                        +" -e TENABLE_SECRET_KEY=$TENABLE_SECRET_KEY -e IMPORT_REPO_NAME="+TioRepo
                        +" -i tenableio-docker-consec-local.jfrog.io/cs-scanner:latest inspect-image "+name+":"+ImageTag);


                    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c","docker save "+name+":"+ImageTag
                        +" | docker run -e TENABLE_ACCESS_KEY=$TENABLE_ACCESS_KEY -e TENABLE_SECRET_KEY=$TENABLE_SECRET_KEY -e IMPORT_REPO_NAME="
                        +TioRepo +" -i tenableio-docker-consec-local.jfrog.io/cs-scanner:latest inspect-image "
                        +name+":"+ImageTag);
                    Map<String, String> env = processBuilder.environment();
                    env.put("TENABLE_SECRET_KEY", TioSecretKey);
                    env.put("TENABLE_ACCESS_KEY", TioAccessKey);
                    processBuilder.redirectErrorStream(true);
                    Process process=processBuilder.start();
                    StringBuilder output = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line + "\n");
                    }
                    int exitVal = process.waitFor();
                    if (exitVal == 0) {
                        listener.getLogger().println("Success running external command:"+output);
                    } else {
                        listener.getLogger().println("ERROR: Error running external command:"+output);
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println("IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println("Interrupted Exception running external command");
                }
                listener.getLogger().println("Finished with on-prem inspector");

            } else {
                listener.getLogger().println("Testing in Tenable.io cloud.");

                listener.getLogger().println("Logging into registry.cloud.tenable.com with access key " + TioAccessKey );
                try {
                    listener.getLogger().println("docker login -u $TIOACCESSKEY -p $TIOSECRETKEY registry.cloud.tenable.com");
                    //Process process=new ProcessBuilder("docker", "login","-u", TioAccessKey,"-p", TioSecretKey,"registry.cloud.tenable.com").start();
                    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", "docker login -u $TIOACCESSKEY -p $TIOSECRETKEY registry.cloud.tenable.com");
                    Map<String, String> env = processBuilder.environment();
                    env.put("TIOSECRETKEY", TioSecretKey);
                    env.put("TIOACCESSKEY", TioAccessKey);
                    processBuilder.redirectErrorStream(true);
                    Process process=processBuilder.start();

                    StringBuilder output = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line + "\n");
                    }
                    int exitVal = process.waitFor();
                    if (exitVal == 0) {
                        listener.getLogger().println("Success running external command:"+output);
                    } else {
                        listener.getLogger().println("Error ("+exitVal+") running external command:"+output);
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println("IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println("Interrupted Exception running external command");
                }

                listener.getLogger().println("Tagging image " + name + " for registry.cloud.tenable.com");
                try {
                    listener.getLogger().println("docker tag "+name+":"+ImageTag+ " registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+ImageTag);
                    Process process=new ProcessBuilder("docker", "tag",name+":"+ImageTag , "registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+ImageTag).start();
                    StringBuilder output = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line + "\n");
                    }
                    int exitVal = process.waitFor();
                    if (exitVal == 0) {
                        listener.getLogger().println("Success running external command: docker tag");
                    } else {
                        listener.getLogger().println("Error running external command: docker tag");
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println("IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println("Interrupted Exception running external command");
                }

                listener.getLogger().println("Pushing image " + name + " to registry.cloud.tenable.com");
                try {
                    listener.getLogger().println("docker push registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+ImageTag);

                    ProcessBuilder processBuilder=new ProcessBuilder("docker", "push", "registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+ImageTag);
                    processBuilder.redirectErrorStream(true);
                    Process process=processBuilder.start();

                    StringBuilder output = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line + "\n");
                    }
                    int exitVal = process.waitFor();
                    if (exitVal == 0) {
                        listener.getLogger().println("Success running external command:"+output);
                    } else {
                        listener.getLogger().println("Error running external command:"+output);
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println("IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println("Interrupted Exception running external command");
                }
            }
            if ( Workflow.equals("Test") ) {
                run.addAction(new TioCSAction(name,ImageTag,TioRepo, TioAccessKey, highcvss, useOnPrem, NumOfVulns, malwareDetected,DebugInfo,Workflow,imagesize,""));
            }
        }

        //Get report and parse
        if ( Workflow.equals("TestEvaluate") || Workflow.equals("Evaluate") ) {
            listener.getLogger().println("Evaluating the results of the image tests." );

            boolean reportReady = false;
            JSONObject responsejson = new JSONObject("{}");

            //Wait 10 seconds for the report to generate and keep looping (waiting 10 seconds) until the report is ready.
            while ( ! reportReady  ) {
                Thread.sleep(10000);
                listener.getLogger().println("Retrieving report of image " + name + " from Tenable.io API");
                String jsonstring="";
                try {
                    URL myUrl = new URL("https://cloud.tenable.com/container-security/api/v2/reports/"+TioRepo+"/"+name+"/"+ImageTag);
                    HttpsURLConnection conn = (HttpsURLConnection)myUrl.openConnection();
                    conn.setRequestProperty("x-apikeys","accessKey="+TioAccessKey+";secretKey="+TioSecretKey);
                    conn.setRequestProperty("accept","application/json");

                    InputStream is = conn.getInputStream();
                    InputStreamReader isr = new InputStreamReader(is);
                    BufferedReader br = new BufferedReader(isr);

                    String inputLine;

                    while ((inputLine = br.readLine()) != null) {
                        jsonstring=jsonstring+inputLine;
                    }

                    br.close();
                } catch (Exception e) {
                    listener.getLogger().println("Error getting image report.  Tenable.io is likely still creating it.");
                    reportReady=false;
                    continue;
                }

                //See if the JSON string from Tenable.io is valid.  If not, it is likely the report is still generating.
                if ( DebugInfo ) {
                    listener.getLogger().println("Attempting to parse JSON string into JSON object:"+jsonstring);
                }
                try {
                    responsejson = new JSONObject(jsonstring);
                } catch (Exception e) {
                    listener.getLogger().println("Didn't get any valid JSON back, so looks like report is still processing.");
                    reportReady=false;
                    continue;
                }

                //Check the JSON to see if the report is finished.
                if ( DebugInfo ) {
                    listener.getLogger().println("DEBUG: JSON received:"+responsejson.toString());
                }
                try {
                    String reportmessage = responsejson.getString("message");
                    listener.getLogger().println("Report status:"+reportmessage);
                    reportReady = false;
                } catch (JSONException e) {
                    reportReady = true;
                    listener.getLogger().println("No report status, so report should be complete.");
                } catch (Exception e) {
                    reportReady = false;
                    listener.getLogger().println("Some other unknown exception: "+e.toString());
                }
            }


            if ( DebugInfo ) {
                listener.getLogger().println("Risk Score:"+responsejson.get("risk_score"));
            }

            JSONArray findings=responsejson.getJSONArray("findings");
            if ( DebugInfo ) {
                listener.getLogger().println("Findings:"+responsejson.get("findings"));
            }

            //Count the malware records and log the number.  Also set the malwareDetected flag.
            JSONArray malware=responsejson.getJSONArray("malware");
            if ( DebugInfo ) {
                listener.getLogger().println("Findings:"+responsejson.get("malware"));
            }
            listener.getLogger().println("Number of malware items found: "+malware.length());
            if ( Integer.compare(malware.length(),0) > 0 ) {
                listener.getLogger().println("Malware detected in this image.");
                malwareDetected=true;
            } else {
                listener.getLogger().println("Malware not detected. Continue with build.");
            }

            for ( int i =0; i    < findings.length(); i++ ) {
                JSONObject ifinding = findings.getJSONObject(i);
                //listener.getLogger().println("Vulnerability finding: "+ifinding);
                JSONObject nvdfinding = ifinding.getJSONObject("nvdFinding");
                //listener.getLogger().println("Vuln NVD info: "+nvdfinding);
                String cvssscorestring=nvdfinding.getString("cvss_score");
                //listener.getLogger().println("CVSSv2 Score: "+cvssscorestring);
                if ( !(cvssscorestring.equals("")) ) {
                    NumOfVulns++;
                    Double cvssscorevalue=nvdfinding.getDouble("cvss_score");
                    listener.getLogger().println("Found vulnerability with CVSSv2 score "+cvssscorevalue);
                    if ( Double.compare(cvssscorevalue,highcvss) > 0 ) {
                        highcvss=cvssscorevalue;
                    }
                }
            }
            listener.getLogger().println("Highest CVSS Score: "+highcvss);
            String ComplianceStatus=getCompliance(listener);
            if ( ComplianceStatus.equals("pass") ) {
                run.addAction(new TioCSAction(name,ImageTag,TioRepo,  TioAccessKey, highcvss, useOnPrem, NumOfVulns, malwareDetected,DebugInfo,Workflow,imagesize, ComplianceStatus ));
                listener.getLogger().println("The image is compliant with the Tenable.io Container Security policy rules.");
            } else  {
                run.addAction(new TioCSAction(name,ImageTag,TioRepo,  TioAccessKey, highcvss, useOnPrem, NumOfVulns, malwareDetected,DebugInfo,Workflow,imagesize, ComplianceStatus));
                listener.getLogger().println("ERROR: The image is non-compliant with the Tenable.io Container Security policy rules.");
                throw new SecurityException();
            }
        }
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {


        public ListBoxModel doFillWorkflowItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Test the image and evaluate","TestEvaluate");
            items.add("Only test the image (In cloud or on-prem)","Test");
            items.add("Only evaluate the image report","Evaluate");
            return items;
        }

        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter String TioRepo,
            @QueryParameter String TioAccessKey,
            @QueryParameter String TioSecretKey, @QueryParameter boolean useOnPrem,
            @QueryParameter boolean DebugInfo, @QueryParameter String Workflow)
            throws IOException, ServletException {
            if (value.length() <= 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingName());
            if (TioRepo.length() <= 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioRepo());
            if (TioAccessKey.length() <= 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioAccessKey());
            if ( ! TioAccessKey.matches("^[a-z0-9]*$") )
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_invalidTioAccessKey());
            if (TioSecretKey.length() <= 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioSecretKey());
            if ( ! TioSecretKey.matches("^[a-z0-9]*$") )
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_invalidTioSecretKeyKey());
            if (ScanID.length() <= 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingScanID());
            if ( ! ScanID.matches("^[0-9]*$") )
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_notnumericScanID());

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
