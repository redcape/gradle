/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.idea.internal

import com.google.common.collect.Sets
import org.gradle.api.internal.file.FileResolver
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.plugins.PlayPluginConfigurations
import org.gradle.plugins.ide.idea.GenerateIdeaModule
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.idea.model.IdeaModule

public class PlayIdeRules extends RuleSource {
    @Mutate
    public void configureIdeaModule(@Path("tasks.ideaModule") GenerateIdeaModule ideaModule,
                                    @Path("binaries.playBinary") final PlayApplicationBinarySpec playApplicationBinarySpec,
                                    @Path("buildDir") File buildDir,
                                    PlayPluginConfigurations configurations,
                                    final FileResolver fileResolver) {
        IdeaModule module = ideaModule.getModule();

        module.conventionMapping.excludeDirs = {
            // TODO: Cannot exclude buildDir since that will also exclude our generated sources
            [fileResolver.resolve('.gradle')] as LinkedHashSet
        }

        module.conventionMapping.sourceDirs = {
            final Set<File> results = Sets.newHashSet()
            playApplicationBinarySpec.getInputs().each {
                results.addAll(it.getSource().getSrcDirs())
            }
            results
        }

        module.conventionMapping.generatedSourceDirs = {
            final Set<File> results = Sets.newHashSet()
            playApplicationBinarySpec.getInputs().findAll {
                it instanceof LanguageSourceSetInternal && it.generated
            }.each {
                results.addAll(it.getSource().getSrcDirs())
            }
            results
        }


        module.conventionMapping.testSourceDirs = {
            // TODO: This should be modeled as a source set
            Collections.singleton(fileResolver.resolve("test"))
        }

        module.scopes = [
            PROVIDED: [plus: [], minus: []],
            COMPILE: [plus: [configurations.play.configuration], minus: []],
            RUNTIME: [plus: [configurations.playRun.configuration], minus: []],
            TEST: [plus: [configurations.playTest.configuration], minus: []]
        ]

        module.conventionMapping.singleEntryLibraries = {
            [
                RUNTIME: [playApplicationBinarySpec.classes.classesDir] + playApplicationBinarySpec.classes.resourceDirs,
                // TODO: This should be modeled as a source set
                TEST: [ new File(buildDir, "playBinary/testClasses") ]
            ]
        }

        // Hacky, include the Scala version that the Play application will build with
        module.ext.scalaPlatform = playApplicationBinarySpec.targetPlatform.scalaPlatform

        module.conventionMapping.targetBytecodeVersion = {
             playApplicationBinarySpec.targetPlatform.javaPlatform.targetCompatibility
        }
        module.conventionMapping.languageLevel = {
            new IdeaLanguageLevel(playApplicationBinarySpec.targetPlatform.javaPlatform.targetCompatibility)
        }
        ideaModule.dependsOn playApplicationBinarySpec.inputs
    }
}
