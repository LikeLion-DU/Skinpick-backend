package com.skinplate.api.domain.report.service;

import com.skinplate.api.domain.plate.repository.SkinPlateRepository;
import com.skinplate.api.domain.report.dto.DailyReportResponse;
import com.skinplate.api.domain.user.entity.SkinConcern;
import com.skinplate.api.domain.user.repository.AppUserRepository;
import com.skinplate.api.global.common.DateRange;
import com.skinplate.api.global.exception.BusinessException;
import com.skinplate.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일일 리포트. 저장된 기록만 읽어 다시 센다 — AI 를 부르지 않고, 아무것도 쓰지 않는다.
 *
 * <p>주간·월간이 이 클래스를 통해 하루치 데이터를 얻는다({@link #getRange}). 집계 화면이
 * 원본 기록을 따로 훑지 않으므로 "하루 점수"의 정의가 한 곳에만 존재한다.
 */
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final SkinPlateRepository skinPlateRepository;
    private final AppUserRepository userRepository;

    /** date 를 생략하면 서버의 오늘(KST) 이다. */
    @Transactional(readOnly = true)
    public DailyReportResponse get(Long userId, LocalDate date) {
        LocalDate target = date == null ? LocalDate.now(DateRange.KST) : date;
        DateRange range = DateRange.of(target, target);

        return DailyReportAssembler.of(target,
                skinPlateRepository.findInRange(userId, range.from(), range.toExclusive()),
                concerns(userId));
    }

    /**
     * 기간 안의 <b>기록이 있는 날만</b> 날짜 오름차순으로 돌려준다.
     *
     * <p>쿼리는 한 번이다. 날짜마다 조회하면 한 달치가 30번의 왕복이 되고, 그 30번이
     * 각자 다른 트랜잭션이라 조회 도중 저장된 기록이 어떤 날에는 보이고 어떤 날에는
     * 안 보이는 상태가 만들어진다.
     */
    @Transactional(readOnly = true)
    public List<DailyReportResponse> getRange(Long userId, DateRange range) {
        Set<SkinConcern> concerns = concerns(userId);

        return skinPlateRepository.findInRange(userId, range.from(), range.toExclusive()).stream()
                .collect(Collectors.groupingBy(plate -> plate.getCreatedAt().toLocalDate()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> DailyReportAssembler.of(entry.getKey(), entry.getValue(), concerns))
                .toList();
    }

    /**
     * 고민은 LAZY 컬렉션이라 트랜잭션 안에서 꺼내 복사한다. 밖으로 그대로 내보내면
     * open-in-view: false 환경에서 LazyInitializationException 이다.
     */
    private Set<SkinConcern> concerns(Long userId) {
        return userRepository.findById(userId)
                .map(user -> Set.copyOf(user.getSkinConcerns()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
