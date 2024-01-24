package br.com.alura.ecomart.chatbot.domain.service;

import br.com.alura.ecomart.chatbot.infra.openai.DadosRequisicaoChatCompletion;
import br.com.alura.ecomart.chatbot.infra.openai.OpenAIClient;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import io.reactivex.Flowable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatBotService {

    @Autowired
    private OpenAIClient client;

    @Value("${app.openai.api.promptSistema}")
    private String promptSistema;

    public Flowable<ChatCompletionChunk> obterResposta(String pergunta){
        return client.enviarRequisicaoChatCompletion(new DadosRequisicaoChatCompletion(promptSistema, pergunta));
    }

    public String obterRespostaAssistente(String pergunta){
        return client.enviarRequisicaoAssistente(new DadosRequisicaoChatCompletion(promptSistema, pergunta));
    }

    public List<String> carregarHistorico(){
        return client.carregarHistorico();
    }

    public void apagarHistorico(){
        client.deletarThread();
    }

}
