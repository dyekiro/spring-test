package com.example.demo.controller;

import com.example.demo.service.GithubCommitService;
import com.example.demo.service.GithubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class GithubController {
    String mybranchName = "test-branch-1234567890123456789012345678901234567890";

    @Value("${github.auth.token}")
    private String AUTH_TOKEN;


    @Autowired
    private GithubService githubService;

    @Autowired
    private GithubCommitService githubCommitService;



    @GetMapping("/github/branches")
    public String getBranches(@RequestParam("orgName") String orgName, @RequestParam("repoName") String repoName) {
        return githubService.getBranches(orgName, repoName);
    }

    @PostMapping("/github/create-branch")
    public String createBranch(
            @RequestParam("orgName") String orgName,
            @RequestParam("repoName") String repoName,
            @RequestParam("branchName") String branchName) {
        return githubService.createBranch(orgName, repoName, mybranchName);
    }

    @PostMapping("/github/create-commit")
    public String createCommit(@RequestParam("orgName") String orgName,
                               @RequestParam("repoName") String repoName,
                               @RequestParam("branchName") String branchName,
                               @RequestParam("filePath") String filePath,
                               @RequestParam("commitMessage") String commitMessage,
                               @RequestParam("fileContent") String fileContent) {
//        return githubService.createCommit(orgName, repoName, branchName, filePath, commitMessage, fileContent);
        return githubService.createCommit(orgName, repoName, githubService.createBranch(orgName, repoName, branchName), filePath, commitMessage, fileContent);
    }

    @PostMapping("/github/create-commits")
    public ResponseEntity<String> createCommit(
            @RequestParam String orgName,
            @RequestParam String repoName,
            @RequestParam String filePath,
            @RequestParam String branch,
            @RequestParam("file") MultipartFile file) {
        try {
            String commitSha = githubCommitService.createCommit(orgName, repoName, file, filePath, branch);
            return ResponseEntity.ok("Commit created with SHA: " + commitSha);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create commit: " + e.getMessage());
        }
    }



    @PostMapping("/github/create-pull-request")
    public String createPullRequest(@RequestBody Map<String, String> request) {
        String orgName = request.get("orgName");
        String repoName = request.get("repoName");
        String title = request.get("title");
        String headBranch = request.get("headBranch");
        String baseBranch = request.get("baseBranch");
        return githubService.createPullRequest(orgName, repoName, title, headBranch, baseBranch);
    }


}


