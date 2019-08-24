package io.jenkins.plugins.TioCS;

import java.net.URL;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;

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
    private String TioUsername;
    private String TioPassword;
    private String TioAccessKey;
    private String TioSecretKey;

    @DataBoundConstructor
    public TioCSBuilder(String name, String TioRepo, String TioAccessKey, String TioSecretKey,String TioUsername, String TioPassword) {
        this.name = name;
        this.TioRepo = TioRepo;
        this.TioAccessKey = TioAccessKey;
        this.TioSecretKey = TioSecretKey;
        this.TioUsername = TioUsername;
        this.TioPassword = TioPassword;
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

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        run.addAction(new TioCSAction(name,TioRepo,TioUsername, TioPassword, TioAccessKey,TioSecretKey));
        if (useOnPrem) {
            listener.getLogger().println("Testing image " + name + " with on-premise inspector.  Results will go into Tenable.io repository "+TioRepo);
        } else {
            listener.getLogger().println("Testing image " + name + " by uploading directly to Tenable.io cloud.  Results will go into Tenable.io repository "+TioRepo);

            listener.getLogger().println("Logging into registry.cloud.tenable.com with username " + TioUsername );
            ProcessBuilder processBuilder = new ProcessBuilder();
            try {
                Process process=new ProcessBuilder("docker", "login","-u", TioUsername,"-p", TioPassword,"registry.cloud.tenable.com").start();
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
                }
            } catch (IOException e) {
                listener.getLogger().println("IO Exception running external command");
            } catch (InterruptedException e) {
                listener.getLogger().println("Interrupted Exception running external command");
            }

            listener.getLogger().println("Tagging image " + name + " for registry.cloud.tenable.com");
            try {
                Process process=new ProcessBuilder("docker", "tag",name+":latest" , "registry.cloud.tenable.com/"+TioRepo+"/"+name+":latest").start();
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
                }
            } catch (IOException e) {
                listener.getLogger().println("IO Exception running external command");
            } catch (InterruptedException e) {
                listener.getLogger().println("Interrupted Exception running external command");
            }

            listener.getLogger().println("Pushing image " + name + " to registry.cloud.tenable.com");
            try {
                Process process=new ProcessBuilder("docker", "push", "registry.cloud.tenable.com/"+TioRepo+"/"+name+":latest").start();
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
                }
            } catch (IOException e) {
                listener.getLogger().println("IO Exception running external command");
            } catch (InterruptedException e) {
                listener.getLogger().println("Interrupted Exception running external command");
            }

            listener.getLogger().println("Retrieving report of image " + name + " from Tenable.io API");
            String jsonstring="";
            try {
                URL myUrl = new URL("https://cloud.tenable.com/container-security/api/v2/reports/"+TioRepo+"/"+name+"/latest");
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
                listener.getLogger().println("Error getting image report");
            }

            listener.getLogger().println("Attempting to parse JSON string into JSON object");
            JSONObject responsejson = new JSONObject(jsonstring);
            //listener.getLogger().println("JSON received:"+responsejson.toString());
            listener.getLogger().println("Risk Score:"+responsejson.get("risk_score"));
            listener.getLogger().println("Findings:"+responsejson.get("findings"));
            JSONArray findings=responsejson.getJSONArray("findings");
            //JSONObject vulns=responsejson.getJSONArray("findings");
            //for ( int i =0; i < vulns.length(); ++i) {
            //    listener.getLogger().println("Vuln: "+vulns.getJSONObject(i).toString());
            //}

        }
    }

    @Symbol("greet")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {


        public FormValidation doCheckName(@QueryParameter String value, @QueryParameter String TioRepo, @QueryParameter String TioUsername, @QueryParameter String TioPassword, @QueryParameter String TioAccessKey, @QueryParameter String TioSecretKey, @QueryParameter boolean useOnPrem)
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
