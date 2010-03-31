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

package org.gradle.api.internal.changedetection;

import org.gradle.api.DefaultTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

import static org.gradle.util.WrapUtil.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultTaskArtifactStateRepositoryTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final CacheRepository cacheRepository = context.mock(CacheRepository.class);
    private final ProjectInternal project = HelperUtil.createRootProject();
    private final Gradle gradle = project.getGradle();
    private final TestFile outputFile = tmpDir.file("output-file");
    private final TestFile outputDir = tmpDir.file("output-dir");
    private final TestFile outputDirFile = outputDir.file("some-file");
    private final TestFile outputDirFile2 = outputDir.file("some-file-2");
    private final TestFile emptyOutputDir = tmpDir.file("empty-output-dir");
    private final TestFile missingOutputFile = tmpDir.file("missing-output-file");
    private final TestFile inputFile = tmpDir.createFile("input-file");
    private final TestFile inputDir = tmpDir.createDir("input-dir");
    private final TestFile missingInputFile = tmpDir.file("missing-input-file");
    private final Set<TestFile> inputFiles = toSet(inputFile, inputDir, missingInputFile);
    private final Set<TestFile> outputFiles = toSet(outputFile, outputDir, emptyOutputDir, missingOutputFile);
    private final Set<TestFile> createFiles = toSet(outputFile, outputDirFile, outputDirFile2);
    private final FileSnapshotter fileSnapshotter = new DefaultFileSnapshotter(new DefaultHasher());
    private PersistentCache persistentCache;
    private final DefaultTaskArtifactStateRepository repository = new DefaultTaskArtifactStateRepository(cacheRepository,
            fileSnapshotter);

    @Test
    public void artifactsAreNotUpToDateWhenCacheIsEmpty() {
        expectEmptyCacheLocated();

        TaskArtifactState state = repository.getStateFor(task());
        assertNotNull(state);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileNoLongerExists() {
        execute();

        outputFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputDirFileNoLongerExists() {
        execute();

        outputDirFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileHasChangedType() {
        execute();

        outputFile.delete();
        outputFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputDirFileHasChangedType() {
        execute();

        outputDirFile.delete();
        outputDirFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFileHasChangedHash() {
        execute();

        outputFile.write("new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputDirFileHasChangedHash() {
        execute();

        outputDirFile.write("new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesAddedToSet() {
        execute();

        TaskInternal task = builder().withOutputFiles(outputFile, outputDir, tmpDir.createFile("output-file-2"), emptyOutputDir, missingOutputFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyOutputFilesRemovedFromSet() {
        execute();

        TaskInternal task = builder().withOutputFiles(outputFile, emptyOutputDir, missingOutputFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentPathProducedAnyOutputFile() {
        execute();

        TaskInternal task = builder().withPath("other").withOutputFiles(outputFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        state.isUpToDate();
        outputFile.write("new content");
        state.update();

        state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentPathProducedAnyOutputDirFile() {
        execute();

        TaskInternal task = builder().withPath("other").withOutputFiles(outputDirFile).task();

        TaskArtifactState state = repository.getStateFor(task);
        state.isUpToDate();
        outputDirFile.write("new content");
        state.update();

        state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskWithDifferentTypeGeneratedAnyOutputFiles() {
        TaskInternal task1 = builder().withOutputFiles(outputFile).task();
        TaskInternal task2 = builder().withType(TaskSubType.class).withOutputFiles(outputFile).task();

        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesAddedToSet() {
        execute();

        TaskInternal task = builder().withInputFiles(inputFile, inputDir, tmpDir.createFile("other-input"), missingInputFile).task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFilesRemovedFromSet() {
        execute();

        TaskInternal task = builder().withInputFiles(inputFile).task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileHasChangedHash() {
        execute();

        inputFile.write("some new content");

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileHasChangedType() {
        execute();

        inputFile.delete();
        inputFile.createDir();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputFileNoLongerExists() {
        execute();

        inputFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputPropertyValueChanged() {
        execute();

        TaskArtifactState state = repository.getStateFor(builder().withProperty("prop", "new value").task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void inputPropertyValueCanBeNull() {
        TaskInternal task = builder().withProperty("prop", null).task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputPropertyAdded() {
        execute();

        TaskArtifactState state = repository.getStateFor(builder().withProperty("prop2", "value").task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenAnyInputPropertyRemoved() {
        execute(builder().withProperty("prop2", "value").task());

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenStateHasNotBeenUpdated() {
        expectEmptyCacheLocated();
        repository.getStateFor(task());

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenNothingHasChangedSinceOutputFilesWereGenerated() {
        execute();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenOutputFileWhichDidNotExistNowExists() {
        execute();

        missingOutputFile.touch();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenOutputDirWhichWasEmptyIsNoLongerEmpty() {
        execute();

        emptyOutputDir.file("some-file").touch();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void multipleTasksCanProduceFilesIntoTheSameOutputDirectory() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).createsFiles(outputDir.file("output2")).task();
        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void multipleTasksCanProduceFilesTheSameFileWithTheSameContents() {
        TaskInternal task1 = builder().withOutputFiles(outputFile).task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputFile).task();
        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void multipleTasksCanProduceTheSameEmptyDir() {
        TaskInternal task1 = task();
        TaskInternal task2 = builder().withPath("other").withOutputFiles(outputDir).task();
        execute(task1, task2);

        TaskArtifactState state = repository.getStateFor(task1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(task2);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void doesNotConsiderExistingFilesInOutputDirectoryAsProducedByTask() {
        TestFile otherFile = outputDir.file("other").createFile();

        execute();

        otherFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
    }

    @Test
    public void considersUpdatedExistingFileInOutputDirectoryAsProducedByTask() {
        expectEmptyCacheLocated();
        
        TestFile otherFile = outputDir.file("other").createFile();

        TaskInternal task = task();
        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());

        task.execute();
        otherFile.write("new content");

        state.update();

        otherFile.delete();

        state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
    }

    @Test
    public void fileIsNoLongerConsideredProducedByTaskOnceItIsDeleted() {
        execute();

        outputDirFile.delete();

        TaskArtifactState state = repository.getStateFor(task());
        assertFalse(state.isUpToDate());
        state.update();

        outputDirFile.write("ignore me");

        state = repository.getStateFor(task());
        assertTrue(state.isUpToDate());
        state.update();
    }

    @Test
    public void artifactsAreUpToDateWhenTaskDoesNotAcceptAnyInputs() {
        TaskInternal task = builder().doesNotAcceptInput().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());

        outputDirFile.delete();

        state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreNotUpToDateWhenTaskDoesNotProduceAnyOutputs() {
        TaskInternal task = builder().doesNotProduceOutput().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertFalse(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenTaskHasNoInputFiles() {
        TaskInternal task = builder().withInputFiles().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void artifactsAreUpToDateWhenTaskHasNoOutputs() {
        TaskInternal task = builder().withOutputFiles().task();
        execute(task);

        TaskArtifactState state = repository.getStateFor(task);
        assertTrue(state.isUpToDate());
    }

    @Test
    public void taskCanProduceIntoDifferentSetsOfOutputFiles() {
        TestFile outputDir2 = tmpDir.createDir("output-dir-2");
        TestFile outputDirFile2 = outputDir2.file("output-file-2");
        TaskInternal instance1 = builder().withOutputFiles(outputDir).createsFiles(outputDirFile).task();
        TaskInternal instance2 = builder().withOutputFiles(outputDir2).createsFiles(outputDirFile2).task();

        execute(instance1, instance2);

        TaskArtifactState state = repository.getStateFor(instance1);
        assertTrue(state.isUpToDate());

        state = repository.getStateFor(instance2);
        assertTrue(state.isUpToDate());
    }

    private void execute() {
        execute(task());
    }

    private void execute(TaskInternal... tasks) {
        expectEmptyCacheLocated();
        for (TaskInternal task : tasks) {
            TaskArtifactState state = repository.getStateFor(task);
            state.isUpToDate();
            task.execute();
            state.update();
        }
    }
    
    private void expectEmptyCacheLocated() {
        context.checking(new Expectations(){{
            CacheBuilder builder = context.mock(CacheBuilder.class);
            persistentCache = context.mock(PersistentCache.class);

            one(cacheRepository).cache("taskArtifacts");
            will(returnValue(builder));

            one(builder).forObject(gradle);
            will(returnValue(builder));

            one(builder).open();
            will(returnValue(persistentCache));
            
            one(persistentCache).openIndexedCache();
            will(returnValue(new TestIndexedCache()));
        }});
    }

    private TaskInternal task() {
        return builder().task();
    }

    private TaskBuilder builder() {
        return new TaskBuilder();
    }

    private class TaskBuilder {
        private String path = "task";
        private Collection<? extends File> inputs = inputFiles;
        private Collection<? extends File> outputs = outputFiles;
        private Collection<? extends TestFile> create = createFiles;
        private Class<? extends TaskInternal> type = TaskInternal.class;
        private Map<String, Object> inputProperties = new HashMap<String, Object>(toMap("prop", "value"));

        TaskBuilder withInputFiles(File... inputFiles) {
            inputs = Arrays.asList(inputFiles);
            return this;
        }

        TaskBuilder withOutputFiles(File... outputFiles) {
            outputs = Arrays.asList(outputFiles);
            return this;
        }

        TaskBuilder createsFiles(TestFile... outputFiles) {
            create = Arrays.asList(outputFiles);
            return this;
        }

        TaskBuilder withPath(String path) {
            this.path = path;
            return this;
        }

        TaskBuilder withType(Class<? extends TaskInternal> type) {
            this.type = type;
            return this;
        }

        TaskBuilder doesNotAcceptInput() {
            inputs = null;
            inputProperties = null;
            return this;
        }

        public TaskBuilder doesNotProduceOutput() {
            outputs = null;
            return this;
        }

        public TaskBuilder withProperty(String name, Object value) {
            inputProperties.put(name, value);
            return this;
        }

        TaskInternal task() {
            final TaskInternal task = HelperUtil.createTask(type, project, path);
            if (inputs != null) {
                task.getInputs().files(inputs);
            }
            if (inputProperties != null) {
                task.getInputs().properties(inputProperties);
            }
            if (outputs != null) {
                task.getOutputs().files(outputs);
            }
            task.doLast(new org.gradle.api.Action<Object>() {
                public void execute(Object o) {
                    for (TestFile file : create) {
                        file.createFile();
                    }
                }
            });

            return task;
        }
    }

    public static class TaskSubType extends DefaultTask {
    }

    public static class TestIndexedCache implements PersistentIndexedCache<Object, Object> {
        Map<Object, Object> entries = new HashMap<Object, Object>();

        public Object get(Object key) {
            return entries.get(key);
        }

        public void put(Object key, Object value) {
            entries.put(key, value);
        }

        public void remove(Object key) {
            entries.remove(key);
        }
    }
}
