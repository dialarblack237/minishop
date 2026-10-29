package com.example.minishop.web;

import com.example.minishop.domain.DlqMessage;
import com.example.minishop.repository.DlqMessageRepository;
import com.example.minishop.service.DlqMessageNotFound;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Tag(name = "Admin: DLQ", description = "Inspect and replay dead-lettered messages.")
@RestController
@RequestMapping("/admin/dlq")
public class DlqAdminController {

    private final DlqMessageRepository dlqRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DlqAdminController(DlqMessageRepository dlqRepo,
                              KafkaTemplate<String, String> kafkaTemplate) {
        this.dlqRepo = dlqRepo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Operation(
            summary = "Replay a DLQ message",
            description = "Re-publishes the message to its original topic with the original eventId. " +
                          "If the event was already processed (dedupe), the replay is a no-op. " +
                          "If it failed before, the retry does the work.")
    @ApiResponse(responseCode = "202", description = "Replay accepted — message re-published")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Requires ADMIN role")
    @ApiResponse(responseCode = "404", description = "DLQ message not found")
    @ApiResponse(responseCode = "409", description = "Message already replayed")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/replay")
    public ResponseEntity<Void> replay(@PathVariable long id) throws Exception {
        DlqMessage row = dlqRepo.findById(id)
                .orElseThrow(() -> new DlqMessageNotFound(id));

        if (row.getReplayedAt() != null) {
            return ResponseEntity.status(409).build();
        }

        kafkaTemplate.send(row.getOriginalTopic(), row.getMessageKey(), row.getPayload())
                .get(10, TimeUnit.SECONDS);

        row.markReplayed();
        dlqRepo.save(row);

        return ResponseEntity.accepted().build();
    }
}