package br.com.alura.ecomart.chatbot.infra.openai;

import br.com.alura.ecomart.chatbot.domain.DadosCalculoFrete;
import br.com.alura.ecomart.chatbot.domain.service.CalculadorDeFrete;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.messages.Message;
import com.theokanning.openai.messages.MessageRequest;
import com.theokanning.openai.runs.Run;
import com.theokanning.openai.runs.RunCreateRequest;
import com.theokanning.openai.runs.SubmitToolOutputRequestItem;
import com.theokanning.openai.runs.SubmitToolOutputsRequest;
import com.theokanning.openai.service.FunctionExecutor;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.threads.ThreadRequest;
import io.reactivex.Flowable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class OpenAIClient {

    private final String apiKey;
    private final OpenAiService service;
    private final String assistantId;
    private final CalculadorDeFrete calculadorDeFrete;

    private String threadId;

    public OpenAIClient(@Value("${app.openai.api.key}") String apiKey, @Value("${app.openai.assistant.id}") String assistantId, CalculadorDeFrete calculadorDeFrete) {
        this.apiKey = apiKey;
        this.assistantId = assistantId;
        this.calculadorDeFrete = calculadorDeFrete;
        this.service = new OpenAiService(this.apiKey, Duration.ofSeconds(60));
    }

    public String enviarRequisicaoAssistente(DadosRequisicaoChatCompletion dados){

        var messageRequest = MessageRequest.builder()
                .role(ChatMessageRole.USER.value())
                .content(dados.promptUsuario())
                .build();

        if(this.threadId == null){
            var threadRequest = ThreadRequest.builder()
                    .messages(Arrays.asList(messageRequest))
                    .build();

            var thread = service.createThread(threadRequest);
            this.threadId = thread.getId();
        } else {
            service.createMessage(this.threadId, messageRequest);
        }

        var runRequest = RunCreateRequest.builder()
                .assistantId(this.assistantId)
                .build();

        var run = service.createRun(this.threadId, runRequest);


        var concluido = false;
        var executarFuncao = false;
        try {
            while (!concluido && !executarFuncao){
                Thread.sleep(1000 * 3);
                run = service.retrieveRun(this.threadId, run.getId());
                concluido = run.getStatus().equalsIgnoreCase("completed");
                executarFuncao = run.getRequiredAction() != null;
            }
        } catch (InterruptedException e){
            throw new RuntimeException(e);
        }

        if(executarFuncao){
            var retornoFuncao = this.chamarFuncao(run);
            var submitCallFunction = SubmitToolOutputsRequest.builder()
                    .toolOutputs(Arrays.asList(
                            new SubmitToolOutputRequestItem(
                            run.getRequiredAction()
                                    .getSubmitToolOutputs()
                                    .getToolCalls()
                                    .get(0)
                                    .getId(),
                            retornoFuncao
                            )
                    )).build();
            service.submitToolOutputs(this.threadId, run.getId(), submitCallFunction);

            try {
                while (!concluido) {
                    Thread.sleep(1000 * 10);
                    run = service.retrieveRun(threadId, run.getId());
                    concluido = run.getStatus().equalsIgnoreCase("completed");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }

        var resposta = service.listMessages(this.threadId)
                .getData()
                .stream()
                .sorted(Comparator.comparing(Message::getCreatedAt).reversed())
                .findFirst().get().getContent().get(0).getText()
                .getValue().replaceAll("\\\u3010.*?\\\u3011", "");

        return resposta;
    }

    //MODO DE CHAT SEM ASSISTENTE COM RESPOSTA STREAM
    public Flowable<ChatCompletionChunk> enviarRequisicaoChatCompletion(DadosRequisicaoChatCompletion dados) {
        var request = ChatCompletionRequest
                .builder()
                .model(ModelType.GPT_3_5_TURBO_16K.getName())
                .messages(Arrays.asList(
                        new ChatMessage(
                                ChatMessageRole.SYSTEM.value(),
                                dados.promptSistema()),
                        new ChatMessage(
                                ChatMessageRole.USER.value(),
                                dados.promptUsuario())))
                .stream(true)
                .build();

        var segundosParaProximaTentiva = 5;
        var tentativas = 0;
        while (tentativas++ != 5) {
            try {
                return service.streamChatCompletion(request);
            } catch (OpenAiHttpException ex) {
                var errorCode = ex.statusCode;
                switch (errorCode) {
                    case 401 -> throw new RuntimeException("Erro com a chave da API!", ex);
                    case 429, 500, 503 -> {
                        try {
                            Thread.sleep(1000 * segundosParaProximaTentiva);
                            segundosParaProximaTentiva *= 2;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        throw new RuntimeException("API Fora do ar! Tentativas finalizadas sem sucesso!");
    }

    public String chamarFuncao(Run run){
        try {

            var function = run.getRequiredAction().getSubmitToolOutputs().getToolCalls().get(0).getFunction();

            var funcaoCalcularFrete = ChatFunction.builder()
                    .name("calcularFrete")
                    .executor(DadosCalculoFrete.class, d -> calculadorDeFrete.calcular(d))
                    .build();

            var executadorDeFuncoes = new FunctionExecutor(Arrays.asList(funcaoCalcularFrete));

            var functionCall = new ChatFunctionCall(function.getName(), new ObjectMapper().readTree(function.getArguments()));

            return executadorDeFuncoes.execute(functionCall).toString();

        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public List<String> carregarHistorico(){

        var mensagens = new ArrayList<String>();

        if(this.threadId != null){

            mensagens.addAll(
              service.listMessages(this.threadId)
                      .getData()
                      .stream()
                      .sorted(Comparator.comparing(Message::getCreatedAt))
                      .map(m -> m.getContent().get(0).getText().getValue())
                      .toList()
            );

        }

        return mensagens;

    }

    public void deletarThread(){
        if(this.threadId != null){
            service.deleteThread(this.threadId);
            this.threadId = null;
        }
    }

}
