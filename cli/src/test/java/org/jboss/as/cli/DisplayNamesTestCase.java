/*
 * Copyright 2017 JBoss by Red Hat.
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
package org.jboss.as.cli;


import java.util.HashMap;
import java.util.Map;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class DisplayNamesTestCase {
    private static final ModelNode SIMPLE_STRING_ALIAS = ModelNode.fromJSONString("{\n" +
"    \"simpleString\" : {\n" +
"        \"type\" : \"STRING\",\n" +
"        \"description\" : \"\"\n" +
"    },\n" +
"    \"realString\" : {\n" +
"        \"type\" : \"STRING\",\n" +
"        \"description\" : \"\",\n" +
"        \"display-name\" : \"alias\"\n" +
"    }\n"+
"}");

    @Test
    public void testAlias() throws CliInitializationException, CommandFormatException {
        // 10 files should be replaced by indexes.
        ModelNode value = ModelNode.fromJSONString("{\"simpleString\" : \"simple\",\n \"alias\" : \"test\"}");
        ModelNode expected = ModelNode.fromJSONString("{\"simpleString\" : \"simple\",\n \"realString\" : \"test\"}");
        ModelNode outcome = SIMPLE_STRING_ALIAS.asObject();
        Attachments attachments = new Attachments();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        ModelNode request = new ModelNode();
        Map<String, String> aliases = new HashMap<>();
        aliases.put("alias", "realString");
        for (String k : value.keys()) {
            Util.convertArgument(ctx, k, value.get(k), outcome, attachments, request, aliases);
        }
        Assert.assertEquals("Should be equal", expected, request);
        Assert.assertEquals(0, attachments.getAttachedFiles().size());
    }

    @Test
    public void testNoAlias() throws CliInitializationException, CommandFormatException {
        // 10 files should be replaced by indexes.
        ModelNode value = ModelNode.fromJSONString("{\"simpleString\" : \"simple\",\n \"realString\" : \"test\"}");
        ModelNode expected = ModelNode.fromJSONString("{\"simpleString\" : \"simple\",\n \"realString\" : \"test\"}");
        ModelNode outcome = SIMPLE_STRING_ALIAS.asObject();
        Attachments attachments = new Attachments();
        ModelNode request = new ModelNode();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        Map<String, String> aliases = new HashMap<>();
        aliases.put("alias", "realString");
        for (String k : value.keys()) {
            Util.convertArgument(ctx, k, value.get(k), outcome, attachments, request, aliases);
        }
        Assert.assertEquals("Should be equal", expected, request);
        Assert.assertEquals(0, attachments.getAttachedFiles().size());
    }
}
