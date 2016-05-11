/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.Project;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.groovy.scripts.BasicScript;
import org.gradle.groovy.scripts.ScriptCompiler;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptRunner;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.BuildScriptData;
import org.gradle.groovy.scripts.internal.BuildScriptDataSerializer;
import org.gradle.groovy.scripts.internal.BuildScriptTransformer;
import org.gradle.groovy.scripts.internal.CompileOperation;
import org.gradle.groovy.scripts.internal.FactoryBackedCompileOperation;
import org.gradle.groovy.scripts.internal.InitialPassStatementTransformer;
import org.gradle.groovy.scripts.internal.SubsetScriptTransformer;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.model.dsl.internal.transform.ClosureCreationInterceptingVerifier;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.plugin.repository.internal.PluginRepositoryFactory;
import org.gradle.plugin.repository.internal.PluginRepositoryRegistry;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginRequestCollector;
import org.gradle.plugin.use.internal.ProjectPluginRequestCollector;
import org.gradle.plugin.use.internal.TargetedPluginRequest;
import org.gradle.plugin.use.internal.UnsupportedPluginRequestCollector;

import java.util.List;

public class DefaultScriptPluginFactory implements ScriptPluginFactory {
    private final static StringInterner INTERNER = new StringInterner();

    private final ScriptCompilerFactory scriptCompilerFactory;
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final Instantiator instantiator;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final PluginRequestApplicator pluginRequestApplicator;
    private final FileLookup fileLookup;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final DocumentationRegistry documentationRegistry;
    private final ModelRuleSourceDetector modelRuleSourceDetector;
    private final BuildScriptDataSerializer buildScriptDataSerializer = new BuildScriptDataSerializer();
    private final PluginRepositoryRegistry pluginRepositoryRegistry;
    private final PluginRepositoryFactory pluginRepositoryFactory;

    public DefaultScriptPluginFactory(ScriptCompilerFactory scriptCompilerFactory,
                                      Factory<LoggingManagerInternal> loggingManagerFactory,
                                      Instantiator instantiator,
                                      ScriptHandlerFactory scriptHandlerFactory,
                                      PluginRequestApplicator pluginRequestApplicator,
                                      FileLookup fileLookup,
                                      DirectoryFileTreeFactory directoryFileTreeFactory,
                                      DocumentationRegistry documentationRegistry,
                                      ModelRuleSourceDetector modelRuleSourceDetector,
                                      PluginRepositoryRegistry pluginRepositoryRegistry,
                                      PluginRepositoryFactory pluginRepositoryFactory) {
        this.scriptCompilerFactory = scriptCompilerFactory;
        this.loggingManagerFactory = loggingManagerFactory;
        this.instantiator = instantiator;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.pluginRequestApplicator = pluginRequestApplicator;
        this.fileLookup = fileLookup;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.documentationRegistry = documentationRegistry;
        this.modelRuleSourceDetector = modelRuleSourceDetector;
        this.pluginRepositoryRegistry = pluginRepositoryRegistry;
        this.pluginRepositoryFactory = pluginRepositoryFactory;
    }

    public ScriptPlugin create(ScriptSource scriptSource, ScriptHandler scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript) {
        return new ScriptPluginImpl(scriptSource, (ScriptHandlerInternal) scriptHandler, targetScope, baseScope, topLevelScript);
    }

    private class ScriptPluginImpl implements ScriptPlugin {
        private final ScriptSource scriptSource;
        private final ClassLoaderScope targetScope;
        private final ClassLoaderScope baseScope;
        private final ScriptHandlerInternal scriptHandler;
        private final boolean topLevelScript;

        public ScriptPluginImpl(ScriptSource scriptSource, ScriptHandlerInternal scriptHandler, ClassLoaderScope targetScope, ClassLoaderScope baseScope, boolean topLevelScript) {
            this.scriptSource = scriptSource;
            this.targetScope = targetScope;
            this.baseScope = baseScope;
            this.scriptHandler = scriptHandler;
            this.topLevelScript = topLevelScript;
        }

        public ScriptSource getSource() {
            return scriptSource;
        }

        public void apply(final Object target) {
            PluginRequestCollector projectPluginRequestCollector = getPluginRequestCollector(target);
            final DefaultServiceRegistry services = new DefaultServiceRegistry() {
                Factory<PatternSet> createPatternSetFactory() {
                    return PatternSets.getNonCachingPatternSetFactory();
                }
            };
            services.add(ScriptPluginFactory.class, DefaultScriptPluginFactory.this);
            services.add(ScriptHandlerFactory.class, scriptHandlerFactory);
            services.add(ClassLoaderScope.class, targetScope);
            services.add(LoggingManagerInternal.class, loggingManagerFactory.create());
            services.add(Instantiator.class, instantiator);
            services.add(ScriptHandler.class, scriptHandler);
            services.add(FileLookup.class, fileLookup);
            services.add(DirectoryFileTreeFactory.class, directoryFileTreeFactory);
            services.add(ModelRuleSourceDetector.class, modelRuleSourceDetector);
            services.add(PluginRepositoryRegistry.class, pluginRepositoryRegistry);
            services.add(PluginRepositoryFactory.class, pluginRepositoryFactory);
            services.add(PluginDependenciesSpec.class, projectPluginRequestCollector);

            final ScriptTarget initialPassScriptTarget = initialPassTarget(target);

            ScriptCompiler compiler = scriptCompilerFactory.createCompiler(scriptSource);

            // Pass 1, extract plugin requests and plugin repositories and execute buildscript {}, ignoring (i.e. not even compiling) anything else

            Class<? extends BasicScript> scriptType = initialPassScriptTarget.getScriptClass();
            InitialPassStatementTransformer initialPassStatementTransformer = new InitialPassStatementTransformer(scriptSource, initialPassScriptTarget, documentationRegistry);
            final SubsetScriptTransformer initialTransformer = new SubsetScriptTransformer(initialPassStatementTransformer);
            final String id = INTERNER.intern("cp_" + initialPassScriptTarget.getId());
            CompileOperation<Void> initialOperation = new VoidCompileOperation(id, initialTransformer);

            ScriptRunner<? extends BasicScript, Void> initialRunner = compiler.compile(scriptType, initialOperation, baseScope.getExportClassLoader(), Actions.doNothing());
            initialRunner.run(target, services);

            List<TargetedPluginRequest> pluginRequests = projectPluginRequestCollector.getRequests();
            pluginRequestApplicator.applyPlugins(pluginRequests, scriptHandler, targetScope);

            // Pass 2, compile everything except buildscript {}, pluginRepositories{}, and plugin requests, then run
            final ScriptTarget scriptTarget = secondPassTarget(target);
            scriptType = scriptTarget.getScriptClass();

            BuildScriptTransformer buildScriptTransformer = new BuildScriptTransformer(scriptSource, scriptTarget);
            String operationId = scriptTarget.getId();
            CompileOperation<BuildScriptData> operation = new FactoryBackedCompileOperation<BuildScriptData>(operationId, buildScriptTransformer, buildScriptTransformer, buildScriptDataSerializer);

            final ScriptRunner<? extends BasicScript, BuildScriptData> runner = compiler.compile(scriptType, operation, targetScope.getLocalClassLoader(), ClosureCreationInterceptingVerifier.INSTANCE);
            if (scriptTarget.getSupportsMethodInheritance() && runner.getHasMethods()) {
                scriptTarget.attachScript(runner.getScript());
            }
            if (!runner.getRunDoesSomething()) {
                return;
            }

            Runnable buildScriptRunner = new Runnable() {
                public void run() {
                    runner.run(target, services);
                }
            };

            boolean hasImperativeStatements = runner.getData().getHasImperativeStatements();
            scriptTarget.addConfiguration(buildScriptRunner, !hasImperativeStatements);
        }

        private PluginRequestCollector getPluginRequestCollector(Object target) {
            if (target instanceof Project) {
                return new ProjectPluginRequestCollector(scriptSource, (Project) target);
            } else {
                return new UnsupportedPluginRequestCollector();
            }
        }

        private ScriptTarget initialPassTarget(Object target) {
            return wrap(target, true /* isInitialPass */);
        }

        private ScriptTarget secondPassTarget(Object target) {
            return wrap(target, false /* isInitialPass */);
        }

        private ScriptTarget wrap(Object target, boolean isInitialPass) {
            if (target instanceof ProjectInternal && topLevelScript) {
                // Only use this for top level project scripts
                if (isInitialPass) {
                    return new InitialPassProjectScriptTarget((ProjectInternal) target);
                } else {
                    return new ProjectScriptTarget((ProjectInternal) target);
                }

            }
            if (target instanceof GradleInternal && topLevelScript) {
                // Only use this for top level init scripts
                return new InitScriptTarget((GradleInternal) target);
            }
            if (target instanceof SettingsInternal && topLevelScript) {
                // Only use this for top level settings scripts
                if (isInitialPass) {
                    return new InitialPassSettingScriptTarget((SettingsInternal) target);
                } else {
                    return new SettingScriptTarget((SettingsInternal) target);
                }
            } else {
                return new DefaultScriptTarget(target);
            }
        }

    }

}
