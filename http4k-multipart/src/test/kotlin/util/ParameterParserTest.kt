/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package util

import org.apache.commons.fileupload.util.ParameterParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ParameterParser].
 */
class ParameterParserTest {

    @Test
    fun testParsing() {
        var s = "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=\"stuff"
        val parser = ParameterParser()
        var params = parser.parse(s)
        assertEquals(null, params["test"])
        assertEquals("stuff", params["test1"])
        assertEquals("stuff; stuff", params["test2"])
        assertEquals("\"stuff", params["test3"])

        s = "  test"
        params = parser.parse(s)
        assertEquals(null, params["test"])

        s = "  "
        params = parser.parse(s)
        assertEquals(0, params.size)

        s = " = stuff "
        params = parser.parse(s)
        assertEquals(0, params.size)
    }

    @Test
    fun testParsingEscapedChars() {
        var s = "param = \"stuff\\\"; more stuff\""
        val parser = ParameterParser()
        var params = parser.parse(s)
        assertEquals(1, params.size)
        assertEquals("stuff\\\"; more stuff", params["param"])

        s = "param = \"stuff\\\\\"; anotherparam"
        params = parser.parse(s)
        assertEquals(2, params.size)
        assertEquals("stuff\\\\", params["param"])
        assertNull(params["anotherparam"])
    }
}