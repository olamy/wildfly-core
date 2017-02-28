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
public class AttachmentDisplayNamesTestCase {
    private static final ModelNode REQUEST_PROPERTIES = ModelNode.fromJSONString("{\n" +
"    \"runtime-name\" : {\n" +
"        \"type\" : {\n" +
"            \"TYPE_MODEL_VALUE\" : \"STRING\"\n" +
"        },\n" +
"        \"description\" : \"Name by which the deployment should be known within a server's runtime. This would be equivalent to the file name of a deployment file, and would form the basis for such things as default Java Enterprise Edition application and module names. This would typically be the same as 'name', but in some cases users may wish to have two deployments with the same 'runtime-name' (e.g. two versions of \\\"foo.war\\\") both available in the deployment content repository, in which case the deployments would need to have distinct 'name' values but would have the same 'runtime-name'.\",\n" +
"        \"expressions-allowed\" : false,\n" +
"        \"required\" : false,\n" +
"        \"nillable\" : true,\n" +
"        \"min-length\" : 1,\n" +
"        \"max-length\" : 2147483647\n" +
"    },\n" +
"    \"content\" : {\n" +
"        \"type\" : {\n" +
"            \"TYPE_MODEL_VALUE\" : \"LIST\"\n" +
"        },\n" +
"        \"description\" : \"List of pieces of content that comprise the deployment.\",\n" +
"        \"expressions-allowed\" : false,\n" +
"        \"required\" : false,\n" +
"        \"nillable\" : true,\n" +
"        \"value-type\" : {\n" +
"            \"input-stream-index\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"INT\"\n" +
"                },\n" +
"                \"description\" : \"The index into the operation's attached input streams of the input stream that contains deployment content that should be uploaded to the domain's or standalone server's deployment content repository.\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"alternatives\" : [\n" +
"                    \"hash\",\n" +
"                    \"bytes\",\n" +
"                    \"url\",\n" +
"                    \"path\",\n" +
"                    \"relative-to\",\n" +
"                    \"empty\"\n" +
"                ],\n" +
"                \"min\" : 1,\n" +
"                \"max\" : 2147483647,\n" +
"                \"filesystem-path\" : true,\n" +
"                \"attached-streams\" : true,\n" +
"                \"display-name\" : \"input\"\n" +
"            },\n" +
"            \"hash\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"BYTES\"\n" +
"                },\n" +
"                \"description\" : \"The hash of managed deployment content that has been uploaded to the domain's or standalone server's deployment content repository.\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"alternatives\" : [\n" +
"                    \"input-stream-index\",\n" +
"                    \"bytes\",\n" +
"                    \"url\",\n" +
"                    \"path\",\n" +
"                    \"relative-to\",\n" +
"                    \"empty\"\n" +
"                ],\n" +
"                \"min-length\" : 20,\n" +
"                \"max-length\" : 20\n" +
"            },\n" +
"            \"bytes\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"BYTES\"\n" +
"                },\n" +
"                \"description\" : \"Byte array containing the deployment content that should uploaded to the domain's or standalone server's deployment content repository.\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"alternatives\" : [\n" +
"                    \"input-stream-index\",\n" +
"                    \"hash\",\n" +
"                    \"url\",\n" +
"                    \"path\",\n" +
"                    \"relative-to\",\n" +
"                    \"empty\"\n" +
"                ]\n" +
"            },\n" +
"            \"url\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"STRING\"\n" +
"                },\n" +
"                \"description\" : \"The URL at which the deployment content is available for upload to the domain's or standalone server's deployment content repository.. Note that the URL must be accessible from the target of the operation (i.e. the Domain Controller or standalone server).\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"alternatives\" : [\n" +
"                    \"input-stream-index\",\n" +
"                    \"hash\",\n" +
"                    \"bytes\",\n" +
"                    \"path\",\n" +
"                    \"relative-to\",\n" +
"                    \"empty\"\n" +
"                ],\n" +
"                \"min-length\" : 1,\n" +
"                \"max-length\" : 2147483647\n" +
"            },\n" +
"            \"path\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"STRING\"\n" +
"                },\n" +
"                \"description\" : \"Path (relative or absolute) to unmanaged content that is part of the deployment.\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"alternatives\" : [\n" +
"                    \"input-stream-index\",\n" +
"                    \"hash\",\n" +
"                    \"bytes\",\n" +
"                    \"url\",\n" +
"                    \"empty\"\n" +
"                ],\n" +
"                \"requires\" : [\"archive\"],\n" +
"                \"min-length\" : 1,\n" +
"                \"max-length\" : 2147483647\n" +
"            },\n" +
"            \"relative-to\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"STRING\"\n" +
"                },\n" +
"                \"description\" : \"Name of a system path to which the value of the 'path' is relative. If not set, the 'path' is considered to be absolute.\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"alternatives\" : [\n" +
"                    \"input-stream-index\",\n" +
"                    \"hash\",\n" +
"                    \"bytes\",\n" +
"                    \"url\",\n" +
"                    \"empty\"\n" +
"                ],\n" +
"                \"requires\" : [\"path\"],\n" +
"                \"min-length\" : 1,\n" +
"                \"max-length\" : 2147483647\n" +
"            },\n" +
"            \"archive\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"BOOLEAN\"\n" +
"                },\n" +
"                \"description\" : \"Flag indicating whether unmanaged content is a zip archive (true) or exploded (false).\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"alternatives\" : [\n" +
"                    \"input-stream-index\",\n" +
"                    \"bytes\",\n" +
"                    \"url\"\n" +
"                ],\n" +
"                \"requires\" : [\n" +
"                    \"path\",\n" +
"                    \"hash\",\n" +
"                    \"empty\"\n" +
"                ]\n" +
"            },\n" +
"            \"empty\" : {\n" +
"                \"type\" : {\n" +
"                    \"TYPE_MODEL_VALUE\" : \"BOOLEAN\"\n" +
"                },\n" +
"                \"description\" : \"Indicates that the deployment to be added is empty - so without any content.\",\n" +
"                \"expressions-allowed\" : false,\n" +
"                \"required\" : false,\n" +
"                \"nillable\" : true,\n" +
"                \"default\" : false,\n" +
"                \"alternatives\" : [\n" +
"                    \"hash\",\n" +
"                    \"input-stream-index\",\n" +
"                    \"bytes\",\n" +
"                    \"url\",\n" +
"                    \"path\",\n" +
"                    \"relative-to\"\n" +
"                ]\n" +
"            }\n" +
"        }\n" +
"    },\n" +
"    \"enabled\" : {\n" +
"        \"type\" : {\n" +
"            \"TYPE_MODEL_VALUE\" : \"BOOLEAN\"\n" +
"        },\n" +
"        \"description\" : \"Boolean indicating whether the deployment content is currently deployed in the runtime (or should be deployed in the runtime the next time the server starts.)\",\n" +
"        \"expressions-allowed\" : false,\n" +
"        \"required\" : false,\n" +
"        \"nillable\" : true,\n" +
"        \"default\" : false\n" +
"    }\n" +
"}");

    @Test
    public void testAlias() throws CliInitializationException {
        // 10 files should be replaced by indexes.
        ModelNode value = ModelNode.fromJSONString("[{\"input\" : \"/home/ehsavoie/derby.log\"}]");
        ModelNode expected = ModelNode.fromJSONString("[{\"input-stream-index\" : 0}]");
        ModelNode req = REQUEST_PROPERTIES;
        Attachments attachments = new Attachments();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        Map<String, String> aliases = new HashMap<>();
        aliases.put("input", "input-stream-index");
        Util.applyReplacements(ctx, "content", value, req.get("content"), req.get(("content")).get(Util.TYPE).asType(), attachments, aliases);
        Assert.assertEquals("Should be equal", expected, value);
        Assert.assertEquals(1, attachments.getAttachedFiles().size());
        for (int i = 0; i < attachments.getAttachedFiles().size(); i++) {
            String p = attachments.getAttachedFiles().get(i);
            Assert.assertEquals("/home/ehsavoie/derby.log",p);
        }
    }
}
