/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.controller.git;

import static org.eclipse.jgit.lib.Constants.DOT_GIT_IGNORE;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jboss.as.server.logging.ServerLogger;
import org.wildfly.client.config.ConfigXMLParseException;

/**
 * Abstraction over a git repository.
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class GitRepository implements Closeable {
    private final Set<String> ignored;
    private final Repository repository;
    private final Path basePath;
    private final String defaultRemoteRepository;
    private final String branch;

    public GitRepository(Path root, String gitRepository, String gitBranch, URI authenticationConfig, Set<String> ignored)
            throws IllegalArgumentException, IOException, ConfigXMLParseException, GeneralSecurityException {
        this.basePath = root;
        this.branch = gitBranch == null || gitBranch.isEmpty() ? Constants.MASTER : gitBranch;
        this.ignored = ignored;
        this.defaultRemoteRepository = gitRepository;
        File baseDir = root.toFile();
        File gitDir = new File(baseDir, Constants.DOT_GIT);
        if(authenticationConfig != null) {
            CredentialsProvider.setDefault(new ElytronClientCredentialsProvider(authenticationConfig));
        }
        if (gitDir.exists()) {
            try {
                repository = new FileRepositoryBuilder().setWorkTree(baseDir).setGitDir(gitDir).setup().build();
            } catch (IOException ex) {
                throw ServerLogger.ROOT_LOGGER.failedToPullRepository(ex, gitRepository);
            }
            try (Git git = Git.wrap(repository)) {
                git.clean();
                if(!isLocalGitRepository(gitRepository)) {
                   PullResult result = git.pull().setRemote(getRemoteName(gitRepository)).setRemoteBranchName(gitBranch).setStrategy(MergeStrategy.RESOLVE).call();
                   if(!result.isSuccessful()) {
                       throw ServerLogger.ROOT_LOGGER.failedToPullRepository(null, gitRepository);
                   }
                }
            } catch (GitAPIException ex) {
                throw ServerLogger.ROOT_LOGGER.failedToPullRepository(ex, gitRepository);
            }
        } else {
            if(isLocalGitRepository(gitRepository)) {
                try (Git git = Git.init().setDirectory(baseDir).call()) {
                    git.add().addFilepattern("configuration/*.xml").call();
                    for(String configFile : root.resolve("configuration").toFile().list()) {
                        if(!"logging.properties".equals(configFile)) {
                            git.add().addFilepattern("configuration/" + configFile).call();
                        }
                    }
                    git.add().addFilepattern("data/content").call();
                    createGitIgnore(git, root);
                    git.commit().setMessage("Repository initialized").call();
                } catch (GitAPIException | IOException ex) {
                    throw ServerLogger.ROOT_LOGGER.failedToInitRepository(ex, gitRepository);
                }
            } else {
                clearExistingFiles(root, gitRepository);
                try (Git git = Git.init().setDirectory(baseDir).call()) {
                    String remoteName = UUID.randomUUID().toString();
                    StoredConfig config = git.getRepository().getConfig();
                    config.setString("remote", remoteName, "url", gitRepository);
                    config.setString("remote", remoteName,"fetch", "+refs/heads/*:refs/remotes/" + remoteName + "/*");
                    config.save();
                    git.clean();
                    git.pull().setRemote(remoteName).setRemoteBranchName(gitBranch).setStrategy(MergeStrategy.RESOLVE).call();
                    if(createGitIgnore(git, root)) {
                        git.commit().setMessage("Adding .gitignore").call();
                    }
                } catch (GitAPIException ex) {
                    throw ServerLogger.ROOT_LOGGER.failedToInitRepository(ex, gitRepository);
                }
            }
            repository = new FileRepositoryBuilder().setWorkTree(baseDir).setGitDir(gitDir).setup().build();
            ServerLogger.ROOT_LOGGER.usingGit();
        }
    }

   public GitRepository(Repository repository) {
        this.repository = repository;
        this.ignored = Collections.emptySet();
        this.defaultRemoteRepository = Constants.DEFAULT_REMOTE_NAME;
        this.branch = Constants.MASTER;
        if(repository.isBare()) {
            this.basePath = repository.getDirectory().toPath();
        } else {
            this.basePath = repository.getDirectory().toPath().getParent();
        }
     }


    private void clearExistingFiles(Path root, String gitRepository) {
        try {
            Files.walkFileTree(root, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    try {
                        Files.delete(dir);
                    } catch (IOException ioex) {
                    }
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (IOException ex) {
            throw ServerLogger.ROOT_LOGGER.failedToInitRepository(ex, gitRepository);
        }
    }

    private boolean createGitIgnore(Git git, Path root) throws IOException, GitAPIException {
        Path gitIgnore = root.resolve(DOT_GIT_IGNORE);
        if (Files.notExists(gitIgnore)) {
            Files.write(gitIgnore, ignored);
            git.add().addFilepattern(DOT_GIT_IGNORE).call();
            return true;
        }
        return false;
    }

    private boolean isLocalGitRepository(String gitRepository) {
        return "local".equals(gitRepository);
    }

    public Git getGit() {
        return Git.wrap(repository);
    }

    public File getDirectory() {
        return repository.getDirectory();
    }

    public boolean isBare() {
        return repository.isBare();
    }

    @Override
    public void close() {
        this.repository.close();
    }

    public String getPattern(File file) {
        return getPattern(file.toPath());
    }

    public String getPattern(Path file) {
        return basePath.toAbsolutePath().relativize(file.toAbsolutePath()).toString();
    }

    public String getBranch() {
        return branch;
    }

    public final boolean isValidRemoteName(String remoteName) {
        return repository.getRemoteNames().contains(remoteName);
    }

    public final String getRemoteName(String gitRepository) {
        return findRemoteName(gitRepository == null || gitRepository.isEmpty() ? defaultRemoteRepository : gitRepository);
    }

    private String findRemoteName(String gitRepository) {
        if(isValidRemoteName(gitRepository)) {
            return gitRepository;
        }
        StoredConfig config = repository.getConfig();
        for(String remoteName : repository.getRemoteNames()) {
            if(gitRepository.equals(config.getString("remote", remoteName, "url"))) {
                return remoteName;
            }
        }
        return null;
    }

    /**
     * Reset hard on HEAD.
     * @throws GitAPIException
     */
    public void rollback() throws GitAPIException {
        try (Git git = getGit()) {
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef(Constants.HEAD).call();
        }
    }

    /**
     * Commit all changes if there are uncommitted changes.
     * @param msg the commit message.
     * @throws GitAPIException
     */
    public void commit(String msg) throws GitAPIException {
        try (Git git = getGit()) {
            Status status = git.status().call();
            if(!status.isClean()) {
                git.commit().setMessage(msg).setAll(true).setNoVerify(true).call();
            }
        }
    }
}
