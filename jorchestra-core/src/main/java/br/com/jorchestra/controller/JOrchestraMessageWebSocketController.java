package br.com.jorchestra.controller;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.IExecutorService;

import br.com.jorchestra.callable.JOrchestraCallable;
import br.com.jorchestra.handle.JOrchestraHandle;

public class JOrchestraMessageWebSocketController extends TextWebSocketHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(JOrchestraMessageWebSocketController.class);

	private final JOrchestraHandle jOrchestraHandle;
	private final IExecutorService executorService;

	public JOrchestraMessageWebSocketController(final JOrchestraHandle jOrchestraHandle,
			final IExecutorService executorService) {
		this.jOrchestraHandle = jOrchestraHandle;
		this.executorService = executorService;
	}

	@Override
	public void handleTextMessage(final WebSocketSession webSocketSession, final TextMessage textMessage)
			throws Exception {
		final String sessionId = webSocketSession.getId();
		final String payload = textMessage.getPayload();
		final UUID requestId = UUID.randomUUID();
		final String logMsg = "m=handleTextMessage, webSocketSession.id=" + sessionId + ", textMessage.payload="
				+ payload + ", requestId=" + requestId.toString() + ", path=" + jOrchestraHandle.getPath() + ", method="
				+ jOrchestraHandle.getMethod();

		LOGGER.debug(logMsg);

		try {
			invokeJOrchestraBean(webSocketSession, payload);
		} catch (Throwable t) {
			logError(new RuntimeException(t));
		}
	}

	@Override
	public void afterConnectionEstablished(final WebSocketSession webSocketSession) throws Exception {
		final String sessionId = webSocketSession.getId();
		final UUID requestId = UUID.randomUUID();
		final String logMsg = "m=afterConnectionEstablished, webSocketSession.id=" + sessionId + ", requestId="
				+ requestId.toString() + ", path=" + jOrchestraHandle.getPath() + ", method="
				+ jOrchestraHandle.getMethod();

		LOGGER.debug(logMsg);
		try {
			super.afterConnectionEstablished(webSocketSession);
		} catch (Exception e) {
			logError(e);
		}
	}

	@Override
	public void afterConnectionClosed(final WebSocketSession webSocketSession, final CloseStatus closeStatus)
			throws Exception {
		final String sessionId = webSocketSession.getId();
		final String reason = closeStatus.getReason();
		final Integer code = closeStatus.getCode();

		final UUID requestId = UUID.randomUUID();
		final String logMsg = "m=afterConnectionClosed, webSocketSession.id=" + sessionId + ", closeStatus.reason="
				+ reason + ", closeStatus.code=" + code + ", requestId=" + requestId + ", path="
				+ jOrchestraHandle.getPath() + ", method=" + jOrchestraHandle.getMethod();

		LOGGER.debug(logMsg);

		try {
			super.afterConnectionClosed(webSocketSession, closeStatus);
		} catch (Exception e) {
			logError(e);
		}
	}

	@Override
	public void handleTransportError(final WebSocketSession webSocketSession, final Throwable e) throws Exception {
		final String sessionId = webSocketSession.getId();
		final UUID requestId = UUID.randomUUID();

		final String logMsg = "m=afterConnectionClosed, webSocketSession.id=" + sessionId + ", requestId=" + requestId
				+ ", path=" + jOrchestraHandle.getPath() + ", method=" + jOrchestraHandle.getMethod();

		LOGGER.debug(logMsg, e);

		webSocketSession.close(CloseStatus.SERVER_ERROR);
	}

	private void invokeJOrchestraBean(final WebSocketSession webSocketSession, final String payload)
			throws IllegalAccessException, InvocationTargetException, IOException, JsonParseException,
			JsonMappingException, JsonProcessingException, InterruptedException, ExecutionException {

		final ObjectMapper objectMapper = new ObjectMapper();
		final Class<?>[] parameterTypes = jOrchestraHandle.getMethod().getParameterTypes();

		final List<Object> list = new ArrayList<>();
		for (Class<?> parameterClass : parameterTypes) {
			final Object parameter = objectMapper.readValue(payload, parameterClass);
			list.add(parameter);
		}

		final JOrchestraCallable JOrchestraCallable = new JOrchestraCallable(jOrchestraHandle.getjOrchestraBeanName(),
				jOrchestraHandle.getMethodName(), jOrchestraHandle.getMethod().getParameterTypes(), list.toArray());
		final Future<Object> future = executorService.submit(JOrchestraCallable);

		try {
			final Object result = future.get();
			sendCallback(webSocketSession, result);
		} catch (Exception e) {
			logError(e);
		}
	}

	private void sendCallback(final WebSocketSession webSocketSession, final Object object)
			throws IOException, JsonProcessingException {
		final ObjectMapper objectMapper = new ObjectMapper();
		final String payload = objectMapper.writeValueAsString(object);
		LOGGER.debug("m=sendCallback, payload=" + payload);
		webSocketSession.sendMessage(new TextMessage(payload));
	}

	private void logError(final Exception e) throws IOException, JsonProcessingException {
		LOGGER.error("m=sendCallback", e);
	}
}