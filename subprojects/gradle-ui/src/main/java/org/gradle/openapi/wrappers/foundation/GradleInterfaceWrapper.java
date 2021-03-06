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
package org.gradle.openapi.wrappers.foundation;

import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.openapi.external.foundation.GradleInterfaceVersion1;
import org.gradle.openapi.external.foundation.ProjectVersion1;
import org.gradle.openapi.external.ui.CommandLineArgumentAlteringListenerVersion1;
import org.gradle.openapi.wrappers.ui.CommandLineArgumentAlteringListenerWrapper;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of GradleInterfaceVersion1 meant to help shield external users from internal changes.
 * @author mhunsicker
 */
public class GradleInterfaceWrapper implements GradleInterfaceVersion1 {

    private GradlePluginLord gradlePluginLord;
    private Map<CommandLineArgumentAlteringListenerVersion1, CommandLineArgumentAlteringListenerWrapper> commandLineListenerMap = new HashMap<CommandLineArgumentAlteringListenerVersion1, CommandLineArgumentAlteringListenerWrapper>();

    public GradleInterfaceWrapper(GradlePluginLord gradlePluginLord) {
        this.gradlePluginLord = gradlePluginLord;
    }

    /**
     * @return the root projects wrapped in a ProjectWrapper
     */
    public List<ProjectVersion1> getRootProjects() {

        return ProjectWrapper.convertProjects( gradlePluginLord.getProjects() );
    }

    public void refreshTaskTree() {
        gradlePluginLord.addRefreshRequestToQueue();
    }

    public boolean isBusy() {
        return gradlePluginLord.isBusy();
    }

    public void executeCommand(String commandLineArguments, String displayName) {
        gradlePluginLord.addExecutionRequestToQueue( commandLineArguments, displayName );
    }

    public File getCurrentDirectory() {
        return gradlePluginLord.getCurrentDirectory();
    }

    public void setCurrentDirectory(File currentDirectory) {
        gradlePluginLord.setCurrentDirectory( currentDirectory );
    }

    public File getGradleHomeDirectory() {
        return gradlePluginLord.getGradleHomeDirectory();
    }

    public File getCustomGradleExecutable() {
        return getCustomGradleExecutable();
    }

    /**
     * This allows you to add a listener that can add additional command line
     * arguments whenever gradle is executed. This is useful if you've customized
     * your gradle build and need to specify, for example, an init script.
     *
     * @param listener the listener that modifies the command line arguments.
     */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        CommandLineArgumentAlteringListenerWrapper wrapper = new CommandLineArgumentAlteringListenerWrapper(listener);

        //we have to store our wrapper so you can call remove the listener using your passed-in object
        commandLineListenerMap.put(listener, wrapper);

        gradlePluginLord.addCommandLineArgumentAlteringListener(wrapper);
    }

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        CommandLineArgumentAlteringListenerWrapper wrapper = commandLineListenerMap.remove(listener);
        if (wrapper != null) {
           gradlePluginLord.removeCommandLineArgumentAlteringListener(wrapper);
        }
    }
}
