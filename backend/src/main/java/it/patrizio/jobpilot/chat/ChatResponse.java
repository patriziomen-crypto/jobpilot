package it.patrizio.jobpilot.chat;

import it.patrizio.jobpilot.ai.ChatMessage;

import java.util.List;

/** Risposta dell'assistente al frontend: messaggio, chi l'ha scritto, cosa ha fatto, eventuale pagina da aprire. */
public record ChatResponse(ChatMessage message, String provider, String model, List<String> done, String navigateTo) {}
