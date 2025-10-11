package com.liuzhihang.doc.view.utils;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MavenRepositoryReader {

    /**
     * 获取 Maven 本地仓库地址
     */
    @Nullable
    public static String getLocalRepositoryPath(@NotNull Project project) {
        try {
            MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
            if (mavenManager.hasProjects()) {
                MavenGeneralSettings generalSettings = mavenManager.getGeneralSettings();
                return generalSettings.getLocalRepository();
            }
        } catch (Exception e) {
            System.err.println("Failed to get local repository: " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取所有远程仓库 URL（去重）
     */
    @NotNull
    public static Set<String> getAllRemoteRepositoryUrls(@NotNull Project project) {
        Set<String> repositoryUrls = new LinkedHashSet<>();

        try {
            MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);

            if (mavenManager.hasProjects()) {
                List<MavenProject> mavenProjects = mavenManager.getProjects();

                for (MavenProject mavenProject : mavenProjects) {
                    List<MavenRemoteRepository> remoteRepositories = mavenProject.getRemoteRepositories();

                    for (MavenRemoteRepository repository : remoteRepositories) {
                        String url = repository.getUrl();
                        if (url != null && !url.trim().isEmpty()) {
                            repositoryUrls.add(url);
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to get remote repositories: " + e.getMessage());
        }

        return repositoryUrls;
    }

    /**
     * 获取详细的仓库信息（包括 ID 和 URL）
     */
    @NotNull
    public static List<RepositoryInfo> getDetailedRepositoryInfo(@NotNull Project project) {
        List<RepositoryInfo> repositories = new ArrayList<>();

        try {
            MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);

            if (mavenManager.hasProjects()) {
                List<MavenProject> mavenProjects = mavenManager.getProjects();

                for (MavenProject mavenProject : mavenProjects) {
                    List<MavenRemoteRepository> remoteRepositories = mavenProject.getRemoteRepositories();

                    for (MavenRemoteRepository repo : remoteRepositories) {
                        repositories.add(new RepositoryInfo(
                                repo.getId(),
                                repo.getUrl(),
                                repo.getName(),
                                repo.getLayout(),
                                repo.getSnapshotsPolicy().toString(),
                                repo.getReleasesPolicy().toString()
                        ));
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Failed to get detailed repository info: " + e.getMessage());
        }

        return repositories;
    }

    /**
     * 检查是否包含特定的仓库（如 Maven Central）
     */
    public static boolean containsRepository(@NotNull Project project, @NotNull String repositoryUrl) {
        Set<String> repositories = getAllRemoteRepositoryUrls(project);
        return repositories.stream()
                .anyMatch(url -> url.contains(repositoryUrl));
    }

    /**
     * 获取所有仓库信息的汇总
     */
    @NotNull
    public static RepositorySummary getRepositorySummary(@NotNull Project project) {
        String localRepo = getLocalRepositoryPath(project);
        Set<String> remoteRepos = getAllRemoteRepositoryUrls(project);

        return new RepositorySummary(localRepo, remoteRepos);
    }

    /**
     * 仓库信息详情类
     */
    public static class RepositoryInfo {
        private final String id;
        private final String url;
        private final String name;
        private final String layout;
        private final String snapshotsPolicy;
        private final String releasesPolicy;

        public RepositoryInfo(String id, String url, String name, String layout,
                              String snapshotsPolicy, String releasesPolicy) {
            this.id = id;
            this.url = url;
            this.name = name;
            this.layout = layout;
            this.snapshotsPolicy = snapshotsPolicy;
            this.releasesPolicy = releasesPolicy;
        }

        // Getters
        public String getId() { return id; }
        public String getUrl() { return url; }
        public String getName() { return name; }
        public String getLayout() { return layout; }
        public String getSnapshotsPolicy() { return snapshotsPolicy; }
        public String getReleasesPolicy() { return releasesPolicy; }

        @Override
        public String toString() {
            return String.format("Repository{id='%s', url='%s', name='%s'}", id, url, name);
        }
    }

    /**
     * 仓库汇总信息类
     */
    public static class RepositorySummary {
        private final String localRepository;
        private final Set<String> remoteRepositories;

        public RepositorySummary(String localRepository, Set<String> remoteRepositories) {
            this.localRepository = localRepository;
            this.remoteRepositories = remoteRepositories;
        }

        // Getters
        public String getLocalRepository() { return localRepository; }
        public Set<String> getRemoteRepositories() { return remoteRepositories; }

        @Override
        public String toString() {
            return String.format("RepositorySummary{local='%s', remote=%s}",
                    localRepository, remoteRepositories);
        }
    }
}