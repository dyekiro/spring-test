package com.example.demo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.*;

@Service
public class GithubService {

    private static final Logger logger = LoggerFactory.getLogger(GithubService.class);


    private static final String GITHUB_API_URL = "https://api.github.com";
    @Value("${github.auth.token}")
    private String AUTH_TOKEN;

    private final RestTemplate restTemplate;

    public GithubService() {
        this.restTemplate = new RestTemplate();
    }

    public HttpHeaders createHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(AUTH_TOKEN);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("Content-Type", "application/json");
        headers.set("User-Agent", "MyApp"); // Replace with your app's name
        return headers;
    }

    public String getBranches(String orgName, String repoName) {
        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/branches";

//        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = createHttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

//        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
//
//        return response.getBody();
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            logger.info("Branches JSON response: {}", response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            logger.error("HTTP error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
        } catch (Exception e) {
            logger.error("General error: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

//    public String extractSha(String branchesJson, String branchName) {
//        try {
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode branches = mapper.readTree(branchesJson);
//
//            for (JsonNode branch : branches) {
//                if (branch.get("name").asText().equals(branchName)) {
//                    return branch.get("commit").get("sha").asText();
//                }
//            }
//
//            throw new RuntimeException("Branch not found: " + branchName);
//        } catch (Exception e) {
//            logger.error("Error parsing branches JSON: {}", branchesJson, e);
//            throw new RuntimeException("Error parsing branches JSON", e);
//        }
//    }

    public String createBranch(String orgName, String repoName, String branchName) {
        if (branchName == null || branchName.length() < 1 || branchName.length() > 255) {
            return "Error: Branch name must be between 1 and 255 characters long.";
        }

        // Add additional validation logic if needed

        final String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/git/refs";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = createHttpHeaders();
        Map<String, String> request = new HashMap<>();
        request.put("ref", "refs/heads/" + branchName);
        request.put("sha", "126b2c23e70ee43387fa93519bebaba14d902475"); // Or specify another base branch
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                System.err.println("HttpClientError");
                // Branch already exists, handle this case gracefully
                return "{\"ref\":\"refs/heads/"+branchName+"\"";
            }
//            return "Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();
//            return  "{\"ref\":\"refs/heads/"+branchName+"\"";\
            return branchName;
        } catch (Exception e) {
            System.err.println("Exception");
            return "Error: " + e.getMessage();
        }
    }

    public String getBranchName(String input) {
       String branchName = "";

        // Define a regular expression pattern to match the branch name
        Pattern pattern = Pattern.compile("\"ref\":\"refs/heads/(.*?)\"");

        // Create a matcher for the input string
        Matcher matcher = pattern.matcher(input);

        // Check if the pattern is found
        if (matcher.find()) {
            // Extract the branch name from the matched group
            branchName = matcher.group(1);
            System.out.println(branchName); // Output: myNewBranch
        }

        return branchName;
    }

    public String createCommit(String orgName, String repoName, String branchName, String filePath, String commitMessage, String newFileContent) {

        System.err.println("getBranchname: "+branchName);
        branchName = getBranchName(branchName);
        System.err.println("after getBranchName: "+branchName);


        System.out.println("going to getExistingBlobSha");
        // Step 1: Get the existing file content
        String existingBlobSha = getExistingBlobSha(restTemplate, orgName, repoName, branchName, filePath);
//
        System.out.println("getExistingBlobSha returned: "+existingBlobSha);
//        // Step 2: Create a new blob if the file doesn't exist or update the existing blob
//        String newBlobSha = (existingBlobSha != null) ? updateBlob(restTemplate, orgName, repoName, existingBlobSha, newFileContent) : createBlob(restTemplate, orgName, repoName, newFileContent);
        String newBlobSha;
        if (existingBlobSha != null) {
            System.out.println("existingBlobSha is not null");
            newBlobSha = updateBlob(restTemplate, orgName, repoName, existingBlobSha, newFileContent,filePath, branchName);
        } else {
            System.out.println("existingBlobSha is null");
            newBlobSha = createBlob(restTemplate, orgName, repoName, newFileContent);
        }
//
//        System.out.println("________________________________________________________\nExisting Blob SHA: "+existingBlobSha+"\n_________________________________________________");
        System.err.println("________________________________________________________\nNew Blob SHA: "+newBlobSha+"\n_________________________________________________");

//        // Step 3: Get the current commit
        String baseTreeSha = getBaseTreeSha(restTemplate, orgName, repoName, branchName);
//        System.out.println("________________________________________________________\nBase Tree SHA: "+baseTreeSha+"\n_________________________________________________");

//
//        // Step 4: Create a new tree
        String newTreeSha = createTree(restTemplate, orgName, repoName, newBlobSha, filePath, baseTreeSha);
//
//        System.out.println("________________________________________________________\nExisting Blob SHA: "+existingBlobSha+"\n_________________________________________________");

//        // Step 5: Create a new commit
        String newCommitSha = createCommitObject(restTemplate, orgName, repoName, commitMessage, newTreeSha, baseTreeSha);
//
//        // Step 6: Update the reference
        updateBranchReference(restTemplate, orgName, repoName, branchName, newCommitSha);

        return newCommitSha;
    }

    private String createBlob(RestTemplate restTemplate, String orgName, String repoName, String fileContent) {
        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/git/blobs";
        HttpHeaders headers = createHttpHeaders();
        String body = "{\"content\": \"" + fileContent + "\", \"encoding\": \"utf-8\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        System.err.println("Create Blob: "+response);
        return (String) response.getBody().get("sha");
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

    private String createCommitObject(RestTemplate restTemplate, String orgName, String repoName, String commitMessage, String newTreeSha, String parentSha) {
        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/git/commits";
        HttpHeaders headers = createHttpHeaders();
        String body = "{\n" +
                "  \"message\": \"" + commitMessage + "\",\n" +
                "  \"tree\": \"" + newTreeSha + "\",\n" +
                "  \"parents\": [\"" + parentSha + "\"]\n" +
                "}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.POST, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
        return (String) response.getBody().get("sha");
    }

    private void updateBranchReference(RestTemplate restTemplate, String orgName, String repoName, String branchName, String newCommitSha) {
        final String uri =  GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/git/refs/heads/" + branchName;
        HttpHeaders headers = createHttpHeaders();
        String body = "{\n" +
                "  \"sha\": \"" + newCommitSha + "\"\n" +
                "}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);
    }

    public String getExistingBlobSha(RestTemplate restTemplate, String orgName, String repoName, String branchName, String filePath) {
        System.out.println("in the getExistingBlobSha");
        // Implementation to retrieve the existing blob SHA
        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/contents/" + filePath + "?ref=" + branchName;
        HttpHeaders headers = createHttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        System.out.println("uri headers and entity got in getExistingBlobSha");

        try {
            System.out.println("in try of getExistingBlobSha");
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            String content = (String) response.getBody().get("content");
            return (content != null) ? (String) response.getBody().get("sha") : null;
        } catch (HttpClientErrorException e) {
            System.err.println("It errors at get existing blob sha");
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null; // File doesn't exist
            }
            throw e; // Other error
        }
    }



//    private String updateBlob(RestTemplate restTemplate, String orgName, String repoName, String existingBlobSha, String newFileContent, String filePath, String branch) {
//        // Step 1: Fetch existing content of the file from the specified branch
//        String existingContent = getExistingFileContent(restTemplate, orgName, repoName, filePath, branch);
//
//        // Step 2: Append new content to existing content (with newline if existing content is not empty)
//        StringBuilder updatedContentBuilder = new StringBuilder();
//        if (!existingContent.isEmpty()) {
//            updatedContentBuilder.append(existingContent).append("\\n");
//        }
//        updatedContentBuilder.append(newFileContent);
//        String updatedContent = updatedContentBuilder.toString();
//
//        // Step 3: Build the JSON request body using a JSON library (e.g., Jackson)
//        ObjectMapper objectMapper = new ObjectMapper();
//        Map<String, Object> requestBody = new HashMap<>();
//        requestBody.put("message", "Append to file content");
//        requestBody.put("content", Base64.getEncoder().encodeToString(updatedContent.getBytes()));
//        requestBody.put("sha", existingBlobSha);
//        requestBody.put("branch", branch);
//
//        System.err.println("Object Mapper: "+requestBody);
//
//        // Convert the request body to a JSON string
//        String body;
//        try {
//            body = objectMapper.writeValueAsString(requestBody);
//            System.err.println("As String: "+body);
//        } catch (JsonProcessingException e) {
//            // Handle JSON serialization error
//            throw new RuntimeException("Error serializing JSON request body", e);
//        }
//
//        // Step 4: Update the file with the combined content
//        final String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/contents/" + filePath; // Using the actual file path
//        HttpHeaders headers = createHttpHeaders();
//        HttpEntity<String> entity = new HttpEntity<>(body, headers);
//
//        // Rest of the method remains the same
//        return updatedContent;
//    }

    private String updateBlob(RestTemplate restTemplate, String orgName, String repoName, String existingBlobSha, String newFileContent, String filePath, String branch) {
        // Step 1: Fetch existing content of the file from the specified branch
        System.out.println("going to getExistingFileContent");
        String existingContent = getExistingFileContent(restTemplate, orgName, repoName, filePath, branch);


        System.out.println("got out of getExistingFileContent");
        // Step 2: Append new content to existing content
        String updatedContent = existingContent +"\n"+ newFileContent.replaceAll("\\\\n", "\n"); // Replace "\\n" with actual newline character


        System.err.println("this is the updated content: "+updatedContent);
        // Step 3: Encode the combined content into Base64
        String base64Content = Base64.getEncoder().encodeToString(updatedContent.getBytes());
        System.err.println("updated content but base64: "+base64Content);
        System.err.println("base64 but escape: "+StringEscapeUtils.escapeJson(base64Content));

        // Step 4: Update the file with the combined content
        final String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/contents/" + filePath; // Using the actual file path
        HttpHeaders headers = createHttpHeaders();
        String body = "{\n" +
                "  \"message\": \"Append to file content\",\n" +
                "  \"content\": \"" + StringEscapeUtils.escapeJson(base64Content) + "\",\n" + // Escape special characters for JSON
                "  \"sha\": \"" + existingBlobSha + "\",\n" +
                "  \"branch\": \"" + branch + "\"\n" +
                "}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.PUT, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> responseBody = response.getBody();
            System.out.println("Response Body: " + responseBody); // Log the response body for inspection

            if (responseBody != null && responseBody.containsKey("content")) {
                Map<String, Object> content = (Map<String, Object>) responseBody.get("content");
                if (content.containsKey("sha")) {
                    return (String) content.get("sha");
                }
            }
            return null; // Unable to extract SHA from response
        } catch (HttpClientErrorException e) {
            System.err.println("HTTP Error: " + e.getRawStatusCode()); // Log the HTTP status code
            System.err.println("Response Body: " + e.getResponseBodyAsString()); // Log the response body for error details

            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // Handle conflict error (e.g., file is out of date)
                return null;
            }
            throw e; // Rethrow other errors
        }
    }
//this original
//    private String updateBlob(RestTemplate restTemplate, String orgName, String repoName, String existingBlobSha, String newFileContent, String filePath, String branchName) {
//        // Step 1: Fetch existing content of the file
//        String existingContent = getExistingFileContent(restTemplate, orgName, repoName, filePath, branchName);
//
//        // Step 2: Append new content to existing content
//        String updatedContent = existingContent + "\\n" + newFileContent; // Append new content with a newline separator (adjust as needed)
//        updatedContent = updatedContent.trim();
////        System.out.println("________________________________________________________\nUpdated Content : "+updatedContent+"\n_________________________________________________");
////
////         Step 3: Update the file with the combined content
//        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/contents/" + filePath; // Using the actual file path
//        HttpHeaders headers = createHttpHeaders();
//        String body = "{\n" +
//                "  \"message\": \"Append to file content\",\n" +
//                "  \"content\": \"" + Base64.getEncoder().encodeToString(updatedContent.getBytes()) + "\",\n" +
//                "  \"sha\": \"" + existingBlobSha + "\",\n" +
//                "  \"branch\": \""+branchName+"\"  // Update with the target branch name\n" +
//                "}";
//        HttpEntity<String> entity = new HttpEntity<>(body, headers);
//
////        return updatedContent;
//        try {
//            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.PUT, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
//            Map<String, Object> responseBody = response.getBody();
//            System.err.println("Update Blob Response Body: " + responseBody); // Log the response body for inspection
//
//            if (responseBody != null && responseBody.containsKey("content")) {
//                Map<String, Object> content = (Map<String, Object>) responseBody.get("content");
//                if (content.containsKey("sha")) {
//                    return (String) content.get("sha");
//                }
//            }
//            return null; // Unable to extract SHA from response
//        } catch (HttpClientErrorException e) {
//            System.err.println("HTTP Error: " + e.getRawStatusCode()); // Log the HTTP status code
//            System.err.println("Response Body: " + e.getResponseBodyAsString()); // Log the response body for error details
//
//            if (e.getStatusCode() == HttpStatus.CONFLICT) {
//                // Handle conflict error (e.g., file is out of date)
//                return null;
//            }
//            throw e; // Rethrow other errors
//        }
//    }


    private String getExistingFileContent(RestTemplate restTemplate, String orgName, String repoName, String filePath, String branch) {
        System.out.println("inside getExistingFileContent");
        final String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/contents/" + filePath + "?ref=" + branch;
        HttpHeaders headers = createHttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        System.out.println("uri headers and entity got in getExistingFileContent");

        try {
            System.out.println("getExistingFileContent try");
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("content")) {
                System.out.println("getExistingFileContent if");
                String encodedContent = (String) responseBody.get("content");
                encodedContent = encodedContent.trim();
                System.out.println("getExistingFileContent encodedContent: "+encodedContent);
//                return new String(Base64.getDecoder().decode(encodedContent));
                return new String(Base64.getDecoder().decode(encodedContent.getBytes()));
            }
            System.out.println("getExistingFileContent else");
            return null; // Unable to extract content from response
        } catch (HttpClientErrorException e) {
            System.out.println("getExistingFileContent catch");
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // File not found, return empty content
                return "";
            }
            throw e; // Rethrow other errors
        }
    }



    //this original
//    private String getExistingFileContent(RestTemplate restTemplate, String orgName, String repoName, String filePath, String branch) {
//        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/contents/" + filePath + "?ref=" + branch;
//        HttpHeaders headers = createHttpHeaders();
//        HttpEntity<String> entity = new HttpEntity<>(headers);
//
//        try {
//            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
//            Map<String, Object> responseBody = response.getBody();
//            if (responseBody != null && responseBody.containsKey("content")) {
//                String content = (String) responseBody.get("content");
//                content = content.trim();
////                System.err.println("________________________________________________________\nContent: "+content+"\n_________________________________________________");
////                System.err.println("responseBody: "+responseBody);
////                String cont = responseBody.get("content").toString();
////                System.err.println("\ncontent: "+cont);
////                System.err.println("\ndecoded content: "+new String(Base64.getDecoder().decode(cont.trim())));
//
//                return new String(Base64.getDecoder().decode(content));
//            }
//            return null; // Unable to extract content from response
//        } catch (HttpClientErrorException e) {
//            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
//                // File not found, return empty content
//                return "";
//            }
//            throw e; // Rethrow other errors
//        }
//    }



//    private String updateBlob(RestTemplate restTemplate, String orgName, String repoName, String existingBlobSha, String newFileContent, String filePath, int maxRetries) {
//        final String uri = "https://api.github.com/repos/" + orgName + "/" + repoName + "/contents/"+filePath; // Update with the actual file path
//        HttpHeaders headers = createHttpHeaders();
//        String body = "{\n" +
//                "  \"message\": \"Update file content\",\n" +
//                "  \"content\": \"" + Base64.getEncoder().encodeToString(newFileContent.getBytes()) + "\",\n" +
//                "  \"sha\": \"" + existingBlobSha + "\",\n" +
//                "  \"branch\": \"main\"  // Update with the target branch name\n" +
//                "}";
//        HttpEntity<String> entity = new HttpEntity<>(body, headers);
//
//        try {
//            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(uri, HttpMethod.PUT, entity, new ParameterizedTypeReference<Map<String, Object>>() {});
//            Map<String, Object> responseBody = response.getBody();
//            System.out.println("Response Body: " + responseBody); // Log the response body for inspection
//
//            if (responseBody != null && responseBody.containsKey("content")) {
//                Map<String, Object> content = (Map<String, Object>) responseBody.get("content");
//                if (content.containsKey("sha")) {
//                    return (String) content.get("sha");
//                }
//            }
//            return null; // Unable to extract SHA from response
//        } catch (HttpClientErrorException e) {
//            System.err.println("HTTP Error: " + e.getRawStatusCode()); // Log the HTTP status code
//            System.err.println("Response Body: " + e.getResponseBodyAsString()); // Log the response body for error details
//
//            if (e.getStatusCode() == HttpStatus.CONFLICT) {
//                // Handle conflict error (e.g., file is out of date)
//                return null;
//            }
//            throw e; // Rethrow other errors
//        }
//    }


    public String createPR(){
        return "hello";
    }

    //Create pull request
    public String createPullRequest(String orgName, String repoName, String title, String headBranch, String baseBranch) {
        RestTemplate restTemplate = new RestTemplate();

        // Build the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("title", title);
        requestBody.put("head", headBranch);
        requestBody.put("base", baseBranch);

        // Set up the HTTP headers
        HttpHeaders headers = createHttpHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Define the URI for creating the pull request
        final String uri = GITHUB_API_URL + "/repos/" + orgName + "/" + repoName + "/pulls";

        try {
            // Send the request to create the pull request
            ResponseEntity<HashMap> response = restTemplate.postForEntity(uri, entity, HashMap.class);
            if (response.getStatusCode() == HttpStatus.CREATED) {
                String pullRequestUrl = (String) response.getBody().get("html_url");
                return "Pull request created successfully: " + pullRequestUrl;
            } else {
                return "Failed to create pull request";
            }
        } catch (HttpClientErrorException e) {
            // Handle any exceptions
            throw new RuntimeException("Error creating pull request: " + e.getMessage(), e);
        }
    }
//


}
