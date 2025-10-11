package com.liuzhihang.doc.view.utils;


import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import groovyjarjarantlr4.v4.runtime.misc.Nullable;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.jetbrains.idea.maven.utils.MavenUtil; // 用到 MavenUtil 里的 KEY
import java.util.Collections;


public class ExternalPsiUtils {

    /**
     * 输入任意 PsiClass（来自 Maven 依赖），
     * 返回对应源码 PsiClass（字段带注释），
     * 整个过程全自动：下载-sources.jar → 解压 → 内存 PSI → 返回
     */
    public static PsiClass toSourcePsiClass(@NotNull PsiClass binaryClass) {
        Project project = binaryClass.getProject();

        // 1. 反推 GAV
        String[] gav = resolveMavenGav(binaryClass);
        if (gav == null) return null;          // 不是 Maven 库
        String group = gav[0], artifact = gav[1], version = gav[2];

        try {
            // 2. 下载 sources.jar → 临时目录
            Path sourcesJar = downloadSources(group, artifact, version,project);
            Path unzipDir = unzipToTemp(sourcesJar);

            // 3. 拼 .java 文件路径
            String qName = binaryClass.getQualifiedName();
            Path javaFile = unzipDir.resolve(qName.replace('.', '/') + ".java");
            if (!Files.exists(javaFile)) return null;

            // 4. 读文本 → 内存 PsiJavaFile
            String text = Files.readString(javaFile);
            PsiJavaFile javaFilePsi = (PsiJavaFile) PsiFileFactory.getInstance(project)
                    .createFileFromText(javaFile.getFileName().toString(),
                            JavaFileType.INSTANCE, text);

            // 5. 按 qualifiedName 找类（支持嵌套）
            return Arrays.stream(javaFilePsi.getClasses())
                    .filter(c -> qName.equals(c.getQualifiedName()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private static String[] resolveMavenGav(PsiClass psiClass) {
        VirtualFile vf = psiClass.getContainingFile().getVirtualFile();
        if (vf == null) return null;
        ProjectFileIndex idx = ProjectRootManager.getInstance(psiClass.getProject()).getFileIndex();
        Library library = idx.getOrderEntriesForFile (vf).stream()
                .filter(e -> e instanceof LibraryOrderEntry)
                .map(e -> ((LibraryOrderEntry) e).getLibrary())
                .findFirst()
                .orElse(null);
        if (library == null) return null;

        // Maven 坐标藏在 Library 名字里： "Maven: com.alibaba:fastjson:1.2.83"
        String name = library.getName();          // 可能为 null
        if (name != null && name.startsWith("Maven: ")) {
            String[] split = name.substring(7).split(":");
            if (split.length >= 3) {
                return new String[]{split[0], split[1], split[2]};
            }
        }
    /* 如果名字拿不到，还可以解析 library 的根路径：
       ~/.m2/repository/com/alibaba/fastjson/1.2.83/fastjson-1.2.83.jar
       用正则一样能抽 GAV，代码略 */
        return null;
    }



    private static Path downloadSources(String g, String a, String v, Project project) throws IOException {
        String jar = a + "-" + v + "-sources.jar";
        String rel = String.join("/", g.split("\\.")) + "/" + a + "/" + v + "/" + jar;
        String repoUrl = getRemoteRepoUrl(project).replaceAll("/$", ""); // 去掉末尾 /
        URI uri = URI.create(repoUrl + "/" + rel);

        Path cache = Paths.get(System.getProperty("user.home"),
                ".cache/idea-auto-sources", rel);
        if (Files.exists(cache)) return cache;

        Files.createDirectories(cache.getParent());
        try (InputStream in = uri.toURL().openStream()) {
            Files.copy(in, cache, StandardCopyOption.REPLACE_EXISTING);
        }
        return cache;
    }

    private static Path unzipToTemp(Path jar) throws IOException {
        Path unzipDir = jar.getParent().resolve(jar.getFileName().toString().replace(".jar", ""));
        if (Files.exists(unzipDir)) return unzipDir;

        Files.createDirectories(unzipDir);
        try (JarFile jf = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                Path out = unzipDir.resolve(e.getName());
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = jf.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return unzipDir;
    }


    /**
     * 返回 IDEA 里 Maven 配置的第一个远程仓库 URL（默认 central）
     */

    public static String getRemoteRepoUrl(Project project) {
        try {
            MavenProjectsManager mgr = MavenProjectsManager.getInstance(project);
            Object embeddersMgr = mgr.getEmbeddersManager();

            Object embedder;
            /* 1. 拿到 embedder */
            try {
                embedder = embeddersMgr.getClass().getMethod("getGlobalEmbedder").invoke(embeddersMgr);
            } catch (NoSuchMethodException ignore) {
                embedder = embeddersMgr.getClass()
                        .getMethod("getEmbedder",
                                com.intellij.openapi.util.Key.class, String.class, String.class)
                        .invoke(embeddersMgr, null, "", "");
            }

            /* 2. 拿到仓库列表 */
            List<ArtifactRepository> repos = getRepos(embedder, project);
            return repos.isEmpty() ? "https://repo1.maven.org/maven2/"
                    : repos.get(0).getUrl();
        } catch (Exception e) {
            return "https://repo1.maven.org/maven2/";
        }
    }
    private static List<ArtifactRepository> getRepos(Object embedder, Project project) throws Exception {
        try {
            // 2023.2+ 无参
            return (List<ArtifactRepository>) embedder.getClass()
                    .getMethod("getRemoteRepositories")
                    .invoke(embedder);
        } catch (NoSuchMethodException ignore) {
            // 2022.3- 需要 Project 参数
            return (List<ArtifactRepository>) embedder.getClass()
                    .getMethod("getRemoteRepositories", Project.class)
                    .invoke(embedder, project);
        }
    }

}
