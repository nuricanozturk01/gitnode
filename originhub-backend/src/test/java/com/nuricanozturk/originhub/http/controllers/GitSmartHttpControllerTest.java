/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nuricanozturk.originhub.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "originhub.http.enabled=true")
class GitSmartHttpControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void testGetInfoRefsWithoutAuth() throws Exception {
    this.mockMvc
        .perform(MockMvcRequestBuilders.get("/git/testuser/testrepo/info/refs")
            .param("service", "git-upload-pack"))
        .andExpect(MockMvcResultMatchers.status().is4xxClientError());
  }

  @Test
  void testGetInfoRefsWithInvalidCredentials() throws Exception {
    this.mockMvc
        .perform(MockMvcRequestBuilders.get("/git/testuser/testrepo/info/refs")
            .param("service", "git-upload-pack")
            .header("Authorization", "Basic aW52YWxpZDppbnZhbGlk"))
        .andExpect(MockMvcResultMatchers.status().is4xxClientError());
  }
}
