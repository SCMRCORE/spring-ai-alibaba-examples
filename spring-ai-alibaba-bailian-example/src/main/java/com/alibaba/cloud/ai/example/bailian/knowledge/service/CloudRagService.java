/*
* Copyright 2024 the original author or authors.
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

package com.alibaba.cloud.ai.example.bailian.knowledge.service;

import com.alibaba.cloud.ai.advisor.DocumentRetrievalAdvisor;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Title Cloud rag service.<br>
 * Description Cloud rag service.<br>
 *
 * @author yuanci.ytb
 * @since 1.0.0-M2
 */

@Service()
public class CloudRagService implements RagService {

	private static final Logger logger = LoggerFactory.getLogger(CloudRagService.class);

	private static final String indexName = "微服务";

	@Value("classpath:/data/spring_ai_alibaba_quickstart.pdf")
	private Resource springAiResource;

	private static final String retrievalSystemTemplate = """
			Context information is below.
			---------------------
			{question_answer_context}
			---------------------
			Given the context and provided history information and not prior knowledge,
			reply to the user comment. If the answer is not in the context, inform
			the user that you can't answer the question.
			""";

	private final ChatClient chatClient;

	private final DashScopeApi dashscopeApi;

	public CloudRagService(ChatClient.Builder builder, DashScopeApi dashscopeApi) {
		DocumentRetriever retriever = new DashScopeDocumentRetriever(dashscopeApi,
				DashScopeDocumentRetrieverOptions.builder().withIndexName(indexName).build());

		this.dashscopeApi = dashscopeApi;
		this.chatClient = builder
				.defaultAdvisors(new DocumentRetrievalAdvisor(retriever, new SystemPromptTemplate(retrievalSystemTemplate)))
				.build();
	}

	@Override
	public void importDocuments() {
		String path = saveToTempFile(springAiResource);

		// 1. import and split documents
		DocumentReader reader = new DashScopeDocumentCloudReader(path, dashscopeApi, null);
		List<Document> documentList = reader.get();
		logger.info("{} documents loaded and split", documentList.size());

		// 1. add documents to DashScope cloud storage
		VectorStore vectorStore = new DashScopeCloudStore(dashscopeApi, new DashScopeStoreOptions(indexName));
		vectorStore.add(documentList);
		logger.info("{} documents added to dashscope cloud vector store", documentList.size());
	}

	private String saveToTempFile(Resource springAiResource) {
		try {
			File tempFile = File.createTempFile("spring_ai_alibaba_quickstart", ".pdf");
			tempFile.deleteOnExit();

			try (InputStream inputStream = springAiResource.getInputStream();
					FileOutputStream outputStream = new FileOutputStream(tempFile)) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
			}

			return tempFile.getAbsolutePath();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Flux<ChatResponse> retrieve(String message) {
		return chatClient.prompt().user(message).stream().chatResponse();
	}

}
