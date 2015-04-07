/*
 * Copyright 2015 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kantega.reststop.maven;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.kantega.reststop.pluginutils.Plugin;
import org.kantega.reststop.pluginutils.PluginUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 */
public abstract class AbstractReststopMojo extends AbstractMojo {


    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue ="${repositorySystemSession}" ,readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue ="${localRepository}" ,required = true, readonly = true)
    protected ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    protected List<RemoteRepository> remoteRepos;

    @Parameter (defaultValue = "org.kantega.reststop:reststop-webapp:war:${plugin.version}")
    protected String warCoords;

    @Parameter(defaultValue = "${project.build.directory}/reststop/temp")
    private File tempDirectory;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}")
    private File pluginJar;

    @Parameter(defaultValue = "${project}")
    protected MavenProject mavenProject;

    @Parameter
    protected List<Plugin> plugins;

    @Parameter(defaultValue =  "${basedir}/src/config")
    private File configDir;

    @Parameter (defaultValue = "/")
    private String contextPath;

    @Parameter (defaultValue = "${plugin.version}")
    private String pluginVersion;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        PluginUtils pluginUtils = new PluginUtils(mavenProject, new File(localRepository.getBasedir()));

        File war = pluginUtils.resolveArtifact(warCoords).getFile();

        startJetty(war);

    }

    private void startJetty(File war) throws MojoExecutionException {
        try {

            System.setProperty("reststopPluginDir", mavenProject.getBasedir().getAbsolutePath());

            int port = nextAvailablePort(8080);

            mavenProject.getProperties().setProperty("reststopPort", Integer.toString(port));
            System.setProperty("reststopPort", Integer.toString(port));

            Server server = new Server(port);

            mavenProject.setContextValue("jettyServer", server);

            JettyWebAppContext context = new JettyWebAppContext();

            context.addServerClass("org.eclipse.aether.");
            context.setWar(war.getAbsolutePath());
            context.setContextPath(contextPath);
            PluginUtils pluginsUtils = new PluginUtils(mavenProject, new File(localRepository.getBasedir()));
            context.getServletContext().setAttribute("pluginsXml", pluginsUtils.createPluginXmlDocument(getPlugins(), false));
            context.setInitParameter("pluginConfigurationDirectory", configDir.getAbsolutePath());

            customizeContext(context);

            tempDirectory.mkdirs();
            context.setTempDirectory(tempDirectory);
            context.setThrowUnavailableOnStartupException(true);

            HandlerCollection handlers = new HandlerCollection();

            handlers.addHandler(new ShutdownHandler(server, getLog()));
            handlers.addHandler(context);
            server.setHandler(handlers);

            server.start();

            afterServerStart(server, port);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed starting Jetty ", e);
        }
    }

    protected void customizeContext(JettyWebAppContext context) {

    }

    protected void afterServerStart(Server server, int port) throws MojoFailureException {

    }

    protected List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>();

        if(this.plugins != null) {
            plugins.addAll(this.plugins);
        }


        return plugins;
    }


    private int nextAvailablePort(int first) {
        int port = first;
        for(;;) {
            try {
                ServerSocket socket = new ServerSocket(port);
                socket.close();
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    protected File getSourceDirectory(Plugin plugin) {
        String path = repoSession.getLocalRepositoryManager().getPathForLocalArtifact(new DefaultArtifact(plugin.getGroupId(), plugin.getArtifactId(), "sourceDir", plugin.getVersion()));

        File file = new File(repoSession.getLocalRepository().getBasedir(), path);
        try {
            return file.exists() ? new File(Files.readAllLines(file.toPath(), Charset.forName("utf-8")).get(0)) : null;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected void addDevelopmentPlugins(List<Plugin> plugins) {
        {
            Plugin devConsolePlugin = new Plugin("org.kantega.reststop", "reststop-development-console", pluginVersion);
            plugins.add(devConsolePlugin);
            devConsolePlugin.setDirectDeploy(false);
        }

        for (Plugin plugin : plugins) {
            plugin.setDirectDeploy(false);
        }
        {
            Plugin developmentPlugin = new Plugin("org.kantega.reststop", "reststop-development-plugin", pluginVersion);
            plugins.add(developmentPlugin);
            developmentPlugin.setDirectDeploy(true);
        }


        for (Plugin plugin : plugins) {
            File sourceDirectory = getSourceDirectory(plugin);
            plugin.setSourceDirectory(sourceDirectory);
            File sourcePom = new File(sourceDirectory, "pom.xml");
            if(sourcePom.exists()){
                plugin.setSourcePomLastModified(new Date(sourcePom.lastModified()));
            }
        }
    }

    private class ShutdownHandler extends AbstractHandler {
        private final Server server;
        private final Log log;

        public ShutdownHandler(Server server, Log log) {
            this.server = server;
            this.log = log;
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            if("/shutdown".equals(target) && ! (server.isStopping() || server.isStopped())) {
                try {
                    log.info("Shutting down Jetty server");
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                server.stop();
                            } catch (Throwable e) {
                                org.eclipse.jetty.util.log.Log.getLogger(getClass()).ignore(e);
                            }
                        }
                    }.start();
                } catch (Exception e) {
                    throw new ServletException(e);
                }
                baseRequest.setHandled(true);
            }
        }
    }
}
