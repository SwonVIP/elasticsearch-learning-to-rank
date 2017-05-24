/*
 * Copyright [2017] Wikimedia Foundation
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

package com.o19s.es.ltr.feature.store;

import org.apache.lucene.util.LuceneTestCase;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.index.query.MatchQueryBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.common.xcontent.NamedXContentRegistry.EMPTY;
import static org.elasticsearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class StoredFeatureParserTests extends LuceneTestCase {
    static final ToXContent.Params NOT_PRETTY;
    static {
        Map<String, String> params = new HashMap<>();
        params.put("pretty", "false");
        NOT_PRETTY = new ToXContent.MapParams(params);
    }

    public void testParseFeatureAsJson() throws IOException {
        String featureString = generateTestFeature();

        StoredFeature feature = parse(featureString);
        assertEquals("testFeature", feature.name());
        assertArrayEquals(Arrays.asList("param1", "param2").toArray(), feature.queryParams().toArray());
        assertEquals("mustache", feature.templateLanguage());
        assertEquals(new MatchQueryBuilder("match_field", "match_word").toString(NOT_PRETTY), feature.template());
    }

    public static String generateTestFeature() {
        return "{\n" +
                    "\"name\": \"testFeature\",\n" +
                    "\"params\": [\"param1\", \"param2\"],\n" +
                    "\"template_language\": \"mustache\",\n" +
                    "\"template\": \n" +
                    new MatchQueryBuilder("match_field", "match_word").toString() +
                    "\n}\n";
    }

    public void testParseFeatureAsString() throws IOException {
        String featureString = "{\n" +
                "\"name\": \"testFeature\",\n" +
                "\"params\": [\"param1\", \"param2\"],\n" +
                "\"template_language\": \"mustache\",\n" +
                "\"template\": \"" +
                new MatchQueryBuilder("match_field", "match_word").toString(NOT_PRETTY)
                        .replace("\"", "\\\"") +
                "\"\n}\n";

        StoredFeature feature = parse(featureString);
        assertEquals("testFeature", feature.name());
        assertArrayEquals(Arrays.asList("param1", "param2").toArray(), feature.queryParams().toArray());
        assertEquals("mustache", feature.templateLanguage());
        assertEquals(new MatchQueryBuilder("match_field", "match_word").toString(NOT_PRETTY),
                feature.template());
    }

    public void testParseErrorOnMissingName() throws IOException {
        String featureString = "{\n" +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                new MatchQueryBuilder("match_field", "match_word").toString() +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                equalTo("Field [name] is mandatory"));
    }

    public void testParseErrorOnMissingTemplate() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\"\n" +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                equalTo("Field [template] is mandatory"));
    }

    public void testParseErrorOnUnknownField() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\n\"bogusField\":\"oops\"," +
                "\"template\": \n" +
                new MatchQueryBuilder("match_field", "match_word").toString() +
                "}";
        assertThat(expectThrows(ParsingException.class, () -> parse(featureString)).getMessage(),
                containsString("bogusField"));
    }

    public void testParseWithoutParams() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                new MatchQueryBuilder("match_field", "match_word").toString() +
                "}";
        StoredFeature feat = parse(featureString);
        assertTrue(feat.queryParams().isEmpty());
    }

    public void testParseWithEmptyParams() throws IOException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\": \n" +
                new MatchQueryBuilder("match_field", "match_word").toString() +
                "}";
        StoredFeature feat = parse(featureString);
        assertTrue(feat.queryParams().isEmpty());
    }

    public void testRamBytesUsed() throws IOException, InterruptedException {
        String featureString = "{\n" +
                "\"name\":\"testFeature\"," +
                "\"params\":[\"param1\",\"param2\"]," +
                "\"template_language\":\"mustache\",\n" +
                "\"template\":\"" +
                new MatchQueryBuilder("match_field", "match_word").toString(NOT_PRETTY).replace("\"", "\\\"") +
                "\"}";
        StoredFeature feature = parse(featureString);
        long approxSize = featureString.length()*Character.BYTES;
        assertThat(feature.ramBytesUsed(),
                allOf(greaterThan((long) (approxSize*0.66)),
                    lessThan((long) (approxSize*1.33))));
    }

    static StoredFeature parse(String featureString) throws IOException {
        return StoredFeature.parse(jsonXContent.createParser(EMPTY, featureString));
    }
}