package com.mnc.autoedit.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public class S3StorageConfig {
    private String bucket;
    private String region;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String inputsPrefix = "inputs";
    private String outputsPrefix = "outputs";

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getInputsPrefix() { return inputsPrefix; }
    public void setInputsPrefix(String inputsPrefix) { this.inputsPrefix = inputsPrefix; }
    public String getOutputsPrefix() { return outputsPrefix; }
    public void setOutputsPrefix(String outputsPrefix) { this.outputsPrefix = outputsPrefix; }

    public String inputKeyForJob(String jobId) {
        return inputsPrefix + "/" + jobId + ".mp4";
    }

    public String outputKeyForJob(String jobId) {
        return outputsPrefix + "/" + jobId + "/cleaned.mp4";
    }
}

