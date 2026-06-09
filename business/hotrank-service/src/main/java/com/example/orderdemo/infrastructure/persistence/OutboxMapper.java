package com.example.orderdemo.infrastructure.persistence;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OutboxMapper {

    @Insert("INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, published, created_at) " +
            "VALUES (#{aggregateType}, #{aggregateId}, #{eventType}, #{payload}, false, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(OutboxDO outbox);

    @Select("SELECT id, aggregate_type, aggregate_id, event_type, payload, published, created_at FROM outbox WHERE published = false ORDER BY id LIMIT #{limit}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "aggregateType", column = "aggregate_type"),
        @Result(property = "aggregateId", column = "aggregate_id"),
        @Result(property = "eventType", column = "event_type"),
        @Result(property = "payload", column = "payload"),
        @Result(property = "published", column = "published"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<OutboxDO> findUnpublished(@Param("limit") int limit);

    @Update("UPDATE outbox SET published = true WHERE id = #{id}")
    void markPublished(Long id);
}