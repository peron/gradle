/*
 * Copyright 2009 the original author or authors.
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
 
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.configurations.Configurations
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.util.HelperUtil
import org.junit.Test
import static org.gradle.util.Matchers.dependsOn
import static org.gradle.util.WrapUtil.toLinkedSet
import static org.gradle.util.WrapUtil.toSet
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */

class GroovyBasePluginTest {
    private final Project project = HelperUtil.createRootProject()
    private final GroovyBasePlugin groovyBasePlugin = new GroovyBasePlugin()

    @Test public void appliesTheJavaBasePluginToTheProject() {
        groovyBasePlugin.apply(project)

        assertTrue(project.getPlugins().hasPlugin(JavaBasePlugin));
    }

    @Test public void addsGroovyConfigurationToTheProject() {
        groovyBasePlugin.apply(project)
        
        def configuration = project.configurations.getByName(GroovyBasePlugin.GROOVY_CONFIGURATION_NAME)
        assertThat(Configurations.getNames(configuration.extendsFrom, false), equalTo(toSet()))
        assertFalse(configuration.visible)
        assertFalse(configuration.transitive)
    }

    @Test public void appliesMappingsToNewSourceSet() {
        groovyBasePlugin.apply(project)

        def sourceSet = project.sourceSets.add('custom')
        assertThat(sourceSet.groovy.displayName, equalTo("custom Groovy source"))
        assertThat(sourceSet.groovy.srcDirs, equalTo(toLinkedSet(project.file("src/custom/groovy"))))
    }

    @Test public void addsCompileTaskToNewSourceSet() {
        groovyBasePlugin.apply(project)
        
        project.sourceSets.add('custom')

        def task = project.tasks['compileCustomGroovy']
        assertThat(task, instanceOf(GroovyCompile.class))
        assertThat(task.description, equalTo('Compiles the custom Groovy source.'))
        assertThat(task.defaultSource, equalTo(project.sourceSets.custom.groovy))
        assertThat(task, dependsOn('compileCustomJava'))
    }

    @Test public void dependenciesOfJavaPluginTasksIncludeGroovyCompileTasks() {
        groovyBasePlugin.apply(project)

        project.sourceSets.add('custom')
        def task = project.tasks['customClasses']
        assertThat(task, dependsOn(hasItem('compileCustomGroovy')))
    }
   
    @Test public void configuresAdditionalTasksDefinedByTheBuildScript() {
        groovyBasePlugin.apply(project)

        def task = project.createTask('otherGroovydoc', type: Groovydoc)
        assertThat(task.destinationDir, equalTo(new File(project.docsDir, 'groovydoc')))
        assertThat(task.docTitle, equalTo(project.apiDocTitle))
        assertThat(task.windowTitle, equalTo(project.apiDocTitle))
    }
}