package com.wind.guild.service;

import com.wind.guild.domain.PartyRole;
import com.wind.guild.repository.PartyRoleRepository;
import com.wind.guild.web.dto.PartyDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PartyRoleService {

    private final PartyRoleRepository roleRepo;

    @Transactional(readOnly = true)
    public List<PartyDto.RoleView> listActive() {
        return roleRepo.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(r -> new PartyDto.RoleView(r.getId(), r.getName(), r.getIcon(), r.getDisplayOrder(), r.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartyDto.RoleView> listAll() {
        return roleRepo.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(r -> new PartyDto.RoleView(r.getId(), r.getName(), r.getIcon(), r.getDisplayOrder(), r.isActive()))
                .toList();
    }

    public PartyDto.RoleView create(PartyDto.RoleUpsertRequest req) {
        int order = req.displayOrder() != null ? req.displayOrder()
                : (int) (roleRepo.count() + 1);
        PartyRole saved = roleRepo.save(PartyRole.builder()
                .name(req.name())
                .icon(req.icon())
                .displayOrder(order)
                .active(req.active() == null || req.active())
                .build());
        return new PartyDto.RoleView(saved.getId(), saved.getName(), saved.getIcon(), saved.getDisplayOrder(), saved.isActive());
    }

    public PartyDto.RoleView update(Long id, PartyDto.RoleUpsertRequest req) {
        PartyRole r = roleRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("역할 없음: " + id));
        r.setName(req.name());
        r.setIcon(req.icon());
        if (req.displayOrder() != null) r.setDisplayOrder(req.displayOrder());
        if (req.active() != null) r.setActive(req.active());
        return new PartyDto.RoleView(r.getId(), r.getName(), r.getIcon(), r.getDisplayOrder(), r.isActive());
    }

    public void delete(Long id) {
        roleRepo.deleteById(id);
    }
}
