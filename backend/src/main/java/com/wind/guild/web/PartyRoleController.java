package com.wind.guild.web;

import com.wind.guild.service.PartyRoleService;
import com.wind.guild.web.dto.PartyDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/party-roles")
@RequiredArgsConstructor
public class PartyRoleController {

    private final PartyRoleService service;

    @GetMapping
    public List<PartyDto.RoleView> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return includeInactive ? service.listAll() : service.listActive();
    }

    @PostMapping
    public PartyDto.RoleView create(@Valid @RequestBody PartyDto.RoleUpsertRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public PartyDto.RoleView update(@PathVariable Long id, @Valid @RequestBody PartyDto.RoleUpsertRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
