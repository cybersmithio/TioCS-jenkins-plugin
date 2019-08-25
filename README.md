# Tenable.io Container Security plugin for Jenkins

This plugin allows a container image to be tested for vulnerabilities and malware using Tenable.io Container Security.
Testing can be done in the cloud or on-premise.

Results will be stored in the Jenkins output for the build, as well as a summary in the Jenkins Action called 
"Tenable.io Container Security" for each build (on the left menu of the build). 

# Requirements
If you need to test on-premise, make sure you have already downloaded time Tenable.io cs-scanner image for use.
