package io.jenkins.plugins.TioCS;

//For Credentials plugin
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.model.Item;
import org.kohsuke.stapler.AncestorInPath;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import java.util.List;
import static com.google.common.collect.Lists.newArrayList;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import hudson.model.AbstractProject;
import hudson.model.Project;

//Needed for Map class, when looking for all environment variables.
import java.util.HashMap;
import java.util.Map;

//Needed for dynamic HTML form list boxes
import hudson.util.ListBoxModel;

//For displaying date and time in the logs
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

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
    private String ScanID;
    private String ScanTarget;
    private boolean WaitForScanFinish;

    @DataBoundConstructor
    //TODO need to validate input
    public TioCSBuilder(String name, String ImageTag, String TioRepo, String TioAccessKey, String TioSecretKey,
        boolean DebugInfo, String Workflow, String ScanID, String ScanTarget, boolean WaitForScanFinish) {

        this.name = name;

        if ( (ImageTag.equals("") ) ) {
            if ( ! Workflow.equals("Scan") )
                this.ImageTag = "latest";
        } else {
            this.ImageTag=ImageTag;
        }

        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.TioSecretKey = TioSecretKey;
        this.DebugInfo = DebugInfo;
        this.WaitForScanFinish = WaitForScanFinish;
        this.Workflow = Workflow;
        if ( ScanID == null ) {
            this.ScanID= "";
        } else {
            this.ScanID= ScanID;
            if ( ! ScanID.matches("^[0-9]*$") )
                this.ScanID= "";
        }

        this.ScanTarget= ScanTarget;
    }

    //All the "get" methods
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
    public boolean getWaitForScanFinish() {
        return WaitForScanFinish;
    }
    public boolean isUseOnPrem() {
        return useOnPrem;
    }
    public String getWorkflow() {
        return Workflow;
    }
    public String getScanID() {
        return ScanID;
    }
    public String getScanTarget() {
        return ScanTarget;
    }

    @DataBoundSetter
    public void setUseOnPrem(boolean useOnPrem) {
        this.useOnPrem = useOnPrem;
    }

    //TODO need to validate input
    public void setImageTag(String ImageTag) {
        if ( (ImageTag.equals("") ) ) {
            if ( ! this.Workflow.equals("Scan") )
                this.ImageTag = "latest";
        } else {
            this.ImageTag=ImageTag;
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

    public void setWaitForScanFinish(boolean WaitForScanFinish) {
        this.WaitForScanFinish = WaitForScanFinish;
    }

    public void setWorkflow(String Workflow) {
        this.Workflow = Workflow;
    }

    public void setScanID(String ScanID) {
        this.ScanID = ScanID;
    }

    public void setScanTarget(String ScanTarget) {
        this.ScanTarget = ScanTarget;
    }

    // Waits for an active scan to finish.  It uses the stored private variables to see what the scan ID is.
    private void waitForScanToFinish(TaskListener listener) throws InterruptedException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss - ");
        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Waiting for scan to finish.  Scan ID "+this.ScanID );

        boolean scanComplete = false;
        JSONObject responsejson = new JSONObject("{}");

        //Wait 10 seconds for the report to generate and keep looping (waiting 10 seconds) until the report is ready.
        while ( ! scanComplete  ) {
            Thread.sleep(60000);
            String jsonstring="";
            try {
                URL myUrl = new URL("https://cloud.tenable.com/scans/"+ScanID+"/latest-status");
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Retrieving scan status for scan ID " + this.ScanID);
                HttpsURLConnection conn = (HttpsURLConnection)myUrl.openConnection();
                conn.setRequestMethod("GET");
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
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Error getting scan status.  Tenable.io is likely still creating it.");
                scanComplete=false;
                continue;
            }

            //See if the JSON string from Tenable.io is valid.  If not, it is likely the report is still generating.
            if ( DebugInfo ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Attempting to parse JSON string into JSON object:"+jsonstring);
            }

            try {
                responsejson = new JSONObject(jsonstring);
            } catch (Exception e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Didn't get any valid JSON back.");
                scanComplete=false;
                continue;
            }

            //Check the JSON to see if the report is finished.
            if ( DebugInfo ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"DEBUG: JSON received:"+responsejson.toString());
            }

            try {
                String scanstatus = responsejson.getString("status");
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Scan status:"+scanstatus);
                if( scanstatus.equals("completed") ) {
                    scanComplete = true;
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Scan has completed");
                }
            } catch (JSONException e) {
                scanComplete = false;
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Exception: No scan status:"+e.toString());
                continue;
            } catch (Exception e) {
                scanComplete = false;
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Some other unknown exception: "+e.toString());
                continue;
            }
        }
    }

    private String getCompliance(TaskListener listener) throws InterruptedException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss - ");
        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Retrieving compliance report for container image" );
        boolean reportReady = false;
        Integer sleepPeriod=0;
        JSONObject responsejson = new JSONObject("{}");

        //This will immediately check for the compliance report and if it is not ready, wait 10 seconds for the report to generate and keep looping until the report is ready.
        while ( ! reportReady  ) {
            Thread.sleep(sleepPeriod);
            sleepPeriod=10000;

            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Retrieving report of image " + name + " from Tenable.io API");
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
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Error getting image report.  Tenable.io is likely still creating it.");
                reportReady=false;
                continue;
            }

            //See if the JSON string from Tenable.io is valid.  If not, it is likely the report is still generating.
            if ( DebugInfo ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Attempting to parse JSON string into JSON object:"+jsonstring);
            }
            try {
                responsejson = new JSONObject(jsonstring);
            } catch (Exception e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Didn't get any valid JSON back, so looks like report is still processing.");
                reportReady=false;
                continue;
            }

            //Check the JSON to see if the report is finished.  If the report is finished, only "status" will be in
            // the JSON.  Otherwise a "message" will exist.  If we see that, the report is likely not ready.
            if ( DebugInfo ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"DEBUG: JSON received:"+responsejson.toString());
            }
            try {
                String reportMessage = responsejson.getString("message");
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Report message:"+reportMessage);
                reportReady = false;
                continue;
            } catch (JSONException e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"No message value returned, so report should be complete.");
            } catch (Exception e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Some other unknown exception getting reportMessage: "+e.toString());
                reportReady = false;
                continue;
            }

            try {
                String reportStatus = responsejson.getString("status");
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Report status:"+reportStatus);
                reportReady = true;
                return reportStatus;
            } catch (JSONException e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"No report status, so report is not ready");
                reportReady = false;
                continue;
            } catch (Exception e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Some other unknown exception getting reportStatus: "+e.toString());
                reportReady = false;
                continue;
            }
        }

        return "";
    }

    // Launches an active scan by the scan ID stored in the private variables.  It does not wait for the scan to finish.
    private String launchActiveScan(TaskListener listener) throws InterruptedException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss - ");
        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Launching scan" );
        boolean reportReady = false;
        JSONObject responsejson = new JSONObject("{}");

        String scanuuid=null;

        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Launching scan with ID " + ScanID + " from Tenable.io API");
        String jsonstring="";
        try {
            URL myUrl = new URL("https://cloud.tenable.com/scans/"+ScanID+"/launch");
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Launching scan with ID " + ScanID + " from Tenable.io API: "+"https://cloud.tenable.com/scans/"+ScanID+"/launch");
            HttpsURLConnection conn = (HttpsURLConnection)myUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-apikeys","accessKey="+TioAccessKey+";secretKey="+TioSecretKey);
            conn.setRequestProperty("Accept","application/json");
            if ( ScanTarget != null && !ScanTarget.equals("") ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Launching with custom scan targets:" +ScanTarget);
                conn.setDoOutput(true);
                JSONArray targets = new JSONArray("["+ScanTarget+"]");
                JSONObject altTargets = new JSONObject();

                altTargets.put("alt_targets", targets);
                if (DebugInfo)
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Scan targets will be overridden with:"+altTargets.toString() );

                OutputStreamWriter wr= new OutputStreamWriter(conn.getOutputStream());
                wr.write(altTargets.toString());
                wr.close();
            }


            InputStream is = conn.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String inputLine;

            while ((inputLine = br.readLine()) != null) {
                jsonstring=jsonstring+inputLine;
            }

            br.close();
        } catch (Exception e) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR launching scan.  Check in Tenable.io if it was launched.");
            listener.getLogger().println(e);
        }

        //See if the JSON string from Tenable.io is valid.  If not, there may have been a problem launching the scan.
        if ( DebugInfo ) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Attempting to parse JSON string into JSON object:"+jsonstring);
        }
        try {
            responsejson = new JSONObject(jsonstring);
        } catch (Exception e) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR: Didn't get any valid JSON back.  Check in Tenable.io if scan was launched.");
        }

        //Check the JSON to see if we got a valid scan UUID back.
        if ( DebugInfo ) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"DEBUG: JSON received:"+responsejson.toString());
        }

        try {
            scanuuid = responsejson.getString("scan_uuid");
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Scan UUID:"+scanuuid);
        } catch (JSONException e) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR: A scan UUID was not found.  Check in Tenable.io if scan was launched.");
        } catch (Exception e) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR: A scan UUID was not found.  Check in Tenable.io if scan was launched.");
        }
        return scanuuid;
    }


    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        Double highcvss=0.0;
        Integer NumOfVulns=0;
        boolean malwareDetected=false;
        String imagesize="";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss - ");

        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Working on image " + name + ":"+ImageTag+".");
        if ( DebugInfo ) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Showing debugging information as requested.");
        } else {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Debugging information will be suppressed.");
        }
        if ( DebugInfo ) {
            Map<String, String> env = System.getenv();
            for (String envName : env.keySet()) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Environment variable list: " + envName+" = " +env.get(envName) );
            }
        }

        switch(Workflow) {
            case "Scan":
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Launching scan.");
                break;
            case "Test":
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Only testing the image.");
                break;
            case "Evaluate":
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Only evaluating the image test results.");
                break;
            case "TestEvaluate":
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Both testing the image and evaluating the results.");
                break;
        }


        //Launch Active Scan (WAS or VM)
        if ( Workflow.equals("Scan") ) {
            if ( launchActiveScan(listener) == null ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Problem launching scan");
            } else {
                if ( WaitForScanFinish ) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Wait for scan to finish as requested...");
                    waitForScanToFinish(listener);
                }
            }
        }

        if ( Workflow.equals("TestEvaluate") || Workflow.equals("Test") ) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Starting image testing.  Results will go into Tenable.io repository "+TioRepo);
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Tenable.io API Access Key: " + TioAccessKey );


            //First, check if the image exists otherwise we need to stop the build since it will fail aways.
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Check if image exists.");
            try {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"docker images -q "+name+":"+ImageTag);

                Process process=new ProcessBuilder("docker", "images","-q",name+":"+ImageTag).start();
                StringBuilder output = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }
                int exitVal = process.waitFor();
                if (exitVal == 0) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Success running external command:"+output);
                } else {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR: Error running external command:"+output);
                    throw new SecurityException();
                }
                if ( output.length() < 12 ) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Image does not exist");
                    throw new SecurityException();
                } else {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Continuing with build as the image exists with ID "+output);
                }
            } catch (IOException e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"IO Exception running external command");
            } catch (InterruptedException e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Interrupted Exception running external command");
            }


            //Now record the image size (more for documentation purposes)
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Checking image size.");
            try {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"docker images --format {{.Size}} "+name+":"+ImageTag);
                Process process=new ProcessBuilder("docker", "images","--format","{{.Size}}",name+":"+ImageTag).start();
                StringBuilder output = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line + "\n");
                }
                int exitVal = process.waitFor();
                if (exitVal == 0) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Success running external command:"+output);
                } else {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR: Error running external command:"+output);
                    throw new SecurityException();
                }
                imagesize=output.toString();
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Image size is "+imagesize);
            } catch (IOException e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"IO Exception running external command");
            } catch (InterruptedException e) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Interrupted Exception running external command");
            }




            if (useOnPrem) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Testing with on-premise inspector.");

                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Piping image into on-premise Tenable.io CS inspector ");
                try {
                    String debugstring="";
                    if( DebugInfo ) {
                        debugstring=" -e DEBUG_MODE=true ";
                    }

                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"sh -c docker save "+name+":"+ImageTag+" | docker run -e TENABLE_ACCESS_KEY=$TENABLE_ACCESS_KEY"
                        +" -e TENABLE_SECRET_KEY=$TENABLE_SECRET_KEY"+debugstring+" -e IMPORT_REPO_NAME="+TioRepo
                        +" -i tenableio-docker-consec-local.jfrog.io/cs-scanner:latest inspect-image "+name+":"+ImageTag);



                    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c","docker save "+name+":"+ImageTag
                        +" | docker run -e TENABLE_ACCESS_KEY=$TENABLE_ACCESS_KEY -e TENABLE_SECRET_KEY=$TENABLE_SECRET_KEY"+debugstring+"  -e IMPORT_REPO_NAME="
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
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Success running external command:"+output);
                    } else {
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR: Error running external command:"+output);
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Interrupted Exception running external command");
                }
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Finished with on-prem inspector");

            } else {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Testing in Tenable.io cloud.");

                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Logging into registry.cloud.tenable.com with access key " + TioAccessKey );
                try {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"docker login -u $TIOACCESSKEY -p $TIOSECRETKEY registry.cloud.tenable.com");
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
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Success running external command:"+output);
                    } else {
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Error ("+exitVal+") running external command:"+output);
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Interrupted Exception running external command");
                }

                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Tagging image " + name + " for registry.cloud.tenable.com");
                try {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"docker tag "+name+":"+ImageTag+ " registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+ImageTag);
                    Process process=new ProcessBuilder("docker", "tag",name+":"+ImageTag , "registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+ImageTag).start();
                    StringBuilder output = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line + "\n");
                    }
                    int exitVal = process.waitFor();
                    if (exitVal == 0) {
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Success running external command: docker tag");
                    } else {
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Error running external command: docker tag");
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Interrupted Exception running external command");
                }

                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Pushing image " + name + " to registry.cloud.tenable.com");
                try {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"docker push registry.cloud.tenable.com/"+TioRepo+"/"+name+":"+ImageTag);

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
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Success running external command:"+output);
                    } else {
                        listener.getLogger().println(dtf.format(LocalDateTime.now())+"Error running external command:"+output);
                        throw new SecurityException();
                    }
                } catch (IOException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"IO Exception running external command");
                } catch (InterruptedException e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Interrupted Exception running external command");
                }
            }
            if ( Workflow.equals("Test") ) {
                run.addAction(new TioCSAction(name,ImageTag,TioRepo, TioAccessKey, highcvss, useOnPrem, NumOfVulns, malwareDetected,DebugInfo,Workflow,imagesize,"",ScanID, ScanTarget));
            }
        }

        //Get report and parse
        if ( Workflow.equals("TestEvaluate") || Workflow.equals("Evaluate") ) {
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Evaluating the results of the image tests." );

            boolean reportReady = false;
            JSONObject responsejson = new JSONObject("{}");

            //Wait 10 seconds for the report to generate and keep looping (waiting 10 seconds) until the report is ready.
            while ( ! reportReady  ) {
                Thread.sleep(10000);

                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Retrieving report of image " + name + " from Tenable.io API");
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
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Error getting image report.  Tenable.io is likely still creating it.");
                    reportReady=false;
                    continue;
                }

                //See if the JSON string from Tenable.io is valid.  If not, it is likely the report is still generating.
                if ( DebugInfo ) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Attempting to parse JSON string into JSON object:"+jsonstring);
                }
                try {
                    responsejson = new JSONObject(jsonstring);
                } catch (Exception e) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Didn't get any valid JSON back, so looks like report is still processing.");
                    reportReady=false;
                    continue;
                }

                //Check the JSON to see if the report is finished.
                if ( DebugInfo ) {
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"DEBUG: JSON received:"+responsejson.toString());
                }
                try {
                    String reportmessage = responsejson.getString("message");
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Report status:"+reportmessage);
                    reportReady = false;
                } catch (JSONException e) {
                    reportReady = true;
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"No report status, so report should be complete.");
                } catch (Exception e) {
                    reportReady = false;
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Some other unknown exception: "+e.toString());
                }
            }


            if ( DebugInfo ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Risk Score:"+responsejson.get("risk_score"));
            }

            JSONArray findings=responsejson.getJSONArray("findings");
            if ( DebugInfo ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Findings:"+responsejson.get("findings"));
            }

            //Count the malware records and log the number.  Also set the malwareDetected flag.
            JSONArray malware=responsejson.getJSONArray("malware");
            if ( DebugInfo ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Findings:"+responsejson.get("malware"));
            }
            listener.getLogger().println("Number of malware items found: "+malware.length());
            if ( Integer.compare(malware.length(),0) > 0 ) {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Malware detected in this image.");
                malwareDetected=true;
            } else {
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"Malware not detected. Continue with build.");
            }

            for ( int i =0; i    < findings.length(); i++ ) {
                JSONObject ifinding = findings.getJSONObject(i);
                //listener.getLogger().println(dtf.format(LocalDateTime.now())+"Vulnerability finding: "+ifinding);
                JSONObject nvdfinding = ifinding.getJSONObject("nvdFinding");
                //listener.getLogger().println(dtf.format(LocalDateTime.now())+"Vuln NVD info: "+nvdfinding);
                String cvssscorestring=nvdfinding.getString("cvss_score");
                //listener.getLogger().println(dtf.format(LocalDateTime.now())+"CVSSv2 Score: "+cvssscorestring);
                if ( !(cvssscorestring.equals("")) ) {
                    NumOfVulns++;
                    Double cvssscorevalue=nvdfinding.getDouble("cvss_score");
                    listener.getLogger().println(dtf.format(LocalDateTime.now())+"Found vulnerability with CVSSv2 score "+cvssscorevalue);
                    if ( Double.compare(cvssscorevalue,highcvss) > 0 ) {
                        highcvss=cvssscorevalue;
                    }
                }
            }
            listener.getLogger().println(dtf.format(LocalDateTime.now())+"Highest CVSS Score: "+highcvss);
            String ComplianceStatus=getCompliance(listener);
            if ( ComplianceStatus.equals("pass") ) {
                run.addAction(new TioCSAction(name,ImageTag,TioRepo,  TioAccessKey, highcvss, useOnPrem, NumOfVulns, malwareDetected,DebugInfo,Workflow,imagesize, ComplianceStatus, ScanID, ScanTarget ));
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"The image is compliant with the Tenable.io Container Security policy rules.");
            } else  {
                run.addAction(new TioCSAction(name,ImageTag,TioRepo,  TioAccessKey, highcvss, useOnPrem, NumOfVulns, malwareDetected,DebugInfo,Workflow,imagesize, ComplianceStatus, ScanID, ScanTarget));
                listener.getLogger().println(dtf.format(LocalDateTime.now())+"ERROR: The image is non-compliant with the Tenable.io Container Security policy rules.");
                throw new SecurityException();
            }
        }
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {


        public ListBoxModel doFillWorkflowItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("VM/WAS: Launch active scan","Scan");
            items.add("CS: Test the image and evaluate","TestEvaluate");
            items.add("CS: Only test the image (In cloud or on-prem)","Test");
            items.add("CS: Only evaluate the image report","Evaluate");
            return items;
        }

        public ListBoxModel doFillTioCredentialsIdItems( @AncestorInPath Item item, @QueryParameter String TioCredentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();

            if (item == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
                item != null && !item.hasPermission(Item.EXTENDED_READ)) {
                System.out.println("Don't have permissions to change the credentials");
                return result.includeCurrentValue(TioCredentialsId);
            }
            System.out.println("Here are the credential options");

            List<DomainRequirement> domainRequirements = newArrayList();
            return result
                .withEmptySelection()
                .withMatching(anyOf(
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, item)))
                .withCurrentValue(TioCredentialsId);
        }

        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter String TioRepo,
            @QueryParameter String TioAccessKey, @QueryParameter String ImageTag,
            @QueryParameter String TioSecretKey, @QueryParameter boolean useOnPrem,
            @QueryParameter boolean DebugInfo, @QueryParameter String Workflow, @QueryParameter String ScanID,
            @QueryParameter String ScanTarget, @QueryParameter boolean WaitForScanFinish)
            throws IOException, ServletException {
            if (TioAccessKey.length() <= 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioAccessKey());
            if ( ! TioAccessKey.matches("^[a-z0-9]*$") )
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_invalidTioAccessKey());
            if (TioSecretKey.length() <= 0)
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioSecretKey());
            if ( ! TioSecretKey.matches("^[a-z0-9]*$") )
                return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_invalidTioSecretKey());

            if ( Workflow.equals("Scan") ) {
                if (value.length() > 0)
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_includedImageNameWhenScanning());
                if (TioRepo.length() > 0)
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_includedRepoWhenScanning());
                if (ImageTag.length() > 0)
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_includedLocalImageTagWhenScanning());
                if ( ScanID.length() <= 0 )
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingScanID());
                if ( ! ScanID.matches("^[0-9]*$") )
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_notnumericScanID());
                if ( useOnPrem )
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_includedUseOnPremWhenScanning());
            } else {
                if (value.length() <= 0)
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingName());
                if (TioRepo.length() <= 0)
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_missingTioRepo());
                if ( ScanID != null )
                    if ( ScanID.length() > 0 )
                        return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_includedScanIDWhenTesting());
                if ( WaitForScanFinish )
                    return FormValidation.error(Messages.TioCSBuilder_DescriptorImpl_errors_includedWaitForScanFinishWhenTesting());
            }

            return FormValidation.ok();
        }


        public FormValidation doCheckTioCredentialsId( @AncestorInPath Item item, @QueryParameter String value )
        throws IOException, ServletException {
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return FormValidation.ok();
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return FormValidation.ok();
                }
            }
            if (StringUtils.isBlank(value)) {
                return FormValidation.ok();
            }
            if (value.startsWith("${") && value.endsWith("}")) {
                return FormValidation.warning("Cannot validate expression based credentials");
            }
            /*
            if ( CredentialsProvider.listCredentials( CredentialsMatchers.withId(value)).isEmpty() ) {
                return FormValidation.error("Cannot find currently selected credentials");
            }
            */
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
