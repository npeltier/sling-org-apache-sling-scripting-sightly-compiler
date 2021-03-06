/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/
package org.apache.sling.scripting.sightly.impl.compiler;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import org.apache.sling.scripting.sightly.compiler.CompilationResult;
import org.apache.sling.scripting.sightly.compiler.CompilationUnit;
import org.apache.sling.scripting.sightly.compiler.CompilerMessage;
import org.apache.sling.scripting.sightly.compiler.SightlyCompiler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SightlyCompiler.class)
public class SightlyCompilerTest {

    private SightlyCompiler compiler = new SightlyCompiler();

    @Test
    public void testEmptyExpression() {
        CompilationResult result = compileFile("/empty-expression.html");
        assertTrue("Didn't expect any warnings or errors.", result.getErrors().size() == 0 && result.getWarnings().size() == 0);
    }

    @Test
    public void testMissingExplicitContext() {
        for (String s : new String[] {"", "-win", "-mac"}) {
            String script = "/missing-explicit-context" + s + ".html";
            testMissingExplicitContext(script);
        }
    }

    private void testMissingExplicitContext(String script) {
        CompilationResult result = compileFile(script);
        List<CompilerMessage> warnings = result.getWarnings();
        assertTrue(script + ": Expected compilation warnings.", warnings.size() == 1);
        CompilerMessage warningMessage = warnings.get(0);
        assertEquals(script + ": Expected warning on a different line.", 18, warningMessage.getLine());
        assertEquals(script + ": Expected warning on a different column.", 14, warningMessage.getColumn());
        assertTrue(script.equals(warningMessage.getScriptName()));
        assertEquals("${some.value}: Element script requires that all expressions have an explicit context specified. The expression will" +
                " be replaced with an empty string.", warningMessage.getMessage());
    }

    @Test
    public void testMissingExplicitContextOnWindows() {
        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.lineSeparator()).thenReturn("\r\n");

        for (String s : new String[] {"", "-win", "-mac"}) {
            String script = "/missing-explicit-context" + s + ".html";
            testMissingExplicitContext(script);
        }
    }

    @Test
    public void testMissingExplicitContextOnMac() {
        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.lineSeparator()).thenReturn("\r");

        for (String s : new String[] {"", "-win", "-mac"}) {
            String script = "/missing-explicit-context" + s + ".html";
            testMissingExplicitContext(script);
        }
    }

    @Test
    public void testSensitiveAttributes() {
        String script = "/sensitive-attributes.html";
        CompilationResult result = compileFile(script);
        List<CompilerMessage> warnings = result.getWarnings();
        assertTrue("Expected compilation warnings.", warnings.size() == 2);
        CompilerMessage _1stWarning = warnings.get(0);
        CompilerMessage _2ndWarning = warnings.get(1);
        assertEquals(script, _1stWarning.getScriptName());
        assertEquals("Expected warning on a different line.", 17, _1stWarning.getLine());
        assertEquals("Expected warning on a different line.", 18, _2ndWarning.getLine());
        assertEquals(script, _2ndWarning.getScriptName());
        assertEquals("${style.string}: Expressions within the value of attribute style need to have an explicit context option. The " +
                "expression will be replaced with an empty string.", _1stWarning.getMessage());
        assertEquals("${onclick.action}: Expressions within the value of attribute onclick need to have an explicit context option. The " +
                "expression will be replaced with an empty string.", _2ndWarning.getMessage());

    }

    @Test
    public void testErrorReporting1() {
        String script = "/error-1.html";
        CompilationResult result = compileFile(script);
        List<CompilerMessage> errors = result.getErrors();
        assertTrue("Expected compilation errors.", errors.size() == 1);
        CompilerMessage error = errors.get(0);
        assertEquals("Error is not reported at the expected line.", 18, error.getLine());
    }

    @Test
    public void testNumberParsing() {
        // integers
        int integerTestRange = 20;
        for (int i = -1 * integerTestRange; i < integerTestRange; i++) {
            assertEquals(0, compileSource("${" + i + "}").getErrors().size());
        }

        // doubles
        double  doubleTestRange = 20.00;
        for (double i = -1.00 * doubleTestRange; i < doubleTestRange; i+= 0.1) {
            assertEquals(0, compileSource("${" + i + "}").getErrors().size());
        }

        assertEquals(0, compileSource("${-0.0}").getErrors().size());
        assertEquals(0, compileSource("${-0.000}").getErrors().size());
        assertEquals(1, compileSource("${-00.0}").getErrors().size());
        assertEquals(1, compileSource("${00.0}").getErrors().size());
        assertEquals(1, compileSource("${00}").getErrors().size());
        assertEquals(1, compileSource("${-0}").getErrors().size());
        assertEquals(1, compileSource("${01}").getErrors().size());
        assertEquals(0, compileSource("${0.1e-2}").getErrors().size());
        assertEquals(0, compileSource("${0.1e+2}").getErrors().size());
        assertEquals(1, compileSource("${00.1e-2}").getErrors().size());
        assertEquals(1, compileSource("${0e-2}").getErrors().size());
        assertEquals(1, compileSource("${01e-2}").getErrors().size());
        assertEquals(0, compileSource("${1e-2}").getErrors().size());
        assertEquals(0, compileSource("${1e+2}").getErrors().size());
    }

    private CompilationResult compileFile(final String file) {
        InputStream stream = this.getClass().getResourceAsStream(file);
        final Reader reader = new InputStreamReader(stream);
        CompilationUnit compilationUnit = new CompilationUnit() {
            @Override
            public String getScriptName() {
                return file;
            }

            @Override
            public Reader getScriptReader() {
                return reader;
            }
        };
        return compiler.compile(compilationUnit);
    }

    private CompilationResult compileSource(final String source) {
        CompilationUnit compilationUnit = new CompilationUnit() {
            @Override
            public String getScriptName() {
                return "NO_NAME";
            }

            @Override
            public Reader getScriptReader() {
                return new StringReader(source);
            }
        };
        return compiler.compile(compilationUnit);
    }


}
