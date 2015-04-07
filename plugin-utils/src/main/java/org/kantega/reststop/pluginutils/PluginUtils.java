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

package org.kantega.reststop.pluginutils;

import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static java.util.Arrays.asList;

public class PluginUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PluginUtils.class);

    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;
    private final List<RemoteRepository> remoteRepos;
    private final File localRepo;

    public PluginUtils(final MavenProject mavenProject, File localRepo) {
        this(mavenProject.getRemoteProjectRepositories(), localRepo);
    }

    public PluginUtils() {
        this.localRepo = new File(new File(new File(System.getProperty("user.home")).getAbsoluteFile(), ".m2"),"repository");
        this.repoSystem = newRepositorySystem();
        this.repoSession = newRepositorySystemSession(repoSystem);
        this.remoteRepos = newRepositories();
    }

    public PluginUtils(List<RemoteRepository> remoteRepos, File localRepo) {
        this.localRepo = localRepo;
        this.repoSystem = newRepositorySystem();
        this.repoSession = newRepositorySystemSession(repoSystem);
        this.remoteRepos = remoteRepos;
    }

    public Document createPluginXmlDocument(List<Plugin> plugins, boolean prod) {

        List<PluginInfo> pluginInfos = getPluginInfos(plugins);

        return buildPluginsDocument(prod, pluginInfos);
    }

    private Document buildPluginsDocument(boolean prod, List<PluginInfo> pluginInfos) {

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element pluginsElem = doc.createElement("plugins");

            doc.appendChild(pluginsElem);

            for (PluginInfo plugin : pluginInfos) {
                Element pluginElem = doc.createElement("plugin");
                pluginsElem.appendChild(pluginElem);

                for (PluginInfo parent : plugin.getParents(pluginInfos)) {
                    Element dependsElem = doc.createElement("depends-on");
                    pluginElem.appendChild(dependsElem);
                    dependsElem.setAttribute("groupId", parent.getGroupId());
                    dependsElem.setAttribute("artifactId", parent.getArtifactId());
                    dependsElem.setAttribute("version", parent.getVersion());

                }
                for (PluginInfo provider : plugin.getServiceProviders(pluginInfos)) {
                    Element dependsElem = doc.createElement("imports-from");
                    pluginElem.appendChild(dependsElem);
                    dependsElem.setAttribute("groupId", provider.getGroupId());
                    dependsElem.setAttribute("artifactId", provider.getArtifactId());
                    dependsElem.setAttribute("version", provider.getVersion());

                }
                if (!prod) {
                    if (!plugin.getConfig().isEmpty()) {
                        Element configElem = doc.createElement("config");

                        for (String name : plugin.getConfig().stringPropertyNames()) {
                            Element propElem = doc.createElement("prop");
                            propElem.setAttribute("name", name);
                            propElem.setAttribute("value", plugin.getConfig().getProperty(name));
                            configElem.appendChild(propElem);
                        }

                        pluginElem.appendChild(configElem);
                    }
                }

                pluginElem.setAttribute("groupId", plugin.getGroupId());
                pluginElem.setAttribute("artifactId", plugin.getArtifactId());
                pluginElem.setAttribute("version", plugin.getVersion());
                if (!prod) {

                    if (plugin.getSourceDirectory() != null) {
                        pluginElem.setAttribute("sourceDirectory", plugin.getSourceDirectory().getAbsolutePath());
                    }

                    if (plugin.getSourcePomLastModified() != null) {
                        pluginElem.setAttribute("sourcePomLastModified", plugin.getSourcePomLastModified().toString());
                    }

                    pluginElem.setAttribute("pluginFile", plugin.getFile().getAbsolutePath());
                    pluginElem.setAttribute("directDeploy", Boolean.toString(plugin.isDirectDeploy()));
                }


                List<String> scopes = prod ? Collections.singletonList(JavaScopes.RUNTIME) : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE);

                for (String scope : scopes) {

                    Element scopeElem = doc.createElement(scope);

                    pluginElem.appendChild(scopeElem);

                    for (org.kantega.reststop.classloaderutils.Artifact artifact : plugin.getClassPath(scope)) {
                        Element artifactElement = doc.createElement("artifact");
                        artifactElement.setAttribute("groupId", artifact.getGroupId());
                        artifactElement.setAttribute("artifactId", artifact.getArtifactId());
                        artifactElement.setAttribute("version", artifact.getVersion());

                        if (!prod) {
                            artifactElement.setAttribute("file", artifact.getFile().getAbsolutePath());
                        }

                        scopeElem.appendChild(artifactElement);
                    }


                }
            }
            return doc;
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    private List<PluginInfo> getPluginInfos(List<Plugin> plugins) {
        List<PluginInfo> pluginInfos = new ArrayList<>();

        for (Plugin plugin : plugins) {
            PluginInfo info = plugin.asPluginInfo();
            pluginInfos.add(info);

            Artifact pluginArtifact = resolveArtifact(plugin.getCoords());

            info.setFile(pluginArtifact.getFile());

            for (String scope : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE)) {

                collectArtifacts(plugin, info, pluginArtifact, scope);
            }
        }

        validateTransitivePluginsMissing(pluginInfos);
        validateNoPluginArtifactsOnRuntimeClasspath(pluginInfos);
        return pluginInfos;
    }

    public void refreshPluginInfos(List<PluginInfo> pluginInfos) {

        for (PluginInfo pluginInfo : pluginInfos) {
            Plugin plugin = new Plugin(pluginInfo.getGroupId(), pluginInfo.getArtifactId(), pluginInfo.getVersion());

            for (String scope : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE)) {

                collectArtifacts(plugin, pluginInfo, resolveArtifact(plugin.getCoords()), scope);
            }
        }
        validateTransitivePluginsMissing(pluginInfos);
        validateNoPluginArtifactsOnRuntimeClasspath(pluginInfos);
    }

    private void collectArtifacts(Plugin plugin, PluginInfo info, Artifact pluginArtifact, String scope) {
        try {

            ArtifactDescriptorResult descriptorResult = repoSystem.readArtifactDescriptor(repoSession, new ArtifactDescriptorRequest(pluginArtifact, remoteRepos, null));

            CollectRequest collectRequest = new CollectRequest();

            for (RemoteRepository repo : remoteRepos) {
                collectRequest.addRepository(repo);
            }
            for (org.eclipse.aether.graph.Dependency dependency : descriptorResult.getDependencies()) {
                collectRequest.addDependency(dependency);
            }

            collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());

            if (plugin.getDependencies() != null) {
                for (Dependency dependency : plugin.getDependencies()) {
                    org.eclipse.aether.graph.Dependency dep = new org.eclipse.aether.graph.Dependency(new DefaultArtifact(dependency.getGroupId(),
                            dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion()), dependency.getScope(), dependency.isOptional());

                    collectRequest.addDependency(dep);

                }
            }


            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(scope));

            DependencyResult dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);

            if (!dependencyResult.getCollectExceptions().isEmpty()) {
                throw new RuntimeException("Failed resolving plugin dependencies", dependencyResult.getCollectExceptions().get(0));
            }

            for (ArtifactResult result : dependencyResult.getArtifactResults()) {
                Artifact artifact = result.getArtifact();
                org.kantega.reststop.classloaderutils.Artifact pa = new org.kantega.reststop.classloaderutils.Artifact();
                info.getClassPath(scope).add(pa);

                pa.setGroupId(artifact.getGroupId());
                pa.setArtifactId(artifact.getArtifactId());
                pa.setVersion(artifact.getVersion());

                pa.setFile(artifact.getFile());
            }

        } catch (DependencyResolutionException | ArtifactDescriptorException e) {
            throw new RuntimeException("Failed resolving plugin dependencies", e);
        }
    }

    private void validateNoPluginArtifactsOnRuntimeClasspath(List<PluginInfo> pluginInfos) {
        for (PluginInfo pluginInfo : pluginInfos) {

            Map<String, org.kantega.reststop.classloaderutils.Artifact> shouldBeProvided = new TreeMap<>();

            for (org.kantega.reststop.classloaderutils.Artifact dep : pluginInfo.getClassPath("runtime")) {


                try {
                    JarFile jar = new JarFile(dep.getFile());
                    ZipEntry entry = jar.getEntry("META-INF/services/ReststopPlugin/");
                    boolean isPlugin = entry != null;
                    jar.close();

                    if (isPlugin) {
                        shouldBeProvided.put(dep.getGroupIdAndArtifactId(), dep);
                        LOG.error("Plugin " + pluginInfo.getPluginId() + " depends on plugin artifact " + dep.getPluginId() + " which must be in <scope>provided</scope> and declared as a <plugin>!");
                        String decl = String.format("\t<plugin>\n\t\t<groupId>%s</groupId>\n\t\t<artifactId>%s</artifactId>\n\t\t<version>%s</version>\n\t</plugin>", dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
                        LOG.error("Please add the following to your <plugins> section:\n" + decl);
                    }


                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }

            }
            if (!shouldBeProvided.isEmpty()) {
                throw new RuntimeException(String.format("Plugin %s has a Maven <dependency> on one or more plugin artifacts which should be made <scope>provided</scope> and directly declared as a <plugin>: %s", pluginInfo.getPluginId(), shouldBeProvided.values()));
            }
        }
    }

    private void validateTransitivePluginsMissing(List<PluginInfo> pluginInfos) {

        for (PluginInfo pluginInfo : pluginInfos) {

            Map<String, org.kantega.reststop.classloaderutils.Artifact> missing = new TreeMap<>();

            for (org.kantega.reststop.classloaderutils.Artifact dep : pluginInfo.getClassPath("compile")) {


                try {
                    JarFile jar = new JarFile(dep.getFile());
                    ZipEntry entry = jar.getEntry("META-INF/services/ReststopPlugin/");
                    boolean isPlugin = entry != null;
                    jar.close();

                    if (isPlugin && !isDeclaredPlugin(dep, pluginInfos)) {
                        missing.put(dep.getGroupIdAndArtifactId(), dep);
                        LOG.error("Plugin " + pluginInfo.getPluginId() + " depends on plugin " + dep.getPluginId() + " which must be declared as a <plugin>!");
                        String decl = String.format("\t<plugin>\n\t\t<groupId>%s</groupId>\n\t\t<artifactId>%s</artifactId>\n\t\t<version>%s</version>\n\t</plugin>", dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
                        LOG.error("Please add the following to your <plugins> section:\n" + decl);
                    }


                } catch (IOException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }

            }
            if (!missing.isEmpty()) {
                throw new RuntimeException(String.format("Plugin %s has a Maven <dependency> on one or more plugin artifacts which should be directly declared as a <plugin>: %s", pluginInfo.getPluginId(), missing.values()));
            }
        }

    }

    private boolean isDeclaredPlugin(org.kantega.reststop.classloaderutils.Artifact dep, List<PluginInfo> pluginInfos) {

        for(PluginInfo declared : pluginInfos) {
            if(declared.getGroupIdAndArtifactId().equals(dep.getGroupIdAndArtifactId())) {
                return true;
            }
        }
        return false;
    }

    public Artifact resolveArtifact(String coords) {
        Artifact artifact;
        try {
            artifact = new DefaultArtifact(coords);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        request.setRepositories(remoteRepos);

        LOG.info("Resolving artifact " + artifact + " from " + remoteRepos);

        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        LOG.info("Resolved artifact " + artifact + " to " + result.getArtifact().getFile() + " from "
                + result.getRepository());

        return result.getArtifact();
    }

    public RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    public DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository local = new LocalRepository(localRepo);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, local));
        return session;
    }

    public List<RemoteRepository> newRepositories() {
        return new ArrayList<>(Arrays.asList(newCentralRepository()));
    }

    private RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build();
    }

}
