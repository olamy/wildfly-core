/*
 * Copyright 2018 JBoss by Red Hat.
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
package org.jboss.as.controller;

import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class CapabilityReferenceRecorderTest {

    @Test
    public void testDefaultRequirementPatternElements() {
        CapabilityReferenceRecorder.DefaultCapabilityReferenceRecorder recorder = new CapabilityReferenceRecorder.DefaultCapabilityReferenceRecorder("org.wildfly.requirement", "org.wildfly.dependent", true);
        String[] result = recorder.getRequirementPatternSegments("test");
        Assert.assertTrue(result != null);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals("test", result[0]);
    }

    @Test
    public void testCompositeRequirementPatternElements() {
        CapabilityReferenceRecorder.CompositeAttributeDependencyRecorder recorder = new CapabilityReferenceRecorder.CompositeAttributeDependencyRecorder("org.wildfly.requirement",
                new SimpleAttributeDefinitionBuilder("stack", ModelType.STRING).build(),
                new SimpleAttributeDefinitionBuilder("cache-container", ModelType.STRING).build());
        String[] result = recorder.getRequirementPatternSegments("test");
        Assert.assertTrue(result != null);
        Assert.assertEquals(3, result.length);
        Assert.assertEquals("stack", result[0]);
        Assert.assertEquals("cache-container", result[1]);
        Assert.assertEquals("test", result[2]);
        result = recorder.getRequirementPatternSegments("stack");
        Assert.assertTrue(result != null);
        Assert.assertEquals(3, result.length);
        Assert.assertEquals("stack_addr", result[0]);
        Assert.assertEquals("cache-container", result[1]);
        Assert.assertEquals("stack", result[2]);
    }

    @Test
    public void testContextRequirementPatternElements() {
        CapabilityReferenceRecorder.ContextDependencyRecorder recorder = new CapabilityReferenceRecorder.ContextDependencyRecorder("org.wildfly.requirement");
        String[] result = recorder.getRequirementPatternSegments("test");
        Assert.assertTrue(result != null);
        Assert.assertEquals(1, result.length);
        Assert.assertEquals("test", result[0]);
    }
}
