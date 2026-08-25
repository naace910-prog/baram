package com.wind.guild.web;

import com.wind.guild.service.TargetService;
import com.wind.guild.web.dto.TargetDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/targets")
@RequiredArgsConstructor
public class TargetController {

    private final TargetService targetService;

    @GetMapping
    public List<TargetDto.View> list() {
        return targetService.list();
    }

    @PostMapping
    public TargetDto.View create(@Valid @RequestBody TargetDto.UpsertRequest req) {
        return targetService.create(req);
    }

    @PutMapping("/{id}")
    public TargetDto.View update(@PathVariable Long id, @Valid @RequestBody TargetDto.UpsertRequest req) {
        return targetService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        targetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
