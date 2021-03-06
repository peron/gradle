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


package org.gradle.integtests

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.gradle.util.TestFile

class BuildScriptExecutionIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void executesBuildScriptWithCorrectEnvironment() {
        TestFile buildScript = testFile('build.gradle')
        buildScript << """
            println 'quiet message'
            captureStandardOutput(LogLevel.ERROR)
            println 'error message'
            assertNotNull(project)
            assertEquals("${buildScript.absolutePath.replace("\\", "\\\\")}", buildscript.sourceFile as String)
            assertEquals("${buildScript.toURI()}", buildscript.sourceURI as String)
            assertSame(buildscript.classLoader, getClass().classLoader.parent)
            assertSame(buildscript.classLoader, Thread.currentThread().contextClassLoader)
            assertSame(gradle.scriptClassLoader, buildscript.classLoader.parent)

            task doStuff
"""

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        assertThat(result.output, containsString('quiet message'))
        assertThat(result.output, not(containsString('error message')))
        assertThat(result.error, containsString('error message'))
        assertThat(result.error, not(containsString('quiet message')))
    }
}