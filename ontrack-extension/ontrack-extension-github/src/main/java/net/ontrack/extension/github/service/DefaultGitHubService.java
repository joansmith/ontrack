package net.ontrack.extension.github.service;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import net.ontrack.core.model.BranchSummary;
import net.ontrack.core.model.ProjectSummary;
import net.ontrack.extension.git.client.GitCommit;
import net.ontrack.extension.git.model.GitCommitInfo;
import net.ontrack.extension.git.service.GitService;
import net.ontrack.extension.github.GitHubExtension;
import net.ontrack.extension.github.client.GitHubClientConfigurator;
import net.ontrack.extension.github.client.GitHubClientConfiguratorFactory;
import net.ontrack.extension.github.client.OntrackGitHubClient;
import net.ontrack.extension.github.model.GitHubCommit;
import net.ontrack.extension.github.model.GitHubIssue;
import net.ontrack.extension.github.model.GitHubIssueInfo;
import net.ontrack.service.ManagementService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultGitHubService implements GitHubService {

    private final ManagementService managementService;
    private final OntrackGitHubClient gitHubClient;
    private final GitHubClientConfiguratorFactory gitHubClientConfiguratorFactory;
    private final GitService gitService;
    private final GitHubConfigurationService gitHubConfigurationService;

    @Autowired
    public DefaultGitHubService(ManagementService managementService, OntrackGitHubClient gitHubClient, GitHubClientConfiguratorFactory gitHubClientConfiguratorFactory, GitService gitService, GitHubConfigurationService gitHubConfigurationService) {
        this.managementService = managementService;
        this.gitHubClient = gitHubClient;
        this.gitHubClientConfiguratorFactory = gitHubClientConfiguratorFactory;
        this.gitService = gitService;
        this.gitHubConfigurationService = gitHubConfigurationService;
    }

    @Override
    public List<GitHubIssue> getGitHubIssues(int branchId, List<GitCommit> commits) {
        // Gets the branch information
        BranchSummary branch = managementService.getBranch(branchId);
        // GitHubClient configuration
        final GitHubClientConfigurator configurator = gitHubClientConfiguratorFactory.getGitHubConfigurator(branch.getProject().getId());
        // Gets the GitHub project
        final String project = gitHubConfigurationService.getGitHubProject(branch.getProject().getId());
        if (StringUtils.isNotBlank(project)) {
            Set<Integer> issues = new TreeSet<>();
            for (GitCommit commit : commits) {
                issues.addAll(getIssueIds(commit.getFullMessage()));
            }
            // Gets the issues
            return Lists.newArrayList(
                    Collections2.filter(
                            Collections2.transform(
                                    issues,
                                    new Function<Integer, GitHubIssue>() {
                                        @Override
                                        public GitHubIssue apply(Integer id) {
                                            return gitHubClient.getIssue(project, configurator, id);
                                        }
                                    }
                            ),
                            Predicates.notNull()
                    )
            );
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<ProjectSummary> getProjectsWithIssue(int issue) {
        // GitHubClient configuration
        Collection<ProjectSummary> result = new ArrayList<>();
        for (ProjectSummary projectSummary : managementService.getProjectList()) {
            int projectId = projectSummary.getId();
            String gitHubProject = gitHubConfigurationService.getGitHubProject(projectId);
            if (StringUtils.isNotBlank(gitHubProject)) {
                GitHubClientConfigurator configurator = gitHubClientConfiguratorFactory.getGitHubConfigurator(projectId);
                GitHubIssue gitHubIssue = gitHubClient.getIssue(gitHubProject, configurator, issue);
                if (gitHubIssue != null) {
                    result.add(projectSummary);
                }
            }
        }
        return result;
    }

    @Override
    public GitHubIssueInfo getIssueInfo(Locale locale, int projectId, int issueKey) {
        GitHubClientConfigurator configurator = gitHubClientConfiguratorFactory.getGitHubConfigurator(projectId);
        // Gets the project information
        String project = gitHubConfigurationService.getGitHubProject(projectId);
        // Gets the details about the issue
        GitHubIssue issue = gitHubClient.getIssue(project, configurator, issueKey);
        if (issue == null) {
            throw new GitHubIssueNotFoundException(project, issueKey);
        }
        // Gets the list of commit IDs for this issue
        List<GitHubCommit> commits = gitHubClient.getCommitsForIssue(project, configurator, issueKey);
        // Gets the commit info for the last commit
        GitCommitInfo commitInfo = null;
        if (!commits.isEmpty()) {
            commitInfo = gitService.getCommitInfo(locale, commits.get(0).getId());
        }
        // OK
        return new GitHubIssueInfo(
                managementService.getProject(projectId),
                issue,
                commitInfo,
                commits
        );
    }

    private Collection<Integer> getIssueIds(String message) {
        Set<Integer> ids = new HashSet<>();
        Pattern p = Pattern.compile(GitHubExtension.GITHUB_ISSUE_PATTERN);
        Matcher m = p.matcher(message);
        while (m.find()) {
            int id = Integer.parseInt(m.group(1).substring(1), 10);
            ids.add(id);
        }
        return ids;
    }
}
