package com.jiawa.lyw.itinerary.infrastructure;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.itinerary.application.DefaultItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryAccessPolicy;
import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryCommandHasher;
import com.jiawa.lyw.itinerary.application.ItineraryIdGenerator;
import com.jiawa.lyw.itinerary.application.ItineraryRepository;
import com.jiawa.lyw.itinerary.application.OwnerOnlyItineraryAccessPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@MapperScan(basePackageClasses = ItineraryMapper.class, annotationClass = Mapper.class)
public class ItineraryConfiguration {
    @Bean
    ItineraryRepository itineraryRepository(ItineraryMapper mapper) {
        return new MyBatisItineraryRepository(mapper);
    }

    @Bean
    ItineraryAccessPolicy itineraryAccessPolicy() {
        return new OwnerOnlyItineraryAccessPolicy();
    }

    @Bean
    ItineraryIdGenerator itineraryIdGenerator() {
        return IdUtil::getSnowflakeNextId;
    }

    @Bean
    ItineraryCommandHasher itineraryCommandHasher() {
        return new ItineraryCommandHasher();
    }

    @Bean
    ItineraryApplicationService itineraryApplicationService(
            ItineraryRepository repository,
            ItineraryAccessPolicy accessPolicy,
            ItineraryIdGenerator ids,
            ItineraryCommandHasher hasher,
            Clock clock
    ) {
        return new DefaultItineraryApplicationService(repository, accessPolicy, ids, hasher, clock);
    }
}
