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

package org.kantega.reststop.webjars;

import org.kantega.reststop.api.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

/**
 * Adds WebJarsFilter with webjars resources, and exports a map of all <artifactId>:<version>, for resource in filter,
 * making it easy to reference webjars in html files.
 */
public class WebjarsReststopPlugin extends DefaultReststopPlugin {

    @Export
    private final WebjarsVersions v;

    private Map<String, String> versions;

    public WebjarsReststopPlugin(final Reststop reststop, final ReststopPluginManager reststopPluginManager) {

        addServletFilter(reststop.createFilter(new WebJarsFilter(reststopPluginManager), "/webjars/*", FilterPhase.USER));

        v = new WebjarsVersions() {
            @Override
            public Map<String, String> getVersions() {
                return getVersionsForWebJars(reststopPluginManager);
            }
        };
    }

    private synchronized Map<String, String> getVersionsForWebJars(ReststopPluginManager reststopPluginManager) {

        if (versions == null) {
            versions = new HashMap<>();

            Set<String> webjars = new HashSet<>();

            try {
                for (ClassLoader loader : reststopPluginManager.getPluginClassLoaders()) {
                    Enumeration<URL> resources = loader.getResources("META-INF/resources/webjars/");
                    while (resources.hasMoreElements()) {
                        URL webJar = resources.nextElement();
                        String file = URLDecoder.decode(webJar.getFile(), "UTF-8");
                        file = file.substring(0, file.indexOf("!"));
                        webjars.add(file);

                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (String webjar : webjars) {
                String file = webjar.substring(0, webjar.lastIndexOf("/"));

                String version = file.substring(file.lastIndexOf("/") + 1);
                String artifact = file.substring(0, file.lastIndexOf("/"));
                String artifactId = artifact.substring(artifact.lastIndexOf("/") + 1);

                if (version.contains("-")) {
                    version = version.substring(0, version.lastIndexOf("-"));
                }

                String key = "versions." + artifactId;
                if (versions.get(key) != null) {
                    Version a = new Version(version);
                    Version b = new Version(versions.get(key));
                    if (a.compareTo(b) == 1) {
                        versions.put(key, version);
                    }
                } else {
                    versions.put(key, version);
                }
            }

            return versions;
        } else {
            return versions;
        }
    }
}

class Version implements Comparable<Version> {

    private String version;

    public final String get() {
        return this.version;
    }

    public Version(String version) {
        if (version == null)
            throw new IllegalArgumentException("Version can not be null");
        if (!version.matches("^[0-9]+(\\.[0-9]+)*$"))
            throw new IllegalArgumentException("Invalid version format");
        this.version = version;
    }

    @Override
    public int compareTo(Version that) {
        if (that == null)
            return 1;
        String[] thisParts = this.get().split("\\.");
        String[] thatParts = that.get().split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ?
                    Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ?
                    Integer.parseInt(thatParts[i]) : 0;
            if (thisPart < thatPart)
                return -1;
            if (thisPart > thatPart)
                return 1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (that == null)
            return false;
        if (this.getClass() != that.getClass())
            return false;
        return this.compareTo((Version) that) == 0;
    }

}