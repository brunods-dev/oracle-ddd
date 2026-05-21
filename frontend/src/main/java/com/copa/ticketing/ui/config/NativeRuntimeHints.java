package com.copa.ticketing.ui.config;

import com.copa.ticketing.ui.client.BackendProperties;
import com.copa.ticketing.ui.client.dto.*;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.lang.Nullable;

public class NativeRuntimeHints implements RuntimeHintsRegistrar {

    private static final Class<?>[] REFLECTION_TYPES = {
            MatchDto.class,
            SectorDto.class,
            SeatDto.class,
            OrderDto.class,
            TicketDto.class,
            DashboardDto.class,
            DashboardDto.TopMatchDto.class,
            DashboardDto.DailySaleDto.class,
            PagedResponse.class,
            BackendProperties.class,
            MatchOptionDto.class,
            SeatRowSummaryDto.class,
            SelloutStartRequest.class,
            SelloutStatusDto.class,
            SelloutStatusDto.SectorStatus.class,
            SelloutStatusDto.Totals.class,
            SelloutStatusDto.StatusMix.class,
            SelloutEventDto.class,
            DashboardStatusDto.class,
            DashboardStatusDto.Summary.class,
            DashboardStatusDto.LabelValue.class,
            DashboardStatusDto.MatchBar.class,
            DashboardStatusDto.HeatBlockItem.class,
            DashboardStatusDto.DashEvent.class,
            HeatwaveAnalyticsDto.class,
            HeatwaveAnalyticsDto.Summary.class,
            HeatwaveAnalyticsDto.TopMatch.class,
            HeatwaveAnalyticsDto.HostCountry.class,
            HeatwaveAnalyticsDto.SectorDemand.class,
            HeatwaveAnalyticsDto.PaymentStatus.class,
            HeatwaveAnalyticsDto.HeatBlock.class,
            HeatwaveAnalyticsDto.LoadStatus.class,
            HeatwaveNlSqlResponseDto.class,
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.reflection().registerType(
                TypeReference.of("org.springframework.core.annotation.TypeMappedAnnotation[]"),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

        for (Class<?> type : REFLECTION_TYPES) {
            hints.reflection().registerType(type, MemberCategory.values());
        }
    }
}
