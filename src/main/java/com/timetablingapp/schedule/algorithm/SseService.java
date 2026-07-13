package com.timetablingapp.schedule.algorithm;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.timetablingapp.schedule.algorithm.dto.ProgressEvent;

@Service
public class SseService {

    private static final long TIMEOUT = 30 * 60 * 1000L;   // 30 min
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** Register a client for a jobId. */
    public SseEmitter register(String jobId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(()   -> emitters.remove(jobId));
        emitter.onError(e      -> emitters.remove(jobId));
        try { emitter.send(SseEmitter.event().name("connected").data(Map.of("jobId", jobId))); }
        catch (IOException ignored) {}
        return emitter;
    }

    public void broadcast(String jobId, ProgressEvent event) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return;
        try { emitter.send(SseEmitter.event().name(event.getStatus()).data(event)); }
        catch (IOException e) { emitters.remove(jobId); }
    }

    public void complete(String jobId) {
        SseEmitter emitter = emitters.remove(jobId);
        if (emitter != null) emitter.complete();
    }
}
