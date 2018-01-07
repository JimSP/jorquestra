# jorchestra
microcontainer para distribuição de execuções, eventos e notificaçes em tempo real, monitorado, administrável e documentado no hazelcast à partir de um endpoint webSocket.

  - Exemplo de Configuração DEFALT:
 
  @Configuration
  @EnableJOrchestra
  public class DefaultConfiguration {

    @Autowired
    private JOrchestraConfigurationProperties jorchestraConfigurationProperties;

    @Bean("hazelcastInstance")
    public HazelcastInstance hazelcastInstance() {
      return Hazelcast.getOrCreateHazelcastInstance(new Config(jorchestraConfigurationProperties.getClusterName()));
    }
  }

  - Exemplo de uso para mensagens distribuídas:
    
    @JOrchestra(path="endpoint") -- A1
    public class DistributedMessage{
      public Response executar(final Request request){ --A2
        ...
        return Response.create(); --A3
      }
    }
    
    A1: durante a fase de loader da aplicação o microcontainer irá interceptar a anotação @JOrchestra e publicar os métodos da classe anotada em endpoints websocket.

    A2: Os endpoint seguem o nome do path descrito em @JOrchestra como prefixo e o nome do método como sulfixo.
       Para que o método "Response DistributedMessage.executar(Request)" seja executado, é preciso estabelecer uma conexão websocket com  endpoint http://servername:port/endpoint-executar
       Ao enviar um payload json do Request, o método do bean java anotado será executado.
   
    A3: Após o processamento do Request e o retorno do Response, será enviado para a conexão estabelecida um json de Response.

*Para Mensagens distribuídas não é obrigatório haver parâmetro ou retorno. 


  - Exemplo de uso para eventos distribuídos:
  
  @JOrchestra(path = "events", jOrchestraSignal = JOrchestraSignal.EVENT) --B1
  public class DistributedEvent implements Consumer<EventType>{--B2
    
    @Autowired
	  private HazelcastInstance hazelcastInstance; --B3
    
    @Override
	  public void accept(final EventType eventType) { --B4
      final ITopic<EventType> topic = hazelcastInstance.getTopic("/events-accept"); -- B5
			topic.publish(eventType); --B6
    }
  }
  
  B1: quando a anotação @JOrchestra possui o atributo jOrchestraSignal = JOrchestraSignal.EVENT o java bean é será registrado para escutar determinados tipos de evento. Quando esse evento ocorrer, o microcontainer irá chamar o método accept passando o eventType como parâmetro.
  
  B2: É preciso que a classe java implemente a interface funcional java.util.function.Consumer, pois o microcontainer irá sempre executar o método accept.
  
  B3: Para que seu evento seja distribuído para outras instancias de JOrchestra na infraestrutura privada é preciso submeter o evento para o Hazelcast, o Hazelcast já possui uma configuração DEFAULT que pode ser utilizada com @Autowired.
  
  B4: assinatura padrão de evento, esse método será chamado pelo microcontainer.
  
  B5: O ITopic "/events-accept" foi criado pelo microcontainer durante a fase de loader e possuí um MessageListener já registrado.
  Esse MessageListener atende no endpoint websocket "/events-accept".
  Quando é estabelicida uma conexão websocket nesse endpoint, será "empurrado" para a conexão estabelecida o evento publicado no ITopic "/events-accept".
  
  B6: publíca o evento no ITopic "/events-accept", todas as conexões websocket estabelecidas no endpoint "/events-accept" nesse momento irão receber um json do EventType.
  
  
  - Exemplo de uso para notificacões distribuídas:
  
  @JOrchestra(path = "notification", jOrchestraSignal = JOrchestraSignal.NOTIFICATION) --C1
  public class DistributedNotification{
    
    @Autowired
	  private HazelcastInstance hazelcastInstance; --C2
    
    public void onNotify(final Notification notification) { --C3
      final ITopic<JOrchestraNotification> topic = hazelcastInstance.getTopic("/notification-onNotify"); --C4
		  topic.publish(new JOrchestraNotification(TransferResponse.class.getName(), messageData)); --C5
    }
  }
  
  C1: registrando o bean como notificação distribuída, O java bean DistributedNotification deve ser chamado em pontos específicos da sua aplicação para que as conexes websocket estabelicidas no dado endpoint recebam o json da Notification.
  
  C2: recebendo instancia default do Hazelcast.
  
  C3: método para enviar a notificação.
  
  C4: ITopic criado pelo loader do microcontainer.
  
  C5: publicação da notificação para o endpoint "/notification-onNotify".
  
  - Documentação automática.
  Deve ser estabelecida uma conexão com o endpoint "/jorchestra-beans", ao enviar uma mensagem "vazia" o microcontainer irá responder com os endpoints disponiveis e templates das mensagens.
  
  Abaixo o template do projeto jorchestra-example, disponível nesse repositório:
  
  [{"jOrchestraBeanName":"JOrchestraNotificationEletronicTransferAccount","jOrchestraPah":"/notification-account","requestTemplate":"{\"transferIdentification\":\"7fffffff-ffff-ffff-7fff-ffffffffffff\",\"statusWithdraw\":\"ERROR\",\"statusTransfer\":\"ERROR\",\"transferRequest\":{\"transferIdentification\":\"eb5445be-159c-4918-9af5-a7f3952eee36\",\"from\":{\"accountNumber\":9223372036854775807},\"to\":{\"accountNumber\":9223372036854775807},\"value\":9223372036854775807}}","responseTemplate":"\"ERROR\"","message":null},{"jOrchestraBeanName":"JOrchestraBeans","jOrchestraPah":"/jorchestra-beans","requestTemplate":null,"responseTemplate":"[]","message":null},{"jOrchestraBeanName":"JOrchestraHelloWordSystemEvent","jOrchestraPah":"/events-accept","requestTemplate":null,"responseTemplate":"\"\"","message":null},{"jOrchestraBeanName":"electronicTransferOfFundsExample","jOrchestraPah":"/account-transfer","requestTemplate":"{\"transferIdentification\":\"4fb4dea4-3247-4f4f-8a7b-b9ea96bc8501\",\"from\":{\"accountNumber\":9223372036854775807},\"to\":{\"accountNumber\":9223372036854775807},\"value\":9223372036854775807}","responseTemplate":"{\"transferIdentification\":\"7fffffff-ffff-ffff-7fff-ffffffffffff\",\"statusWithdraw\":\"ERROR\",\"statusTransfer\":\"ERROR\",\"transferRequest\":{\"transferIdentification\":\"14e69b5f-2196-46c1-9e0b-bb83c213eff8\",\"from\":{\"accountNumber\":9223372036854775807},\"to\":{\"accountNumber\":9223372036854775807},\"value\":9223372036854775807}}","message":null}]
  
  *Os valores prenchidos no template são meramente exemplos do tipo de dado, não devem ser utilizados.
  
  - Monitoramento automático:
  Todas as conexões websocket estabelecidas e mensagens distribuídas enviadas no microcontainer são gerenciadas e seus estados são classificados como:
    
    SESSION_OPEN - conexão estabelecida
    DATA_WAITING - payload aguardando processamento.
    DATA_PROCESSING - payload em processamento.
    DATA_SUCCESS - payload processado com sucesso.
    DATA_ERROR - erro ao tentar processar payload.
    SESSION_CLOSE - conexão fechada.
    
  Ao estabelecer uma conexão com o endpoint websocket "/jOrchestra-monitor" é possivel receber do microcontainer o estado das conexões estabelecidas e payload enviados aos endpoints disponíveis à partir desse momento.
  
  Abaixo o payload recebido pela conexão estabelecida no endpoint "/jOrchestra-monitor".
  Esse payload foi enviado quando uma conexão foi estabelecida no path "jorchestra-beans", enviado um payload e encerrada a conexão.
  
  {"id":"jOcrhestra#JOrchestraExampleApp-Dev#2#4a4d39e6-4342-489e-8d21-e04d461ff815","clusterName":"jOcrhestra","jOcrhestrName":"JOrchestraExampleApp-Dev","sessionId":"2","requestId":"4a4d39e6-4342-489e-8d21-e04d461ff815","beginTimestamp":1515344992534,"endTimestamp":null,"jOrchestraState":"SESSION_OPEN","payload":null}
{"id":"jOcrhestra#JOrchestraExampleApp-Dev#2#b78d1c6a-a080-4ffc-8fa8-8b872272897a","clusterName":"jOcrhestra","jOcrhestrName":"JOrchestraExampleApp-Dev","sessionId":"2","requestId":"b78d1c6a-a080-4ffc-8fa8-8b872272897a","beginTimestamp":null,"endTimestamp":null,"jOrchestraState":"DATA_WAITING","payload":""}
{"id":"jOcrhestra#JOrchestraExampleApp-Dev#2#a230de4a-8aac-4953-8059-82783d26e672","clusterName":"jOcrhestra","jOcrhestrName":"JOrchestraExampleApp-Dev","sessionId":"2","requestId":"a230de4a-8aac-4953-8059-82783d26e672","beginTimestamp":1515344997551,"endTimestamp":null,"jOrchestraState":"DATA_PROCESSING","payload":""}
{"id":"jOcrhestra#JOrchestraExampleApp-Dev#2#5bffcc48-d95b-470f-b4dd-6c4cfe34f605","clusterName":"jOcrhestra","jOcrhestrName":"JOrchestraExampleApp-Dev","sessionId":"2","requestId":"5bffcc48-d95b-470f-b4dd-6c4cfe34f605","beginTimestamp":null,"endTimestamp":1515344997691,"jOrchestraState":"DATA_SUCCESS","payload":""}
{"id":"jOcrhestra#JOrchestraExampleApp-Dev#2#979f3728-1684-444e-9d05-880011ac889a","clusterName":"jOcrhestra","jOcrhestrName":"JOrchestraExampleApp-Dev","sessionId":"2","requestId":"979f3728-1684-444e-9d05-880011ac889a","beginTimestamp":null,"endTimestamp":1515345000688,"jOrchestraState":"SESSION_CLOSE","payload":null}
