package com.liuzhihang.doc.view.utils;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LocalSourceJarProcessor {
    private static final Logger LOG = Logger.getInstance(LocalSourceJarProcessor.class);

    /**
     * 同步方法：直接将没有注释的PsiClass转换成带注释的PsiClass
     */
    public static PsiClass convertToClassWithComments(@NotNull PsiClass originalClass) {
        Project project = originalClass.getProject();

        try {
            LOG.info("start trans: " + originalClass.getQualifiedName());

            // 1. 查找本地source jar
            SourceJarInfo sourceJarInfo = findLocalSourceJar(originalClass, project);
            if (sourceJarInfo == null) {
                LOG.warn("can not find jar: " + originalClass.getQualifiedName());
                return originalClass;
            }

            // 2. 提取源码到临时目录
            File extractedDir = extractSourceJarToProject(sourceJarInfo.getSourceJarFile(), project);
            if (extractedDir == null) {
                LOG.warn("unzip jar fail: " + sourceJarInfo.getSourceJarFile().getAbsolutePath());
                return originalClass;
            }

            // 3. 重新解析源码文件
            PsiClass classWithComments = reloadClassFromSource(project, sourceJarInfo, extractedDir);
            if (classWithComments != null) {
                LOG.info("success: " + classWithComments.getQualifiedName());
                return classWithComments;
            } else {
                LOG.warn("resove source code fail: " + originalClass.getQualifiedName());
                return originalClass;
            }

        } catch (Exception e) {
            LOG.error("trans source code faild: " + originalClass.getQualifiedName(), e);
            return originalClass;
        }
    }

    /**
     * 查找本地Maven仓库中的source jar
     */
    private static SourceJarInfo findLocalSourceJar(PsiClass psiClass, Project project) {
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }

        // 获取准确的Maven artifact信息
        Optional<MavenArtifactInfo> artifactInfoOpt = getAccurateMavenArtifactInfo(psiClass, project);
        if (!artifactInfoOpt.isPresent()) {
            LOG.warn("can not find Maven artifact info: " + qualifiedName);
            return null;
        }

        MavenArtifactInfo artifactInfo = artifactInfoOpt.get();
        String localRepoPath = getLocalMavenRepositoryPath(project);
        if (localRepoPath == null) {
            LOG.warn("can not find local Maven path");
            return null;
        }

        // 构建source jar路径
        File sourceJarFile = findSourceJarInRepository(localRepoPath, artifactInfo);
        if (sourceJarFile == null) {
            LOG.warn("can not find source.jar " + artifactInfo);
            return null;
        }

        LOG.info("find source.jar : " + sourceJarFile.getAbsolutePath());
        return new SourceJarInfo(sourceJarFile, artifactInfo, qualifiedName);
    }

    /**
     * 获取准确的Maven artifact信息
     */
    private static Optional<MavenArtifactInfo> getAccurateMavenArtifactInfo(PsiClass psiClass, Project project) {
        // 方法1: 从类所在的JAR文件路径中提取准确信息（最可靠）
        Optional<MavenArtifactInfo> fromJarPath = extractArtifactInfoFromJarPath(psiClass, project);
        if (fromJarPath.isPresent()) {
            LOG.info(" find JAR path artifact  info: " + fromJarPath.get());
            return fromJarPath;
        }

        // 方法2: 从项目依赖中查找
        Optional<MavenArtifactInfo> fromDependencies = findArtifactFromProjectDependencies(psiClass, project);
        if (fromDependencies.isPresent()) {
            LOG.info("find artifact info from depend: " + fromDependencies.get());
            return fromDependencies;
        }

        // 方法3: 从Maven项目中查找
        return findArtifactFromMavenProjects(project, psiClass);
    }

    /**
     * 从JAR文件路径中提取artifact信息
     */
    private static Optional<MavenArtifactInfo> extractArtifactInfoFromJarPath(PsiClass psiClass, Project project) {
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile == null) {
            return Optional.empty();
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            return Optional.empty();
        }

        String filePath = virtualFile.getPath();
        LOG.debug("分析文件路径: " + filePath);

        // 检查是否在Maven仓库中
        if (filePath.contains(".m2/repository")) {
            return extractFromMavenRepositoryPath(filePath);
        }

        // 检查是否在Gradle缓存中
        if (filePath.contains(".gradle/caches") || filePath.contains("caches/modules-2")) {
            return extractFromGradleCachePath(filePath);
        }

        return Optional.empty();
    }

    /**
     * 从Maven仓库路径中提取artifact信息
     */
    private static Optional<MavenArtifactInfo> extractFromMavenRepositoryPath(String filePath) {
        String[] pathParts = filePath.split("/");

        for (int i = 0; i < pathParts.length; i++) {
            if ("repository".equals(pathParts[i]) && i + 4 < pathParts.length) {
                StringBuilder groupId = new StringBuilder();
                int groupIdEndIndex = i + 4;
                for (int j = i + 1; j < groupIdEndIndex; j++) {
                    if (j > i + 1) groupId.append(".");
                    groupId.append(pathParts[j]);
                }

                String artifactId = pathParts[groupIdEndIndex];
                String versionDir = pathParts[groupIdEndIndex + 1];

                String version = versionDir;
                if (version.contains(artifactId + "-")) {
                    version = version.substring(artifactId.length() + 1);
                }

                if (version.endsWith(".jar")) {
                    version = version.substring(0, version.length() - 4);
                }

                return Optional.of(new MavenArtifactInfo(groupId.toString(), artifactId, version));
            }
        }

        return Optional.empty();
    }

    /**
     * 从Gradle缓存路径中提取artifact信息
     */
    private static Optional<MavenArtifactInfo> extractFromGradleCachePath(String filePath) {
        String[] pathParts = filePath.split("/");

        for (int i = 0; i < pathParts.length; i++) {
            if (("files-2".equals(pathParts[i]) || "modules-2".equals(pathParts[i])) && i + 3 < pathParts.length) {
                String groupId = pathParts[i + 1];
                String artifactId = pathParts[i + 2];
                String version = pathParts[i + 3];

                return Optional.of(new MavenArtifactInfo(groupId, artifactId, version));
            }
        }

        return Optional.empty();
    }

    /**
     * 从项目依赖中查找artifact信息
     */
    private static Optional<MavenArtifactInfo> findArtifactFromProjectDependencies(PsiClass psiClass, Project project) {
        PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile == null) {
            return Optional.empty();
        }

        VirtualFile virtualFile = containingFile.getVirtualFile();
        if (virtualFile == null) {
            return Optional.empty();
        }

        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        List<OrderEntry> orderEntriesForFile = fileIndex.getOrderEntriesForFile(virtualFile);

        for (OrderEntry orderEntry : orderEntriesForFile) {
            if (orderEntry instanceof LibraryOrderEntry) {
                LibraryOrderEntry libraryEntry = (LibraryOrderEntry) orderEntry;
                Optional<MavenArtifactInfo> info = extractAccurateArtifactInfoFromLibrary(libraryEntry.getLibrary());
                if (info.isPresent()) {
                    return info;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 从Library中提取准确的artifact信息
     */
    private static Optional<MavenArtifactInfo> extractAccurateArtifactInfoFromLibrary(Library library) {
        if (library == null) {
            return Optional.empty();
        }

        String libraryName = library.getName();
        if (libraryName != null) {
            if (libraryName.startsWith("Maven: ")) {
                String mavenPart = libraryName.substring(7);
                String[] parts = mavenPart.split(":");
                if (parts.length >= 3) {
                    return Optional.of(new MavenArtifactInfo(parts[0], parts[1], parts[2]));
                }
            }

            if (libraryName.startsWith("Gradle: ")) {
                String gradlePart = libraryName.substring(8);
                String[] parts = gradlePart.split(":");
                if (parts.length >= 3) {
                    return Optional.of(new MavenArtifactInfo(parts[0], parts[1], parts[2]));
                }
            }
        }

        String[] urls = library.getUrls(OrderRootType.CLASSES);
        for (String url : urls) {
            if (url.contains(".m2/repository")) {
                Optional<MavenArtifactInfo> info = extractFromMavenRepositoryPath(url);
                if (info.isPresent()) {
                    return info;
                }
            } else if (url.contains(".gradle/caches")) {
                Optional<MavenArtifactInfo> info = extractFromGradleCachePath(url);
                if (info.isPresent()) {
                    return info;
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 从Maven项目中查找artifact信息
     */
    private static Optional<MavenArtifactInfo> findArtifactFromMavenProjects(Project project, PsiClass psiClass) {
        try {
            Class<?> mavenProjectsManagerClass = Class.forName("org.jetbrains.idea.maven.project.MavenProjectsManager");
            Object mavenManager = mavenProjectsManagerClass.getMethod("getInstance", Project.class).invoke(null, project);

            java.util.List<?> mavenProjects = (java.util.List<?>) mavenManager.getClass().getMethod("getProjects").invoke(mavenManager);

            String qualifiedName = psiClass.getQualifiedName();
            if (qualifiedName == null) {
                return Optional.empty();
            }

            String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));

            for (Object mavenProject : mavenProjects) {
                java.util.List<?> dependencies = (java.util.List<?>) mavenProject.getClass().getMethod("getDependencies").invoke(mavenProject);

                for (Object artifact : dependencies) {
                    String groupId = (String) artifact.getClass().getMethod("getGroupId").invoke(artifact);
                    String artifactId = (String) artifact.getClass().getMethod("getArtifactId").invoke(artifact);
                    String version = (String) artifact.getClass().getMethod("getVersion").invoke(artifact);

                    if (packageName.startsWith(groupId)) {
                        return Optional.of(new MavenArtifactInfo(groupId, artifactId, version));
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Maven项目管理器不可用或访问出错", e);
        }

        return Optional.empty();
    }

    /**
     * 在Maven仓库中查找source jar
     */
    private static File findSourceJarInRepository(String localRepoPath, MavenArtifactInfo artifactInfo) {
        String groupPath = artifactInfo.getGroupId().replace('.', '/');
        String artifactDir = artifactInfo.getArtifactId();
        String version = artifactInfo.getVersion();

        String[] possiblePaths = {
                String.format("%s/%s/%s/%s/%s-%s-sources.jar",
                        localRepoPath, groupPath, artifactDir, version, artifactDir, version),
                String.format("%s/%s/%s/%s/%s-sources.jar",
                        localRepoPath, groupPath, artifactDir, version, artifactDir),
                String.format("%s/%s/%s/%s/sources.jar",
                        localRepoPath, groupPath, artifactDir, version),
                String.format("%s/%s/%s/%s/%s-%s-sources.jar",
                        localRepoPath, groupPath, artifactDir, version, artifactDir, cleanVersion(version))
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }

        return null;
    }

    /**
     * 清理版本号
     */
    private static String cleanVersion(String version) {
        if (version.contains("-")) {
            return version.substring(0, version.indexOf('-'));
        }
        return version;
    }

    /**
     * 获取本地Maven仓库路径
     */
    private static String getLocalMavenRepositoryPath(Project project) {
        try {
            Class<?> mavenProjectsManagerClass = Class.forName("org.jetbrains.idea.maven.project.MavenProjectsManager");
            Object mavenManager = mavenProjectsManagerClass.getMethod("getInstance", Project.class).invoke(null, project);

            java.util.List<?> mavenProjects = (java.util.List<?>) mavenManager.getClass().getMethod("getProjects").invoke(mavenManager);
            if (!mavenProjects.isEmpty()) {
                Object firstProject = mavenProjects.get(0);
                String localRepo = (String) firstProject.getClass().getMethod("getLocalRepository").invoke(firstProject);
                if (localRepo != null && !localRepo.isEmpty()) {
                    return localRepo;
                }
            }
        } catch (Exception e) {
            LOG.debug("无法从项目设置获取Maven本地仓库", e);
        }

        String userHome = System.getProperty("user.home");
        return userHome + "/.m2/repository";
    }

    /**
     * 解压source jar到项目目录
     */
    private static File extractSourceJarToProject(File sourceJar, Project project) {
        String projectBasePath = project.getBasePath();
        if (projectBasePath == null) {
            return null;
        }

        File tempDir = new File(projectBasePath, ".idea/sources-extracted");
        tempDir.mkdirs();

        String jarName = sourceJar.getName().replace(".jar", "").replace("-sources", "");
        File extractedDir = new File(tempDir, jarName);

        if (extractedDir.exists() && extractedDir.isDirectory()) {
            return extractedDir;
        }

        extractedDir.mkdirs();

        try (JarFile jarFile = new JarFile(sourceJar)) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                File entryFile = new File(extractedDir, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                    continue;
                }

                entryFile.getParentFile().mkdirs();

                try (InputStream is = jarFile.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(entryFile)) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }

            return extractedDir;

        } catch (IOException e) {
            LOG.error(" unzip source jar fail: " + sourceJar.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 从源码重新解析类
     */
    private static PsiClass reloadClassFromSource(Project project, SourceJarInfo sourceJarInfo, File extractedDir) {
        String qualifiedName = sourceJarInfo.getQualifiedClassName();

        String relativePath = qualifiedName.replace('.', '/') + ".java";
        File sourceFile = new File(extractedDir, relativePath);

        if (!sourceFile.exists()) {
            LOG.warn("source faile not exist: " + sourceFile.getAbsolutePath());
            return null;
        }

        VirtualFile virtualSourceFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceFile);
        if (virtualSourceFile == null) {
            LOG.warn("vm source faile not exist: " + sourceFile.getAbsolutePath());
            return null;
        }

        // 同步解析源码文件
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualSourceFile);

        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) psiFile;

            // 查找对应的类
            for (PsiClass psiClass : javaFile.getClasses()) {
                if (qualifiedName.equals(psiClass.getQualifiedName())) {
                    // 验证注释是否已加载
                    if (hasComments(psiClass)) {
                        LOG.info("load comment success: " + psiClass.getQualifiedName());
                    } else {
                        LOG.warn("class has no comment: " + psiClass.getQualifiedName());
                    }
                    return psiClass;
                }
            }
        }

        return null;
    }

    /**
     * 检查类是否有注释
     */
    private static boolean hasComments(PsiClass psiClass) {
        // 检查类文档注释
        if (psiClass.getDocComment() != null) {
            return true;
        }

        // 检查类前的注释
        PsiElement prevSibling = psiClass.getPrevSibling();
        while (prevSibling instanceof PsiComment || prevSibling instanceof PsiWhiteSpace) {
            if (prevSibling instanceof PsiComment) {
                return true;
            }
            prevSibling = prevSibling.getPrevSibling();
        }

        // 检查方法注释
        for (PsiMethod method : psiClass.getMethods()) {
            if (method.getDocComment() != null) {
                return true;
            }
        }

        // 检查字段注释
        for (PsiField field : psiClass.getFields()) {
            if (field.getDocComment() != null) {
                return true;
            }
        }

        return false;
    }

    // 辅助类
    private static class SourceJarInfo {
        private final File sourceJarFile;
        private final MavenArtifactInfo artifactInfo;
        private final String qualifiedClassName;

        public SourceJarInfo(File sourceJarFile, MavenArtifactInfo artifactInfo, String qualifiedClassName) {
            this.sourceJarFile = sourceJarFile;
            this.artifactInfo = artifactInfo;
            this.qualifiedClassName = qualifiedClassName;
        }

        public File getSourceJarFile() { return sourceJarFile; }
        public MavenArtifactInfo getArtifactInfo() { return artifactInfo; }
        public String getQualifiedClassName() { return qualifiedClassName; }
    }

    private static class MavenArtifactInfo {
        private final String groupId;
        private final String artifactId;
        private final String version;

        public MavenArtifactInfo(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }
}