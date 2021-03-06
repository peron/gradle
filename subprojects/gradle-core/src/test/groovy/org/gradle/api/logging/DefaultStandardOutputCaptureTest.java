/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.logging;

import org.gradle.api.InvalidUserDataException;
import org.hamcrest.Matchers;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class DefaultStandardOutputCaptureTest {
    private DefaultStandardOutputCapture standardOutputCapture;

    @After
    public void tearDown() {
        StandardOutputLogging.off();
    }

    @Test
    public void initDefault() {
        assertFalse(new DefaultStandardOutputCapture().isEnabled());
    }

    @Test
    public void initWithArgs() {
        standardOutputCapture = new DefaultStandardOutputCapture(true, LogLevel.ERROR);
        assertTrue(standardOutputCapture.isEnabled());
        assertEquals(LogLevel.ERROR, standardOutputCapture.getLevel());
    }

    @Test(expected = InvalidUserDataException.class)
    public void initWithArgsAndNullLevel() {
        new DefaultStandardOutputCapture(true, null);
    }
    
    @Test
    public void startStopWithDisabled() {
        StandardOutputLogging.on(LogLevel.ERROR);
        standardOutputCapture = new DefaultStandardOutputCapture();
        StandardOutputState state = StandardOutputLogging.getStateSnapshot();
        standardOutputCapture.start();
        assertSame(StandardOutputLogging.DEFAULT_OUT, System.out);
        assertSame(StandardOutputLogging.DEFAULT_ERR, System.err);
        standardOutputCapture.stop();
        assertEquals(state, StandardOutputLogging.getStateSnapshot());
    }

    @Test
    public void startStopWithEnabled() {
        StandardOutputLogging.onOut(LogLevel.ERROR);
        StandardOutputLogging.onErr(LogLevel.DEBUG);
        standardOutputCapture = new DefaultStandardOutputCapture(true, LogLevel.DEBUG);
        StandardOutputState oldState = StandardOutputLogging.getStateSnapshot();
        standardOutputCapture.start();
        assertSame(StandardOutputLogging.OUT_LOGGING_STREAM.get(), System.out);
        assertSame(StandardOutputLogging.ERR_LOGGING_STREAM.get(), System.err);
        assertEquals(StandardOutputLogging.getOutAdapter().getLevel(), LogLevel.DEBUG);
        assertEquals(StandardOutputLogging.getErrAdapter().getLevel(), LogLevel.ERROR);
        standardOutputCapture.stop();
        assertEquals(oldState, StandardOutputLogging.getStateSnapshot());
    }

    @Test
    public void equalityAndHashcode() {
        standardOutputCapture = new DefaultStandardOutputCapture(true, LogLevel.DEBUG);
        assertEquals(standardOutputCapture, new DefaultStandardOutputCapture(true, LogLevel.DEBUG));
        assertThat(standardOutputCapture, Matchers.not(Matchers.equalTo(
                new DefaultStandardOutputCapture(false, LogLevel.DEBUG))));
        assertEquals(standardOutputCapture.hashCode(), new DefaultStandardOutputCapture(true, LogLevel.DEBUG).hashCode());
    }
}
