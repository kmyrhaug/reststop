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

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kantega.reststop.api.DefaultReststopPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Creates a new Reststop plugins in an already created Reststop Maven project.
 */
@Mojo(name = "createplugin", requiresProject = true, aggregator = true)
public class CreatePluginMojo extends AbstractCreateMojo {

    @Parameter(defaultValue = "${project.groupId}", property = "groupId")
    private String groupId;

    @Parameter(defaultValue = "${project.version}", property = "version")
    private String version;

    @Parameter(defaultValue = "${project.artifactId}", property = "artifactId")
    private String artifactId;

    @Parameter(property = "name")
    private String pluginName;

    @Parameter(property = "package")
    private String pack;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        String rootArtifactId;
        File basedir = mavenProject.getBasedir();

        try {

            File pluginsDir;
            File webappDir;
            if (basedir.getName().equalsIgnoreCase("plugins")) {
                rootArtifactId = mavenProject.getParent().getArtifactId();
                pluginsDir = basedir;
                webappDir = new File(basedir.getParent(), "webapp");
            } else if (basedir.getName().equalsIgnoreCase("webapp")) {
                rootArtifactId = mavenProject.getParent().getArtifactId();
                pluginsDir = new File(basedir.getParent(), "plugins");
                if (!pluginsDir.exists()) {
                    pluginsDir.mkdirs();
                }
                webappDir = basedir;
            } else if (basedir.getName().equalsIgnoreCase(artifactId)) {
                rootArtifactId = artifactId;
                pluginsDir = new File(basedir, "plugins");
                if (!pluginsDir.exists()) {
                    pluginsDir.mkdirs();
                }
                webappDir = new File(basedir, "webapp");
            } else {
                throw new MojoFailureException("Could not find a proper Reststop directory structure, please use create goal.");
            }
            if (!webappDir.exists()) {
                throw new MojoFailureException("Could not find the webapp directory, resulting in an improper Reststop directory structure, please use create goal.");
            }

            Map<String, String> options = getOptions();
            pack = options.get("package").toLowerCase();
            pluginName = options.get("name").toLowerCase();

            File pluginDir = new File(pluginsDir, pluginName);

            if (pluginDir.exists()) {
                throw new MojoFailureException(String.format("Plugin %s in %s already exists.", pluginName, pluginsDir));
            }
            File pluginPomFile = new File(pluginDir, "pom.xml");

            Map<String, String> tokens = new HashMap<>();
            tokens.put("${groupId}", groupId);
            tokens.put("${name}", pluginName);
            tokens.put("${rootArtifactId}", rootArtifactId);
            createMavenModule(tokens, getClass().getResourceAsStream("dist/template-newplugin-pom.xml"), pluginPomFile);

            new File(pluginDir, "src/main/resources").mkdirs();
            new File(pluginDir, "src/test/resources").mkdirs();
            File sourceDir = new File(pluginDir, "src/main/java");
            sourceDir.mkdirs();

            createPluginClass(pluginName, sourceDir, DefaultReststopPlugin.class, pack);
            pomAddModule(new File(pluginsDir, "pom.xml"), pluginName);
            pomAddPluginToReststop(new File(webappDir, "pom.xml"), groupId, rootArtifactId + "-" + pluginName, "${project.version}");

            getLog().info(String.format("Successfully generated new plugin '%s' in %s.", pluginName, pluginDir));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


    }

    private void pomAddModule(File pom, String name) throws MojoExecutionException {
        Model model = getModelFromPom(pom);

        if (!model.getModules().contains(name)) {
            model.addModule(name);
        }

        writeModelToPom(pom, model);
    }

    private void pomAddPluginToReststop(File pom, String groupId, String artifactId, String version) throws MojoExecutionException {
        Model model = getModelFromPom(pom);

        boolean present = false;
        for (Plugin plugin : model.getBuild().getPlugins()) {
            if (plugin.getArtifactId().equalsIgnoreCase("reststop-maven-plugin")) {
                Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
                Xpp3Dom plugins = configuration.getChild("plugins");
                Xpp3Dom[] children = plugins.getChildren();
                if (children.length != 0) {
                    for (Xpp3Dom p : children) {
                        if (p.getChild("artifactId").getValue().equalsIgnoreCase(artifactId)) {
                            present = true;
                        }
                    }

                    if (!present) {
                        Xpp3Dom dom = new Xpp3Dom("plugin");
                        addDom(dom, "groupId", groupId);
                        addDom(dom, "artifactId", artifactId);
                        addDom(dom, "version", version);

                        plugins.addChild(dom);
                    }
                }
            }
        }

        writeModelToPom(pom, model);
    }

    private void addDom(Xpp3Dom dom, String name, String value) {
        Xpp3Dom toAdd = new Xpp3Dom(name);
        toAdd.setValue(value);
        dom.addChild(toAdd);
    }

    private void writeModelToPom(File pom, Model model) throws MojoExecutionException {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try {
            writer.write(new FileOutputStream(pom), model);
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Could not write to file: %s", pom), e);
        }
    }

    private Model getModelFromPom(File pom) throws MojoExecutionException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model;
        try {
            model = reader.read(new FileInputStream(pom));
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Could not read file: %s", pom), e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException(String.format("Error parsing XML in file: %s: ", pom), e);
        }
        return model;
    }

    private Map<String, String> getOptions() {
        Map<String, String> values = new LinkedHashMap<>();

        do {

            if (pluginName == null) {
                readValue(values, "name", "example");
            } else {
                values.put("name", pluginName);
            }

            if (pack == null) {
                String defaultPackage = groupId + "." + values.get("name");
                String pack;
                for (; ; ) {
                    pack = readLineWithDefault("package", defaultPackage).trim();
                    if (pack.isEmpty()) pack = defaultPackage;

                    Pattern p = Pattern.compile("^[a-zA-Z_\\$][\\w\\$]*(?:\\.[a-zA-Z_\\$][\\w\\$]*)*$");
                    if (p.matcher(pack).matches()) {
                        break;
                    }
                }

                values.put("package", pack);
            } else {
                values.put("package", pack);
            }

            System.out.println();
            System.out.println("Please confirm configuration:");
            for (String option : values.keySet()) {
                System.console().printf("  %s = '%s'\n", option, values.get(option));
            }
        } while (!System.console().readLine(" Y: ").equalsIgnoreCase("y"));

        return values;
    }

}