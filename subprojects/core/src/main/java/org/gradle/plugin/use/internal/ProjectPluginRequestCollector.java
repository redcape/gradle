/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.use.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.PluginDependencySpec;
import org.gradle.plugin.use.ProjectPluginDependenciesSpec;
import org.gradle.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;

import static org.gradle.util.CollectionUtils.collect;

/**
 * The delegate of the plugins {} block.
 */
public class ProjectPluginRequestCollector implements ProjectPluginDependenciesSpec, PluginRequestCollector {

    private final ScriptSource scriptSource;
    private final Project project;
    private final List<DependencySpecImpl> specs = new LinkedList<DependencySpecImpl>();

    public ProjectPluginRequestCollector(ScriptSource scriptSource, Project project) {
        this.scriptSource = scriptSource;
        this.project = project;
    }

    @Override
    public PluginDependencySpec id(String id) {
        DependencySpecImpl spec = new DependencySpecImpl(id);
        specs.add(spec);
        return spec;
    }

    @Override
    public void allProjects(Action<? super ProjectPluginDependenciesSpec> configuration) {

    }

    @Override
    public void subProjects(Action<? super ProjectPluginDependenciesSpec> configuration) {

    }

    public List<TargetedPluginRequest> getRequests() {
        List<TargetedPluginRequest> pluginRequests = collect(specs, new Transformer<TargetedPluginRequest, DependencySpecImpl>() {
            public TargetedPluginRequest transform(DependencySpecImpl original) {
                return new TargetedPluginRequest(project, new DefaultPluginRequest(original.id, original.version, scriptSource.getDisplayName()));
            }
        });

//        ListMultimap<PluginId, PluginRequest> groupedById = CollectionUtils.groupBy(pluginRequests, new Transformer<PluginId, PluginRequest>() {
//            public PluginId transform(PluginRequest pluginRequest) {
//                return pluginRequest.getId();
//            }
//        });
//
//        // Check for duplicates
//        for (PluginId key : groupedById.keySet()) {
//            List<PluginRequest> pluginRequestsForId = groupedById.get(key);
//            if (pluginRequestsForId.size() > 1) {
//                PluginRequest first = pluginRequests.get(0);
//                PluginRequest second = pluginRequests.get(1);
//
//                throw new LocationAwareException(new InvalidPluginRequestException(second, "Plugin with id '" + key + "' was already requested in " + first.getScriptDisplayName()), second.getScriptDisplayName(), null);
//            }
//        }
//
//        return new DefaultPluginRequests(pluginRequests);
        return pluginRequests;
    }

    private static class DependencySpecImpl implements PluginDependencySpec {
        private final PluginId id;
        private String version;

        private DependencySpecImpl(String id) {
            this.id = PluginId.of(id);
        }

        public void version(String version) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(version), "plugin version cannot be null or empty");
            this.version = version;
        }
    }

}
