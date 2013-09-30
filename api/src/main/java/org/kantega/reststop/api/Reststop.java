/*
 * Copyright 2013 Kantega AS
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

package org.kantega.reststop.api;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import java.util.Properties;

/**
 *
 */
public interface Reststop {
    Filter createFilter(Filter filter, String mapping, FilterPhase phase);

    ClassLoader getPluginParentClassLoader();
    PluginClassLoaderChange changePluginClassLoaders();

    FilterChain newFilterChain(FilterChain filterChain);

    ServletConfig createServletConfig(String name, Properties properties);
    FilterConfig createFilterConfig(String name, Properties properties);

    interface PluginClassLoaderChange {
        PluginClassLoaderChange add(ClassLoader classLoader);
        PluginClassLoaderChange remove(ClassLoader classLoader);
        void commit();

    }
}
