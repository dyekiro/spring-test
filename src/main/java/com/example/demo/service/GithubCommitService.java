package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class GithubCommitService {


    @Value("${github.auth.token}")
    private String AUTH_TOKEN;

    private static final String GITHUB_API_URL = "https://api.github.com";

    public String createCommit(String orgName, String repoName, MultipartFile file, String filePath, String branch) {
        System.out.println("---------------------------------------------------------");
        System.out.println("Create commmmmmiiiiit");
        System.out.println("---------------------------------------------------------");
        RestTemplate restTemplate = new RestTemplate();

        // Step 1: Get the SHA of the existing file blob
        String existingBlobSha = getExistingBlobSha(restTemplate, orgName, repoName, filePath, branch);
        System.err.println("Existing Blob SHA: "+existingBlobSha);

        // Step 2: Update the blob with new content from the uploaded file
        // Step 2: Update the blob with new content from the uploaded file
        String newBlobSha;
        try {
            if (existingBlobSha != null) {
                System.err.println("going to updateBlob");
                newBlobSha = updateBlob(restTemplate, orgName, repoName, existingBlobSha, new String(file.getBytes()), filePath, branch);
            } else {
                System.err.println("going to createBlob");
                newBlobSha = createBlob(restTemplate, orgName, repoName, new String(file.getBytes()), filePath, branch);
            }
        } catch (IOException e) {
            System.err.println("IOEXception");
            throw new RuntimeException("Error reading file content", e);
        }
        System.err.println("New Blob SHA: "+newBlobSha);

        // Step 3: Create a new tree with the updated blob
        String baseTreeSha = getBaseTreeSha(restTemplate, orgName, repoName, branch);
        System.err.println("Base Tree SHA: "+baseTreeSha);
        String newTreeSha = createTree(restTemplate, orgName, repoName, filePath, newBlobSha, baseTreeSha);
        System.err.println("New Tree SHA: "+newTreeSha);

        // Step 4: Create a new commit with the new tree
        System.err.println("Entering Parent commit SHA ");
        System.err.println("orgName: "+orgName);
        System.err.println("repName: "+repoName);
        System.err.println("branch: "+branch);
        String parentCommitSha = getParentCommitSha(restTemplate, orgName, repoName, branch);
        System.err.println("Parent commit SHA: "+parentCommitSha);
//        return createCommitInternal(restTemplate, orgName, repoName, newTreeSha, parentCommitSha, branch);
            return newBlobSha;
    }

    private String createCommitInternal(RestTemplate restTemplate, String orgName, String repoName, String newTreeSha, String parentCommitSha, String branch) {
        System.err.println("commit start");
        HttpHeaders headers = createHttpHeaders();
        System.err.println("commit start 2");
        headers.setContentType(MediaType.APPLICATION_JSON);
        System.err.println("headers created");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", "Commit message");
        requestBody.put("tree", newTreeSha);
        requestBody.put("parents", new String[]{parentCommitSha});

        System.err.println("Request Body");
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        System.err.println("Request Entity");

        String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/git/commits";
        ResponseEntity<Map> responseEntity = restTemplate.exchange(uri, HttpMethod.POST, requestEntity, Map.class);
        System.err.println("Request Entity 2: "+uri);

        if (responseEntity.getStatusCode() == HttpStatus.CREATED) {
            Map<String, Object> responseBody = responseEntity.getBody();
            System.err.println("Response Body: "+responseBody);
            if (responseBody != null && responseBody.containsKey("sha")) {
                return (String) responseBody.get("sha");
            }
            System.err.println("Going out of if ");
        }

        throw new RuntimeException("Failed to create commit.");
    }


    private String createBlob(RestTemplate restTemplate, String orgName, String repoName, String content, String filePath, String branch) {
        HttpHeaders headers = createHttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String encodedContent = Base64.getEncoder().encodeToString(content.getBytes());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", "Creating new file: " + filePath);
        requestBody.put("content", encodedContent);
        requestBody.put("branch", branch);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/contents/" + filePath;
        ResponseEntity<Map> responseEntity;

        try {
            responseEntity = restTemplate.exchange(uri, HttpMethod.PUT, requestEntity, Map.class);
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Status: " + e.getStatusCode());
            System.err.println("Response Body: " + e.getResponseBodyAsString());
            throw new RuntimeException("Failed to create blob: " + filePath + " with error: " + e.getResponseBodyAsString(), e);
        }

        if (responseEntity.getStatusCode() == HttpStatus.CREATED || responseEntity.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody != null && responseBody.containsKey("content")) {
                Map<String, Object> contentMap = (Map<String, Object>) responseBody.get("content");
                if (contentMap.containsKey("sha")) {
                    return (String) contentMap.get("sha");
                }
            }
        }

        throw new RuntimeException("Failed to create blob: " + filePath);
    }



    private String updateBlob(RestTemplate restTemplate, String orgName, String repoName, String existingBlobSha, MultipartFile file, String filePath, String branch) {
        // Step 1: Read content from the uploaded file
        String newFileContent;
        try {
            System.err.println("pass if in updateBlob");
            newFileContent = new String(file.getBytes());
        } catch (IOException e) {
            System.err.println("IOException in updateBlob");
            throw new RuntimeException("Error reading file content", e);
        }

            System.err.println("returnin in updateBlob");
        // Step 2: Update the blob using the file content
        return updateBlob(restTemplate, orgName, repoName, existingBlobSha, newFileContent, filePath, branch);
    }

    private String updateBlob(RestTemplate restTemplate, String orgName, String repoName, String existingBlobSha, String newFileContent, String filePath, String branch) {
        HttpHeaders headers = createHttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String encodedContent = Base64.getEncoder().encodeToString(newFileContent.getBytes());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", "Updating file: " + filePath);
        requestBody.put("content", encodedContent);
        requestBody.put("sha", existingBlobSha);
        requestBody.put("branch", branch);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/contents/" + filePath;
        ResponseEntity<Map> responseEntity;

        try {
            responseEntity = restTemplate.exchange(uri, HttpMethod.PUT, requestEntity, Map.class);
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Status: " + e.getStatusCode());
            System.err.println("Response Body: " + e.getResponseBodyAsString());
            throw new RuntimeException("Failed to update blob: " + filePath, e);
        }

        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> responseBody = responseEntity.getBody();
            if (responseBody != null && responseBody.containsKey("content")) {
                Map<String, Object> content = (Map<String, Object>) responseBody.get("content");
                if (content.containsKey("sha")) {
                    return (String) content.get("sha");
                }
            }
        }

        throw new RuntimeException("Failed to update blob: " + filePath);
    }

    private String getParentCommitSha(RestTemplate restTemplate, String orgName, String repoName, String branch) {
        System.err.println("Welcome to getParent ");
        final String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/git/refs/heads/" + branch;
        System.err.println("URI in getParent: "+uri);
        HttpHeaders headers = createHttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("object")) {
            Map<String, Object> object = (Map<String, Object>) responseBody.get("object");
            if (object.containsKey("sha")) {
                System.out.println("Return SHA from getparent");
                return (String) object.get("sha");
            }
        }
        System.err.println("Exiting getparent");
        return null; // Return null if the SHA is not found or any error occurs
    }

    public HttpHeaders createHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(AUTH_TOKEN);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("Content-Type", "application/json");
        headers.set("User-Agent", "MyApp"); // Replace with your app's name
        return headers;
    }


    private String getExistingBlobSha(RestTemplate restTemplate, String orgName, String repoName, String filePath, String branch) {
        HttpHeaders headers = createHttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/contents/" + filePath + "?ref=" + branch;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("sha")) {
                    return (String) responseBody.get("sha");
                }
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                System.err.println("Returning null");
                return null;  // File does not exist
            } else {
                throw new RuntimeException("Failed to get existing blob SHA: " + filePath, e);
            }
        }

        System.err.println("Exiting existing blob Sha");

        return null;  // Default return if the file does not exist
    }


    public String getBaseTreeSha(RestTemplate restTemplate, String orgName, String repoName, String branchName) {
        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/git/ref/heads/" + branchName;
        HttpHeaders headers = createHttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> object = (Map<String, Object>) response.getBody().get("object");
        return (String) object.get("sha");
    }


    public String createTree(RestTemplate restTemplate, String orgName, String repoName, String blobSha, String filePath, String baseTreeSha) {
        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/git/trees";
        HttpHeaders headers = createHttpHeaders();
        String body = "{\n" +
                "  \"base_tree\": \"" + baseTreeSha + "\",\n" +
                "  \"tree\": [\n" +
                "    {\n" +
                "      \"path\": \"" + filePath + "\",\n" +
                "      \"mode\": \"100644\",\n" +
                "      \"type\": \"blob\",\n" +
                "      \"sha\": \"" + blobSha + "\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        System.err.println("create tree body:"+body);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        System.err.println("create tree response: "+response);
        return (String) response.getBody().get("sha");
    }




}
