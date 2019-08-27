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
//Done: Record the size of the image
//TODO: Hide API keys using environment variables.
public class TioCSBuilder extends Builder implements SimpleBuildStep {

    private final String name;
    private String ImageTag;
    private String TioRepo;
    private boolean useOnPrem;
    private String TioUsername;
    private String TioPassword;
    private String TioAccessKey;
    private String TioSecretKey;
    private Double FailCVSS;        // If there is a vulnerability with this CVSS or higher, fail the build.
    private boolean FailMalware;
    private boolean DebugInfo;
    private String Workflow;

    @DataBoundConstructor
    public TioCSBuilder(String name, String ImageTag, String TioRepo, String TioAccessKey, String TioSecretKey,String TioUsername,
        String TioPassword, Double FailCVSS, boolean FailMalware, boolean DebugInfo, String Workflow) {
        this.name = name;
        this.ImageTag = ImageTag;
        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.TioSecretKey = TioSecretKey;
        this.TioUsername = TioUsername;
        this.TioPassword = TioPassword;
        this.FailCVSS = FailCVSS;
        this.FailMalware = FailMalware;
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

    public boolean getFailMalware() {
        return FailMalware;
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

    public void setImageTag(String ImageTag) {
        this.ImageTag = ImageTag;
    }

    public void setTioRepo(String TioRepo) {
        this.TioRepo = TioRepo;
    }

    public void setTioUsername(String TioUsername) {
        this.TioUsername = TioUsername;
    }

    public void setTioPassword(String TioPassword) {
        this.TioPassword = TioPassword;
    }

    public void setTioAccessKey(String TioAccessKey) {
        this.TioAccessKey = TioAccessKey;
    }

    public void setTioSecretKey(String TioSecretKey) {
        this.TioSecretKey = TioSecretKey;
    }

    public void setFailCVSS(Double FailCVSS) {
        this.FailCVSS = FailCVSS;
    }

    public void setFailMalware(boolean FailMalware) {
        this.FailMalware = FailMalware;
    }

    public void setDebugInfo(boolean DebugInfo) {
        this.DebugInfo = DebugInfo;
    }

    public void setWorkflow(String Workflow) {
        this.Workflow = Workflow;
    }


    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        Double highcvss=0.0;
        Integer NumOfVulns=0;
        boolean malwareDetected=false;
        String imagetagstring="latest";
        String imagesize="";

        if ( !(ImageTag.equals("") ) ) {
            imagetagstring=ImageTag;
        }

        listener.getLogger().println("Working on image " + name + ":"+imagetagstring+".");
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
                listener.getLogger().println("docker images -q "+name+":"+imagetagstring);

                Process process=new ProcessBuilder("docker", "images","-q",name+":"+imagetagstring).start();
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
                listener.getLogger().println("docker images --format {{.Size}} "+name+":"+imagetagstring);
                Process process=new ProcessBuilder("docker", "images","--format","{{.Size}}",name+":"+imagetagstring).start();
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
                    listener.getLogger().println("sh -c docker save "+name+":"+imagetagstring+" | docker run -e TENABLE_ACCESS_KEY=$TENABLE_ACCESS_KEY"
                        +" -e TENABLE_SECRET_KEY=$TENABLE_SECRET_KEY -e IMPORT_REPO_NAME="+TioRepo
                        +" -i tenableio-docker-consec-local.jfrog.io/cs-scanner:latest inspect-image "+name+":"+imagetagstring);


                    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c","docker save "+name+":"+imagetagstring
                        +" | docker run -e TENABLE_ACCESS_KEY=$TENABLE_ACCESS_KEY -e TENABLE_SECRET_KEY=$TENABLE_SECRET_KEY -e IMPORT_REPO_NAME="
                        +TioRepo +" -i tenableio-docker-consec-local.jfrog.io/cs-scanner:latest inspect-image "
                        +name+":"+imagetagstring);
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

                listener.getLogger().println("Logging into registry.cloud.tenable.com with username " + TioUsername );
                try {
                    listener.getLogger().println("docker login -u $TENABLE_USERNAME -p $TENABLE_PASSWORD registry.cloud.tenable.com");
                    //Process process=new ProcessBuilder("docker", "login","-u", TioUsername,"-p", TioPassword,"registry.cloud.tenable.com").start();
                    ProcessBuilder processBuilder = new ProcessBuilder("sh","-c","echo", "login","-u", "$TENABLE_USERNAME","-p", "$TENABLE_PASSWORD","registry.cloud.tenable.com");
                    Map<String, String> env = processBuilder.environment();
                    env.put("TENABLE_PASSWORD", TioPassword);
                    env.put("TENABLE_USERNAME", TioUsername);
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
                    listener.getLogger().println("docker tag "+name+":"+imagetagstring+ " registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+imagetagstring);
                    Process process=new ProcessBuilder("docker", "tag",name+":"+imagetagstring , "registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+imagetagstring).start();
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
                    listener.getLogger().println("docker push registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+imagetagstring);

                    ProcessBuilder processBuilder=new ProcessBuilder("docker", "push", "registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+imagetagstring);
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
                run.addAction(new TioCSAction(name,ImageTag,TioRepo,TioUsername, TioAccessKey,FailCVSS, highcvss, useOnPrem, NumOfVulns, FailMalware,malwareDetected,DebugInfo,Workflow,imagesize));
            }
        }

        //Get report and parse
        if ( Workflow.equals("TestEvaluate") || Workflow.equals("Evaluate") ) {
            listener.getLogger().println("Evaluating the results of the image tests." );
            listener.getLogger().println("Any vulnerability with a CVSS of "+FailCVSS+ " or higher will be considered a failed build." );
            listener.getLogger().println("Fail build if malware detected: "+FailMalware );

            boolean reportReady = false;
            JSONObject responsejson = new JSONObject("{}");

            while ( ! reportReady  ) {
                Thread.sleep(10000);
                listener.getLogger().println("Retrieving report of image " + name + " from Tenable.io API");
                String jsonstring="";
                try {
                    URL myUrl = new URL("https://cloud.tenable.com/container-security/api/v2/reports/"+TioRepo+"/"+name+"/"+imagetagstring);
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

            JSONArray malware=responsejson.getJSONArray("malware");
            if ( DebugInfo ) {
                listener.getLogger().println("Findings:"+responsejson.get("malware"));
            }
            listener.getLogger().println("Number of malware items found: "+malware.length());

            if ( FailMalware ) {
                listener.getLogger().println("If malware is detected, this build is set to fail.");
                if ( Integer.compare(malware.length(),0) > 0 ) {
                    listener.getLogger().println("Malware detected, so failing the build.");
                    malwareDetected=true;
                    run.addAction(new TioCSAction(name,ImageTag,TioRepo,TioUsername, TioAccessKey,FailCVSS, highcvss, useOnPrem, NumOfVulns, FailMalware,malwareDetected,DebugInfo,Workflow,imagesize));
                } else {
                    listener.getLogger().println("Malware not detected. Continue with build.");
                }
            }

            //JSONObject vulns=responsejson.getJSONArray("findings");
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
            if (Double.compare(highcvss,FailCVSS) >= 0 ) {
                listener.getLogger().println("ERROR: There are vulnerabilities equal to or higher than "+FailCVSS);
                listener.getLogger().println("ERROR: Failing this build!");
                run.addAction(new TioCSAction(name,ImageTag,TioRepo,TioUsername, TioAccessKey,FailCVSS, highcvss, useOnPrem, NumOfVulns, FailMalware, malwareDetected,DebugInfo,Workflow,imagesize));
                throw new SecurityException();
            } else {
                listener.getLogger().println("Vulnerabilities are below threshold of "+FailCVSS);
            }

            run.addAction(new TioCSAction(name,ImageTag,TioRepo,TioUsername,  TioAccessKey,FailCVSS, highcvss, useOnPrem, NumOfVulns, FailMalware, malwareDetected,DebugInfo,Workflow,imagesize));
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
            @QueryParameter String TioUsername, @QueryParameter String TioPassword, @QueryParameter String TioAccessKey,
            @QueryParameter String TioSecretKey, @QueryParameter boolean useOnPrem, @QueryParameter Double FailCVSS,
            @QueryParameter boolean FailMalware, @QueryParameter boolean DebugInfo, @QueryParameter String Workflow)
            throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingName());
            if (TioRepo.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioRepo());
            if (TioUsername.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioUsername());
            if (TioPassword.length() == 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioPassword());
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
