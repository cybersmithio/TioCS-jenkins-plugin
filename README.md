# Tenable.io Container Security plugin for Jenkins

This plugin allows a container image to be tested for vulnerabilities and malware using Tenable.io Container Security.
Testing can be done in the cloud or on-premise.

Results will be stored in the Jenkins output for the build, as well as a summary in the Jenkins Action called 
"Tenable.io Container Security" for each build (on the left menu of the build). 

# Requirements
If you need to test on-premise, make sure you have already downloaded time Tenable.io cs-scanner image for use.


# References

Websites that helped out along the way:

https://wiki.jenkins.io/display/JENKINS/Plugin+tutorial#Plugintutorial-DistributingaPlugin
https://wiki.jenkins.io/display/JENKINS/Extend+Jenkins
https://wiki.jenkins.io/display/JENKINS/Basic+guide+to+Jelly+usage+in+Jenkins
https://wiki.jenkins.io/display/JENKINS/Jelly+form+controls
https://wiki.jenkins.io/display/JENKINS/Writing+a+foldable+section+controlled+by+a+checkbox
https://github.com/jenkinsci/credentials-plugin
https://github.com/jenkinsci/credentials-plugin/blob/master/docs/consumer.adoc
https://docs.oracle.com/javase/7/docs/api/java/lang/ProcessBuilder.html
https://stackoverflow.com/questions/21404252/post-request-send-json-data-java-httpurlconnection
https://docs.oracle.com/javase/tutorial/networking/urls/readingWriting.html
