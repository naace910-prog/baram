package com.wind.guild.service;

import com.wind.guild.domain.RaidTarget;
import com.wind.guild.repository.RaidTargetRepository;
import com.wind.guild.web.dto.TargetDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TargetService {

    private final RaidTargetRepository repo;

    @Transactional(readOnly = true)
    public List<TargetDto.View> list() {
        return repo.findAll().stream().map(TargetDto.View::of).toList();
    }

    public TargetDto.View create(TargetDto.UpsertRequest req) {
        RaidTarget saved = repo.save(RaidTarget.builder()
                .name(req.name())
                .dropItemName(req.dropItemName())
                .memo(req.memo())
                .build());
        return TargetDto.View.of(saved);
    }

    public TargetDto.View update(Long id, TargetDto.UpsertRequest req) {
        RaidTarget t = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("대상 없음: " + id));
        t.setName(req.name());
        t.setDropItemName(req.dropItemName());
        t.setMemo(req.memo());
        return TargetDto.View.of(t);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
