package br.com.alura.ecomart.chatbot.web.controller;

import br.com.alura.ecomart.chatbot.domain.service.ChatBotService;
import br.com.alura.ecomart.chatbot.web.dto.PerguntaDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@Controller
@RequestMapping({"/", "chat"})
public class ChatController {

    private static final String PAGINA_CHAT = "chat";

    @Autowired
    private ChatBotService chatBotService;

    @GetMapping
    public String carregarPaginaChatbot(Model model) {
        var mensagens = chatBotService.carregarHistorico();
        model.addAttribute("historico", mensagens);
        return PAGINA_CHAT;
    }

    @PostMapping
    @ResponseBody
    public String responderPergunta(@RequestBody PerguntaDto dto) {
        return chatBotService.obterRespostaAssistente(dto.pergunta());
    }

    @GetMapping("limpar")
    public String limparConversa() {
        chatBotService.apagarHistorico();
        return "redirect:/chat";
    }

}
