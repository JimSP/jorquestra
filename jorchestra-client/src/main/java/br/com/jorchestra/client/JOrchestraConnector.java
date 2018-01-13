package br.com.jorchestra.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import br.com.jorchestra.client.exception.JOrchestraConnectionCloseException;
import br.com.jorchestra.client.exception.JOrchestraConnectionErrorException;
import br.com.jorchestra.dto.JOrchestraBeanResponse;

public final class JOrchestraConnector implements Closeable{

	public static JOrchestraConnector create(final String url) throws DeploymentException, IOException {
		return new JOrchestraConnector(url);
	}

	private static JOrchestraBeanClient connect(final String url)
			throws DeploymentException, IOException {
		return connect(URI.create(url));
	}

	private static JOrchestraBeanClient connect(final URI uri)
			throws DeploymentException, IOException {
		final WebSocketContainer webSocketContainer = createContainer();
		final JOrchestraBeanClient jOrchestraClient = JOrchestraBeanClient.create();
		webSocketContainer.connectToServer(jOrchestraClient, uri);

		return jOrchestraClient;
	}

	private static WebSocketContainer createContainer() {
		return ContainerProvider.getWebSocketContainer();
	}

	private final JOrchestraBeanClient jOrchestraClient;

	private JOrchestraConnector(final String url)
			throws DeploymentException, IOException {
		jOrchestraClient = JOrchestraConnector.connect(url);
	}

	public List<JOrchestraBeanResponse> send() throws InterruptedException, ExecutionException, IOException,
			JOrchestraConnectionCloseException, JOrchestraConnectionErrorException {
		return jOrchestraClient.sendMessage();
	}

	@Override
	public void close() throws IOException {
		jOrchestraClient.close();
	}
}
