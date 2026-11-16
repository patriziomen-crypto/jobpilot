package it.patrizio.jobpilot.chat;

import it.patrizio.jobpilot.ai.ChatMessage;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Assistente in chat: storia, invio messaggio, nuova conversazione. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public record SendRequest(String text) {}

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChatMessage> history() {
        return service.history();
    }

    @PostMapping
    public ChatResponse send(@RequestBody SendRequest request) {
        return service.send(request == null ? null : request.text());
    }

    @DeleteMapping
    public void reset() {
        service.reset();
    }
}
