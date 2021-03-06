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
package org.gradle.api.testing.execution.control.refork;

import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowConfig extends ReforkReasonKeyLink implements ReforkReasonConfig {

    static final double DEFAULT_MEMORY_LOW_THRESHOLD = 100;
    
    private double memoryLowThreshold = DEFAULT_MEMORY_LOW_THRESHOLD;

    public ForkMemoryLowConfig(ReforkReasonKey reforkReasonKey) {
        super(reforkReasonKey);
    }

    public double getMemoryLowThreshold() {
        return memoryLowThreshold;
    }

    public void setMemoryLowThreshold(double memoryLowThreshold) {
        if (memoryLowThreshold <= 0) {
            throw new IllegalArgumentException("memoryLowThreshold can't be smaller than or equal to zero!");
        }

        this.memoryLowThreshold = memoryLowThreshold;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeDouble(memoryLowThreshold);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        memoryLowThreshold = in.readDouble();
    }
}
