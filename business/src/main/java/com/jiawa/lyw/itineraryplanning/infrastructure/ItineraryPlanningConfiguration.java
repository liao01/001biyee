package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryIdGenerator;
import com.jiawa.lyw.itineraryplanning.application.DefaultItineraryPlanningApplicationService;
import com.jiawa.lyw.itineraryplanning.application.ItineraryPlannerGateway;
import com.jiawa.lyw.itineraryplanning.application.ItineraryPlanningApplicationService;
import com.jiawa.lyw.itineraryplanning.application.PlanningRepository;
import com.jiawa.lyw.itineraryplanning.domain.RevisionProposalValidator;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@ConditionalOnProperty(prefix = "app.ai.itinerary.dify", name = "base-url")
@EnableConfigurationProperties(DifyItineraryPlanningProperties.class)
@MapperScan(basePackageClasses = PlanningMapper.class, annotationClass = Mapper.class)
public class ItineraryPlanningConfiguration {
    @Bean
    RevisionContractParser revisionContractParser(
            ObjectMapper objectMapper,
            DifyItineraryPlanningProperties properties
    ) {
        return new RevisionContractParser(objectMapper, properties.contractVersion());
    }

    @Bean
    RevisionProposalValidator revisionProposalValidator(DifyItineraryPlanningProperties properties) {
        return new RevisionProposalValidator(properties.contractVersion(), properties.maxOperations());
    }

    @Bean
    ItineraryPlannerGateway itineraryPlannerGateway(
            DifyItineraryPlanningProperties properties,
            ObjectMapper objectMapper,
            RevisionContractParser parser
    ) {
        return new DifyItineraryPlannerGateway(properties, objectMapper, parser);
    }

    @Bean
    PlanningRepository planningRepository(
            PlanningMapper mapper,
            ItineraryIdGenerator ids,
            ObjectMapper objectMapper,
            RevisionContractParser parser
    ) {
        return new MyBatisPlanningRepository(mapper, ids, objectMapper, parser);
    }

    @Bean
    ItineraryPlanningApplicationService itineraryPlanningApplicationService(
            PlanningRepository repository,
            ItineraryPlannerGateway gateway,
            RevisionProposalValidator validator,
            ItineraryApplicationService itineraries,
            ItineraryIdGenerator ids,
            Clock clock
    ) {
        return new DefaultItineraryPlanningApplicationService(
                repository, gateway, validator, itineraries, ids, clock
        );
    }
}
